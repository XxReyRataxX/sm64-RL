// -----------------------------------------------------------------------------
// rl_state.c — Implementación del export de estado a memoria compartida POSIX.
//
// Protocolo de lectura para Python (seqlock):
//   do { s1 = seq; if (s1 & 1) continue; <copiar struct>; s2 = seq; } while (s1 != s2);
// -----------------------------------------------------------------------------

#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <semaphore.h>

#include "sm64.h"

#include "game/level_update.h"
#include "game/area.h"
#include "game/game_init.h"

#include "rl_state.h"

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

// --- Barreras de memoria -----------------------------------------------------
// __sync_synchronize() es un full fence disponible en GCC/Clang sin depender de
// <stdatomic.h>, que choca con algunos shims del SDK de N64 en este codebase.
#define RL_FENCE() __sync_synchronize()

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

    memset(sShm, 0, sizeof(struct RLStateShm));
    sShm->magic   = RL_STATE_MAGIC;
    sShm->version = RL_STATE_VERSION;
    sShm->seq     = 0;

    if (sync_mode) {
        // Arrancamos los dos semáforos a 0: el juego publica el primer estado y
        // hace post antes de esperar la primera acción.
        sStateSem  = sem_open(sStateSemPath,  O_CREAT, 0600, 0);
        sActionSem = sem_open(sActionSemPath, O_CREAT, 0600, 0);
        if (sStateSem == SEM_FAILED || sActionSem == SEM_FAILED) {
            fprintf(stderr, "[rl_state] sem_open fallo: %s\n", strerror(errno));
            if (sStateSem != SEM_FAILED) {
                sem_close(sStateSem);
                sStateSem = SEM_FAILED;
            }
            if (sActionSem != SEM_FAILED) {
                sem_close(sActionSem);
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

    gRLActive = 1;
    atexit(rl_state_shutdown);

    fprintf(stderr, "[rl_state] shm %s lista (%zu bytes, sync=%d)\n",
            sShmPath, sizeof(struct RLStateShm), sSyncMode);
    return 0;
}

void rl_state_publish(void) {
    if (sShm == NULL) {
        return;
    }

    struct MarioState *m = gMarioState;

    // --- seqlock: entrar en escritura (seq impar) ---
    RL_FENCE();
    sShm->seq++;
    RL_FENCE();

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

        sShm->isDead = (uint8_t)((m->health < 0x100) ||
                                 action_in_set(actionId, sDeathActions,
                                               sizeof(sDeathActions) / sizeof(sDeathActions[0])));

        sShm->starCollected = (uint8_t)action_in_set(actionId, sStarActions,
                                                     sizeof(sStarActions) / sizeof(sStarActions[0]));

        sShm->inLevel = (uint8_t)(gCurrLevelNum > 0 && m->action != 0 && m->marioObj != NULL);
        sShm->inAir   = (uint8_t)((m->action & ACT_FLAG_AIR) != 0);
    } else {
        sShm->inLevel = 0;
    }

    // --- seqlock: salir de escritura (seq par) ---
    RL_FENCE();
    sShm->seq++;
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
