"""
Entorno Gymnasium para el agente Killer de SM64 (Bob-omb Battlefield).
Objetivo: eliminar enemigos.

Action space — MultiDiscrete([9, 2, 2, 3]) = 108 acciones
  dim 0: stick     — 9 direcciones (neutro + 8 direcciones)
  dim 1: saltar    — A (0/1)
  dim 2: atacar    — B (0/1)
  dim 3: cámara    — 0 ninguna / 1 izquierda / 2 derecha

Observation space — Box(16,) float32
  pos(3), vel(3), forwardVel, sin(yaw), cos(yaw),
  coins, health, isDead, starCollected, inLevel, inAir, frame
"""
import numpy as np
import gymnasium as gym
from gymnasium import spaces

from gymnasium_env.envs.sm64_connection import SM64Connection
from gymnasium_env.envs.sm64_actions import encode_action

OBS_DIM = 16


def build_obs(s: dict) -> np.ndarray:
    yaw_rad = s['faceAngle1'] * (2 * np.pi / 65536.0)
    return np.array([
        s['pos0'], s['pos1'], s['pos2'],
        s['vel0'], s['vel1'], s['vel2'],
        s['forwardVel'],
        np.sin(yaw_rad), np.cos(yaw_rad),
        float(s['numCoins']),
        float(s['health']) / 0x880,
        float(s['isDead']),
        float(s['starCollected']),
        float(s['inLevel']),
        float(s['inAir']),
        float(s['frame']),
    ], dtype=np.float32)


class SM64KillerEnv(gym.Env):
    metadata = {'render_modes': ['human'], 'render_fps': 30}

    def __init__(self, shm_name: str = 'rl0',
                 game_binary: str = './build/us_pc/sm64.us',
                 render_mode=None):
        super().__init__()
        self.render_mode = render_mode
        self._conn = SM64Connection(shm_name=shm_name, game_binary=game_binary)
        self._prev_coins = 0

        # stick × saltar × atacar × cámara
        self.action_space = spaces.MultiDiscrete([9, 2, 2, 3])
        self.observation_space = spaces.Box(
            low=-np.inf, high=np.inf, shape=(OBS_DIM,), dtype=np.float32
        )

    def reset(self, seed=None, options=None):
        super().reset(seed=seed)
        if not self._conn.is_alive():
            self._conn.launch()
        s = self._conn.wait_state()
        self._prev_coins = s['numCoins']
        return build_obs(s), self._build_info(s)

    def step(self, action: np.ndarray):
        bits = encode_action(
            stick=int(action[0]),
            jump=int(action[1]),
            attack=int(action[2]),
            camera=int(action[3]),
            # crouch no usado en killer — disponible en sm64_actions para dodger
        )
        self._conn.send_action(bits)
        s = self._conn.wait_state()

        reward     = self._compute_reward(s)
        terminated = bool(s['isDead']) or bool(s['starCollected'])
        self._prev_coins = s['numCoins']
        return build_obs(s), reward, terminated, False, self._build_info(s)

    def close(self):
        self._conn.close()

    def render(self):
        pass

    def _compute_reward(self, s: dict) -> float:
        if s['starCollected']:
            return 1000.0
        if s['isDead']:
            return -100.0

        reward = -0.1  # penalización por frame

        coin_delta = s['numCoins'] - self._prev_coins
        if coin_delta > 0:
            reward += coin_delta * 10.0  # los enemigos dan monedas al morir

        return reward

    def _build_info(self, s: dict) -> dict:
        return {
            'frame':         s['frame'],
            'pos':           (s['pos0'], s['pos1'], s['pos2']),
            'health':        s['health'],
            'numCoins':      s['numCoins'],
            'isDead':        bool(s['isDead']),
            'starCollected': bool(s['starCollected']),
            'inAir':         bool(s['inAir']),
            'action':        hex(s['action']),
        }
