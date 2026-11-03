#!/bin/bash
# SM64 PC Port — dependency installer
# Run this once before compiling the game or the Java launcher.

set -e

echo "==> Updating package list..."
sudo apt-get update

echo ""
echo "==> Installing SM64 build dependencies..."
sudo apt-get install -y \
    build-essential \
    pkg-config \
    git \
    libglew-dev \
    libsdl2-dev \
    libsdl2-mixer-dev \
    libasound2-dev \
    libpulse-dev \
    python3

echo ""
echo "==> Installing Java launcher dependencies..."
sudo apt-get install -y \
    default-jdk \
    xdotool

echo ""
echo "==> All dependencies installed."
echo "    Build the game with:  make VERSION=us -j\$(nproc)"
echo "    Launch the selector:  cd java && ./compile_and_run.sh"
