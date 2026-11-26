#ifndef SPAWN_OVERRIDE_H
#define SPAWN_OVERRIDE_H

#include "types.h"

/*
 * Override del punto de spawn de Mario (flags CLI --spawn-x/-y/-z/-yaw).
 *
 * Definidos en src/pc/pc_main.c, parseados en main().
 * Consumidos en src/game/mario.c (init_mario()), que parchea
 * gMarioSpawnInfo->startPos / startAngle antes de que init_mario los lea.
 *
 * El override se activa solo si llegan al menos --spawn-x y --spawn-z
 * (gSpawnOverride == 1). Si no, el comportamiento es el original (warp del
 * nivel). --spawn-y es opcional: sin ella (gSpawnHasY == 0) se busca el suelo
 * con find_floor(); si no hay suelo valido se cae al warp original.
 *
 * Nota: esto NO forma parte del ABI de rl_state; es solo input por CLI.
 */

extern s8 gSpawnOverride; // 0 = usar el warp del nivel, 1 = override activo
extern s8 gSpawnHasY;     // 0 = auto-floor con find_floor(), 1 = Y explicita
extern f32 gSpawnX;
extern f32 gSpawnY;
extern f32 gSpawnZ;
extern s16 gSpawnYaw;     // ya convertido a unidades s16 (grados * 65536 / 360)

#endif // SPAWN_OVERRIDE_H
