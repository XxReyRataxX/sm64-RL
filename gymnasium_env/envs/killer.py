"""
Entorno Gymnasium para el agente Killer de SM64 (Bob-omb Battlefield).
Objetivo: eliminar enemigos.

Action space - MultiDiscrete([9, 2, 2, 3]) = 108 acciones
  dim 0: stick     - 9 direcciones (neutro + 8 direcciones)
  dim 1: saltar    - A (0/1)
  dim 2: atacar    - B (0/1)
  dim 3: camara    - 0 ninguna / 1 izquierda / 2 derecha

Observation space - Box(96,) float32 normalizado aprox a [-1, 1]
  Mario (16 features):
    pos/10000 (3), vel/100 (3), forwardVel/100 (1),
    sin(yaw), cos(yaw),
    coins/100, health/0x880, isDead, starCollected, inLevel, inAir,
    frame/10000
  Enemigos (16 slots x 5 features = 80):
    type/6, active, min(dist/10000, 1), sin(angle), cos(angle)
"""
import numpy as np
import gymnasium as gym
from gymnasium import spaces

from gymnasium_env.envs.sm64_connection import SM64Connection, RL_MAX_ENEMIES
from gymnasium_env.envs.sm64_actions import encode_action

MARIO_OBS_DIM   = 16
ENEMY_FEATS     = 5
ENEMY_OBS_DIM   = RL_MAX_ENEMIES * ENEMY_FEATS   # 80
OBS_DIM         = MARIO_OBS_DIM + ENEMY_OBS_DIM  # 96

DEFAULT_MAX_EPISODE_STEPS = 3000   # env steps (tras frame_skip)
DEFAULT_FRAME_SKIP        = 4


def _norm_angle_s16(angle: int):
    """angulo s16 -> (sin, cos) en [-1, 1]."""
    rad = angle * (2 * np.pi / 65536.0)
    return np.sin(rad), np.cos(rad)


def build_obs(s: dict) -> np.ndarray:
    yaw_sin, yaw_cos = _norm_angle_s16(s['faceAngle1'])

    mario = [
        s['pos0'] / 10000.0, s['pos1'] / 10000.0, s['pos2'] / 10000.0,
        s['vel0'] / 100.0,   s['vel1'] / 100.0,   s['vel2'] / 100.0,
        s['forwardVel'] / 100.0,
        yaw_sin, yaw_cos,
        float(s['numCoins']) / 100.0,
        float(s['health']) / 0x880,
        float(s['isDead']),
        float(s['starCollected']),
        float(s['inLevel']),
        float(s['inAir']),
        float(s['frame']) / 10000.0,
    ]

    enemies = []
    for i in range(RL_MAX_ENEMIES):
        etype   = s[f'e{i}_type']
        active  = s[f'e{i}_active']
        dist    = s[f'e{i}_dist']
        angle   = s[f'e{i}_angle']
        a_sin, a_cos = _norm_angle_s16(angle)
        enemies.extend([
            etype / 6.0,
            float(active),
            float(min(dist / 10000.0, 1.0)),
            a_sin,
            a_cos,
        ])

    return np.asarray(mario + enemies, dtype=np.float32)


class SM64KillerEnv(gym.Env):
    metadata = {'render_modes': ['human'], 'render_fps': 30}

    def __init__(self, shm_name: str = 'rl0',
                 game_binary: str = './build/us_pc/sm64.us',
                 render_mode=None,
                 frame_skip: int = DEFAULT_FRAME_SKIP,
                 max_episode_steps: int = DEFAULT_MAX_EPISODE_STEPS,
                 warmup_max_steps: int = 1800):
        super().__init__()
        self.render_mode        = render_mode
        self._conn              = SM64Connection(shm_name=shm_name,
                                                 game_binary=game_binary)
        self._frame_skip        = max(1, int(frame_skip))
        self._max_episode_steps = int(max_episode_steps)
        self._warmup_max_steps  = int(warmup_max_steps)

        self._prev_coins = 0
        self._steps      = 0
        self._last_obs   = np.zeros(OBS_DIM, dtype=np.float32)
        self._last_info  = {}

        # stick x saltar x atacar x camara
        self.action_space = spaces.MultiDiscrete([9, 2, 2, 3])
        # Rango finito - SB3 avisa si es +/-inf. Dejo margen (-10, 10) porque
        # las normalizaciones no son estrictas (pos de Mario puede salir algo).
        self.observation_space = spaces.Box(
            low=-10.0, high=10.0, shape=(OBS_DIM,), dtype=np.float32
        )

    # ------------------------------------------------------------------

    def reset(self, seed=None, options=None):
        super().reset(seed=seed)

        # Unica via fiable hoy: relanzar el proceso. El C no soporta "respawn"
        # del nivel, y tras un step() terminal queda bloqueado en sem_wait.
        self._conn.close()
        self._conn.launch()

        s = self._conn.wait_for_level(self._warmup_max_steps)

        self._steps      = 0
        self._prev_coins = s['numCoins']
        obs              = build_obs(s)
        info             = self._build_info(s)
        self._last_obs   = obs
        self._last_info  = info
        return obs, info

    def step(self, action):
        bits = encode_action(
            stick=int(action[0]),
            jump=int(action[1]),
            attack=int(action[2]),
            camera=int(action[3]),
        )

        total_reward = 0.0
        terminated   = False
        truncated    = False
        s            = None

        for _ in range(self._frame_skip):
            try:
                self._conn.send_action(bits)
                s = self._conn.wait_state()
            except RuntimeError:
                # Timeout o juego muerto: truncamos con la ultima obs valida.
                # Propagar la excepcion mata al learner (SB3 no la captura).
                return (self._last_obs, total_reward, False, True,
                        dict(self._last_info))

            total_reward += self._compute_reward(s)
            self._prev_coins = s['numCoins']
            self._steps += 1

            if bool(s['isDead']) or bool(s['starCollected']):
                terminated = True
                break
            if self._steps >= self._max_episode_steps:
                truncated = True
                break

        obs             = build_obs(s)
        info            = self._build_info(s)
        self._last_obs  = obs
        self._last_info = info
        return obs, float(total_reward), terminated, truncated, info

    def close(self):
        self._conn.close()

    def render(self):
        pass

    # ------------------------------------------------------------------

    def _compute_reward(self, s: dict) -> float:
        if s['starCollected']:
            return 1000.0
        if s['isDead']:
            return -100.0

        reward = -0.1  # penalizacion por frame

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
            'numEnemies':    s['numEnemies'],
        }
