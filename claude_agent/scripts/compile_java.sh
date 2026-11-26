#!/bin/bash
# Compila el launcher JavaFX SIN lanzar la ventana.
# Pensado para verificar cambios en java/ sin abrir UI (CI, agentes, WSL sin X11).
# Usa la misma JAVAFX_LIB y los mismos módulos que java/compile_and_run.sh.
set -u

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT/java" || exit 1

JAVAFX_LIB="../javalib/javafx-sdk-25.0.5/lib"
MODS="javafx.controls"

if [ ! -d "$JAVAFX_LIB" ]; then
    echo "JavaFX SDK no encontrado en java/$JAVAFX_LIB"
    exit 1
fi

# Todos los .java del directorio: así añadir un fichero nuevo no obliga a tocar esto.
mapfile -t SRC < <(ls -1 *.java)
echo "Compilando: ${SRC[*]}"
javac -Xlint:all --module-path "$JAVAFX_LIB" --add-modules "$MODS" "${SRC[@]}"
rc=$?
[ $rc -eq 0 ] && echo "OK: compilación limpia." || echo "FALLO: revisa los errores de arriba."
exit $rc
