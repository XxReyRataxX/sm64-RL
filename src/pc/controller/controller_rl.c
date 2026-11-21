#include <ultra64.h>

#include "controller_api.h"
#include "controller_rl.h"

#include "../rl_state.h"

// -----------------------------------------------------------------------------
// controller_rl — Backend de input sintético para el entorno de RL.
//
// Python escribe la acción en la shm; rl_state_publish() la decodifica en los
// globals gRLButtons / gRLStickX / gRLStickY. Este backend simplemente los
// copia a la OSContPad que el juego lee cada frame.
//
// Cuando gRLActive == 0 el backend es un no-op total, de modo que el teclado y
// el gamepad siguen funcionando con normalidad (ver controller_entry_point.c).
// -----------------------------------------------------------------------------

static void rl_init(void) {
    // No-op: la inicialización real (shm + semáforos) la hace rl_state_init()
    // desde pc_main.c antes de que arranque el loop del juego.
}

static void rl_read(OSContPad *pad) {
    if (!gRLActive) {
        return;
    }

    // Sobrescritura total (no OR): la acción del agente es la única fuente de
    // input durante el entrenamiento, así que un botón no pedido debe quedar a 0.
    pad->button = (u16) gRLButtons;
    pad->stick_x = (s8) gRLStickX;
    pad->stick_y = (s8) gRLStickY;
    pad->errnum = 0;
}

struct ControllerAPI controller_rl = {
    rl_init,
    rl_read
};
