#!/bin/bash
# Compile and launch the SM64 Level Selector
# Run this script from the java/ directory or the project root.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "Compiling..."
javac LevelSelector.java GameLauncher.java

if [ $? -ne 0 ]; then
    echo "Compilation failed."
    exit 1
fi

# Kill any previous instance so X11 focus is clean
pkill -f "java LevelSelector" 2>/dev/null
sleep 0.3

echo "Launching selector..."
java LevelSelector
