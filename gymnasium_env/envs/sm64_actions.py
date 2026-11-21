"""
Definición completa de acciones para agentes SM64.

Botones disponibles (según sm64config.txt y include/PR/os_cont.h):
  JUMP    → key_a   (salto, triple salto con secuencia)
  ATTACK  → key_b   (golpe, dive en el aire, slide kick corriendo)
  CROUCH  → key_z   (agacharse; + JUMP = long jump; en aire = ground pound)
  CAM_L   → key_cleft
  CAM_R   → key_cright

Moves que emergen de combinaciones:
  Long jump      : CROUCH + JUMP mientras corre
  Ground pound   : CROUCH en el aire
  Dive           : ATTACK en el aire
  Slide kick     : ATTACK corriendo en el suelo
  Triple salto   : JUMP × 3 en secuencia (el juego lo detecta vía actionState)
  Backflip       : CROUCH breve + JUMP parado
"""

# ---------------------------------------------------------------------------
# Bitmasks N64 (include/PR/os_cont.h)
# ---------------------------------------------------------------------------
JUMP   = 0x8000  # A_BUTTON
ATTACK = 0x4000  # B_BUTTON
CROUCH = 0x0020  # Z_TRIG
CAM_L  = 0x0002  # L_CBUTTONS
CAM_R  = 0x0001  # R_CBUTTONS

# ---------------------------------------------------------------------------
# Vectores de stick (stick_x, stick_y), rango -80..80
# Índices según sm64config.txt: stickup/down/left/right
# ---------------------------------------------------------------------------
STICK_DIRS = [
    (  0,   0),   # 0: neutro
    (  0,  80),   # 1: adelante       (stickup)
    ( 57,  57),   # 2: adelante-dcha  (stickup + stickright)
    ( 80,   0),   # 3: derecha        (stickright)
    ( 57, -57),   # 4: atrás-dcha     (stickdown + stickright)
    (  0, -80),   # 5: atrás          (stickdown)
    (-57, -57),   # 6: atrás-izda     (stickdown + stickleft)
    (-80,   0),   # 7: izquierda      (stickleft)
    (-57,  57),   # 8: adelante-izda  (stickup + stickleft)
]

# Opciones de cámara
CAM_OPTIONS = [0, CAM_L, CAM_R]  # 0: ninguna, 1: izquierda, 2: derecha


def encode_action(stick: int = 0, jump: int = 0, attack: int = 0,
                  crouch: int = 0, camera: int = 0) -> int:
    """
    Convierte acciones semánticas → actionBits uint32 (C lee esto).

      stick  : 0-8  (índice en STICK_DIRS)
      jump   : 0/1  (botón A)
      attack : 0/1  (botón B — dive en aire, golpe en suelo)
      crouch : 0/1  (botón Z — long jump con jump, ground pound en aire)
      camera : 0-2  (ninguna / izquierda / derecha)

    Encoding del uint32:
      bits  0-15 : bitmask de botones N64
      bits 16-23 : stick_x (int8)
      bits 24-31 : stick_y (int8)
    """
    sx, sy = STICK_DIRS[stick]
    buttons = (jump * JUMP) | (attack * ATTACK) | (crouch * CROUCH) | CAM_OPTIONS[camera]
    return (buttons & 0xFFFF) | ((sx & 0xFF) << 16) | ((sy & 0xFF) << 24)
