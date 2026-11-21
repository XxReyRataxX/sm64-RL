"""
Conexión Python ↔ SM64 via POSIX shared memory + semáforos.

Protocolo (modo --state-sync):
  Juego publica estado → sem_post(state) → Python lee → escribe actionBits
  → sem_post(action) → Juego avanza un frame → repite.

Dependencia: pip install posix-ipc
"""
import struct
import subprocess
import time
import mmap

import posix_ipc

# ---------------------------------------------------------------------------
# Layout del struct RLStateShm  (src/pc/rl_state.h)
#
# uint32 magic, version, seq, frame
# float  pos[3], vel[3], forwardVel
# int32  faceAngle[3]
# uint32 action, prevAction, actionState, actionTimer
# int32  numCoins, health, floorAngle
# uint32 flags, input
# uint8  isDead, starCollected, inLevel, inAir
# uint32 actionBits, requestedFrame
# ---------------------------------------------------------------------------
SHM_FMT  = '<IIIIfffffffiiiIIIIiiiIIBBBBI'
SHM_SIZE = struct.calcsize(SHM_FMT)
SHM_MAGIC = 0x534D3634  # 'SM64'

_FIELDS = [
    'magic', 'version', 'seq', 'frame',
    'pos0', 'pos1', 'pos2',
    'vel0', 'vel1', 'vel2',
    'forwardVel',
    'faceAngle0', 'faceAngle1', 'faceAngle2',
    'action', 'prevAction', 'actionState', 'actionTimer',
    'numCoins', 'health', 'floorAngle',
    'flags', 'input',
    'isDead', 'starCollected', 'inLevel', 'inAir',
    'actionBits',
]
_IDX = {name: i for i, name in enumerate(_FIELDS)}

# Offset en bytes de actionBits dentro del struct
ACTIONBITS_OFFSET = struct.calcsize('<IIIIfffffffiiiIIIIiiiIIBBBB')


def read_state(buf) -> dict:
    """Lee el estado del juego con reintentos de seqlock."""
    for _ in range(200):
        raw = bytes(buf[:SHM_SIZE])
        vals = struct.unpack_from(SHM_FMT, raw)
        seq1 = vals[_IDX['seq']]
        if seq1 & 1:
            continue  # escritura en curso, reintentar
        vals2 = struct.unpack_from(SHM_FMT, bytes(buf[:SHM_SIZE]))
        if vals2[_IDX['seq']] == seq1:
            return {name: vals2[i] for i, name in enumerate(_FIELDS)}
    return {name: vals[i] for i, name in enumerate(_FIELDS)}


def write_action(buf, action_bits: int):
    """Escribe actionBits en el bloque compartido."""
    struct.pack_into('<I', buf, ACTIONBITS_OFFSET, action_bits & 0xFFFFFFFF)


class SM64Connection:
    """Gestiona el ciclo de vida del proceso del juego y la shm."""

    def __init__(self, shm_name: str, game_binary: str,
                 extra_args: list = None, timeout: float = 2.0):
        self.shm_name    = shm_name
        self.game_binary = game_binary
        self.extra_args  = extra_args or []
        self.timeout     = timeout

        self._proc      = None
        self._shm_obj   = None
        self._buf       = None
        self._state_sem = None
        self._action_sem = None

    # ------------------------------------------------------------------

    def launch(self):
        """Lanza el juego y espera a que la shm esté lista."""
        args = [
            self.game_binary,
            '--no-render',
            '--state-shm', self.shm_name,
            '--state-sync',
        ] + self.extra_args

        self._proc = subprocess.Popen(args)

        shm_path = f'/sm64_state_{self.shm_name}'
        for _ in range(50):
            try:
                self._shm_obj = posix_ipc.SharedMemory(shm_path)
                break
            except posix_ipc.ExistentialError:
                time.sleep(0.1)
        else:
            self._proc.terminate()
            raise RuntimeError(f"Timeout esperando shm {shm_path}")

        self._buf = mmap.mmap(self._shm_obj.fd, SHM_SIZE)
        self._shm_obj.close_fd()

        self._state_sem  = posix_ipc.Semaphore(f'/sm64_state_{self.shm_name}')
        self._action_sem = posix_ipc.Semaphore(f'/sm64_action_{self.shm_name}')

    def close(self):
        """Cierra la conexión y termina el proceso."""
        if self._buf:
            self._buf.close()
            self._buf = None
        if self._proc and self._proc.poll() is None:
            self._proc.terminate()
            self._proc.wait(timeout=3)
            self._proc = None

    def is_alive(self) -> bool:
        return self._proc is not None and self._proc.poll() is None

    # ------------------------------------------------------------------

    def wait_state(self) -> dict:
        """Bloquea hasta recibir nuevo estado del juego."""
        self._state_sem.acquire(timeout=self.timeout)
        return read_state(self._buf)

    def send_action(self, action_bits: int):
        """Envía una acción al juego y lo desbloquea."""
        write_action(self._buf, action_bits)
        self._action_sem.release()
