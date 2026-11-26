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
de las N instancias (ver §9.10 en `sm64-c-expert-rl-state.md`). La ruta del binario viaja como un argumento más:

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
| 2026-11-26 | Sistema de enemigos en la shm: `struct RLEnemy` + 16 slots ordenados por distancia, `RL_STATE_VERSION 2→3`, `SHM_FMT` actualizado → ver §10 en `sm64-c-expert-rl-state.md` | `src/pc/rl_state.h`, `src/pc/rl_state.c`, `gymnasium_env/envs/sm64_connection.py` |
| 2026-11-25 | Eliminado el modo Manual de la UI: §2 reescrita (Java ya no lanza el binario, lanza el runner de Python; velocidad por Spinner 1–100; `GameLauncher.launch`/`showError` borrados) | doc only (`LevelSelector.java`, `GameLauncher.java`) |

---

## 9. El Subsistema `rl_state` (Bridge C↔Python)

> Movido a su propio fichero: **lee `.claude/sm64-c-expert-rl-state.md`**
> antes de tocar `rl_state.*`, `controller_rl.c`, los flags
> `--state-shm`/`--state-sync`, o el lado Python del bridge. No hace falta
> cargarlo para tareas normales de niveles, args CLI o build (§1-§8).

