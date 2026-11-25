# SM64 C Expert — Knowledge Base

> Este archivo es la fuente de verdad del agente `sm64-c-expert`.
> **Leer aquí primero** antes de tocar cualquier archivo del proyecto.
> Si un dato aquí contradice lo que ves en el código, actualiza este archivo.
> Escanear el código solo cuando una sección esté marcada `[DESACTUALIZADO]`
> o cuando el usuario pida añadir algo que no esté documentado aquí.

---

## 1. Estructura del Proyecto

```
sm64-RL/
├── src/
│   ├── pc/
│   │   ├── pc_main.c          ← Entry point; argv parsing; globals del bridge
│   │   ├── gfx/
│   │   │   ├── gfx_sdl2.c     ← Usa gSpeedMultiplier para framerate
│   │   │   └── gfx_glx.c      ← Usa gSpeedMultiplier para framerate
│   │   ├── configfile.c/.h    ← Persistencia de configuración del usuario
│   │   └── controller/        ← Input de controlador/teclado
│   ├── game/
│   │   ├── game_init.c/.h     ← Inicialización del loop principal
│   │   ├── level_update.c/.h  ← Lógica de actualización de niveles
│   │   └── ...
│   └── menu/
│       └── title_screen.c     ← Lee gAutoStartLevelNum para saltar menú
├── java/
│   ├── GameLauncher.java      ← Construye el ProcessBuilder con los args
│   └── LevelSelector.java     ← UI JavaFX principal (ComboBox + Spinner)
├── include/
│   └── sm64.h                 ← Tipos globales: s8, s16, s32, f32, u8...
└── Makefile                   ← Build system; VERSION=us; salida: build/us_pc/sm64.us
```

---

## 2. El Bridge C↔Java

### Flujo completo

```
LevelSelector.java  (JavaFX UI)
  │  usuario elige nivel + velocidad + [futuros params]
  ▼
GameLauncher.java  .launch(courseId, speed)
  │  ProcessBuilder: binary --level <num> --speed <float>
  ▼
build/us_pc/sm64.us  (binario nativo)
  │
  ▼
src/pc/pc_main.c  main()  ← argv parsing
  │  escribe globals en memoria
  ▼
game loop / level loader / RL hooks
```

### Globals activos hoy

| Global | Tipo | Declarado en | Usado en | Valor por defecto |
|--------|------|-------------|----------|-------------------|
| `gAutoStartLevelNum` | `s8` | `pc_main.c:46` | `title_screen.c:92,165` | `0` (no saltar) |
| `gAutoStartActNum` | `s8` | `pc_main.c:47` | `title_screen.c:94` (→ `gCurrActNum`) | `1` |
| `gSpeedMultiplier` | `float` | `pc_main.c:48` | `gfx_sdl2.c:275`, `gfx_glx.c:455` | `1.0f` |
| `gDebugLevelSelect` | `s8` | `pc_main.c:43` | activado al parsear `--level` | `0` |

### Args CLI activos hoy

```c
// src/pc/pc_main.c, línea ~238
if (strcmp(argv[i], "--level") == 0 && i + 1 < argc) {
    gAutoStartLevelNum = (s8)atoi(argv[++i]);
    gDebugLevelSelect = 1;
} else if (strcmp(argv[i], "--star") == 0 && i + 1 < argc) {
    gAutoStartActNum = (s8)atoi(argv[++i]);
    if (gAutoStartActNum < 1) gAutoStartActNum = 1;
    if (gAutoStartActNum > 6) gAutoStartActNum = 6;
} else if (strcmp(argv[i], "--speed") == 0 && i + 1 < argc) {
    gSpeedMultiplier = (float)atof(argv[++i]);
    if (gSpeedMultiplier < 1.0f) gSpeedMultiplier = 1.0f;
    if (gSpeedMultiplier > 100.0f) gSpeedMultiplier = 100.0f;
}
```

| Arg CLI | Tipo | Global destino | Rango / clamp |
|---------|------|----------------|---------------|
| `--level <n>` | `s8` | `gAutoStartLevelNum` (+ `gDebugLevelSelect = 1`) | sin clamp |
| `--star <n>` | `s8` | `gAutoStartActNum` → `gCurrActNum` | clamp 1–6 |
| `--speed <f>` | `float` | `gSpeedMultiplier` | clamp 1.0–100.0 |

### `--speed` y el throughput

El binario acepta **1.0–100.0×** (clamp en `src/pc/pc_main.c:276`). La UI ya no
limita a 5.0×: al eliminarse el modo Manual, el slider desapareció y la velocidad
se elige con un `Spinner` editable de **1 a 100** en `java/LevelSelector.java`.

`--speed` es un **limitador por sueño, no un acelerador**: `src/pc/gfx/gfx_dummy.c:80-93`
calcula el presupuesto de frame como `1/(30 × spd)` y duerme lo que sobra; si el frame
ya tardó más que ese presupuesto, no duerme nada y sigue. Consecuencia: pedir más
velocidad de la que la CPU puede dar **no rompe nada, solo degrada**.

**El throughput escala de verdad con este flag** (medido headless, `--no-render`):

| `--speed` | FPS aprox. |
|-----------|-----------|
| `1.0` | ~30 fps |
| `10.0` | ~300 fps |

Con varias instancias, cada una es un proceso aparte que compite por CPU. El tope de
instancias de la UI es `max(1, cores - 1)`, dejando un núcleo al proceso Python del
runner y al sistema.

### Correspondencia Java

**Java ya no lanza el binario del juego.** Lanza el runner de Python, y Python es dueño
de las N instancias (ver §9.10). La ruta del binario viaja como un argumento más:

```java
// java/GameLauncher.java — buildTrainingCommand(TrainingConfig)
args.add(findPython());               // .venv/bin/python3 si existe, si no "python3"
args.add("-u"); args.add("-m"); args.add("gymnasium_env.runner");
args.add("--binary");  args.add(binaryPath);
args.add("--level");   args.add(cfg.courseId());
args.add("--star");    args.add(String.valueOf(cfg.starId()));
args.add("--speed");   args.add(String.format(java.util.Locale.ROOT, "%.1f", cfg.speed()));
args.add("--instances"); args.add(String.valueOf(cfg.instances()));
// ... --env --shm-prefix --episodes --max-steps --algo --mode [--render]
```

`Locale.ROOT` no es decorativo: sin él, una JVM en locale español emite `--speed 2,5`,
y el `atof()` del lado C lo interpreta como `2.0` sin dar ningún error.

Los métodos `GameLauncher.launch(...)` y `showError(...)`, que lanzaban el binario
directamente en el desaparecido modo Manual, **ya no existen**.

### Flujo del acto (estrella)

`main()` parsea `--star` → `gAutoStartActNum` → `intro_regular()` devuelve `101`
(pantalla de level select de debug) → `intro_level_select()` detecta
`gAutoStartLevelNum > 0` y asigna `gCurrSaveFileNum = 1`,
`gCurrActNum = gAutoStartActNum`, desbloquea todas las caps con
`save_file_set_flags(SAVE_FLAG_HAVE_WING_CAP | SAVE_FLAG_HAVE_METAL_CAP | SAVE_FLAG_HAVE_VANISH_CAP)`,
y devuelve `gCurrLevelNum`.
`gCurrActNum` es `s16`, declarado en `src/game/area.c:33`, `extern` en
`src/game/area.h:145`.
Las caps se desbloquean **siempre** en todo auto-arranque (no hay flag CLI para esto).

---

## 3. Niveles registrados en la UI

```java
// java/LevelSelector.java, línea ~31
private static final Level[] LEVELS = {
    new Level("Bob-omb Battlefield", "Course 1", "9", new Star[] {
        new Star("Big Bob-omb on the Summit",      1),
        new Star("Footrace with Koopa the Quick",  2),
        new Star("Shoot to the Island in the Sky", 3),
        new Star("Find the 8 Red Coins",           4),
        new Star("Mario Wings to the Sky",         5),
        new Star("Behind Chain Chomp's Gate",      6)
    })
};
```

El `courseId` (tercer campo del Level) es el número que se pasa como `--level`.
Corresponde a `gCurrLevelNum` internamente (`src/menu/title_screen.c`).
El `Star.id` se pasa como `--star` y corresponde a `gCurrActNum`.
Nombres de estrellas extraídos de `text/us/courses.h` (fuente oficial).

---

## 4. Tipos C del Codebase

| Alias | Tipo real | Tamaño |
|-------|-----------|--------|
| `s8`  | `signed char` | 8 bit |
| `s16` | `short` | 16 bit |
| `s32` | `int` | 32 bit |
| `f32` | `float` | 32 bit |
| `u8`  | `unsigned char` | 8 bit |
| `u16` | `unsigned short` | 16 bit |
| `u32` | `unsigned int` | 32 bit |

Definidos en `include/sm64.h` via los includes de `include/ultra64/types.h`.

---

## 5. Build System

| Campo | Valor |
|-------|-------|
| Comando canónico | `make version=us` |
| Paralelo | `make -j$(nproc) version=us` |
| Binario de salida | `build/us_pc/sm64.us` |
| Directorio de trabajo | raíz del proyecto `/home/reyrata/sm64-RL` |
| VERSION por defecto | `us` |
| Target Linux | `EXE = $(BUILD_DIR)/$(TARGET)` |
| Makefile principal | `Makefile` (incluye `util.mk`, `Makefile.split`) |

**Nota:** Cambios en `.h` requieren make completo (no incremental).

---

## 6. Patrón para Añadir un Nuevo Parámetro

Cuando el usuario pida exponer un nuevo parámetro C a Java, seguir este checklist exacto:

1. **`src/pc/pc_main.c`** — Declarar el global cerca de la línea 44:
   ```c
   <tipo> gNuevoParam = <default>;
   ```
2. **`src/pc/pc_main.c` `main()`** — Añadir bloque de parsing (línea ~244):
   ```c
   } else if (strcmp(argv[i], "--nuevo-param") == 0 && i + 1 < argc) {
       gNuevoParam = (<tipo>)atoi(argv[++i]);   // o atof si es float
   }
   ```
3. **Header relevante** — Si otro `.c` necesita el global, añadir:
   ```c
   extern <tipo> gNuevoParam;
   ```
4. **`java/GameLauncher.java`** — Añadir al `ProcessBuilder`:
   ```java
   "--nuevo-param", String.valueOf(nuevoParam),
   ```
5. **`java/LevelSelector.java`** — Exponer el control en la UI y pasarlo a `launch()`.
6. **Actualizar este archivo** — Sección "Globals activos" y "Args CLI activos".
7. **Compilar:** `make version=us`

---

## 7. Cuándo Sí Escanear

Escanear código **sólo en estos casos** (y preguntar antes):

- El usuario pide una feature que involucra un archivo no listado aquí.
- Hay un error de compilación con un símbolo desconocido.
- El usuario dice que el código fue refactorizado y este doc puede estar desactualizado.
- Se necesita trazar cómo un global llega a un subsistema nuevo (audio, cámara, etc.).

Scripts disponibles en `.claude/scripts/`:
- `scan_bridge.sh` — Audita el estado actual del bridge completo
- `scan_args.sh` — Lista args CLI con contexto de código
- `scan_symbol.sh <nombre>` — Busca un símbolo en src/ e include/
- `scan_feature.sh <keyword>` — Archivos que mencionan una feature
- `scan_makefile.sh` — Variables y targets del Makefile

---

## 8. Historial de Cambios

| Fecha | Cambio | Archivos afectados |
|-------|--------|--------------------|
| 2026-11-03 | Creación del doc con estado inicial | — |
| 2026-11-03 | Añadido arg `--star` + global `gAutoStartActNum` (→ `gCurrActNum`) | `src/pc/pc_main.c`, `src/menu/title_screen.c` |
| 2026-11-25 | Documentado el subsistema `rl_state` (bridge C↔Python) → §9 | `src/pc/rl_state.c/.h`, `src/pc/pc_main.c`, `src/pc/controller/controller_rl.c`, `src/pc/controller/controller_entry_point.c` |
| 2026-11-25 | Corregido clamp de `--speed` en §2 (era 1.0–5.0, el real es 1.0–100.0) + anotado el límite de 5.0× del slider de la UI y el throughput medido | doc only (`pc_main.c:276`, `LevelSelector.java:298`) |
| 2026-11-25 | Eliminado el modo Manual de la UI: §2 reescrita (Java ya no lanza el binario, lanza el runner de Python; velocidad por Spinner 1–100; `GameLauncher.launch`/`showError` borrados) | doc only (`LevelSelector.java`, `GameLauncher.java`) |

---

## 9. El Subsistema `rl_state` (Bridge C↔Python)

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
| `actionBits` | `uint32_t` | **escrito por Python** |

**Regla dura:** no reordenar ni insertar campos sin subir `RL_STATE_VERSION` (hoy `2`),
y `SHM_FMT` en `gymnasium_env/envs/sm64_connection.py` tiene que cambiar **en el mismo
commit**.

Datos verificados (2026-11-25, x86-64 Linux):

```
sizeof(struct RLStateShm)              == 96
offsetof(struct RLStateShm, actionBits) == 92
offsetof(struct RLStateShm, isDead)     == 88
Python: SHM_FMT = '<IIIfffffffiiiIIIIiiiIIBBBBI'
        struct.calcsize(SHM_FMT)                        == 96   ✓
        ACTIONBITS_OFFSET = calcsize(SHM_FMT sin la 'I') == 92   ✓
```

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
