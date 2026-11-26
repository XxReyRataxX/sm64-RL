import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class GameLauncher {

    /** Default env id registered in gymnasium_env/__init__.py */
    public static final String DEFAULT_ENV_ID = "gymnasium_env/Killer-v0";

    /** Valor de --spawn-mode del runner. DEFAULT = no emitir el flag. */
    public enum SpawnMode {
        DEFAULT("default"),
        FIXED("fixed"),
        RANDOM_AREA("random_area"),
        PRESET("preset");

        private final String flag;
        SpawnMode(String flag) { this.flag = flag; }
        public String flag() { return flag; }
    }

    /**
     * Configuration handed to the Python RL runner.
     * One single Python process drives the N game instances.
     *
     * <p>Campos de spawn (todos opcionales; null / vacío = no emitir el flag):
     * <ul>
     *   <li>{@code spawns}: cada entrada es {X, Y, Z, YAW}. Y = {@link Double#NaN}
     *       significa "sin Y", y el runner la manda como campo vacío para que el
     *       binario C la resuelva con find_floor(). Se usa NaN y no un Double porque
     *       la firma acordada con el launcher es {@code double[]}.</li>
     *   <li>{@code spawnArea}: {x0, z0, x1, z1}.</li>
     * </ul>
     */
    public record TrainingConfig(
        String  envId,
        String  courseId,
        int     starId,
        double  speed,
        int     instances,
        boolean render,
        String  mode,        // "train" | "eval"
        int     episodes,
        int     maxSteps,
        String  algo,
        String  shmPrefix,
        SpawnMode      spawnMode,
        List<double[]> spawns,
        double[]       spawnArea,
        Double         spawnY,
        Long           spawnSeed
    ) {
        /** Atajo para las rutas que no tocan spawn: mantiene el warp del juego. */
        public TrainingConfig(String envId, String courseId, int starId, double speed,
                              int instances, boolean render, String mode, int episodes,
                              int maxSteps, String algo, String shmPrefix) {
            this(envId, courseId, starId, speed, instances, render, mode, episodes,
                 maxSteps, algo, shmPrefix,
                 SpawnMode.DEFAULT, List.of(), null, null, null);
        }
    }

    /** Project root: the directory that contains build/, gymnasium_env/, java/ ... */
    public static File projectRoot() {
        try {
            return new File(
                GameLauncher.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI()
            ).getParentFile();
        } catch (Exception e) {
            return null;
        }
    }

    public static File findBinary() {
        File root = projectRoot();
        if (root == null) return null;
        return root.toPath().resolve("build/us_pc/sm64.us").toFile();
    }

    /** Prefer the project venv interpreter; fall back to whatever python3 is on PATH. */
    public static String findPython() {
        File root = projectRoot();
        if (root != null) {
            String[] candidates = { ".venv/bin/python3", "venv/bin/python3" };
            for (String c : candidates) {
                File f = root.toPath().resolve(c).toFile();
                if (f.isFile() && f.canExecute()) return f.getAbsolutePath();
            }
        }
        return "python3";
    }

    /**
     * Builds the exact CLI contract consumed by gymnasium_env.runner.
     * Java never spawns the game binary in RL mode — Python owns every instance.
     */
    public static List<String> buildTrainingCommand(TrainingConfig cfg) {
        File binary = findBinary();
        String binaryPath = (binary != null) ? binary.getAbsolutePath() : "build/us_pc/sm64.us";

        List<String> args = new ArrayList<>();
        args.add(findPython());
        args.add("-u");
        args.add("-m");
        args.add("gymnasium_env.runner");
        args.add("--env");        args.add(cfg.envId());
        args.add("--binary");     args.add(binaryPath);
        args.add("--shm-prefix"); args.add(cfg.shmPrefix());
        args.add("--instances");  args.add(String.valueOf(cfg.instances()));
        args.add("--level");      args.add(cfg.courseId());
        args.add("--star");       args.add(String.valueOf(cfg.starId()));
        args.add("--speed");      args.add(String.format(java.util.Locale.ROOT, "%.1f", cfg.speed()));
        args.add("--episodes");   args.add(String.valueOf(cfg.episodes()));
        args.add("--max-steps");  args.add(String.valueOf(cfg.maxSteps()));
        args.add("--algo");       args.add(cfg.algo());
        args.add("--mode");       args.add(cfg.mode());
        if (cfg.render()) args.add("--render");
        appendSpawnArgs(args, cfg);
        return args;
    }

    /**
     * Flags de spawn. En modo default no se emite NADA (ni --spawn-mode), porque el
     * runner ya trata la ausencia del flag como "usa el warp del nivel"; mandar
     * "--spawn-mode default" sería ruido equivalente.
     *
     * <p>Cada bloque va condicionado a su propio dato y no al modo: así una lista de
     * spawns vacía en modo fixed llega al runner, que es quien tiene la validación
     * cruzada (--spawn-mode fixed sin --spawn aborta con mensaje claro). Duplicar esa
     * validación aquí solo crearía dos sitios donde se puede quedar desincronizada.
     */
    private static void appendSpawnArgs(List<String> args, TrainingConfig cfg) {
        SpawnMode mode = (cfg.spawnMode() == null) ? SpawnMode.DEFAULT : cfg.spawnMode();
        if (mode == SpawnMode.DEFAULT) return;

        args.add("--spawn-mode"); args.add(mode.flag());

        if (cfg.spawns() != null) {
            for (double[] s : cfg.spawns()) {
                if (s == null || s.length < 4) continue;
                args.add("--spawn");
                args.add(num(s[0]) + "," + (Double.isNaN(s[1]) ? "" : num(s[1]))
                         + "," + num(s[2]) + "," + num(s[3]));
            }
        }
        double[] area = cfg.spawnArea();
        if (area != null && area.length == 4) {
            args.add("--spawn-area");
            args.add(num(area[0]) + "," + num(area[1]) + ","
                     + num(area[2]) + "," + num(area[3]));
        }
        if (cfg.spawnY() != null) {
            args.add("--spawn-y"); args.add(num(cfg.spawnY()));
        }
        if (cfg.spawnSeed() != null) {
            args.add("--spawn-seed"); args.add(String.valueOf(cfg.spawnSeed()));
        }
    }

    /** Locale.ROOT obligatorio: con locale es_ES el separador sería "," y rompería el CSV. */
    private static String num(double v) {
        return String.format(java.util.Locale.ROOT, "%.3f", v);
    }

}
