import java.io.*;
import javax.swing.*;

/**
 * Launches the SM64 PC port binary with the given course ID.
 *
 * Usage:
 *   GameLauncher.launch("1");   // Bob-omb Battlefield
 *   GameLauncher.launch("4");   // Cool, Cool Mountain
 *
 * The binary path below assumes the standard make output location.
 * Adjust BINARY_PATH if your build is elsewhere.
 */
public class GameLauncher {

    // Relative to the project root (one level up from java/)
    private static final String BINARY_PATH = "../build/us_pc/sm64.us";

    public static void launch(String courseId) {
        try {
            File binary = new File(
                GameLauncher.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI()
            ).getParentFile()   // project root (class files are in java/, parent is root)
             .toPath()
             .resolve("build/us_pc/sm64.us")
             .toFile();

            if (!binary.exists()) {
                JOptionPane.showMessageDialog(null,
                    "Binary not found:\n" + binary.getAbsolutePath() +
                    "\n\nCompile the game first with: make VERSION=us",
                    "SM64 — Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (!binary.canExecute()) binary.setExecutable(true);

            ProcessBuilder pb = new ProcessBuilder(
                binary.getAbsolutePath(),
                "--level", courseId
            );
            pb.inheritIO();
            pb.start();

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(null,
                "Failed to launch the game:\n" + ex.getMessage(),
                "SM64 — Error", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }
}
