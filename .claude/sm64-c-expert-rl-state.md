> Fichero separado de `sm64-c-expert-config.md` porque documenta un subsistema
> aparte (el puente IPC con Python: shm POSIX, semaforos, ABI, banderas
> derivadas) que no hace falta cargar para tareas normales de niveles, args CLI
> o build. **Leer esto en vez del fichero principal** cuando la tarea toque
> `src/pc/rl_state.*`, `src/pc/controller/controller_rl.c`, los flags
> `--state-shm`/`--state-sync`, o el lado Python del bridge
> (`gymnasium_env/envs/sm64_connection.py`, `gymnasium_env/runner.py`).

---

# El Subsistema `rl_state` (Bridge C↔Python)

> Pieza central del TFG: exporta `gMarioState` a memoria compartida POSIX y recibe
> de vuelta la acción del agente. Archivos: `src/pc/rl_state.h`, `src/pc/rl_state.c`,
> hook en `src/pc/pc_main.c`, backend de input en
> `src/pc/controller/controller_rl.c` (+ registro en `controller_entry_point.c`).

### 9.1 Args CLI que parsea `main()` (`src/pc/pc_main.c:264-287`)

| Arg CLI | Destino | Tipo | Notas |
|---------|---------|------|-------|
| `--state-shm <name>` | `sRLShmName` (`static const char *`, `pc_main.c:55`) | — | **No es un global**: es file-scope static. Activa todo el subsistema |
| `--state-sync` | `sRLSyncMode` (`static int`, `pc_main.c:56`) | — | Modo semáforos (handshake bloqueante) |
| `--no-render` | `gNoRender` | `s8` | Headless: fuerza `gfx_dummy_renderer_api` + `gfx_dummy_wm_api` (`pc_main.c:179`) |
| `--level <n>` | `gAutoStartLevelNum` (+ `gDebugLevelSelect = 1`) | `s8` | Ver §2 |
| `--star <n>` | `gAutoStartActNum` | `s8` | clamp 1–6 |
| `--speed <f>` | `gSpeedMultiplier` | `float` | clamp **1.0–100.0**. La UI lo expone con un Spinner 1–100 — ver §2 |
| `--spawn-x/-y/-z/-yaw` | `gSpawnX/Y/Z`, `gSpawnYaw` | `f32`/`s16` | Override del spawn de Mario — ver §11 |

Ninguno de ellos usa getopt: `strcmp` + `atoi`/`atof`, igual que el resto.

### 9.2 Ciclo de vida y hook del frame

```
main()                      → parsea argv
main_func()                 → si sRLShmName != NULL: rl_state_init(sRLShmName, sRLSyncMode)
                              (pc_main.c:236; si falla pone sRLShmName = NULL y el
                               juego sigue corriendo SIN export, no aborta)
while(1) main_loop(produce_one_frame)
produce_one_frame()         → gfx_start_frame / game_loop_one_iteration / audio / gfx_end_frame
                              y AL FINAL: if (sRLShmName != NULL) rl_state_publish();
                              (pc_main.c:113-117)
```

### 9.3 Objetos POSIX y la regla de un `<name>` por instancia

Prefijos como constantes en `rl_state.h`:

| Objeto | Macro | Nombre final |
|--------|-------|--------------|
| Shared memory | `RL_SHM_PREFIX` | `/sm64_state_<name>` |
| Semáforo de estado | `RL_STATE_SEM_PREFIX` | `/sm64_state_<name>` |
| Semáforo de acción | `RL_ACTION_SEM_PREFIX` | `/sm64_action_<name>` |

La shm y el semáforo de estado comparten literal de prefijo pero viven en
namespaces POSIX distintos, así que no colisionan.

**Regla operativa: un `<name>` distinto por instancia.** En modo sync el init hace
`sem_unlink()` de los dos semáforos (barre restos de un run que murió sin limpiar) y
luego `sem_open(O_CREAT | O_EXCL)`. Si otra instancia viva ya usa ese nombre,
`sem_open` devuelve `EEXIST` y **el init falla a propósito** con un mensaje explícito,
en vez de hacer fallback a `O_CREAT` sin `O_EXCL` — que heredaría un contador de
semáforo desconocido y desincronizaría el handshake en silencio. `RL_NAME_MAX = 96`
limita la longitud de las rutas construidas.

### 9.4 El handshake de `--state-sync`

Orden exacto por frame (`rl_state_publish()`):

```
juego: escribe todos los campos en la shm
juego: RL_FENCE()                      // __sync_synchronize(), full fence
juego: sem_post(state)                 // "estado listo"
juego: sem_wait(action)                 ← SE BLOQUEA AQUÍ
Python: sem_wait(state) / lee el struct / decide
Python: escribe actionBits en la shm
Python: sem_post(action)
juego: despierta, RL_FENCE(), decodifica actionBits en gRLButtons/gRLStickX/gRLStickY
juego: avanza el frame siguiente
```

`sem_wait` se reintenta en bucle si devuelve `EINTR`; cualquier otro errno rompe el
bucle con un mensaje a stderr.

**Consecuencia que hay que tener siempre presente: en modo sync el juego solo avanza
cuando recibe una acción.** Cualquier cosa que mantenga la conexión abierta (un test,
un modo "connect" del runner, un episodio pausado) tiene que contestar cada frame o el
proceso del juego se queda congelado indefinidamente dentro de `produce_one_frame()`.

Sin `--state-sync` (solo `--state-shm`), `rl_state_publish()` escribe y hace `return`
tras el fence: no hay semáforos ni protocolo de polling, el juego corre libre.

### 9.5 El contrato de `magic`

`magic` es **la última escritura del init, deliberadamente** (`rl_state.c:200-201`,
con un comentario largo marcado "NO MOVER ESTA LINEA"). El invariante:

> `magic == RL_STATE_MAGIC` es la única señal fiable de que la shm tiene su tamaño
> final (`ftruncate` hecho), está inicializada (`memset` + `version`) y los semáforos
> ya existen.

`shm_open()` crea el objeto con tamaño 0 antes del `ftruncate`, así que publicar
`magic` antes permitiría que Python mapease una shm truncada o esperase semáforos
inexistentes. Si el init falla a mitad, `magic` se queda en 0. En `rl_state_shutdown()`
se pone `magic = 0` antes del `munmap`, porque el mapeo de Python sobrevive al
`shm_unlink`: así Python detecta el cierre en lugar de leer datos rancios.

`RL_STATE_MAGIC = 0x534D3634` (`'S','M','6','4'`).

### 9.6 ABI: `struct RLStateShm`

El layout **es un contrato con Python**, que lo lee con `struct`/`mmap`. Por eso usa
tipos de ancho fijo de `<stdint.h>` y **no** los aliases del codebase (`s8`/`s16`/`f32`).

| Campo | Tipo | Origen |
|-------|------|--------|
| `magic`, `version`, `frame` | `uint32_t` ×3 | constantes + `gGlobalTimer` |
| `pos[3]`, `vel[3]` | `float` | `m->pos`, `m->vel` |
| `forwardVel` | `float` | `m->forwardVel` |
| `faceAngle[3]` | `int32_t` | `m->faceAngle` (s16 promocionado) |
| `action`, `prevAction`, `actionState`, `actionTimer` | `uint32_t` | `gMarioState` |
| `numCoins`, `health` | `int32_t` | `gMarioState` |
| `floorAngle` | `int32_t` | `m->floorAngle` |
| `flags`, `input` | `uint32_t` | `gMarioState` |
| `isDead`, `starCollected`, `inLevel`, `inAir` | `uint8_t` ×4 | derivados (§9.8). Cubren 4 bytes exactos, sin `_pad` |
| `numEnemies` | `uint32_t` | derivado, guardado por `inLevel` (§10) |
| `enemies[16]` | `struct RLEnemy` | derivado, guardado por `inLevel` (§10) |
| `actionBits` | `uint32_t` | **escrito por Python**. Siempre el ÚLTIMO campo |

**Regla dura:** no reordenar ni insertar campos sin subir `RL_STATE_VERSION` (hoy `3`),
y `SHM_FMT` en `gymnasium_env/envs/sm64_connection.py` tiene que cambiar **en el mismo
commit**.

`actionBits` se mantiene como último campo **a propósito**: así el lado Python puede
calcular `ACTIONBITS_OFFSET = SHM_SIZE - 4` sin duplicar el formato, y cualquier bloque
de datos nuevo se inserta antes de él. Hay dos guardas de ABI en tiempo de compilación
al principio de `rl_state.c` (`typedef char rl_abi_check_*[... ? 1 : -1]`, truco de array
de tamaño negativo porque el syntax-check del Makefile no garantiza C11) que validan
`sizeof(struct RLEnemy) == 28` y `offsetof(actionBits) == sizeof - 4`. El lado Python
tiene tres `assert` equivalentes.

Datos verificados (2026-11-26, x86-64 Linux, versión 3):

```
sizeof(struct RLEnemy)                  == 28
sizeof(struct RLStateShm)               == 548
offsetof(struct RLStateShm, isDead)     == 88
offsetof(struct RLStateShm, numEnemies) == 92
offsetof(struct RLStateShm, enemies)    == 96
offsetof(struct RLStateShm, actionBits) == 544
Python: SHM_FMT = '<IIIfffffffiiiIIIIiiiIIBBBB' + 'I' + 'BBxx3ffiI'*16 + 'I'
        struct.calcsize(SHM_FMT)   == 548   ✓
        ACTIONBITS_OFFSET          == 544   ✓
```

Histórico (versión 2, por si hay binarios viejos rondando): `sizeof == 96`,
`offsetof(actionBits) == 92`, `SHM_FMT = '<IIIfffffffiiiIIIIiiiIIBBBBI'`.

### 9.7 Codificación de `actionBits` y backend de input

```
bits  0..15 : botones N64 crudos (A_BUTTON, B_BUTTON, ... valores CONT_*)
bits 16..23 : stick_x como int8 (-80..80)
bits 24..31 : stick_y como int8 (-80..80)
```

Macros de decodificación en `rl_state.h`: `RL_ACTION_BUTTONS(bits)`,
`RL_ACTION_STICK_X(bits)`, `RL_ACTION_STICK_Y(bits)`.

Globals del subsistema (definidos en `rl_state.c:30-35`, `extern` en `rl_state.h:80-85`):

| Global | Tipo | Significado |
|--------|------|-------------|
| `gRLActive` | `int` | 1 si `rl_state_init()` tuvo éxito |
| `gRLSyncActive` | `int` | 1 si además está en modo `--state-sync` |
| `gRLActionBits` | `uint32_t` | última acción recibida, cruda |
| `gRLButtons` | `uint16_t` | bits 0-15 decodificados |
| `gRLStickX` | `int8_t` | bits 16-23 decodificados |
| `gRLStickY` | `int8_t` | bits 24-31 decodificados |

`controller_rl.c` copia esos globals al `OSContPad` y **sobrescribe, no hace OR**
(`pad->button = gRLButtons`, etc.): la acción del agente es la única fuente de input,
así que un botón no pedido debe quedar a 0. Si `gRLActive == 0` el backend es un
**no-op total** (`return` inmediato), de modo que el teclado y el gamepad siguen
funcionando en modo normal. Su `init()` también es no-op: la shm y los semáforos los
crea `rl_state_init()` desde `pc_main.c` antes del game loop.

Registro en `controller_entry_point.c`: `&controller_rl` es el **primer** elemento de
`controller_implementations[]`, y además `osContGetReadData()` tiene un cortocircuito
explícito — si `gRLActive`, llama solo a `controller_rl.read(pad)` y hace `return`, sin
recorrer el resto del array. Esto es necesario porque los demás backends hacen OR sobre
`pad->button` y podrían inyectar input por encima del agente.

### 9.8 Contrato de las banderas derivadas

```
inLevel = (gCurrLevelNum > 0 && m->action != 0 && m->marioObj != NULL)
isDead        = inLevel && (m->health < 0x100 || actionId ∈ sDeathActions)
starCollected = inLevel && (actionId ∈ sStarActions)
inAir         = inLevel && (m->action & ACT_FLAG_AIR)   // 0x00000800
```
donde `actionId = m->action & 0x1FF` (identificador de acción sin flags).

- **`inLevel` dice si te puedes creer el resto del struct.** Los otros tres van
  **guardados por `inLevel`** y valen 0 fuera del nivel, por construcción.
- Los campos **crudos** (`pos`, `health`, `action`…) son un espejo honesto de
  `gMarioState`: durante la carga valen 0 de verdad, no están "mal".
- Si `gMarioState == NULL`, `rl_state_publish()` solo pone `inLevel = 0` y sale.

**Estado transitorio de carga — no es un caso raro.** En Bob-omb Battlefield el nivel
tarda **~169 frames** en cargar y durante todos ellos Mario no existe:
`health = 0x0000`, `action = 0`, `pos = (0,0,0)`. Sin la guarda de `inLevel`, `isDead`
valía 1 durante esos frames (porque `health < 0x100`) y cualquier entorno que empezase
el episodio ahí lo terminaba en el primer `step()`.

**Dato no obvio, verificado:** de los tres términos de `inLevel`, `gCurrLevelNum > 0`
**no filtra nada en la práctica**. `gCurrLevelNum` se inicializa a `LEVEL_MIN` en
`src/game/area.c:53`, y `LEVEL_MIN = LEVEL_NONE + 1 == 1` en `include/level_table.h:16`;
ninguna asignación del codebase le pone nunca `LEVEL_NONE`. Los términos que gatean de
verdad son `m->action != 0` (con `ACT_UNINITIALIZED == 0`, `include/sm64.h:172`) y
`m->marioObj != NULL`.

Sets de acciones (`rl_state.c:52-60`, comparados contra `actionId`):

```c
sDeathActions[] = { 0x111..0x117,      // death actions
                    0x0C4, 0x0C7,      // drowning / water death
                    0x128, 0x12A, 0x067 };  // death exit variants
sStarActions[]  = { 0x102, 0x103, 0x107, 0x104 };  // star dance / fall after star grab
```

### 9.9 Limpieza (importante: el juego siempre muere por señal)

`rl_state_init()`, solo si tuvo éxito, registra dos cosas:

1. `atexit(rl_state_shutdown)`.
2. Un handler vía `sigaction` para **`SIGTERM` y `SIGINT`** que llama a
   `rl_state_shutdown()` y sale con **`_exit(128 + sig)`** — no `exit()`, que ejecutaría
   los `atexit` desde el contexto de la señal (reentrancia) y no es async-signal-safe.

Esto no es cosmético: el juego acaba en un `while(1) { wm_api->main_loop(...) }` y
**siempre** termina por señal (SIGTERM del launcher, SIGINT de Ctrl+C), y las señales no
ejecutan los `atexit`. Sin el handler, la shm y los dos semáforos quedarían huérfanos en
el kernel **en cada run** (y el siguiente run con el mismo `<name>` chocaría, §9.3).

`rl_state_shutdown()` es **idempotente** (flag `sShutdownDone`), así que es seguro
llamarlo desde el handler y desde `atexit`. Hace: `magic = 0` → fence → `munmap` →
`shm_unlink`; y en modo sync, un **`sem_post(state)` final** para que Python no quede
bloqueado si el juego muere, luego `sem_close` + `sem_unlink` de los dos. El handler se
instala solo en caso de éxito, para no alterar el comportamiento del juego en modo normal.

### 9.10 Contexto de los otros dos lados (orientación)

- **Python** — `gymnasium_env/envs/sm64_connection.py` es **dueño del proceso del juego**:
  lo lanza con `subprocess.Popen([binary, '--state-shm', name, '--state-sync', ...])` y lo
  mata en `close()` (`terminate()` y `kill()` como fallback). `gymnasium_env/runner.py` es
  el punto de entrada (`python -m gymnasium_env.runner`) que invoca el launcher Java.
- **Java** — `java/GameLauncher.java` + `java/LevelSelector.java`. En modo RL **Java no
  lanza nunca el binario del juego**: construye la CLI de `gymnasium_env.runner` (con
  `--binary`, `--level`, `--star`, `--speed`…) y Python es dueño de las instancias.
  Decisión deliberada para que la propiedad del proceso no quede partida en dos.
  (`GameLauncher` conserva además un camino legacy que sí lanza el binario directo, fuera
  del flujo RL.)

---

## §10 Sistema de enemigos

> Añadido en `RL_STATE_VERSION 3` (2026-11-26). Implementado en
> `src/pc/rl_state.h` (`struct RLEnemy`, `RL_MAX_ENEMIES`, `RL_ENEMY_*`) y
> `src/pc/rl_state.c` (`rl_collect_enemies()`, `rl_enemy_type_of()`).
> El `observation_space` de Gymnasium **todavía no** consume este bloque: el ABI
> ya lo transporta y `SHM_FMT` ya lo parsea, pero el entorno lo ignora.

### 10.1 ABI de `struct RLEnemy`

**28 bytes por slot**, alineación 4. El `_pad[2]` es explícito para que el layout no
dependa del padding que elija el compilador.

| Offset | Campo | Tipo | Significado |
|--------|-------|------|-------------|
| 0 | `type` | `uint8_t` | `RL_ENEMY_*`; `0` ⇒ slot vacío, ignora el resto |
| 1 | `active` | `uint8_t` | `1` si `activeFlags != ACTIVE_FLAG_DEACTIVATED` |
| 2 | `_pad[2]` | `uint8_t` ×2 | relleno |
| 4 | `pos[3]` | `float` ×3 | `oPosX` / `oPosY` / `oPosZ` |
| 16 | `distToMario` | `float` | distancia euclídea 3D a `gMarioState->pos` |
| 20 | `angleToMario` | `int32_t` | `(s16)(atan2s(dz,dx) - m->faceAngle[1])` |
| 24 | `action` | `uint32_t` | `oAction` del objeto (semántica propia de cada behavior) |

Formato Python por slot: **`BBxx3ffiI`** (28 B). En `sm64_connection.py` está en
`ENEMY_FMT`, y los campos se exponen como `e<i>_type`, `e<i>_active`, `e<i>_pos0..2`,
`e<i>_dist`, `e<i>_angle`, `e<i>_action`. Hay un helper `enemy_slots(state)` que
devuelve la lista de dicts ya recortada a `numEnemies`.

### 10.2 Tabla de `type`

| Valor | Constante C | Behavior | Nota |
|-------|-------------|----------|------|
| 0 | `RL_ENEMY_NONE` | — | slot vacío |
| 1 | `RL_ENEMY_GOOMBA` | `bhvGoomba` | |
| 2 | `RL_ENEMY_KOOPA` | `bhvKoopa` | `oBehParams2ndByte` ∈ {0 unshelled, 1 normal, 4 tiny} |
| 3 | `RL_ENEMY_KOOPA_THE_QUICK` | `bhvKoopa` | `oBehParams2ndByte` ∈ {2 BoB, 3 THI} |
| 4 | `RL_ENEMY_CHAIN_CHOMP` | `bhvChainChomp` | |
| 5 | `RL_ENEMY_KING_BOBOMB` | `bhvKingBobomb` | jefe del acto 1 |
| 6 | `RL_ENEMY_BOBOMB` | `bhvBobomb` | bob-omb negro hostil |

### 10.3 Slots: 16 fijos, ordenados por distancia

`RL_MAX_ENEMIES = 16`. El ABI de la shm tiene que tener tamaño constante, así que se
exponen **siempre** 16 slots y los que sobran llevan `type = 0`.

- Los slots están **ordenados por `distToMario` ascendente**: el slot 0 es siempre el
  enemigo más cercano a Mario.
- `numEnemies` = número de slots válidos (≤ 16). **`numEnemies == 16` significa
  "16 o más enemigos presentes"**, no "exactamente 16": solo se exponen los 16 más
  cercanos. BoB llega a los 16 slots con facilidad (11 `macro_bobomb` + 2
  `macro_goomba` + 3 `macro_goomba_triplet_spawner` + chain chomp + jefe).
- `rl_collect_enemies()` hace **inserción ordenada directa sobre un buffer local de 16**,
  no "recolectar todo y ordenar". Motivo: el coste queda acotado por diseño y no hay
  array temporal que pueda desbordarse si un área tiene decenas de enemigos. El
  resultado es idéntico.
- El bloque está **guardado por `inLevel`** igual que `isDead`/`inAir` (§9.8): fuera del
  nivel `numEnemies = 0` y `enemies[]` se pone a cero, en vez de publicar los objetos
  del área anterior a medio descargar.

### 10.4 Cómo se iteran las object lists

Se recorren **solo tres** de las `NUM_OBJ_LISTS` listas (`src/game/object_list_processor.h`):

| Lista | Por qué |
|-------|---------|
| `OBJ_LIST_PUSHABLE` | koopas y bob-ombs empujables |
| `OBJ_LIST_GENACTOR` | goombas, chain chomp, King Bob-omb |
| `OBJ_LIST_DESTRUCTIVE` | bob-ombs que explotan |

Las demás (`LEVEL`, `POLELIKE`, `SURFACE`, `UNIMPORTANT`, `PLAYER`, `SPAWNER`…) no
contienen enemigos de interés; escanearlas sería coste por frame sin información.
Mario vive en `OBJ_LIST_PLAYER`, que no se toca.

Patrón de iteración (lista circular doblemente enlazada con nodo centinela, copiado de
`unload_deactivated_objects_in_list()`):

```c
struct ObjectNode *listHead = &gObjectLists[OBJ_LIST_GENACTOR];
struct ObjectNode *node     = listHead->next;
while (node != NULL && node != listHead) {
    struct Object *obj = (struct Object *) node;
    node = node->next;                 // avanzar ANTES de tocar obj
    if (obj->activeFlags == ACTIVE_FLAG_DEACTIVATED) continue;
    ...
}
```

`ACTIVE_FLAG_DEACTIVATED == 0` (`include/object_constants.h:8`): es un objeto que sigue
en la lista pero ya está muerto, pendiente de que
`unload_deactivated_objects_in_list()` lo saque. Hay que saltarlo o publicarías enemigos
fantasma durante un frame.

`gObjectLists` es `extern struct ObjectNode *gObjectLists` y apunta a `gObjectListArray`;
se comprueba `!= NULL` por si se publica antes de `clear_objects()`.

### 10.5 Filtro por behavior

Se usa **`obj_has_behavior(obj, bhvX)`**, no `obj->behavior == bhvX`. Razón: el campo
`behavior` guarda la dirección ya traducida (`segmented_to_virtual()`), y el helper
aplica la misma traducción antes de comparar. En PC con `NO_SEGMENTED_MEMORY` la
traducción es identidad y el `==` crudo funcionaría, pero el helper es el idioma canónico
del codebase y no se rompe si eso cambia.

**Behaviors incluidos:** `bhvGoomba`, `bhvKoopa`, `bhvChainChomp`, `bhvKingBobomb`,
`bhvBobomb`.

**Behaviors Bob-omb deliberadamente EXCLUIDOS** (existen todos en
`include/behavior_data.h`):

| Behavior | Por qué se excluye |
|----------|--------------------|
| `bhvBobombBuddy` | Bob-omb rosa amistoso (diálogo). No puede dañar a Mario. BoB tiene dos |
| `bhvBobombBuddyOpensCannon` | Variante del anterior en actos 2–6 |
| `bhvBobombAnchorMario` | Sub-objeto invisible del agarre de King Bob-omb, no una entidad |
| `bhvBobombFuseSmoke`, `bhvBobombExplosionBubble`, `bhvBobombExplosionBubble3600`, `bhvBobombBullyDeathSmoke` | Partículas, no enemigos |
| `bhvGoombaTripletSpawner` | Spawner invisible; sus goombas sí se detectan al nacer |

### 10.6 Koopa: el orden de comprobación NO es libre

```c
if (obj->oBehParams2ndByte == KOOPA_BP_TINY)                      -> RL_ENEMY_KOOPA
if (obj->oBehParams2ndByte >= KOOPA_BP_KOOPA_THE_QUICK_BASE)      -> RL_ENEMY_KOOPA_THE_QUICK
else                                                              -> RL_ENEMY_KOOPA
```

Constantes (`include/object_constants.h:576-581`): `UNSHELLED 0`, `NORMAL 1`,
`KOOPA_THE_QUICK_BASE 2` (BoB = 2, THI = 3), **`TINY 4`**.

`KOOPA_BP_TINY` vale **4**, que también cumple `>= 2`. Si se comprobara el rango de
Koopa the Quick primero, el koopa diminuto de THI quedaría clasificado como el NPC de la
carrera. Este es exactamente el mismo orden que usa `bhv_koopa_init()`
(`src/game/behaviors/koopa.inc.c:72-81`), que evalúa `== KOOPA_BP_TINY` antes del rango.

**Koopa the Quick no es hostil**: es el NPC de la carrera del acto 2, no hace daño por
contacto. Se expone como `type` separado (3) justamente para que la política pueda
distinguirlo de un koopa normal y no aprenda a huir de él — o pueda usarlo como
referencia de trazada en la estrella 2.

### 10.7 `distToMario` y `angleToMario` se CALCULAN, no se leen del objeto

**No se usan `oDistanceToMario` ni `oAngleToMario`.** Son campos de scratch que varios
behaviors sobreescriben con valores centinela:

- `treat_far_home_as_mario()` (`src/game/obj_behaviors_2.c:877-896`) pone
  `oDistanceToMario = 25000.0f` si el objeto está lejos de casa, o `20000.0f` si Mario
  está lejos, y `oAngleToMario = atan2s` **hacia su casa**, no hacia Mario.
- `src/game/spawn_object.c:321` lo inicializa a `19000.0f`.
- Varios behaviors (`chuckya.inc.c`, etc.) lo reasignan a su antojo.

Por eso `rl_collect_enemies()` recalcula ambos desde las posiciones, que sí son verdad
siempre:

```c
f32 dx = m->pos[0] - obj->oPosX;   // etc.
dist   = sqrtf(dx*dx + dy*dy + dz*dz);
angle  = (int32_t)(s16)(atan2s(dz, dx) - m->faceAngle[1]);
```

El `atan2s(dz, dx)` con `d = Mario - enemigo` es la misma convención que
`obj_angle_to_object(enemigo, Mario)` (`object_helpers.c:383`). El cast a `s16` antes del
`int32_t` es el que hace el **wrap correcto del ángulo binario de N64**: sin él, la resta
de dos `s32` se saldría del rango ±32768. Semántica: ángulo *del enemigo hacia Mario*
relativo a la cara de Mario; el ángulo *de Mario hacia el enemigo* es este + `0x8000`
(información equivalente, difieren en una constante).

### 10.8 Enemigos de Bob-omb Battlefield por acto

Fuente: `levels/bob/script.c` (`OBJECT_WITH_ACTS`) + `levels/bob/areas/1/macro.inc.c`.
Los **macro objects no tienen filtro de acto**: están en los seis.

| Enemigo | Cantidad | Actos | Origen |
|---------|----------|-------|--------|
| Bob-omb (`bhvBobomb`) | 11 | 1–6 | `macro_bobomb` |
| Goomba (`bhvGoomba`) | 2 sueltos + 3 spawners de tripleta (≤ 9 más) | 1–6 | `macro_goomba`, `macro_goomba_triplet_spawner` |
| Chain Chomp (`bhvChainChomp`) | 1 | 1–6 | `macro_chain_chomp` @ (260, 735, 1920) |
| King Bob-omb (`bhvKingBobomb`) | 1 | **solo ACT_1** | `script.c:48` @ (1636, 4242, −5567) |
| Koopa the Quick (`bhvKoopa`, bp2 = 2) | 1 | **solo ACT_2** | `script.c:49` @ (−4004, 0, 5221) |
| Koopa normal (`bhvKoopa`, bp2 = 1) | 1 | **ACT_3–6** | `script.c:43` @ (3400, 770, 6500) |
| Bob-omb Buddy (rosa, **excluido**) | 2 | 1–6 | `script.c:36-39` |

Los goombas de los `macro_goomba_triplet_spawner` **solo existen cuando Mario está
cerca**, así que el conteo real fluctúa durante el episodio.

Verificado en caliente (headless, `--speed 20`, frame ~205):

| Acto | Slot 0 observado | Tipos presentes |
|------|------------------|-----------------|
| 1 | goomba (`type=1`, dist 3936) | 1, 4, 5, 6 — `numEnemies = 16` |
| 2 | **Koopa the Quick** (`type=3`, dist 2840) | 1, 3, 4, 6 — `numEnemies = 16` |

### 10.9 Regla de versión (recordatorio)

Este bloque subió `RL_STATE_VERSION` de **2 a 3**. `SHM_VERSION` en
`gymnasium_env/envs/sm64_connection.py` y `SHM_FMT` se actualizaron **en el mismo
commit**: si no, `SM64Connection` aborta el arranque con el mensaje
"Recompila el juego…" (la comprobación de `head['version']` del §9.6), que es
precisamente el comportamiento deseado frente a un binario viejo.

### 10.10 Pendiente

- El `observation_space` del entorno Gymnasium **no** incluye todavía los enemigos
  (decisión explícita: va en otro commit).
- `action` se publica como `oAction` crudo. Su significado depende del behavior
  (`GOOMBA_ACT_*`, `KOOPA_ACT_*`, `CHAIN_CHOMP_ACT_*`… en
  `include/object_constants.h`); no hay normalización entre tipos.
- No se expone `oHealth` / "muerto vs vivo" más allá de `active`, ni la velocidad del
  enemigo.

---

## §11 Override de spawn de Mario

> Añadido 2026-11-26. Permite arrancar (y respawnear) a Mario en una posición
> arbitraria del área en vez de en el warp del nivel. Pensado para currículo de RL:
> empezar el episodio cerca del objetivo e ir alejándolo.
> **NO afecta al ABI de `rl_state`**: es solo input por CLI, no hay campo nuevo en la
> shm, `RL_STATE_VERSION` sigue en `3` y `SHM_FMT` no cambia.

### 11.1 Flags CLI

| Flag | Tipo | Obligatorio | Notas |
|------|------|-------------|-------|
| `--spawn-x <float>` | `f32` | sí (para activar) | clamp a ±32767 con warning a stderr |
| `--spawn-z <float>` | `f32` | sí (para activar) | clamp a ±32767 con warning a stderr |
| `--spawn-y <float>` | `f32` | **no** | si falta → auto-floor con `find_floor()` |
| `--spawn-yaw <deg>` | grados → `s16` | no | `fmodf(deg, 360)` y `* 65536 / 360`; 90° = `16384` |

**El override se activa solo si llegan X **y** Z.** Con solo una (o solo `--spawn-y`)
se imprime un aviso, se resetea `gSpawnHasY` y se usa el comportamiento viejo. El clamp
a ±32767 existe porque `SpawnInfo::startPos` es `Vec3s` (`s16`), no floats.

### 11.2 Globals

Definidos en `src/pc/pc_main.c` (justo antes del bloque de `rl_state`), `extern` en el
header nuevo **`src/pc/spawn_override.h`**. `src/game/mario.c` lo incluye como
`#include "pc/spawn_override.h"` — funciona porque `src` está en `INCLUDE_DIRS`
(`Makefile:386`).

| Global | Tipo | Default | Significado |
|--------|------|---------|-------------|
| `gSpawnOverride` | `s8` | 0 | 1 = override activo (llegaron X y Z) |
| `gSpawnHasY` | `s8` | 0 | 1 = Y explícita; 0 = auto-floor |
| `gSpawnX`, `gSpawnY`, `gSpawnZ` | `f32` | 0 | ya clampados en el parse |
| `gSpawnYaw` | `s16` | 0 | ya en unidades de ángulo N64 |

Hay además dos `static s8 sSpawnHasX / sSpawnHasZ` file-scope en `pc_main.c` (flags de
presencia); no se exportan.

### 11.3 Hook: `init_mario()`, no el warp

El parche va **al principio de `init_mario()`** (`src/game/mario.c:1788`), antes de
`unused80339F10 = 0` y por tanto antes de que la función lea `gMarioSpawnInfo`
(`find_water_level` en la línea ~1855 es el primer lector). Se modifica
`gMarioSpawnInfo->startPos[0..2]` y `startAngle[1]`; el resto de `init_mario`
(`vec3s_to_vec3f`, `find_floor`, clamp al suelo, elección de `ACT_IDLE`/`ACT_WATER_IDLE`)
funciona sin tocarlo.

**Por qué ahí y no en el level script:** `init_mario()` se llama en el arranque y en cada
respawn (`src/game/level_update.c:397, 521, 1188`), y el proceso del juego **no se relanza
entre episodios** (la conexión de Python es dueña de un proceso de larga vida, §9.10). Un
hook en el warp solo cubriría la primera carga.

### 11.4 Política de la Y opcional y fallback

```
if (gSpawnOverride && gMarioSpawnInfo != NULL):
    if gSpawnHasY:            spawnY = gSpawnY
    else:                     find_floor(gSpawnX, 20000.0f, gSpawnZ, &floor)
                              floor != NULL -> spawnY = floorY + 1
                              floor == NULL -> NO parchear startPos (fallback al warp)
    startAngle[1] = gSpawnYaw   // SIEMPRE que el override esté activo
```

Decisión deliberada: el **yaw se aplica incluso en el fallback de posición** (orientar a
Mario no depende de que haya suelo). El ray de `find_floor` se lanza desde `y = 20000`,
por encima de cualquier geometría de BoB.

### 11.5 Comportamiento verificado (headless, BoB acto 1, `--speed 20`)

| Caso | Resultado |
|------|-----------|
| `--spawn-x 1000 --spawn-z 1000 --spawn-yaw 90` | `pos = (1000, 768, 1000)`, `faceAngle[1] = 16384`, `ACT_IDLE`, estable |
| sin flags de spawn | warp original `(-6558, 0, 6464)`, yaw `24576` |
| `--spawn-x 99999 --spawn-z 99999 --spawn-yaw 90` | clamp a 32767 → `find_floor` NULL → fallback al warp (yaw sí aplicado). **No crashea** |
| `--spawn-x 1000 --spawn-y 3000 --spawn-z 1000` | Mario cae desde 3000 y aterriza en 768 con daño de caída (`health 0x880 → 0x680`) |
| `--spawn-x 20000 --spawn-y 1000 --spawn-z 20000` (Y explícita, sin geometría) | **Mario se queda flotando quieto** en `ACT_IDLE`, no cae ni muere. No crashea, pero es un estado inútil para el agente |

Último caso = la única trampa real: con `--spawn-y` explícita **no hay validación de que
haya suelo debajo**, así que una coordenada fuera de la malla deja a Mario en un idle
flotante permanente. Si se quiere la red de seguridad, omitir `--spawn-y` y dejar el
auto-floor.

### 11.6 `printf` está stubbeado en `pc_main.c`

Trampa descubierta implementando esto: **`src/pc/pc_main.c:113` tiene un `#define printf`
a secas** (heredado del port). Cualquier `printf` escrito *después* de esa línea —
incluido todo `main()` — se compila a nada, con un warning de `-Wunused-value` como única
pista. Los logs de este subsistema usan **`fprintf(stderr, ...)`**, que no está afectado y
además no se bufferiza. Misma convención que `rl_state.c`.

### 11.7 Pendiente

- El lado Java/Python **no pasa todavía** estos flags: hay que añadirlos a
  `extra_args` de `SM64Connection` / a la CLI de `gymnasium_env.runner` y a
  `java/GameLauncher.java` cuando se quiera exponerlo en la UI.

