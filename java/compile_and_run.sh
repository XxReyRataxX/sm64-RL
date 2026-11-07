#!/bin/bash
# Compile and launch the SM64 Level Selector (JavaFX)
# Run this script from the java/ directory or the project root.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

JAVAFX_LIB="../javalib/javafx-sdk-25.0.5/lib"
MODS="javafx.controls"

echo "Compiling..."
javac --module-path "$JAVAFX_LIB" --add-modules "$MODS" LevelSelector.java GameLauncher.java

if [ $? -ne 0 ]; then
    echo "Compilation failed."
    exit 1
fi

# Kill any previous instance so X11 focus is clean
pkill -f "java.*LevelSelector" 2>/dev/null
sleep 0.3

echo "Launching selector..."
java --module-path "$JAVAFX_LIB" --add-modules "$MODS" --enable-native-access=javafx.graphics LevelSelector
