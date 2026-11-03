import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import java.io.File;

public class GameLauncher {

    public static void launch(String courseId, double speed) {
        try {
            File binary = new File(
                GameLauncher.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI()
            ).getParentFile()
             .toPath()
             .resolve("build/us_pc/sm64.us")
             .toFile();

            if (!binary.exists()) {
                showError("Binary not found:\n" + binary.getAbsolutePath() +
                    "\n\nCompile the game first with: make VERSION=us");
                return;
            }

            if (!binary.canExecute()) binary.setExecutable(true);

            new ProcessBuilder(
                binary.getAbsolutePath(),
                "--level", courseId,
                "--speed", String.format("%.1f", speed)
            ).inheritIO().start();

        } catch (Exception ex) {
            showError("Failed to launch the game:\n" + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private static void showError(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(AlertType.ERROR);
            alert.setTitle("SM64 — Error");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
}
