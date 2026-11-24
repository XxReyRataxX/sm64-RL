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
# uint32 magic, version, frame
# float  pos[3], vel[3], forwardVel
# int32  faceAngle[3]
# uint32 action, prevAction, actionState, actionTimer
# int32  numCoins, health, floorAngle
# uint32 flags, input
# uint8  isDead, starCollected, inLevel, inAir
# uint32 actionBits
#
# No hay seqlock: la sincronizacion la dan los semaforos POSIX (--state-sync),
# que garantizan que el struct ya esta escrito por completo cuando Python lee.
# ---------------------------------------------------------------------------
SHM_FMT  = '<IIIfffffffiiiIIIIiiiIIBBBBI'
SHM_SIZE = struct.calcsize(SHM_FMT)
SHM_MAGIC = 0x534D3634  # 'SM64'
SHM_VERSION = 2         # RL_STATE_VERSION en src/pc/rl_state.h

_FIELDS = [
    'magic', 'version', 'frame',
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
ACTIONBITS_OFFSET = struct.calcsize('<IIIfffffffiiiIIIIiiiIIBBBB')


def read_state(buf) -> dict:
    """Lee el estado del juego desde la shm.

    Lectura directa: el semaforo `state` solo se postea despues de que C haya
    terminado de escribir el struct, asi que nunca se observa a medio escribir.
    """
    vals = struct.unpack_from(SHM_FMT, bytes(buf[:SHM_SIZE]))
    return {name: vals[i] for i, name in enumerate(_FIELDS)}


def write_action(buf, action_bits: int):
    """Escribe actionBits en el bloque compartido."""
    struct.pack_into('<I', buf, ACTIONBITS_OFFSET, action_bits & 0xFFFFFFFF)


class SM64Connection:
    """Gestiona el ciclo de vida del proceso del juego y la shm."""

    def __init__(self, shm_name: str, game_binary: str,
                 extra_args: list = None, timeout: float = 2.0,
                 startup_timeout: float = 15.0):
        self.shm_name        = shm_name
        self.game_binary     = game_binary
        self.extra_args      = extra_args or []
        self.timeout         = timeout
        self.startup_timeout = startup_timeout

        # Nombres de los objetos POSIX (deben coincidir con los prefijos de
        # src/pc/rl_state.h: RL_SHM_PREFIX / RL_*_SEM_PREFIX).
        self._shm_path        = f'/sm64_state_{shm_name}'
        self._state_sem_path  = f'/sm64_state_{shm_name}'
        self._action_sem_path = f'/sm64_action_{shm_name}'

        self._proc      = None
        self._shm_obj   = None
        self._buf       = None
        self._state_sem = None
        self._action_sem = None

    # ------------------------------------------------------------------

    def launch(self):
        """Lanza el juego y espera a que la shm esté publicada.

        El juego crea la shm con `shm_open`, que la deja a tamaño 0 antes del
        `ftruncate` y del `memset`, y solo despues crea los semaforos. Por eso
        NO basta con esperar a que el fichero exista: C escribe `magic` como
        ULTIMA operacion de su init (ver el contrato en src/pc/rl_state.c), asi
        que `magic == SHM_MAGIC` es la unica senal fiable de "todo listo":
        tamanio final + struct inicializado + semaforos creados.
        """
        args = [
            self.game_binary,
            '--no-render',
            '--state-shm', self.shm_name,
            '--state-sync',
        ] + self.extra_args

        self._proc = subprocess.Popen(args)

        try:
            head = self._wait_for_shm()
        except Exception:
            self.close()
            raise

        if head['version'] != SHM_VERSION:
            self.close()
            raise RuntimeError(
                f"ABI de shm incompatible: version={head['version']} "
                f"(esperado {SHM_VERSION}). Recompila el juego con "
                "`make version=us`."
            )

        # Garantizado por el contrato de `magic`: si la shm esta publicada, los
        # semaforos ya existen, asi que estas dos aperturas no pueden competir
        # con la creacion en C.
        self._state_sem  = posix_ipc.Semaphore(self._state_sem_path)
        self._action_sem = posix_ipc.Semaphore(self._action_sem_path)

    def _wait_for_shm(self) -> dict:
        """Hace polling hasta que la shm esta publicada (`magic` escrito).

        Reintenta el ciclo completo abrir + mapear + leer `magic`, porque cada
        paso puede caer en una ventana distinta del init de C:
          - antes del ftruncate : la shm mide 0 bytes y el mmap falla
          - antes del memset    : el mapeo funciona pero magic todavia vale 0
        """
        deadline = time.monotonic() + self.startup_timeout
        while time.monotonic() < deadline:
            if self._proc.poll() is not None:
                raise RuntimeError(
                    f"El juego murio durante el arranque "
                    f"(codigo {self._proc.returncode}) sin publicar "
                    f"{self._shm_path}"
                )

            try:
                shm_obj = posix_ipc.SharedMemory(self._shm_path)
            except posix_ipc.ExistentialError:
                time.sleep(0.02)   # C aun no ha llegado al shm_open
                continue

            try:
                buf = mmap.mmap(shm_obj.fd, SHM_SIZE)
            except ValueError:
                # La shm existe pero C aun no ha hecho ftruncate: mide 0 bytes.
                time.sleep(0.02)
                continue
            finally:
                shm_obj.close_fd()

            head = read_state(buf)
            if head['magic'] != SHM_MAGIC:
                # Mapeada pero sin publicar todavia (magic sigue a 0).
                buf.close()
                time.sleep(0.02)
                continue

            self._shm_obj = shm_obj
            self._buf     = buf
            return head

        raise RuntimeError(
            f"Timeout de {self.startup_timeout}s esperando a que el juego "
            f"publique {self._shm_path}"
        )

    def close(self):
        """Cierra la conexión, termina el proceso y limpia los objetos POSIX."""
        if self._buf is not None:
            self._buf.close()
            self._buf = None
        self._shm_obj = None

        for sem in (self._state_sem, self._action_sem):
            if sem is not None:
                try:
                    sem.close()
                except posix_ipc.Error:
                    pass
        self._state_sem  = None
        self._action_sem = None

        if self._proc is not None:
            if self._proc.poll() is None:
                # El juego instala un handler de SIGTERM que limpia la shm y
                # los semaforos; le damos margen para que corra.
                self._proc.terminate()
                try:
                    self._proc.wait(timeout=3)
                except subprocess.TimeoutExpired:
                    self._proc.kill()
                    self._proc.wait(timeout=3)
            self._proc = None

        # Cinturon y tirantes: si el juego murio por SIGKILL no corrio ningun
        # handler, y un semaforo huerfano conserva su contador. `sem_open` con
        # O_CREAT ignora el valor inicial si el objeto ya existe, asi que el
        # run siguiente heredaria ese contador y el handshake se
        # desincronizaria en silencio. Mejor desvincular aqui tambien.
        self._unlink_ipc()

    def _unlink_ipc(self):
        """Desvincula shm y semaforos. Idempotente: si ya no estan, no pasa nada."""
        try:
            posix_ipc.unlink_shared_memory(self._shm_path)
        except posix_ipc.ExistentialError:
            pass
        for path in (self._state_sem_path, self._action_sem_path):
            try:
                posix_ipc.unlink_semaphore(path)
            except posix_ipc.ExistentialError:
                pass

    def is_alive(self) -> bool:
        return self._proc is not None and self._proc.poll() is None

    # ------------------------------------------------------------------

    def wait_state(self) -> dict:
        """Bloquea hasta recibir nuevo estado del juego.

        Lanza RuntimeError si el juego murio: su shutdown pone `magic` a 0 antes
        del munmap, y nuestro mapeo sobrevive al shm_unlink, asi que sin ese
        check leeriamos datos rancios indefinidamente.
        """
        self._state_sem.acquire(timeout=self.timeout)
        s = read_state(self._buf)
        if s['magic'] != SHM_MAGIC:
            raise RuntimeError(
                "El juego cerro la shm (magic=0): el proceso ha terminado."
            )
        return s

    def send_action(self, action_bits: int):
        """Envía una acción al juego y lo desbloquea."""
        write_action(self._buf, action_bits)
        self._action_sem.release()
