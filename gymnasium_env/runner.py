"""
runner — Punto de entrada que une el launcher JavaFX con el lado Python.

Es la frontera Java ↔ Python, y nada más: aquí NO vive ninguna política ni
ninguna recompensa. El launcher (java/LevelSelector.java) lanza UN proceso:

    python3 -u -m gymnasium_env.runner \
        --binary /ruta/build/us_pc/sm64.us \
        --shm-prefix rl_1732556000 --instances 4 \
        --level 9 --star 1 --speed 1.0 [--render] \
        --algo connect --mode train

...y este runner lanza y es dueño de las N instancias del juego. Java NO toca
nunca el binario en modo RL: si lo hiciera, la propiedad del proceso quedaría
partida entre los dos lados y nadie podría garantizar la limpieza de la shm y
los semáforos. Cada instancia usa su propio nombre POSIX `<shm-prefix>_<i>`;
dos instancias con el mismo nombre no arrancan (`sem_open` con `O_EXCL`).

De lo que se encarga:
  - traducir los flags de la UI a la configuración de la conexión
  - repartir un nombre POSIX único por instancia
  - ser dueño del ciclo de vida de los juegos y cerrarlos limpio en SIGTERM
    (el botón "Stop all"), para no dejar shm ni semáforos huérfanos
  - escribir por stdout lo que la terminal del launcher muestra

Salida por stdout, en dos canales sobre el mismo flujo:
  - líneas de texto normales  → se muestran tal cual en la terminal de la UI
  - líneas `@@{json}`         → eventos que la UI parsea
        @@{"ev":"start","instances":4,"algo":"connect"}
        @@{"ev":"ready","inst":0,"shm":"rl_1732556000_0"}
        @@{"ev":"in_level","inst":0,"warmup":169,"pos":[-6558.0,0.0,6464.0]}
        @@{"ev":"state","inst":0,"frame":1200,"pos":[...],"coins":3}
        @@{"ev":"instance_done","inst":0,"frames":3000,"fps":30.1}
        @@{"ev":"error","inst":0,"msg":"..."}
        @@{"ev":"done","stopped":false}

Códigos de salida: 0 ok · 1 error de ejecución · 2 error de uso/entorno.
"""
from __future__ import annotations

import argparse
import json
import os
import signal
import sys
import threading
import time

from gymnasium_env.envs.sm64_connection import SM64Connection

# ---------------------------------------------------------------------------
# Eventos / log — un solo lock para que los hilos no entrelacen líneas.
# ---------------------------------------------------------------------------
_OUT_LOCK = threading.Lock()


def emit(ev: str, **fields) -> None:
    """Escribe un evento legible por la UI (línea `@@` + JSON compacto)."""
    payload = {'ev': ev}
    payload.update(fields)
    line = '@@' + json.dumps(payload, separators=(',', ':'))
    with _OUT_LOCK:
        sys.stdout.write(line + '\n')
        sys.stdout.flush()


def log(msg: str) -> None:
    """Escribe una línea de texto plano para la terminal del launcher."""
    with _OUT_LOCK:
        sys.stdout.write(msg + '\n')
        sys.stdout.flush()


def _pos(s: dict) -> list:
    return [round(s['pos0'], 1), round(s['pos1'], 1), round(s['pos2'], 1)]


# ---------------------------------------------------------------------------
# --algo connect — comprobación del puente, sin política
# ---------------------------------------------------------------------------

def run_connect(idx: int, args: argparse.Namespace, stop: threading.Event) -> None:
    """Abre el puente, espera a que cargue el nivel y lo mantiene vivo.

    No decide nada: manda input neutro (`actionBits = 0`) frame a frame. Eso no
    es una política, es lo que hace falta para que el juego no se quede parado:
    en modo --state-sync el juego avanza solo cuando recibe una acción, así que
    "mantener la conexión abierta" obliga a contestar cada frame.

    Sirve para que el botón Launch haga algo observable mientras no haya
    algoritmo, y como diagnóstico de C ↔ Python ↔ Java cuando algo falle.
    """
    shm_name = f'{args.shm_prefix}_{idx}'
    conn = SM64Connection(
        shm_name=shm_name,
        game_binary=args.binary,
        extra_args=['--level', str(args.level),
                    '--star',  str(args.star),
                    '--speed', f'{args.speed:.1f}'],
        no_render=not args.render,
        timeout=args.step_timeout,
    )

    # El finally no es opcional: sin el close() se queda vivo el proceso del
    # juego (y su shm y sus semáforos) en cuanto algo lance aquí dentro.
    try:
        conn.launch()
        emit('ready', inst=idx, shm=shm_name)
        log(f'[inst {idx}] shm={shm_name} nivel={args.level} estrella={args.star}')

        s = conn.wait_for_level(args.warmup_max_steps)
        emit('in_level', inst=idx, frame=s['frame'],
             warmup=conn.warmup_steps, pos=_pos(s))
        log(f'[inst {idx}] nivel cargado en {conn.warmup_steps} frames '
            f'(health=0x{s["health"]:04x})')

        frames = 0
        t0 = time.monotonic()
        while not stop.is_set():
            if args.max_steps > 0 and frames >= args.max_steps:
                break
            conn.send_action(0)          # input neutro
            s = conn.wait_state()
            frames += 1
            if args.report_every > 0 and frames % args.report_every == 0:
                emit('state', inst=idx, frame=s['frame'], pos=_pos(s),
                     coins=s['numCoins'], health=s['health'],
                     action=f'0x{s["action"]:08x}')

        emit('instance_done', inst=idx, frames=frames,
             fps=round(frames / max(time.monotonic() - t0, 1e-6), 1))
    finally:
        conn.close()


ALGOS = {
    'connect': run_connect,
    # Engancha aquí tus algoritmos: 'ppo': run_ppo, 'dqn': run_dqn, ...
}


# ---------------------------------------------------------------------------
# Ciclo de vida de una instancia
# ---------------------------------------------------------------------------

def run_instance(idx: int, args: argparse.Namespace, stop: threading.Event) -> None:
    try:
        ALGOS[args.algo](idx, args, stop)
    except Exception as exc:                      # noqa: BLE001
        # Una instancia que muere no debe tumbar a las demás, pero sí tiene que
        # verse en la UI: un fallo silencioso aquí parece "va muy lento".
        emit('error', inst=idx, msg=f'{type(exc).__name__}: {exc}')
        log(f'[inst {idx}] ERROR {type(exc).__name__}: {exc}')


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(
        prog='python -m gymnasium_env.runner',
        description='Lanza N instancias de SM64 y las conecta con el lado Python.',
    )
    p.add_argument('--env', default='gymnasium_env/Killer-v0',
                   help='ID del entorno Gymnasium sobre el que entrenar; parte '
                        'del contrato con Java, lo usará el algoritmo. '
                        '"connect" no lo usa: habla con la conexión directamente')
    p.add_argument('--binary', default='./build/us_pc/sm64.us',
                   help='binario del juego (make version=us)')
    p.add_argument('--shm-prefix', default=None,
                   help='prefijo de los nombres POSIX; cada instancia usa '
                        '<prefijo>_<i> (por defecto rl_<timestamp>)')
    p.add_argument('--instances', type=int, default=1)
    p.add_argument('--level', default='9', help='nivel (9 = Bob-omb Battlefield)')
    p.add_argument('--star',  type=int, default=1)
    p.add_argument('--speed', type=float, default=1.0,
                   help='multiplicador de velocidad del juego; es lo que manda '
                        'en el throughput (1.0 ≈ 30 fps, 10.0 ≈ 300 fps)')
    p.add_argument('--render', action='store_true',
                   help='abre la ventana del juego (por defecto headless)')
    p.add_argument('--algo', default='connect', choices=sorted(ALGOS),
                   help='qué ejecutar; "connect" solo mantiene el puente vivo')
    p.add_argument('--mode', default='train', choices=['train', 'eval'],
                   help='metadato: se propaga a los eventos, lo usará el algoritmo')
    p.add_argument('--episodes', type=int, default=0,
                   help='parte del contrato con Java, para los algoritmos; '
                        '"connect" no lo usa')
    p.add_argument('--max-steps', type=int, default=0,
                   help='frames por instancia; <= 0 significa hasta "Stop all"')
    p.add_argument('--warmup-max-steps', type=int, default=1800,
                   help='tope de frames esperando a que cargue el nivel')
    p.add_argument('--report-every', type=int, default=300,
                   help='frames entre eventos "state"; 0 los desactiva')
    p.add_argument('--step-timeout', type=float, default=2.0,
                   help='segundos de espera por frame antes de dar el juego '
                        'por colgado')
    return p


def preflight(args: argparse.Namespace) -> None:
    """Comprueba lo que, si falla, falla mejor aquí que a medio run.

    Acumula TODOS los problemas antes de abortar: si faltan a la vez las
    dependencias y el binario, enseñar solo el primero obliga a dos rondas de
    "arreglar, relanzar, descubrir el siguiente".
    """
    problems: list[str] = []

    try:
        import posix_ipc  # noqa: F401
    except ImportError:
        problems.append('falta el módulo posix_ipc  →  pip install posix-ipc')

    if not os.path.isfile(args.binary):
        problems.append(f'no existe el binario del juego: {args.binary}'
                        '  →  make version=us')
    elif not os.access(args.binary, os.X_OK):
        problems.append(f'el binario no es ejecutable: {args.binary}'
                        f'  →  chmod +x {args.binary}')
    if args.instances < 1:
        problems.append('--instances tiene que ser >= 1')

    if problems:
        raise SystemExit('\n'.join(f'[error] {p}' for p in problems))


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    if args.shm_prefix is None:
        args.shm_prefix = f'rl_{int(time.time())}'

    try:
        preflight(args)
    except SystemExit as exc:
        msg = str(exc)
        log(msg)
        emit('error', msg=msg.replace('\n', ' '))
        return 2

    stop = threading.Event()

    def on_signal(signum, _frame):
        # SIGTERM es la vía normal de parada: el botón "Stop all" del launcher.
        # Solo marcamos el flag; cada hilo cierra su conexión, y close() hace
        # terminate() del juego, que a su vez limpia su shm y sus semáforos en
        # su handler de SIGTERM. Matar esto a lo bruto dejaría objetos POSIX
        # huérfanos y el run siguiente arrancaría desincronizado.
        log(f'[runner] señal {signum} recibida: parando instancias…')
        stop.set()

    signal.signal(signal.SIGTERM, on_signal)
    signal.signal(signal.SIGINT, on_signal)

    log(f'[runner] algo={args.algo} instancias={args.instances} '
        f'modo={args.mode} nivel={args.level} estrella={args.star} '
        f'speed={args.speed:.1f}× render={args.render}')
    emit('start', instances=args.instances, algo=args.algo, mode=args.mode,
         shm_prefix=args.shm_prefix)

    threads = [
        threading.Thread(target=run_instance, args=(i, args, stop),
                         name=f'inst-{i}', daemon=True)
        for i in range(args.instances)
    ]
    for t in threads:
        t.start()

    try:
        # join con timeout en bucle: un join() a secas no se despierta con
        # señales y dejaría el Ctrl+C sin efecto.
        while any(t.is_alive() for t in threads):
            for t in threads:
                t.join(timeout=0.2)
    except KeyboardInterrupt:
        stop.set()

    # Margen para que los hilos corran su finally (cada close() espera hasta 3s
    # a que el juego muera limpio).
    deadline = time.monotonic() + 10.0
    for t in threads:
        t.join(timeout=max(deadline - time.monotonic(), 0.1))

    emit('done', stopped=stop.is_set())
    log('[runner] terminado')
    return 0


if __name__ == '__main__':
    sys.exit(main())
