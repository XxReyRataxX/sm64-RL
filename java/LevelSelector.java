import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import java.util.Base64;

public class LevelSelector extends Application {

    // ── Palette ────────────────────────────────────────────────
    private static final String BG      = "#0C0C0E";
    private static final String SURFACE = "#141416";
    private static final String SURF_H  = "#1C1C20";
    private static final String BORDER  = "#252528";
    private static final String TEXT1   = "#F0F0F2";
    private static final String TEXT2   = "#72727A";
    private static final String ACCENT  = "#4F8EF7";
    private static final String ACCENT2 = "#6FA3FF";

    // ── Levels ─────────────────────────────────────────────────
    private static final Level[] LEVELS = {
        new Level("Bob-omb Battlefield", "Course 1", "9"),
        new Level("Cool, Cool Mountain",  "Course 4", "5")
    };

    // ── State ──────────────────────────────────────────────────
    private ComboBox<String> levelCombo;
    private Slider           speedSlider;
    private Label            speedLabel;
    private double           dragX, dragY;

    // ══════════════════════════════════════════════════════════

    @Override
    public void start(Stage stage) {
        stage.initStyle(StageStyle.TRANSPARENT);

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: " + BG + "; -fx-border-color: " + BORDER + "; -fx-border-width: 1;");

        Rectangle clip = new Rectangle(480, 360);
        clip.setArcWidth(16);
        clip.setArcHeight(16);
        root.setClip(clip);

        root.setTop(buildTitleBar(stage));
        root.setCenter(buildBody(stage));

        Scene scene = new Scene(root, 480, 360);
        scene.setFill(Color.TRANSPARENT);
        scene.getStylesheets().add(
            "data:text/css;base64," + Base64.getEncoder().encodeToString(buildCSS().getBytes())
        );

        stage.setScene(scene);
        stage.show();
    }

    // ── CSS (embedded) ─────────────────────────────────────────
    private String buildCSS() {
        return
            ".combo-box { -fx-background-color: " + SURFACE + "; -fx-border-color: " + BORDER + "; " +
            "  -fx-font-family: monospace; -fx-font-size: 13px; }\n" +
            ".combo-box .list-cell { -fx-background-color: " + SURFACE + "; -fx-text-fill: " + TEXT1 + "; " +
            "  -fx-font-family: monospace; -fx-font-size: 13px; -fx-padding: 6px 10px; }\n" +
            ".combo-box-popup .list-view { -fx-background-color: " + SURFACE + "; -fx-border-color: " + BORDER + "; }\n" +
            ".combo-box-popup .list-cell:hover { -fx-background-color: " + SURF_H + "; }\n" +
            ".combo-box-popup .list-cell:selected, .combo-box-popup .list-cell:selected:hover " +
            "  { -fx-background-color: " + SURF_H + "; }\n" +
            ".combo-box .arrow-button { -fx-background-color: " + SURFACE + "; }\n" +
            ".combo-box .arrow { -fx-background-color: " + TEXT2 + "; }\n" +
            ".slider { -fx-background-color: transparent; }\n" +
            ".slider .track { -fx-background-color: " + BORDER + "; -fx-pref-height: 3px; " +
            "  -fx-background-insets: 0; -fx-background-radius: 2; }\n" +
            ".slider .thumb { -fx-background-color: " + BG + "; " +
            "  -fx-border-color: " + ACCENT + "; -fx-border-width: 2; " +
            "  -fx-background-radius: 50%; -fx-border-radius: 50%; " +
            "  -fx-pref-width: 14px; -fx-pref-height: 14px; -fx-effect: null; }\n" +
            ".slider .thumb:hover { -fx-border-color: " + ACCENT2 + "; }\n" +
            ".slider .axis { -fx-tick-label-fill: transparent; }\n";
    }

    // ── Title bar ──────────────────────────────────────────────
    private HBox buildTitleBar(Stage stage) {
        Label title = label("SM64 Launcher", 12, TEXT2, false);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button close = new Button("×");
        close.setStyle(closeStyle(TEXT2));
        close.setOnMouseEntered(e -> close.setStyle(closeStyle(TEXT1)));
        close.setOnMouseExited(e  -> close.setStyle(closeStyle(TEXT2)));
        close.setOnAction(e -> Platform.exit());

        HBox bar = new HBox(title, spacer, close);
        bar.setPadding(new Insets(14, 16, 10, 20));
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setStyle("-fx-background-color: transparent;");

        bar.setOnMousePressed(e -> { dragX = e.getSceneX(); dragY = e.getSceneY(); });
        bar.setOnMouseDragged(e -> {
            stage.setX(e.getScreenX() - dragX);
            stage.setY(e.getScreenY() - dragY);
        });
        return bar;
    }

    // ── Body ───────────────────────────────────────────────────
    private VBox buildBody(Stage stage) {
        Label heading = label("Select level", 20, TEXT1, true);
        Label sub     = label("Choose where to start, then launch.", 12, TEXT2, false);
        VBox.setMargin(sub, new Insets(4, 0, 22, 0));

        HBox  levelRow  = buildLevelRow();
        Separator div1  = divider();
        VBox.setMargin(div1, new Insets(20, 0, 18, 0));

        HBox  speedRow  = buildSpeedRow();
        Slider slider   = buildSlider();
        VBox.setMargin(slider, new Insets(8, 0, 0, 0));
        HBox  hints     = buildSpeedHints();
        VBox.setMargin(hints, new Insets(6, 0, 0, 0));

        Separator div2  = divider();
        VBox.setMargin(div2, new Insets(20, 0, 16, 0));

        HBox launchRow  = buildLaunchRow(stage);

        VBox body = new VBox(heading, sub, levelRow, div1, speedRow, slider, hints, div2, launchRow);
        body.setPadding(new Insets(4, 28, 28, 28));
        body.setStyle("-fx-background-color: transparent;");
        return body;
    }

    // ── Level row ──────────────────────────────────────────────
    private HBox buildLevelRow() {
        Label lbl = label("Level", 12, TEXT2, false);
        lbl.setMinWidth(54);

        levelCombo = new ComboBox<>();
        for (Level l : LEVELS) levelCombo.getItems().add(l.name + "  (" + l.course + ")");
        levelCombo.getSelectionModel().selectFirst();
        levelCombo.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(levelCombo, Priority.ALWAYS);

        HBox row = new HBox(12, lbl, levelCombo);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    // ── Speed section ──────────────────────────────────────────
    private HBox buildSpeedRow() {
        Label lbl = label("Game speed", 12, TEXT2, false);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        speedLabel = label("1.0×", 13, ACCENT, true);

        HBox row = new HBox(lbl, spacer, speedLabel);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private Slider buildSlider() {
        speedSlider = new Slider(10, 50, 10);
        speedSlider.setMaxWidth(Double.MAX_VALUE);
        speedSlider.setShowTickLabels(false);
        speedSlider.setShowTickMarks(false);
        speedSlider.valueProperty().addListener((obs, o, n) ->
            speedLabel.setText(String.format("%.1f×", n.doubleValue() / 10.0))
        );
        return speedSlider;
    }

    private HBox buildSpeedHints() {
        Label lo = label("1.0× — normal", 11, TEXT2, false);
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        Label hi = label("5.0× — max", 11, TEXT2, false);
        return new HBox(lo, sp, hi);
    }

    // ── Launch row ─────────────────────────────────────────────
    private HBox buildLaunchRow(Stage stage) {
        Label hint = label("Ready to launch.", 12, TEXT2, false);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button btn = new Button("Launch");
        btn.setStyle(launchStyle(ACCENT));
        btn.setOnMouseEntered(e -> btn.setStyle(launchStyle(ACCENT2)));
        btn.setOnMouseExited(e  -> btn.setStyle(launchStyle(ACCENT)));
        btn.setOnAction(e -> {
            int idx    = levelCombo.getSelectionModel().getSelectedIndex();
            double spd = speedSlider.getValue() / 10.0;
            GameLauncher.launch(LEVELS[idx].courseId, spd);
            stage.close();
        });

        HBox row = new HBox(12, hint, spacer, btn);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    // ── Helpers ────────────────────────────────────────────────
    private Label label(String text, int size, String color, boolean bold) {
        Label l = new Label(text);
        l.setStyle(
            "-fx-font-family: monospace; -fx-font-size: " + size + "px;" +
            " -fx-text-fill: " + color + ";" +
            (bold ? " -fx-font-weight: bold;" : "")
        );
        return l;
    }

    private Separator divider() {
        Separator sep = new Separator();
        sep.setStyle("-fx-background-color: " + BORDER + "; -fx-pref-height: 1px; -fx-border-color: transparent;");
        return sep;
    }

    private String closeStyle(String color) {
        return "-fx-font-family: monospace; -fx-font-size: 16px; -fx-text-fill: " + color + ";" +
               "-fx-background-color: transparent; -fx-border-color: transparent;" +
               "-fx-cursor: hand; -fx-padding: 0 4 2 4;";
    }

    private String launchStyle(String bg) {
        return "-fx-background-color: " + bg + "; -fx-text-fill: white;" +
               "-fx-font-family: monospace; -fx-font-size: 13px; -fx-font-weight: bold;" +
               "-fx-background-radius: 8; -fx-cursor: hand; -fx-padding: 8 20 8 20;";
    }

    // ══════════════════════════════════════════════════════════
    public static void main(String[] args) { launch(args); }

    record Level(String name, String course, String courseId) {}
}
