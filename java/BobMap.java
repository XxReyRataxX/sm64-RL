import java.util.ArrayList;
import java.util.List;

/**
 * Datos estáticos de Bob-omb Battlefield para la vista top-down del launcher.
 *
 * <p>Todo lo de aquí está copiado literalmente de los fuentes del juego, así que si
 * alguien mueve un objeto en el nivel esto se queda obsoleto en silencio:
 * <ul>
 *   <li>{@code levels/bob/areas/1/macro.inc.c} → objetos macro (goombas, bob-ombs,
 *       chain chomp, spawners…).</li>
 *   <li>{@code levels/bob/script.c} → objetos con acto (king bob-omb, koopas,
 *       bowling balls) y el {@code MARIO_POS} del warp por defecto.</li>
 * </ul>
 *
 * <p>Deliberadamente SIN dependencias de JavaFX: son datos puros, los colores van
 * como hex y los convierte quien dibuja. Así esta clase se puede compilar y testear
 * sin el SDK de JavaFX delante.
 */
public final class BobMap {

    private BobMap() {}

    // ── Encuadre del plano ─────────────────────────────────────
    /**
     * Bounding box usado para el plano. Los objetos reales de BoB llegan a ~7157 en X
     * y ~-7161 en Z, así que ±8000 los mete todos con un pequeño margen y además es un
     * número redondo: la rejilla de 1000 cae clavada en los bordes.
     */
    public static final double WORLD_MIN  = -8000.0;
    public static final double WORLD_MAX  =  8000.0;
    public static final double GRID_STEP  =  1000.0;

    /** Warp por defecto: {@code MARIO_POS(area 1, yaw 135, pos -6558, 0, 6464)}. */
    public static final double WARP_X   = -6558.0;
    public static final double WARP_Z   =  6464.0;
    public static final double WARP_YAW =    135.0;

    // ── Presets (mismos valores y orden que _BOB_PRESETS en runner.py) ──
    /** Y siempre a null/auto-floor: el binario la resuelve con find_floor(). */
    public record Preset(String name, String label, double x, double z, double yaw) {}

    public static final List<Preset> PRESETS = List.of(
        new Preset("salida",   "Salida (warp)", -6558.0,  6464.0,   0.0),
        new Preset("cima",     "Cima",           1636.0, -5567.0, 180.0),
        new Preset("canon",    "Cañón",         -5694.0,  5600.0,  90.0),
        new Preset("red_coin", "Red coin",      -6000.0,  2400.0,   0.0)
    );

    // ── Tipos de marcador ──────────────────────────────────────
    /** label = texto de leyenda, hex = color, r = radio en píxeles de canvas. */
    public enum Kind {
        GOOMBA         ("goomba",          "#8B5A2B", 4.0),
        GOOMBA_TRIPLET ("goomba x3",       "#C08A45", 6.5),
        CHAIN_CHOMP    ("chain chomp",     "#101010", 7.5),
        GATE           ("puerta chomp",    "#8A6A3A", 4.0),
        BOBOMB         ("bob-omb",         "#C0392B", 4.0),
        BOBOMB_STILL   ("bob-omb quieto",  "#7E2A20", 4.0),
        KING_BOBOMB    ("king bob-omb",    "#FF3B30", 9.5),
        KOOPA_QUICK    ("koopa the quick", "#4A90E2", 7.0),
        KOOPA          ("koopa troopa",    "#49B265", 6.0),
        WATER_BOMB     ("water bomb spw.", "#9B59B6", 5.0),
        BOWLING_BALL   ("bola de bolos",   "#9AA0A6", 5.0),
        BUDDY          ("bob-omb buddy",   "#FF8FB1", 4.0);

        public final String label;
        public final String hex;
        public final double r;

        Kind(String label, String hex, double r) {
            this.label = label; this.hex = hex; this.r = r;
        }
    }

    /** tag = texto corto dibujado encima del marcador (p.ej. "3"); puede ser null. */
    public record Marker(Kind kind, double x, double z, String tag) {}

    // ── Objetos presentes en todos los actos (macro.inc.c) ─────
    private static final Marker[] ALWAYS = {
        // chain chomp + su puerta (la puerta viene de script_func_local_1)
        new Marker(Kind.CHAIN_CHOMP, 260,   1920, null),
        new Marker(Kind.GATE,        1456,   446, null),

        // goombas sueltos
        new Marker(Kind.GOOMBA, -2713, 5778, null),
        new Marker(Kind.GOOMBA,  -342, 5433, null),

        // spawners de trío: cada uno suelta 3 goombas, de ahí el tag "3"
        new Marker(Kind.GOOMBA_TRIPLET,  3640, 6280, "3"),
        new Marker(Kind.GOOMBA_TRIPLET,  6060, 2000, "3"),
        new Marker(Kind.GOOMBA_TRIPLET, -6050, 1250, "3"),

        // bob-ombs enemigos (macro_bobomb)
        new Marker(Kind.BOBOMB, -3080, -5200, null),
        new Marker(Kind.BOBOMB, -3688, -3813, null),
        new Marker(Kind.BOBOMB, -4629, -1772, null),
        new Marker(Kind.BOBOMB, -3480, -2120, null),
        new Marker(Kind.BOBOMB, -3800,  -460, null),
        new Marker(Kind.BOBOMB,  6888, -5608, null),
        new Marker(Kind.BOBOMB,  2350,  3700, null),
        new Marker(Kind.BOBOMB, -1750, -2800, null),
        new Marker(Kind.BOBOMB, -1400,  -950, null),
        new Marker(Kind.BOBOMB, -2650,  1750, null),
        new Marker(Kind.BOBOMB, -1900,  3450, null),
        new Marker(Kind.BOBOMB_STILL, 1127, -2495, null),

        // water bomb spawners (lluvia de bombas de agua sobre la montaña)
        new Marker(Kind.WATER_BOMB,  3620,  4800, null),
        new Marker(Kind.WATER_BOMB, -5141,  -743, null),
        new Marker(Kind.WATER_BOMB, -4208, -2036, null),

        // bob-omb buddies: inofensivos, pero son referencias visuales claras
        new Marker(Kind.BUDDY, -5723, 6017, null),
        new Marker(Kind.BUDDY, -6250, 6680, null),
    };

    /**
     * Bolas de bolos. Los spawners cambian de comportamiento según el acto
     * (bhvBobBowlingBallSpawner en actos 1-2, bhvTtmBowlingBallSpawner en 3-6) pero las
     * posiciones son idénticas, así que para el plano da igual el acto.
     */
    private static final Marker[] BOWLING_ALWAYS = {
        new Marker(Kind.BOWLING_BALL, 1535, -5561, null),
        new Marker(Kind.BOWLING_BALL,  524, -5400, null),
        new Marker(Kind.BOWLING_BALL, -993, -3565, null),
        new Marker(Kind.BOWLING_BALL, -785, -4301, null),
    };
    /** bhvPitBowlingBall extra, declarada con ACT_2|ACT_3|ACT_4|ACT_5|ACT_6. */
    private static final Marker BOWLING_ACT2_PLUS =
        new Marker(Kind.BOWLING_BALL, -93, -3414, null);

    /**
     * Marcadores a dibujar para un acto (1-6).
     *
     * <p>Lo que NO está aquí y existe en el nivel: los goombas que el chain chomp o los
     * spawners generan en runtime (posición dinámica), las bombas de agua ya lanzadas,
     * y los objetos no hostiles (cajas, cañones cerrados, monedas, carteles, postes,
     * plataformas). Se dejan fuera a propósito: el plano es para ver amenazas y spawns,
     * no para replicar el nivel.
     */
    public static List<Marker> enemiesFor(int act) {
        List<Marker> out = new ArrayList<>(ALWAYS.length + 8);
        for (Marker m : ALWAYS) out.add(m);
        for (Marker m : BOWLING_ALWAYS) out.add(m);
        if (act >= 2) out.add(BOWLING_ACT2_PLUS);

        if (act == 1) {
            // bhvKingBobomb, ACT_1
            out.add(new Marker(Kind.KING_BOBOMB, 1636, -5567, null));
        }
        if (act == 2) {
            // bhvKoopa con behParam 0x01020000, ACT_2 → Koopa the Quick
            out.add(new Marker(Kind.KOOPA_QUICK, -4004, 5221, null));
        }
        if (act >= 3) {
            // bhvKoopa con behParam 0x00010000, ACT_3..ACT_6 → koopa normal
            out.add(new Marker(Kind.KOOPA, 3400, 6500, null));
        }
        return out;
    }

    // ══════════════════════════════════════════════════════════
    /**
     * Mersenne Twister compatible bit a bit con el módulo {@code random} de CPython.
     *
     * <p>Existe por una razón concreta: en modo "área aleatoria" el reparto de spawns lo
     * hace {@code runner.py} con {@code random.Random(seed + idx*1_000_003)}. Si la
     * previsualización usara {@code java.util.Random} (LCG de 48 bits, otro algoritmo)
     * los puntos del plano NO serían los que el juego usa, y una vista previa que miente
     * es peor que no tener vista previa. Con esto, canvas y entrenamiento coinciden.
     *
     * <p>Port de {@code init_genrand} / {@code init_by_array} / {@code genrand_uint32} /
     * {@code genrand_res53} tal y como están en {@code Modules/_randommodule.c}.
     */
    public static final class PyRandom {
        private static final int N = 624;
        private static final int M = 397;
        private static final int MATRIX_A = 0x9908b0df;

        private final int[] mt = new int[N];
        private int mti = N + 1;

        /** Equivalente a {@code random.Random(seed)} con seed entero (usa |seed|). */
        public PyRandom(long seed) {
            long s = Math.abs(seed);
            // CPython parte el entero en palabras de 32 bits, little-endian.
            int words = 0;
            for (long t = s; t != 0; t >>>= 32) words++;
            int[] key = new int[Math.max(1, words)];
            long t = s;
            for (int i = 0; i < key.length; i++) { key[i] = (int) t; t >>>= 32; }
            initByArray(key);
        }

        private void initGenrand(int s) {
            mt[0] = s;
            for (int i = 1; i < N; i++) {
                mt[i] = 1812433253 * (mt[i - 1] ^ (mt[i - 1] >>> 30)) + i;
            }
            mti = N;
        }

        private void initByArray(int[] key) {
            initGenrand(19650218);
            int i = 1, j = 0;
            for (int k = Math.max(N, key.length); k != 0; k--) {
                mt[i] = (mt[i] ^ ((mt[i - 1] ^ (mt[i - 1] >>> 30)) * 1664525)) + key[j] + j;
                i++; j++;
                if (i >= N) { mt[0] = mt[N - 1]; i = 1; }
                if (j >= key.length) j = 0;
            }
            for (int k = N - 1; k != 0; k--) {
                mt[i] = (mt[i] ^ ((mt[i - 1] ^ (mt[i - 1] >>> 30)) * 1566083941)) - i;
                i++;
                if (i >= N) { mt[0] = mt[N - 1]; i = 1; }
            }
            mt[0] = 0x80000000;
            mti = N;
        }

        private int genrandUint32() {
            int y;
            if (mti >= N) {
                int kk = 0;
                for (; kk < N - M; kk++) {
                    y = (mt[kk] & 0x80000000) | (mt[kk + 1] & 0x7fffffff);
                    mt[kk] = mt[kk + M] ^ (y >>> 1) ^ ((y & 1) != 0 ? MATRIX_A : 0);
                }
                for (; kk < N - 1; kk++) {
                    y = (mt[kk] & 0x80000000) | (mt[kk + 1] & 0x7fffffff);
                    mt[kk] = mt[kk + (M - N)] ^ (y >>> 1) ^ ((y & 1) != 0 ? MATRIX_A : 0);
                }
                y = (mt[N - 1] & 0x80000000) | (mt[0] & 0x7fffffff);
                mt[N - 1] = mt[M - 1] ^ (y >>> 1) ^ ((y & 1) != 0 ? MATRIX_A : 0);
                mti = 0;
            }
            y = mt[mti++];
            y ^= (y >>> 11);
            y ^= (y << 7) & 0x9d2c5680;
            y ^= (y << 15) & 0xefc60000;
            y ^= (y >>> 18);
            return y;
        }

        /** {@code random.random()} — genrand_res53, 53 bits de mantisa. */
        public double random() {
            long a = genrandUint32() >>> 5;
            long b = genrandUint32() >>> 6;
            return (a * 67108864.0 + b) * (1.0 / 9007199254740992.0);
        }

        /** {@code random.uniform(a, b)} = a + (b-a)*random(). */
        public double uniform(double a, double b) {
            return a + (b - a) * random();
        }
    }

    /**
     * Mismo reparto que {@code spawn_args_for()} en modo random_area: semilla derivada
     * por instancia y orden de consumo x → z → yaw. Devuelve {x, z, yaw}.
     */
    public static double[] sampleArea(double x0, double z0, double x1, double z1,
                                      long seed, int idx) {
        PyRandom rng = new PyRandom(seed + (long) idx * 1_000_003L);
        double x   = rng.uniform(Math.min(x0, x1), Math.max(x0, x1));
        double z   = rng.uniform(Math.min(z0, z1), Math.max(z0, z1));
        double yaw = rng.uniform(0.0, 360.0);
        return new double[] { x, z, yaw };
    }
}
