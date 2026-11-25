#!/bin/bash
# -----------------------------------------------------------------------------
# sm64-RL — Crea el entorno virtual de Python del proyecto (.venv en la raiz).
#
# Idempotente: puedes ejecutarlo tantas veces como quieras.
#
#   ./lib_helper/setup_venv.sh              # versiones exactas (requirements.lock.txt)
#   ./lib_helper/setup_venv.sh --loose      # resuelve versiones desde pyproject.toml
#   ./lib_helper/setup_venv.sh --recreate   # borra .venv y lo construye de cero
#   ./lib_helper/setup_venv.sh --freeze     # regenera requirements.lock.txt y sale
#
# Por que un venv y no 'pip install' a secas: el Python del sistema esta marcado
# como externally-managed (PEP 668), asi que pip se niega a instalar nada en el.
# Y 'pip install --break-system-packages' escribiria gymnasium y su numpy encima
# del numpy que gestiona apt.
#
# OJO: el directorio .venv NO es portable entre equipos. Lleva rutas absolutas
# y binarios compilados para esta plataforma (posix_ipc y numpy se compilan).
# Esta en .gitignore a proposito. Lo que viaja al repo es este script mas
# requirements.lock.txt; en el otro equipo se vuelve a ejecutar esto.
# -----------------------------------------------------------------------------
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VENV="$ROOT/.venv"
LOCK="$ROOT/requirements.lock.txt"
PY="${PYTHON:-python3}"

MODE="locked"
RECREATE=0
for arg in "$@"; do
    case "$arg" in
        --loose)    MODE="loose" ;;
        --recreate) RECREATE=1 ;;
        --freeze)   MODE="freeze" ;;
        -h|--help)  sed -n '2,20p' "${BASH_SOURCE[0]}"; exit 0 ;;
        *) echo "Opcion desconocida: $arg (prueba --help)" >&2; exit 2 ;;
    esac
done

# --- --freeze: fotografiar el entorno actual y salir -------------------------
if [ "$MODE" = "freeze" ]; then
    if [ ! -x "$VENV/bin/pip" ]; then
        echo "No hay entorno en $VENV todavia. Ejecuta el script sin --freeze." >&2
        exit 1
    fi
    # --exclude-editable deja fuera el propio paquete del proyecto: si acabara
    # en el lock, pip intentaria bajarse 'gymnasium_env' de PyPI y fallaria.
    "$VENV/bin/pip" freeze --exclude-editable > "$LOCK"
    echo "==> $LOCK regenerado ($(grep -c . "$LOCK") paquetes)."
    exit 0
fi

# --- Comprobaciones previas --------------------------------------------------
if ! command -v "$PY" >/dev/null 2>&1; then
    echo "[error] no encuentro '$PY'. Instala Python 3 o exporta PYTHON=/ruta/python3" >&2
    exit 1
fi

echo "==> Python: $("$PY" --version) ($(command -v "$PY"))"

if ! "$PY" -c "import venv, ensurepip" >/dev/null 2>&1; then
    cat >&2 <<EOF
[error] A este Python le falta el modulo venv o ensurepip.
        En Debian/Ubuntu vienen en un paquete aparte:

            sudo apt install python3-venv python3-pip

EOF
    exit 1
fi

# posix-ipc trae extensiones en C: si no hay wheel para esta version de Python,
# pip compila desde fuente y necesita los headers de Python.
if ! "$PY" - <<'EOF' >/dev/null 2>&1
import os, sys, sysconfig
sys.exit(0 if os.path.exists(os.path.join(sysconfig.get_paths()['include'], 'Python.h')) else 1)
EOF
then
    echo "[aviso] no encuentro Python.h. Si la instalacion de posix-ipc falla al"
    echo "        compilar, instala los headers:  sudo apt install python3-dev"
fi

# --- Crear / recrear ---------------------------------------------------------
if [ "$RECREATE" -eq 1 ] && [ -d "$VENV" ]; then
    echo "==> Borrando $VENV"
    rm -rf "$VENV"
fi

if [ -d "$VENV" ]; then
    echo "==> El entorno ya existe en $VENV (se reutiliza; usa --recreate para rehacerlo)"
else
    echo "==> Creando entorno virtual en $VENV"
    "$PY" -m venv "$VENV"
fi

echo "==> Actualizando pip"
"$VENV/bin/python" -m pip install --quiet --upgrade pip

# --- Instalar dependencias ---------------------------------------------------
if [ "$MODE" = "locked" ] && [ -f "$LOCK" ]; then
    echo "==> Instalando versiones exactas de $(basename "$LOCK")"
    "$VENV/bin/pip" install --quiet -r "$LOCK"
    # --no-deps: las dependencias ya las puso el lock, y sin esta bandera pip
    # volveria a resolverlas desde pyproject.toml y podria moverlas de version,
    # que es justo lo que el lock esta evitando.
    "$VENV/bin/pip" install --quiet -e "$ROOT" --no-deps
else
    if [ "$MODE" = "locked" ]; then
        echo "==> No hay $LOCK: resolviendo versiones desde pyproject.toml"
    else
        echo "==> Modo --loose: resolviendo versiones desde pyproject.toml"
    fi
    "$VENV/bin/pip" install --quiet -e "$ROOT"
fi

# --- Verificacion ------------------------------------------------------------
echo "==> Verificando el entorno"
"$VENV/bin/python" - <<'EOF'
import sys
fallos = []
for mod, etiqueta in (('gymnasium', 'gymnasium'), ('posix_ipc', 'posix-ipc'),
                      ('numpy', 'numpy'), ('gymnasium_env', 'gymnasium_env (proyecto)')):
    try:
        m = __import__(mod)
        print(f'    OK  {etiqueta:26s} {getattr(m, "__version__", getattr(m, "VERSION", ""))}')
    except Exception as exc:
        fallos.append(f'    FALLA {etiqueta}: {type(exc).__name__}: {exc}')
if fallos:
    print('\n'.join(fallos))
    sys.exit(1)
EOF

cat <<EOF

==> Entorno listo: $VENV

    El launcher JavaFX lo encuentra solo (GameLauncher.findPython() prefiere
    .venv/bin/python3), asi que no hace falta activarlo para usar la UI.

    Para usarlo a mano:
        source .venv/bin/activate
    O sin activar nada:
        .venv/bin/python3 -m gymnasium_env.runner --help

    Si añades o actualizas una dependencia, vuelve a fijar las versiones:
        ./lib_helper/setup_venv.sh --freeze
EOF
