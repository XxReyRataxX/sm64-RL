// -----------------------------------------------------------------------------
// rl_state.c — Implementación del export de estado a memoria compartida POSIX.
//
// El estado se publica entre barreras de memoria; la sincronizacion con Python la
// dan los semaforos del modo --state-sync (no hay protocolo de polling).
// -----------------------------------------------------------------------------

#include <stdint.h>
#include <stddef.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <semaphore.h>
#include <signal.h>

#include "sm64.h"
#include "types.h"
#include "object_fields.h"
#include "object_constants.h"
#include "behavior_data.h"

#include "game/level_update.h"
#include "game/area.h"
#include "game/game_init.h"
#include "game/object_list_processor.h"
#include "game/object_helpers.h"
#include "engine/math_util.h"

#include "rl_state.h"

// --- Guardas de ABI ----------------------------------------------------------
// El layout de la shm es un contrato con Python (SHM_FMT en
// gymnasium_env/envs/sm64_connection.py). Si alguien cambia un campo y se olvida
// del lado Python, al menos que no compile en silencio con otro tamaño.
typedef char rl_abi_check_enemy[(sizeof(struct RLEnemy) == 28) ? 1 : -1];
typedef char rl_abi_check_actionbits[
    (offsetof(struct RLStateShm, actionBits) == sizeof(struct RLStateShm) - 4) ? 1 : -1];

// --- Estado del módulo -------------------------------------------------------

int      gRLActive     = 0;
int      gRLSyncActive = 0;
uint32_t gRLActionBits = 0;
uint16_t gRLButtons    = 0;
int8_t   gRLStickX     = 0;
int8_t   gRLStickY     = 0;

static struct RLStateShm *sShm        = NULL;
static int    sShmFd                  = -1;
static sem_t *sStateSem               = SEM_FAILED;
static sem_t *sActionSem              = SEM_FAILED;
static int    sSyncMode               = 0;
static int    sShutdownDone           = 0;

#define RL_NAME_MAX 96
static char sShmPath[RL_NAME_MAX];
static char sStateSemPath[RL_NAME_MAX];
static char sActionSemPath[RL_NAME_MAX];

// --- Sets de acciones derivadas ----------------------------------------------
// Se comparan contra (action & 0x1FF), el identificador de acción sin flags.

static const uint32_t sDeathActions[] = {
    0x111, 0x112, 0x113, 0x114, 0x115, 0x116, 0x117, // death actions
    0x0C4, 0x0C7,                                     // drowning / water death
    0x128, 0x12A, 0x067                               // death exit variants
};

static const uint32_t sStarActions[] = {
    0x102, 0x103, 0x107, 0x104 // star dance / fall after star grab
};

static int action_in_set(uint32_t actionId, const uint32_t *set, size_t count) {
    for (size_t i = 0; i < count; i++) {
        if (set[i] == actionId) {
            return 1;
        }
    }
    return 0;
}

// --- Recoleccion de enemigos --------------------------------------------------
//
// Se recorren solo las tres object lists donde viven los enemigos que nos
// interesan: PUSHABLE (koopas y bob-ombs empujables), GENACTOR (goombas, chain
// chomp, King Bob-omb) y DESTRUCTIVE (bob-ombs que explotan). El resto de listas
// (LEVEL, POLELIKE, SURFACE, UNIMPORTANT, PLAYER...) no contiene enemigos, asi
// que escanearlas seria coste por frame sin informacion.
//
// NO se usan oDistanceToMario / oAngleToMario: son campos de scratch que varios
// behaviors sobreescriben con valores centinela (treat_far_home_as_mario() pone
// 20000/25000 en obj_behaviors_2.c, spawn_object.c pone 19000). Se recalculan
// aqui desde las posiciones, que si son verdad siempre.

static const s32 sRLEnemyLists[] = {
    OBJ_LIST_PUSHABLE,
    OBJ_LIST_GENACTOR,
    OBJ_LIST_DESTRUCTIVE,
};

// Clasifica un objeto en un RL_ENEMY_*. Devuelve RL_ENEMY_NONE si no nos
// interesa (lo cual es el caso de la gran mayoria de objetos de las listas).
static uint8_t rl_enemy_type_of(struct Object *obj) {
    if (obj_has_behavior(obj, bhvGoomba)) {
        return RL_ENEMY_GOOMBA;
    }
    if (obj_has_behavior(obj, bhvChainChomp)) {
        return RL_ENEMY_CHAIN_CHOMP;
    }
    if (obj_has_behavior(obj, bhvKingBobomb)) {
        return RL_ENEMY_KING_BOBOMB;
    }
    if (obj_has_behavior(obj, bhvBobomb)) {
        return RL_ENEMY_BOBOMB;
    }
    if (obj_has_behavior(obj, bhvKoopa)) {
        // MISMO ORDEN DE COMPROBACION que bhv_koopa_init() (koopa.inc.c:72-81):
        // KOOPA_BP_TINY (4) se evalua ANTES del rango de Koopa the Quick, porque
        // 4 >= KOOPA_BP_KOOPA_THE_QUICK_BASE (2) tambien seria cierto y el
        // koopa diminuto de THI acabaria clasificado como el NPC de la carrera.
        if (obj->oBehParams2ndByte == KOOPA_BP_TINY) {
            return RL_ENEMY_KOOPA;
        }
        if (obj->oBehParams2ndByte >= KOOPA_BP_KOOPA_THE_QUICK_BASE) {
            return RL_ENEMY_KOOPA_THE_QUICK;
        }
        return RL_ENEMY_KOOPA;
    }
    return RL_ENEMY_NONE;
}

// Rellena sShm->numEnemies y sShm->enemies[] con los RL_MAX_ENEMIES enemigos mas
// cercanos a Mario, ordenados por distancia ascendente.
//
// Se usa insercion directa sobre un buffer de 16 en vez de "recolectar todo y
// ordenar": el coste esta acotado por diseño (no hay array temporal que pueda
// desbordarse si un area tiene decenas de enemigos) y el resultado es el mismo.
static void rl_collect_enemies(struct MarioState *m) {
    struct RLEnemy best[RL_MAX_ENEMIES];
    uint32_t count = 0;
    size_t li;

    memset(best, 0, sizeof(best));

    if (m == NULL || gObjectLists == NULL) {
        sShm->numEnemies = 0;
        memset(sShm->enemies, 0, sizeof(sShm->enemies));
        return;
    }

    for (li = 0; li < sizeof(sRLEnemyLists) / sizeof(sRLEnemyLists[0]); li++) {
        struct ObjectNode *listHead = &gObjectLists[sRLEnemyLists[li]];
        struct ObjectNode *node     = listHead->next;

        while (node != NULL && node != listHead) {
            struct Object *obj = (struct Object *) node;
            node = node->next;

            // ACTIVE_FLAG_DEACTIVATED == 0: objeto en la lista pero ya muerto,
            // pendiente de que unload_deactivated_objects_in_list() lo saque.
            if (obj->activeFlags == ACTIVE_FLAG_DEACTIVATED) {
                continue;
            }

            uint8_t type = rl_enemy_type_of(obj);
            if (type == RL_ENEMY_NONE) {
                continue;
            }

            f32 dx = (f32) m->pos[0] - obj->oPosX;
            f32 dy = (f32) m->pos[1] - obj->oPosY;
            f32 dz = (f32) m->pos[2] - obj->oPosZ;
            f32 dist = sqrtf(dx * dx + dy * dy + dz * dz);

            // Si ya tenemos 16 y este esta mas lejos que el peor, se descarta.
            if (count == RL_MAX_ENEMIES && dist >= best[RL_MAX_ENEMIES - 1].distToMario) {
                continue;
            }

            struct RLEnemy e;
            e.type        = type;
            e.active      = 1;
            e._pad[0]     = 0;
            e._pad[1]     = 0;
            e.pos[0]      = (float) obj->oPosX;
            e.pos[1]      = (float) obj->oPosY;
            e.pos[2]      = (float) obj->oPosZ;
            e.distToMario = (float) dist;
            // Angulo enemigo->Mario, relativo a la cara de Mario. El cast a s16
            // es el que hace el wrap correcto del angulo binario de N64.
            e.angleToMario = (int32_t) (s16) (atan2s(dz, dx) - m->faceAngle[1]);
            e.action       = (uint32_t) obj->oAction;

            // Insercion ordenada por distToMario ascendente.
            uint32_t slot = (count < RL_MAX_ENEMIES) ? count : RL_MAX_ENEMIES - 1;
            while (slot > 0 && best[slot - 1].distToMario > dist) {
                best[slot] = best[slot - 1];
                slot--;
            }
            best[slot] = e;
            if (count < RL_MAX_ENEMIES) {
                count++;
            }
        }
    }

    sShm->numEnemies = count;
    memcpy(sShm->enemies, best, sizeof(best));
}

// --- Barreras de memoria -----------------------------------------------------
// __sync_synchronize() es un full fence disponible en GCC/Clang sin depender de
// <stdatomic.h>, que choca con algunos shims del SDK de N64 en este codebase.
#define RL_FENCE() __sync_synchronize()

// --- Handler de señales ------------------------------------------------------
// Limpia la shm y los semáforos cuando el proceso recibe SIGTERM/SIGINT.
// rl_state_shutdown() es idempotente (sShutdownDone), así que es seguro llamarlo
// aquí aunque también esté registrado en atexit().
//
// Salimos con _exit() y no con exit(): exit() ejecutaría los atexit desde el
// contexto de la señal (reentrancia) y no es async-signal-safe. _exit() sí lo es.
static void rl_signal_handler(int sig) {
    rl_state_shutdown();
    _exit(128 + sig);
}

// --- API ---------------------------------------------------------------------

int rl_state_init(const char *name, int sync_mode) {
    if (name == NULL || name[0] == '\0') {
        fprintf(stderr, "[rl_state] nombre de shm vacio\n");
        return -1;
    }

    snprintf(sShmPath,       sizeof(sShmPath),       "%s%s", RL_SHM_PREFIX,        name);
    snprintf(sStateSemPath,  sizeof(sStateSemPath),  "%s%s", RL_STATE_SEM_PREFIX,  name);
    snprintf(sActionSemPath, sizeof(sActionSemPath), "%s%s", RL_ACTION_SEM_PREFIX, name);

    sShmFd = shm_open(sShmPath, O_CREAT | O_RDWR, 0600);
    if (sShmFd < 0) {
        fprintf(stderr, "[rl_state] shm_open(%s) fallo: %s\n", sShmPath, strerror(errno));
        return -1;
    }

    if (ftruncate(sShmFd, (off_t)sizeof(struct RLStateShm)) != 0) {
        fprintf(stderr, "[rl_state] ftruncate fallo: %s\n", strerror(errno));
        close(sShmFd);
        sShmFd = -1;
        shm_unlink(sShmPath);
        return -1;
    }

    sShm = (struct RLStateShm *)mmap(NULL, sizeof(struct RLStateShm),
                                     PROT_READ | PROT_WRITE, MAP_SHARED, sShmFd, 0);
    if (sShm == MAP_FAILED) {
        fprintf(stderr, "[rl_state] mmap fallo: %s\n", strerror(errno));
        sShm = NULL;
        close(sShmFd);
        sShmFd = -1;
        shm_unlink(sShmPath);
        return -1;
    }

    // El fd ya no es necesario una vez mapeado.
    close(sShmFd);
    sShmFd = -1;

    // magic se queda deliberadamente en 0 aqui: se publica al final del init.
    memset(sShm, 0, sizeof(struct RLStateShm));
    sShm->version = RL_STATE_VERSION;

    if (sync_mode) {
        // Arrancamos los dos semáforos a 0: el juego publica el primer estado y
        // hace post antes de esperar la primera acción.
        //
        // sem_open() con O_CREAT sobre un semáforo que YA existe ignora el valor
        // inicial (comportamiento POSIX). Si un run anterior murió por señal sin
        // limpiar, heredaríamos su contador (>0), sem_wait() no bloquearía y el
        // handshake con Python se desincronizaría en silencio. Por eso borramos
        // cualquier resto y creamos con O_EXCL: así el contador arranca a 0 de
        // forma garantizada o el init falla de forma ruidosa.
        sem_unlink(sStateSemPath);   // ENOENT es el caso normal: se ignora
        sem_unlink(sActionSemPath);

        int semErr = 0;
        sStateSem = sem_open(sStateSemPath, O_CREAT | O_EXCL, 0600, 0);
        if (sStateSem == SEM_FAILED) {
            semErr = errno;
        } else {
            sActionSem = sem_open(sActionSemPath, O_CREAT | O_EXCL, 0600, 0);
            if (sActionSem == SEM_FAILED) {
                semErr = errno;
            }
        }

        if (semErr != 0) {
            if (semErr == EEXIST) {
                // Carrera con otra instancia usando el mismo <name>. No caemos a
                // O_CREAT sin O_EXCL: sería heredar un contador desconocido.
                fprintf(stderr, "[rl_state] el nombre '%s' ya esta en uso por otra "
                                "instancia (semaforos existentes): usa otro nombre\n", name);
            } else {
                fprintf(stderr, "[rl_state] sem_open fallo: %s\n", strerror(semErr));
            }
            if (sStateSem != SEM_FAILED) {
                sem_close(sStateSem);
                sem_unlink(sStateSemPath);
                sStateSem = SEM_FAILED;
            }
            if (sActionSem != SEM_FAILED) {
                sem_close(sActionSem);
                sem_unlink(sActionSemPath);
                sActionSem = SEM_FAILED;
            }
            munmap(sShm, sizeof(struct RLStateShm));
            sShm = NULL;
            shm_unlink(sShmPath);
            return -1;
        }
        sSyncMode     = 1;
        gRLSyncActive = 1;
    }

    // -------------------------------------------------------------------------
    // (2) BARRERA DE PUBLICACION — CONTRATO CON PYTHON. NO MOVER ESTA LINEA.
    //
    // `magic` tiene que ser LA ULTIMA escritura del init. El invariante que usa
    // el lado Python para saber que puede mapear y leer la shm es:
    //
    //     magic == RL_STATE_MAGIC  =>  la shm tiene su tamaño final (ftruncate
    //                                  hecho), está inicializada (memset+version)
    //                                  y los semáforos ya existen.
    //
    // shm_open() crea el objeto con tamaño 0 antes del ftruncate, así que si
    // `magic` se escribiera antes Python podría mapear una shm truncada o
    // esperar semáforos que aún no existen. Si el init falla a mitad, `magic`
    // debe quedarse en 0: nunca lo asignes antes de este punto.
    // -------------------------------------------------------------------------
    RL_FENCE();
    sShm->magic = RL_STATE_MAGIC;

    gRLActive = 1;
    atexit(rl_state_shutdown);

    // (4) El juego acaba en un while(1) y siempre muere por señal (SIGTERM del
    // launcher, SIGINT de Ctrl+C), y las señales no ejecutan los atexit. Sin este
    // handler la shm y los semáforos quedan huérfanos en el kernel en cada run.
    // Se instala solo en caso de éxito para no alterar el juego en modo normal.
    {
        struct sigaction sa;
        memset(&sa, 0, sizeof(sa));
        sa.sa_handler = rl_signal_handler;
        sigemptyset(&sa.sa_mask);
        sa.sa_flags = 0;
        sigaction(SIGTERM, &sa, NULL);
        sigaction(SIGINT,  &sa, NULL);
    }

    fprintf(stderr, "[rl_state] shm %s lista (%zu bytes, sync=%d)\n",
            sShmPath, sizeof(struct RLStateShm), sSyncMode);
    return 0;
}

void rl_state_publish(void) {
    if (sShm == NULL) {
        return;
    }

    struct MarioState *m = gMarioState;

    sShm->frame = (uint32_t)gGlobalTimer;

    if (m != NULL) {
        sShm->pos[0] = (float)m->pos[0];
        sShm->pos[1] = (float)m->pos[1];
        sShm->pos[2] = (float)m->pos[2];

        sShm->vel[0] = (float)m->vel[0];
        sShm->vel[1] = (float)m->vel[1];
        sShm->vel[2] = (float)m->vel[2];

        sShm->forwardVel = (float)m->forwardVel;

        sShm->faceAngle[0] = (int32_t)m->faceAngle[0];
        sShm->faceAngle[1] = (int32_t)m->faceAngle[1];
        sShm->faceAngle[2] = (int32_t)m->faceAngle[2];

        sShm->action      = (uint32_t)m->action;
        sShm->prevAction  = (uint32_t)m->prevAction;
        sShm->actionState = (uint32_t)m->actionState;
        sShm->actionTimer = (uint32_t)m->actionTimer;

        sShm->numCoins = (int32_t)m->numCoins;
        sShm->health   = (int32_t)m->health;

        sShm->floorAngle  = (int32_t)m->floorAngle;

        sShm->flags = (uint32_t)m->flags;
        sShm->input = (uint32_t)m->input;

        const uint32_t actionId = (uint32_t)(m->action & 0x1FF);

        // inLevel se calcula PRIMERO: las demas banderas derivadas dependen de el.
        const int inLevel = (gCurrLevelNum > 0 && m->action != 0 && m->marioObj != NULL);
        sShm->inLevel = (uint8_t)inLevel;

        // Fuera del nivel Mario no existe (health=0, action=0): un Mario que todavia
        // no ha nacido no esta muerto, ni en el aire, ni cogiendo una estrella. Sin
        // esta guarda, isDead valia 1 durante los ~169 frames de carga y cualquier
        // entorno que empezase el episodio ahi lo terminaba en el primer step.
        sShm->isDead = (uint8_t)(inLevel &&
                                 ((m->health < 0x100) ||
                                  action_in_set(actionId, sDeathActions,
                                                sizeof(sDeathActions) / sizeof(sDeathActions[0]))));

        sShm->starCollected = (uint8_t)(inLevel &&
                                        action_in_set(actionId, sStarActions,
                                                      sizeof(sStarActions) / sizeof(sStarActions[0])));

        sShm->inAir = (uint8_t)(inLevel && (m->action & ACT_FLAG_AIR) != 0);

        // Mismo contrato que las demas banderas derivadas: fuera del nivel los
        // objetos no existen (o son los del area anterior a medio descargar), asi
        // que el bloque de enemigos se publica vacio en vez de con basura.
        if (inLevel) {
            rl_collect_enemies(m);
        } else {
            sShm->numEnemies = 0;
            memset(sShm->enemies, 0, sizeof(sShm->enemies));
        }
    } else {
        sShm->inLevel = 0;
        sShm->numEnemies = 0;
        memset(sShm->enemies, 0, sizeof(sShm->enemies));
    }

    // Asegura que todas las escrituras del estado sean visibles antes del post.
    RL_FENCE();

    if (!sSyncMode) {
        return;
    }

    // Estado publicado: despertar a Python.
    if (sStateSem != SEM_FAILED) {
        sem_post(sStateSem);
    }

    // Esperar la acción del agente (reintentar si nos interrumpe una señal).
    if (sActionSem != SEM_FAILED) {
        while (sem_wait(sActionSem) != 0) {
            if (errno != EINTR) {
                fprintf(stderr, "[rl_state] sem_wait fallo: %s\n", strerror(errno));
                break;
            }
        }

        RL_FENCE();
        gRLActionBits = sShm->actionBits;
        gRLButtons    = RL_ACTION_BUTTONS(gRLActionBits);
        gRLStickX     = RL_ACTION_STICK_X(gRLActionBits);
        gRLStickY     = RL_ACTION_STICK_Y(gRLActionBits);
    }
}

void rl_state_shutdown(void) {
    if (sShutdownDone) {
        return;
    }
    sShutdownDone = 1;

    gRLActive     = 0;
    gRLSyncActive = 0;

    if (sShm != NULL) {
        // (3) El mapeo de Python sobrevive al shm_unlink, así que marcamos la shm
        // como muerta para que detecte el cierre en vez de leer datos rancios.
        sShm->magic = 0;
        RL_FENCE();
        munmap(sShm, sizeof(struct RLStateShm));
        sShm = NULL;
    }
    if (sShmFd >= 0) {
        close(sShmFd);
        sShmFd = -1;
    }
    if (sShmPath[0] != '\0') {
        shm_unlink(sShmPath);
    }

    if (sSyncMode) {
        if (sStateSem != SEM_FAILED) {
            // Un post final evita que Python quede bloqueado si el juego muere.
            sem_post(sStateSem);
            sem_close(sStateSem);
            sStateSem = SEM_FAILED;
        }
        if (sActionSem != SEM_FAILED) {
            sem_close(sActionSem);
            sActionSem = SEM_FAILED;
        }
        if (sStateSemPath[0] != '\0') {
            sem_unlink(sStateSemPath);
        }
        if (sActionSemPath[0] != '\0') {
            sem_unlink(sActionSemPath);
        }
        sSyncMode = 0;
    }
}
