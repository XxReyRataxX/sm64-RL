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

    // ── Palette (green tech) ───────────────────────────────────
    private static final String BG      = "#060E08";
    private static final String SURFACE = "#0C1510";
    private static final String SURF_H  = "#132018";
    private static final String BORDER  = "#1E3024";
    private static final String TEXT1   = "#D8EFD8";
    private static final String TEXT2   = "#4D7255";
    private static final String ACCENT  = "#3DB060";
    private static final String ACCENT2 = "#52C875";

    private static final String TERM_BG   = "#030804";
    private static final String TERM_TEXT = "#3DB060";
    private static final String TERM_DIM  = "#1E4028";

    // ── Levels ─────────────────────────────────────────────────
    private static final Level[] LEVELS = {
        new Level("Bob-omb Battlefield", "Course 1", "9", new Star[] {
            new Star("Big Bob-omb on the Summit",       1),
            new Star("Footrace with Koopa the Quick",   2),
            new Star("Shoot to the Island in the Sky",  3),
            new Star("Find the 8 Red Coins",            4),
            new Star("Mario Wings to the Sky",          5),
            new Star("Behind Chain Chomp's Gate",       6)
        })
    };

    // ── Layout constants ───────────────────────────────────────
    private static final int W           = 480;
    private static final int BASE_H      = 520;
    private static final int TERM_H      = 160;

    // ── State ──────────────────────────────────────────────────
    private ComboBox<String> levelCombo;
    private ComboBox<String> starCombo;
    private ComboBox<String>          modeCombo;
    private ToggleButton              renderToggle;
    private final java.util.List<Process> activeProcesses = new java.util.ArrayList<>();
    private Slider           speedSlider;
    private Label            speedLabel;
    private Label            blocksFilled;
    private Label            blocksEmpty;
    private Spinner<Integer> instancesSpinner;
    private Label            instancesHint;
    private Label            launchHint;
    private TextArea         terminal;
    private VBox             terminalPane;
    private Rectangle        windowClip;
    private double           dragX, dragY;

    // ══════════════════════════════════════════════════════════

    @Override
    public void start(Stage stage) {
        stage.initStyle(StageStyle.TRANSPARENT);

        BorderPane root = new BorderPane();
        root.setStyle(
            "-fx-background-color: " + BG + ";" +
            "-fx-border-color: " + BORDER + ";" +
            "-fx-border-width: 1;"
        );

        windowClip = new Rectangle(W, BASE_H);
        windowClip.setArcWidth(16);
        windowClip.setArcHeight(16);
        root.setClip(windowClip);

        root.setTop(buildTitleBar(stage));
        root.setCenter(buildBody(stage));
        root.setBottom(buildTerminalPane());

        Scene scene = new Scene(root, W, BASE_H);
        scene.setFill(Color.TRANSPARENT);
        scene.getStylesheets().add(
            "data:text/css;base64," + Base64.getEncoder().encodeToString(buildCSS().getBytes())
        );

        stage.setScene(scene);
        stage.show();
    }

    // ── Embedded CSS ───────────────────────────────────────────
    private String buildCSS() {
        return
            ".combo-box { -fx-background-color:" + SURFACE + "; -fx-border-color:" + BORDER + ";" +
            "  -fx-font-family:monospace; -fx-font-size:13px; }\n" +
            ".combo-box .list-cell { -fx-background-color:" + SURFACE + "; -fx-text-fill:" + TEXT1 + ";" +
            "  -fx-font-family:monospace; -fx-font-size:13px; -fx-padding:6px 10px; }\n" +
            ".combo-box-popup .list-view { -fx-background-color:" + SURFACE + "; -fx-border-color:" + BORDER + "; }\n" +
            ".combo-box-popup .list-cell:hover { -fx-background-color:" + SURF_H + "; }\n" +
            ".combo-box-popup .list-cell:selected,.combo-box-popup .list-cell:selected:hover" +
            "  { -fx-background-color:" + SURF_H + "; }\n" +
            ".combo-box .arrow-button { -fx-background-color:" + SURFACE + "; }\n" +
            ".combo-box .arrow { -fx-background-color:" + TEXT2 + "; }\n" +
            ".slider { -fx-background-color:transparent; }\n" +
            ".slider .track { -fx-background-color:" + BORDER + "; -fx-pref-height:3px;" +
            "  -fx-background-insets:0; -fx-background-radius:2; }\n" +
            ".slider .thumb { -fx-background-color:" + BG + "; -fx-border-color:" + ACCENT + ";" +
            "  -fx-border-width:2; -fx-background-radius:50%; -fx-border-radius:50%;" +
            "  -fx-pref-width:14px; -fx-pref-height:14px; -fx-effect:null; }\n" +
            ".slider .thumb:hover { -fx-border-color:" + ACCENT2 + "; }\n" +
            ".slider .axis { -fx-tick-label-fill:transparent; }\n" +
            ".spinner { -fx-background-color:" + SURFACE + "; -fx-border-color:" + BORDER + ";" +
            "  -fx-border-width:1; -fx-background-radius:4; -fx-border-radius:4;" +
            "  -fx-focus-color:transparent; -fx-faint-focus-color:transparent; }\n" +
            ".spinner .text-field { -fx-background-color:" + SURFACE + "; -fx-text-fill:" + TEXT1 + ";" +
            "  -fx-font-family:monospace; -fx-font-size:13px; -fx-background-insets:0;" +
            "  -fx-highlight-fill:" + BORDER + "; -fx-highlight-text-fill:" + TEXT1 + ";" +
            "  -fx-prompt-text-fill:" + TEXT2 + "; }\n" +
            ".spinner .increment-arrow-button,.spinner .decrement-arrow-button" +
            "  { -fx-background-color:" + SURFACE + "; -fx-background-insets:0; }\n" +
            ".spinner .increment-arrow-button:hover,.spinner .decrement-arrow-button:hover" +
            "  { -fx-background-color:" + SURF_H + "; }\n" +
            ".spinner .increment-arrow,.spinner .decrement-arrow" +
            "  { -fx-background-color:" + TEXT2 + "; }\n" +
            ".spinner .increment-arrow-button:hover .increment-arrow," +
            ".spinner .decrement-arrow-button:hover .decrement-arrow" +
            "  { -fx-background-color:" + ACCENT + "; }\n" +
            ".term { -fx-control-inner-background:" + TERM_BG + "; -fx-text-fill:" + TERM_TEXT + ";" +
            "  -fx-font-family:monospace; -fx-font-size:12px; -fx-background-color:" + TERM_BG + ";" +
            "  -fx-border-color:transparent; -fx-focus-color:transparent; -fx-faint-focus-color:transparent; }\n" +
            ".term .content { -fx-background-color:" + TERM_BG + "; }\n" +
            ".term .scroll-pane { -fx-background-color:" + TERM_BG + "; }\n" +
            ".term .scroll-pane .viewport { -fx-background-color:" + TERM_BG + "; }\n" +
            ".term .scroll-bar { -fx-background-color:" + TERM_BG + "; }\n" +
            ".term .scroll-bar .thumb { -fx-background-color:" + BORDER + "; }\n";
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
        bar.setStyle("-fx-background-color:transparent;");
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

        HBox  levelRow = buildLevelRow();
        HBox  starRow  = buildStarRow();
        VBox.setMargin(starRow, new Insets(10, 0, 0, 0));

        HBox  modeRow  = buildModeRow();
        VBox.setMargin(modeRow, new Insets(10, 0, 0, 0));

        Separator div1 = divider();
        VBox.setMargin(div1, new Insets(20, 0, 18, 0));

        HBox  speedRow = buildSpeedRow();
        Slider slider  = buildSlider();
        VBox.setMargin(slider, new Insets(8, 0, 0, 0));
        HBox blockBar  = buildBlockBar();
        VBox.setMargin(blockBar, new Insets(6, 0, 0, 0));
        HBox hints     = buildSpeedHints();
        VBox.setMargin(hints, new Insets(4, 0, 0, 0));

        VBox instancesSection = buildInstancesSection();
        VBox.setMargin(instancesSection, new Insets(18, 0, 0, 0));

        Separator div2 = divider();
        VBox.setMargin(div2, new Insets(18, 0, 16, 0));

        HBox launchRow = buildLaunchRow(stage);

        VBox body = new VBox(heading, sub, levelRow, starRow, modeRow, div1, speedRow, slider, blockBar, hints,
                             instancesSection, div2, launchRow);
        body.setPadding(new Insets(4, 28, 28, 28));
        body.setStyle("-fx-background-color:transparent;");
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

    // ── Star row ───────────────────────────────────────────────
    private HBox buildStarRow() {
        Label lbl = label("Star", 12, TEXT2, false);
        lbl.setMinWidth(54);
        starCombo = new ComboBox<>();
        starCombo.setMaxWidth(Double.MAX_VALUE);
        starCombo.setAccessibleText("Star to play in the selected level");
        HBox.setHgrow(starCombo, Priority.ALWAYS);

        // Repopulate stars whenever the level changes
        levelCombo.getSelectionModel().selectedIndexProperty()
            .addListener((obs, o, n) -> populateStars(n.intValue()));
        populateStars(levelCombo.getSelectionModel().getSelectedIndex());

        HBox row = new HBox(12, lbl, starCombo);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private void populateStars(int levelIdx) {
        starCombo.getItems().clear();
        if (levelIdx < 0 || levelIdx >= LEVELS.length) return;
        for (Star s : LEVELS[levelIdx].stars) {
            starCombo.getItems().add(s.id + ".  " + s.name);
        }
        starCombo.getSelectionModel().selectFirst();
    }

    private int selectedStarId() {
        int lvl  = levelCombo.getSelectionModel().getSelectedIndex();
        int star = starCombo.getSelectionModel().getSelectedIndex();
        if (lvl < 0 || lvl >= LEVELS.length) return 1;
        Star[] stars = LEVELS[lvl].stars;
        if (star < 0 || star >= stars.length) return 1;
        return stars[star].id;
    }

    // ── Mode row (frontend only) ───────────────────────────────
    private HBox buildModeRow() {
        Label lbl = label("Modo", 12, TEXT2, false);
        lbl.setMinWidth(54);
        modeCombo = new ComboBox<>();
        modeCombo.getItems().addAll("Entrenamiento", "Evaluación");
        modeCombo.getSelectionModel().selectFirst();
        modeCombo.setMaxWidth(Double.MAX_VALUE);
        modeCombo.setAccessibleText("Modo de ejecución");
        HBox.setHgrow(modeCombo, Priority.ALWAYS);
        HBox row = new HBox(12, lbl, modeCombo);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    // ── Speed row ──────────────────────────────────────────────
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
        speedSlider.valueProperty().addListener((obs, o, n) -> {
            speedLabel.setText(String.format("%.1f×", n.doubleValue() / 10.0));
            updateBlockBar(n.doubleValue());
        });
        return speedSlider;
    }

    // ── Block bar ──────────────────────────────────────────────
    private static final int BLOCKS = 16;

    private HBox buildBlockBar() {
        blocksFilled = new Label("");
        blocksFilled.setStyle("-fx-font-family:monospace; -fx-font-size:13px; -fx-text-fill:" + ACCENT + ";");
        blocksEmpty  = new Label("░".repeat(BLOCKS));
        blocksEmpty.setStyle("-fx-font-family:monospace; -fx-font-size:13px; -fx-text-fill:" + BORDER + ";");
        HBox bar = new HBox(0, blocksFilled, blocksEmpty);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private void updateBlockBar(double value) {
        int filled = (int) Math.round((value - 10) / 40.0 * BLOCKS);
        blocksFilled.setText("█".repeat(filled));
        blocksEmpty.setText("░".repeat(BLOCKS - filled));
    }

    // ── Speed hints ────────────────────────────────────────────
    private HBox buildSpeedHints() {
        Label lo = label("1.0× — normal", 11, TEXT2, false);
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        Label hi = label("5.0× — max", 11, TEXT2, false);
        return new HBox(lo, sp, hi);
    }

    // ── Instances section ──────────────────────────────────────
    private VBox buildInstancesSection() {
        Label lbl = label("Lanzamientos", 12, TEXT2, false);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        int maxCores = Math.min(Runtime.getRuntime().availableProcessors(), 6);
        instancesSpinner = new Spinner<>(
            new SpinnerValueFactory.IntegerSpinnerValueFactory(1, maxCores, 1));
        instancesSpinner.setEditable(true);
        instancesSpinner.setPrefWidth(80);
        instancesSpinner.setMinWidth(80);
        instancesSpinner.setMaxWidth(80);
        instancesSpinner.setAccessibleText("Number of parallel game instances to launch");
        // Commit manually typed text when the editor loses focus
        instancesSpinner.focusedProperty().addListener((obs, was, is) -> {
            if (!is) instancesSpinner.increment(0);
        });
        instancesSpinner.valueProperty().addListener(
            (obs, o, n) -> updateInstanceTexts(n == null ? 1 : n));

        HBox top = new HBox(12, lbl, spacer, instancesSpinner);
        top.setAlignment(Pos.CENTER_LEFT);

        instancesHint = label("1 instancia  (máx. " + maxCores + ")", 11, TEXT2, false);
        HBox hintRow = new HBox(instancesHint);
        hintRow.setAlignment(Pos.CENTER_RIGHT);
        VBox.setMargin(hintRow, new Insets(5, 0, 0, 0));

        VBox box = new VBox(top, hintRow);
        box.setStyle("-fx-background-color:transparent;");
        return box;
    }

    private int instanceCount() {
        if (instancesSpinner == null) return 1;
        Integer v = instancesSpinner.getValue();
        return v == null ? 1 : Math.max(1, v);
    }

    private void updateInstanceTexts(int n) {
        if (instancesHint != null) {
            int max = Runtime.getRuntime().availableProcessors();
            String suffix = "  (máx. " + max + ")";
            instancesHint.setText(n == 1 ? "1 instancia" + suffix : n + " instancias en paralelo" + suffix);
        }
        if (launchHint != null) {
            launchHint.setText(n == 1 ? "Ready to launch." : "Launching " + n + " instances.");
        }
    }

    // ── Launch row ─────────────────────────────────────────────
    private HBox buildLaunchRow(Stage stage) {
        Label hint = label("Ready to launch.", 12, TEXT2, false);
        launchHint = hint;

        renderToggle = new ToggleButton("⬜ Render");
        renderToggle.setSelected(false);
        renderToggle.setStyle(renderToggleStyle(false));
        renderToggle.setAccessibleText("Activar o desactivar el renderizado del juego");
        renderToggle.selectedProperty().addListener((obs, o, n) -> {
            renderToggle.setText(n ? "⬛ Sin render" : "⬜ Render");
            renderToggle.setStyle(renderToggleStyle(n));
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button btn = new Button("Launch");
        btn.setStyle(launchStyle(ACCENT));
        btn.setOnMouseEntered(e -> btn.setStyle(launchStyle(ACCENT2)));
        btn.setOnMouseExited(e  -> btn.setStyle(launchStyle(ACCENT)));
        btn.setOnAction(e -> {
            int idx    = levelCombo.getSelectionModel().getSelectedIndex();
            int starId = selectedStarId();
            double spd = speedSlider.getValue() / 10.0;
            int    n   = instanceCount();
            revealTerminal(stage);
            for (int i = 0; i < n; i++) {
                runProcess(LEVELS[idx].courseId, spd, starId);
            }
        });

        HBox row = new HBox(12, hint, renderToggle, spacer, btn);
        row.setAlignment(Pos.CENTER_LEFT);
        updateInstanceTexts(instanceCount());
        return row;
    }

    // ── Terminal pane ──────────────────────────────────────────
    private void stopAllProcesses() {
        synchronized (activeProcesses) {
            for (Process p : activeProcesses) p.destroyForcibly();
            activeProcesses.clear();
        }
        termAppend("[stopped all instances]");
    }

    private VBox buildTerminalPane() {
        HBox header = new HBox();
        header.setPadding(new Insets(7, 14, 7, 14));
        header.setStyle("-fx-background-color:" + BORDER + ";");
        Label title = label("console output", 11, TEXT2, false);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button stopBtn = new Button("Stop all");
        stopBtn.setStyle(
            "-fx-background-color:transparent; -fx-border-color:" + BORDER + ";" +
            "-fx-text-fill:" + TEXT2 + "; -fx-font-family:monospace; -fx-font-size:11px;" +
            "-fx-cursor:hand; -fx-padding:2 8 2 8; -fx-background-radius:4; -fx-border-radius:4;");
        stopBtn.setOnMouseEntered(e -> stopBtn.setStyle(
            "-fx-background-color:transparent; -fx-border-color:" + ACCENT + ";" +
            "-fx-text-fill:" + TEXT1 + "; -fx-font-family:monospace; -fx-font-size:11px;" +
            "-fx-cursor:hand; -fx-padding:2 8 2 8; -fx-background-radius:4; -fx-border-radius:4;"));
        stopBtn.setOnMouseExited(e -> stopBtn.setStyle(
            "-fx-background-color:transparent; -fx-border-color:" + BORDER + ";" +
            "-fx-text-fill:" + TEXT2 + "; -fx-font-family:monospace; -fx-font-size:11px;" +
            "-fx-cursor:hand; -fx-padding:2 8 2 8; -fx-background-radius:4; -fx-border-radius:4;"));
        stopBtn.setOnAction(e -> stopAllProcesses());

        header.getChildren().addAll(title, spacer, stopBtn);

        terminal = new TextArea();
        terminal.getStyleClass().add("term");
        terminal.setEditable(false);
        terminal.setWrapText(false);
        terminal.setPrefHeight(TERM_H - 30);
        terminal.setFocusTraversable(false);

        terminalPane = new VBox(header, terminal);
        terminalPane.setVisible(false);
        terminalPane.setManaged(false);
        return terminalPane;
    }

    private void revealTerminal(Stage stage) {
        terminalPane.setVisible(true);
        terminalPane.setManaged(true);
        int newH = BASE_H + TERM_H;
        windowClip.setHeight(newH);
        stage.setHeight(newH);
    }

    private void runProcess(String courseId, double speed, int starId) {
        java.io.File binary = GameLauncher.findBinary();
        boolean noRender = renderToggle != null && renderToggle.isSelected();
        String cmd = (binary != null ? binary.getAbsolutePath() : "sm64.us") +
                     " --level " + courseId +
                     " --speed " + String.format("%.1f", speed) +
                     " --star "  + starId +
                     (noRender ? " --no-render" : "");

        termAppend("$ " + cmd);

        if (binary == null || !binary.exists()) {
            termAppend("[error] binary not found — run: make VERSION=us");
            return;
        }

        try {
            if (!binary.canExecute()) binary.setExecutable(true);
            java.util.List<String> args = new java.util.ArrayList<>(java.util.List.of(
                binary.getAbsolutePath(),
                "--level", courseId,
                "--speed", String.format("%.1f", speed),
                "--star",  String.valueOf(starId)
            ));
            if (noRender) args.add("--no-render");
            ProcessBuilder pb = new ProcessBuilder(args);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            synchronized (activeProcesses) { activeProcesses.add(p); }

            Thread reader = new Thread(() -> {
                try (var br = new java.io.BufferedReader(
                        new java.io.InputStreamReader(p.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        final String ln = line;
                        Platform.runLater(() -> termAppend(ln));
                    }
                    int code = p.waitFor();
                    synchronized (activeProcesses) { activeProcesses.remove(p); }
                    Platform.runLater(() -> termAppend("[exited with code " + code + "]"));
                } catch (Exception e) {
                    synchronized (activeProcesses) { activeProcesses.remove(p); }
                    Platform.runLater(() -> termAppend("[error] " + e.getMessage()));
                }
            });
            reader.setDaemon(true);
            reader.start();

        } catch (Exception e) {
            termAppend("[error] " + e.getMessage());
        }
    }

    private void termAppend(String line) {
        terminal.appendText(line + "\n");
    }

    // ── Helpers ────────────────────────────────────────────────
    private Label label(String text, int size, String color, boolean bold) {
        Label l = new Label(text);
        l.setStyle(
            "-fx-font-family:monospace; -fx-font-size:" + size + "px;" +
            " -fx-text-fill:" + color + ";" +
            (bold ? " -fx-font-weight:bold;" : "")
        );
        return l;
    }

    private Separator divider() {
        Separator s = new Separator();
        s.setStyle("-fx-background-color:" + BORDER + "; -fx-pref-height:1px; -fx-border-color:transparent;");
        return s;
    }

    private String closeStyle(String color) {
        return "-fx-font-family:monospace; -fx-font-size:16px; -fx-text-fill:" + color + ";" +
               "-fx-background-color:transparent; -fx-border-color:transparent;" +
               "-fx-cursor:hand; -fx-padding:0 4 2 4;";
    }

    private String renderToggleStyle(boolean selected) {
        return "-fx-background-color:" + (selected ? SURF_H : SURFACE) + ";" +
               "-fx-text-fill:" + (selected ? TEXT1 : TEXT2) + ";" +
               "-fx-font-family:monospace; -fx-font-size:12px;" +
               "-fx-border-color:" + BORDER + "; -fx-border-width:1;" +
               "-fx-background-radius:6; -fx-border-radius:6;" +
               "-fx-cursor:hand; -fx-padding:5 12 5 12;" +
               "-fx-focus-color:transparent; -fx-faint-focus-color:transparent;";
    }

    private String launchStyle(String bg) {
        return "-fx-background-color:" + bg + "; -fx-text-fill:white;" +
               "-fx-font-family:monospace; -fx-font-size:13px; -fx-font-weight:bold;" +
               "-fx-background-radius:8; -fx-cursor:hand; -fx-padding:8 20 8 20;";
    }

    // ══════════════════════════════════════════════════════════
    public static void main(String[] args) { launch(args); }

    record Star(String name, int id) {}
    record Level(String name, String course, String courseId, Star[] stars) {}
}
