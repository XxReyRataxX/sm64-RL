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
│   └── LevelSelector.java     ← UI JavaFX principal (ComboBox + Slider)
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
    if (gSpeedMultiplier > 5.0f) gSpeedMultiplier = 5.0f;
}
```

| Arg CLI | Tipo | Global destino | Rango / clamp |
|---------|------|----------------|---------------|
| `--level <n>` | `s8` | `gAutoStartLevelNum` (+ `gDebugLevelSelect = 1`) | sin clamp |
| `--star <n>` | `s8` | `gAutoStartActNum` → `gCurrActNum` | clamp 1–6 |
| `--speed <f>` | `float` | `gSpeedMultiplier` | clamp 1.0–5.0 |

### Correspondencia Java

```java
// java/GameLauncher.java, línea ~30
new ProcessBuilder(
    binary.getAbsolutePath(),
    "--level", courseId,          // String del Level.id en LevelSelector
    "--speed", String.format("%.1f", speed),
    "--star",  String.valueOf(starId)
).inheritIO().start();
```

Firma actual: `GameLauncher.launch(String courseId, double speed, int starId)`.

### Flujo del acto (estrella)

`main()` parsea `--star` → `gAutoStartActNum` → `intro_regular()` devuelve `101`
(pantalla de level select de debug) → `intro_level_select()` detecta
`gAutoStartLevelNum > 0` y asigna `gCurrSaveFileNum = 1` y
`gCurrActNum = gAutoStartActNum` antes de devolver `gCurrLevelNum`.
`gCurrActNum` es `s16`, declarado en `src/game/area.c:33`, `extern` en
`src/game/area.h:145`.

---

## 3. Niveles registrados en la UI

```java
// java/LevelSelector.java, línea ~31
private static final Level[] LEVELS = {
    new Level("Bob-omb Battlefield", "Course 1", "9"),
    new Level("Cool, Cool Mountain",  "Course 4", "5")
};
```

El `id` (tercer campo) es el número que se pasa como `--level`. Corresponde a `gCurrLevelNum` internamente (`src/menu/title_screen.c:164`).

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
| Directorio de trabajo | raíz del proyecto `/home/reyra/sm64-RL` |
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
