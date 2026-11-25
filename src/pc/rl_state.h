#ifndef RL_STATE_H
#define RL_STATE_H

// -----------------------------------------------------------------------------
// rl_state — Exportación del estado interno del juego a memoria compartida POSIX
// para el entorno de Reinforcement Learning (Gymnasium) en Python.
//
// Se usan tipos de ancho fijo (<stdint.h>) en lugar de los aliases del codebase
// (s8/s16/f32) porque el layout del struct forma parte del ABI que Python lee
// vía `struct`/`mmap`. No reordenar ni insertar campos sin bumpear RL_STATE_VERSION.
//
// ESTADO TRANSITORIO DE CARGA — NO ES UN CASO RARO: en Bob-omb Battlefield el
// nivel tarda unos ~169 frames en cargar, y durante todos ellos Mario todavía no
// existe. `inLevel` es la bandera que dice si te puedes creer el resto del
// struct: hasta que valga 1, ignora los demás campos. Los campos crudos (`pos`,
// `health`, `action`…) son un espejo honesto de `gMarioState` y durante la carga
// valen 0 de verdad: no están "mal", es que Mario aún no ha nacido.
// -----------------------------------------------------------------------------

#include <stdint.h>

#define RL_STATE_MAGIC   0x534D3634u  // 'S','M','6','4'
#define RL_STATE_VERSION 2u

// Nombres de los objetos POSIX (se les concatena el <name> pasado por CLI).
#define RL_SHM_PREFIX        "/sm64_state_"
#define RL_STATE_SEM_PREFIX  "/sm64_state_"
#define RL_ACTION_SEM_PREFIX "/sm64_action_"

struct RLStateShm {
    uint32_t magic;          // RL_STATE_MAGIC
    uint32_t version;        // RL_STATE_VERSION
    uint32_t frame;          // gGlobalTimer

    float    pos[3];         // gMarioState->pos
    float    vel[3];         // gMarioState->vel
    float    forwardVel;     // gMarioState->forwardVel
    int32_t  faceAngle[3];   // gMarioState->faceAngle (s16 -> int32_t)

    uint32_t action;
    uint32_t prevAction;
    uint32_t actionState;
    uint32_t actionTimer;

    int32_t  numCoins;
    int32_t  health;

    int32_t  floorAngle;

    uint32_t flags;
    uint32_t input;

    // --- Banderas derivadas ---
    // CONTRATO: `inLevel` dice si te puedes creer el resto de los derivados.
    // Los otros tres solo son validos cuando inLevel != 0; fuera del nivel valen
    // 0 por construccion (estan guardados por inLevel en rl_state_publish()).
    uint8_t  isDead;         // derivado: inLevel && (health < 0x100 || action en set de muerte)
    uint8_t  starCollected;  // derivado: inLevel && action en set de star dance
    uint8_t  inLevel;        // derivado: nivel cargado y Mario instanciado
    uint8_t  inAir;          // derivado: inLevel && action & ACT_FLAG_AIR (0x00000800)
    // sin _pad: los 4 uint8_t cubren exactamente 4 bytes, alineado a uint32_t

    // --- Sincronización para step(): Python escribe aquí ---
    uint32_t actionBits;     // bitmask de la acción que Python quiere ejecutar
};

// -----------------------------------------------------------------------------
// Codificación de `actionBits` (escrito por Python, leído por el juego)
//
//   bits  0..15 : botones N64 crudos (valores CONT_* / A_BUTTON, B_BUTTON, ...)
//   bits 16..23 : stick_x  como int8  (-80..80)
//   bits 24..31 : stick_y  como int8  (-80..80)
// -----------------------------------------------------------------------------
#define RL_ACTION_BUTTONS(bits) ((uint16_t)((bits) & 0xFFFFu))
#define RL_ACTION_STICK_X(bits) ((int8_t)(((bits) >> 16) & 0xFFu))
#define RL_ACTION_STICK_Y(bits) ((int8_t)(((bits) >> 24) & 0xFFu))

// Última acción recibida de Python, ya decodificada. Un backend de controlador
// puede consumir estos globals cuando gRLActive != 0.
extern int      gRLActive;      // 1 si rl_state_init() tuvo éxito
extern int      gRLSyncActive;  // 1 si además está en modo semáforos (--state-sync)
extern uint32_t gRLActionBits;
extern uint16_t gRLButtons;
extern int8_t   gRLStickX;
extern int8_t   gRLStickY;

// Abre/crea la shm `/sm64_state_<name>`. Si sync_mode != 0 abre además los
// semáforos `/sm64_state_<name>` y `/sm64_action_<name>`. Registra atexit().
// Devuelve 0 en éxito, -1 en error (el juego sigue corriendo sin export).
int  rl_state_init(const char *name, int sync_mode);

// Publica el estado actual en la shm. En modo sync: sem_post(state) y luego
// sem_wait(action), aplicando `actionBits` a los globals gRL*.
void rl_state_publish(void);

// munmap + shm_unlink (+ sem_close/sem_unlink en modo sync). Idempotente.
void rl_state_shutdown(void);

#endif // RL_STATE_H
