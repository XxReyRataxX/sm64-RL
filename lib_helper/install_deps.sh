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
    python3 \
    python3-venv \
    python3-pip \
    python3-dev

echo ""
echo "==> Installing Java launcher dependencies..."
sudo apt-get install -y \
    default-jdk \
    xdotool

echo ""
echo "==> Installing Python RL dependencies..."
# En un venv, NO con 'pip install' contra el Python del sistema: en Ubuntu el
# interprete del sistema esta marcado como externally-managed (PEP 668) y pip se
# niega a tocarlo. El script es idempotente y fija las versiones del lock.
"$(dirname "${BASH_SOURCE[0]}")/setup_venv.sh"

echo ""
echo "==> All dependencies installed."
echo "    Build the game with:  make VERSION=us -j\$(nproc)"
echo "    Launch the selector:  cd java && ./compile_and_run.sh"
