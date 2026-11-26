#ifndef NO_MUSIC_H
#define NO_MUSIC_H

#include "types.h"

/*
 * Silenciado de musica para entrenamiento RL (flag CLI --no-music).
 *
 * Definido en src/pc/pc_main.c, parseado en main().
 * Consumido en src/audio/external.c (seq_player_play_sequence()), que es el
 * unico punto por el que arrancan TODAS las secuencias del juego:
 *   - SEQ_PLAYER_LEVEL (0) -> musica de fondo del nivel
 *   - SEQ_PLAYER_ENV   (1) -> jingles (estrella, puzzle, carrera, Peach...)
 *   - SEQ_PLAYER_SFX   (2) -> secuencia del reproductor de efectos de sonido
 *
 * Con gNoMusic == 1 se saltan los players LEVEL y ENV y se deja intacto el
 * player SFX, asi que los efectos de sonido siguen funcionando.
 *
 * El corte se hace DESPUES de la contabilidad interna
 * (sCurrentBackgroundMusicSeqId, dinamicas de musica, fades de canal), de modo
 * que get_current_background_music() y la cola sBackgroundMusicQueue se
 * comportan igual que en el juego original; lo unico que no ocurre es el
 * load_sequence() / arranque real de la reproduccion.
 *
 * Nota: esto NO forma parte del ABI de rl_state; es solo input por CLI.
 */

extern s8 gNoMusic; // 0 = audio normal, 1 = musica y jingles silenciados

#endif // NO_MUSIC_H
