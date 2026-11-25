import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class GameLauncher {

    /** Default env id registered in gymnasium_env/__init__.py */
    public static final String DEFAULT_ENV_ID = "gymnasium_env/Killer-v0";

    /**
     * Configuration handed to the Python RL runner.
     * One single Python process drives the N game instances.
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
        String  shmPrefix
    ) {}

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
        return args;
    }

}
