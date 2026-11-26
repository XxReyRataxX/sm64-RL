import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

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
    /**
     * Window width. Was 480 while the body was a single column of combos; the spawn
     * section needs the coord table and the 280px map side by side, and 480 left only
     * ~124px for the table (three coord fields do not fit). 640 = 28px padding × 2 +
     * 288 controls + 14 gap + 282 map.
     */
    private static final int W           = 640;
    /**
     * Natural height of the window with the terminal collapsed (title bar 48 + body +
     * 1px border top/bottom). The RL-only body was 497; the spawn section adds ~354
     * (mode row 27 + map 282 + hint + margins), hence 856. If the body grows again and
     * gets clipped at the bottom, this is the number to raise — the scene height is
     * fixed, so nothing scrolls on its own.
     */
    private static final int BASE_H      = 856;
    private static final int TERM_H      = 160;

    // ── RL defaults (no RL algorithm wired yet) ────────────────
    private static final int    RL_EPISODES  = 5;
    private static final int    RL_MAX_STEPS = 3000;
    // Value for the runner's --algo flag. "connect" is not an algorithm: it only
    // opens the bridge, waits for the level to load and keeps the game alive.
    private static final String RL_ALGO_ARG  = "connect";

    // ── State ──────────────────────────────────────────────────
    private ComboBox<String> levelCombo;
    private ComboBox<String> starCombo;
    private ComboBox<String>          modeCombo;
    private ToggleButton              renderToggle;
    private final java.util.List<Process> activeProcesses = new java.util.ArrayList<>();
    private Spinner<Integer> rlSpeedSpinner;
    private Label            loadHint;
    private Spinner<Integer> instancesSpinner;
    private Label            instancesHint;
    private Label            launchHint;
    // Spawn section
    private ComboBox<String> spawnModeCombo;
    private Canvas           mapCanvas;
    private GridPane         legendBox;
    private Label            spawnHint;
    private VBox             defaultPanel, fixedPanel, areaPanel, presetPanel;
    private VBox             fixedRows;
    private final List<SpawnRow> fixedRowList = new ArrayList<>();
    private int              selectedFixedRow = 0;
    private Spinner<Integer> areaX0, areaZ0, areaX1, areaZ1, seedSpinner;
    private TextField        areaYField;
    private CheckBox[]       presetChecks;
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

        stage.setOnCloseRequest(e -> { e.consume(); shutdownAndExit(); });

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
            // Spawn section: coord fields, the row scroller and the preset checkboxes.
            ".coord { -fx-background-color:" + SURFACE + "; -fx-text-fill:" + TEXT1 + ";" +
            "  -fx-font-family:monospace; -fx-font-size:11px; -fx-padding:2 4 2 4;" +
            "  -fx-border-color:" + BORDER + "; -fx-border-width:1;" +
            "  -fx-background-radius:3; -fx-border-radius:3;" +
            "  -fx-highlight-fill:" + BORDER + "; -fx-highlight-text-fill:" + TEXT1 + ";" +
            "  -fx-prompt-text-fill:" + TEXT2 + ";" +
            "  -fx-focus-color:transparent; -fx-faint-focus-color:transparent; }\n" +
            ".coord:focused { -fx-border-color:" + ACCENT + "; }\n" +
            ".spawn-scroll,.spawn-scroll > .viewport { -fx-background-color:transparent; }\n" +
            ".spawn-scroll { -fx-background-insets:0; -fx-padding:0; -fx-border-color:transparent;" +
            "  -fx-focus-color:transparent; -fx-faint-focus-color:transparent; }\n" +
            ".spawn-scroll .scroll-bar:vertical { -fx-background-color:transparent; -fx-pref-width:6; }\n" +
            ".spawn-scroll .scroll-bar .thumb { -fx-background-color:" + BORDER + "; }\n" +
            ".spawn-scroll .scroll-bar .increment-button," +
            ".spawn-scroll .scroll-bar .decrement-button { -fx-pref-height:0; -fx-padding:0; }\n" +
            // -fx-padding:0 and nothing else on purpose: "-fx-shape:null" is not a legal
            // value and only earns a CSS parse warning on every start-up.
            ".spawn-scroll .scroll-bar .increment-arrow," +
            ".spawn-scroll .scroll-bar .decrement-arrow { -fx-padding:0; }\n" +
            ".spawn-check { -fx-text-fill:" + TEXT1 + "; -fx-font-family:monospace;" +
            "  -fx-font-size:11px; -fx-focus-color:transparent; -fx-faint-focus-color:transparent; }\n" +
            ".spawn-check .box { -fx-background-color:" + SURFACE + ";" +
            "  -fx-border-color:" + BORDER + "; -fx-border-width:1;" +
            "  -fx-background-radius:3; -fx-border-radius:3; }\n" +
            ".spawn-check:selected .mark { -fx-background-color:" + ACCENT + "; }\n" +
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
        close.setOnAction(e -> shutdownAndExit());

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

        VBox  spawnSection = buildSpawnSection();
        VBox.setMargin(spawnSection, new Insets(14, 0, 0, 0));

        Separator div1 = divider();
        VBox.setMargin(div1, new Insets(20, 0, 18, 0));

        // Single speed control: the 1–100× spinner. No visibility juggling left.
        VBox rlSpeedBox = buildRlSpeedBox();

        VBox instancesSection = buildInstancesSection();
        VBox.setMargin(instancesSection, new Insets(18, 0, 0, 0));

        Separator div2 = divider();
        VBox.setMargin(div2, new Insets(18, 0, 16, 0));

        HBox launchRow = buildLaunchRow(stage);

        VBox body = new VBox(heading, sub, levelRow, starRow, modeRow, spawnSection, div1,
                             rlSpeedBox, instancesSection, div2, launchRow);
        body.setPadding(new Insets(4, 28, 28, 28));
        body.setStyle("-fx-background-color:transparent;");

        updateLoadTexts();
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

    // ── Mode row ───────────────────────────────────────────────
    private static final String MODE_TRAIN  = "Entrenamiento";
    private static final String MODE_EVAL   = "Evaluación";

    private HBox buildModeRow() {
        Label lbl = label("Modo", 12, TEXT2, false);
        lbl.setMinWidth(54);
        modeCombo = new ComboBox<>();
        modeCombo.getItems().addAll(MODE_TRAIN, MODE_EVAL);
        modeCombo.getSelectionModel().selectFirst();   // Entrenamiento por defecto
        modeCombo.setMaxWidth(Double.MAX_VALUE);
        modeCombo.setAccessibleText("Modo de ejecución");
        HBox.setHgrow(modeCombo, Priority.ALWAYS);
        HBox row = new HBox(12, lbl, modeCombo);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    // ══════════════════════════════════════════════════════════
    // Spawn section — controls on the left, top-down map on the right
    // ══════════════════════════════════════════════════════════
    private static final String SP_DEFAULT = "Default del juego";
    private static final String SP_FIXED   = "Punto fijo";
    private static final String SP_AREA    = "Área aleatoria";
    private static final String SP_PRESET  = "Presets BoB";

    /** Side of the map in pixels. 280 over a ±8000 bbox → 17.5px per 1000 units. */
    private static final double MAP_PX = 280;
    /** Width of the controls column; see the W javadoc for how the 640 breaks down. */
    private static final double LEFT_W = 288;

    // Map colours (not in the palette above: they are data colours, not chrome)
    private static final String MAP_BG    = "#030804";
    private static final String MAP_AXIS  = "#1C3524";
    private static final String WARP_COL  = "#7A8A7E";
    private static final String SPAWN_COL = "#FFD400";

    private VBox buildSpawnSection() {
        Label lbl = label("Spawn", 12, TEXT2, false);
        lbl.setMinWidth(54);
        spawnModeCombo = new ComboBox<>();
        spawnModeCombo.getItems().addAll(SP_DEFAULT, SP_FIXED, SP_AREA, SP_PRESET);
        spawnModeCombo.getSelectionModel().selectFirst();
        spawnModeCombo.setMaxWidth(Double.MAX_VALUE);
        spawnModeCombo.setAccessibleText("Cómo se decide dónde aparece Mario en cada instancia");
        HBox.setHgrow(spawnModeCombo, Priority.ALWAYS);
        HBox modeRow = new HBox(12, lbl, spawnModeCombo);
        modeRow.setAlignment(Pos.CENTER_LEFT);

        mapCanvas = new Canvas(MAP_PX, MAP_PX);
        mapCanvas.setAccessibleText("Plano superior de Bob-omb Battlefield con enemigos y puntos de aparición");
        mapCanvas.setOnMousePressed(this::onMapPressed);
        mapCanvas.setOnMouseDragged(this::onMapDragged);
        StackPane mapFrame = new StackPane(mapCanvas);
        mapFrame.setStyle("-fx-border-color:" + BORDER + "; -fx-border-width:1;");
        mapFrame.setMinSize(MAP_PX + 2, MAP_PX + 2);
        mapFrame.setMaxSize(MAP_PX + 2, MAP_PX + 2);

        defaultPanel = buildDefaultPanel();
        fixedPanel   = buildFixedPanel();
        areaPanel    = buildAreaPanel();
        presetPanel  = buildPresetPanel();
        // StackPane + managed(false) on the hidden panels: the section's height follows
        // the visible panel only, instead of always reserving room for the tallest.
        StackPane panels = new StackPane(defaultPanel, fixedPanel, areaPanel, presetPanel);
        panels.setAlignment(Pos.TOP_LEFT);

        legendBox = new GridPane();
        legendBox.setHgap(6);
        legendBox.setVgap(2);

        VBox left = new VBox(8, panels, legendBox);
        left.setMinWidth(LEFT_W);
        left.setPrefWidth(LEFT_W);
        left.setMaxWidth(LEFT_W);

        HBox content = new HBox(14, left, mapFrame);
        content.setAlignment(Pos.TOP_LEFT);

        spawnHint = label("", 11, TEXT2, false);

        spawnModeCombo.getSelectionModel().selectedItemProperty()
            .addListener((obs, o, n) -> applySpawnMode());
        // The act decides which enemies exist (king bob-omb act 1, KtQ act 2, …)
        starCombo.getSelectionModel().selectedIndexProperty()
            .addListener((obs, o, n) -> redrawMap());

        VBox box = new VBox(8, modeRow, content, spawnHint);
        box.setStyle("-fx-background-color:transparent;");
        applySpawnMode();
        return box;
    }

    private String spawnModeName() {
        if (spawnModeCombo == null) return SP_DEFAULT;
        String v = spawnModeCombo.getSelectionModel().getSelectedItem();
        return v == null ? SP_DEFAULT : v;
    }

    private void applySpawnMode() {
        String m = spawnModeName();
        showPanel(defaultPanel, SP_DEFAULT.equals(m));
        showPanel(fixedPanel,   SP_FIXED.equals(m));
        showPanel(areaPanel,    SP_AREA.equals(m));
        showPanel(presetPanel,  SP_PRESET.equals(m));
        // Entering "punto fijo" with an empty table would show a map with no spawn at
        // all; seed it with the level's own warp so there is always something to drag.
        if (SP_FIXED.equals(m) && fixedRowList.isEmpty()) {
            addFixedRow(BobMap.WARP_X, BobMap.WARP_Z, BobMap.WARP_YAW);
        }
        redrawMap();
    }

    private static void showPanel(VBox p, boolean on) {
        if (p == null) return;
        p.setVisible(on);
        p.setManaged(on);
    }

    // ── Per-mode panels ───────────────────────────────────────
    private VBox buildDefaultPanel() {
        Label a = label("Sin override: cada instancia usa el warp", 11, TEXT2, false);
        Label b = label("del nivel (" + (int) BobMap.WARP_X + ", " + (int) BobMap.WARP_Z + ").", 11, TEXT2, false);
        Label c = label("El plano solo muestra enemigos.", 11, TEXT2, false);
        VBox v = new VBox(3, a, b, c);
        v.setStyle("-fx-background-color:transparent;");
        return v;
    }

    /** One editable coord row per spawn point; fewer rows than instances = round-robin. */
    private record SpawnRow(HBox node, Label idx, TextField x, TextField z, TextField yaw) {}

    private VBox buildFixedPanel() {
        Label head = label("Puntos (round-robin entre instancias)", 11, TEXT2, false);
        HBox cols = new HBox(4, colHeader("#", 26), colHeader("X", 72),
                                colHeader("Z", 72), colHeader("Yaw°", 56));

        fixedRows = new VBox(4);
        fixedRows.setStyle("-fx-background-color:transparent;");
        ScrollPane sp = new ScrollPane(fixedRows);
        sp.getStyleClass().add("spawn-scroll");
        sp.setFitToWidth(true);
        sp.setPrefViewportHeight(104);
        sp.setMaxHeight(104);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.setFocusTraversable(false);

        Button add = miniButton("+ fila");
        add.setOnAction(e -> {
            // New rows clone the last one: adding a point near the previous one and
            // nudging it is the common case, and an empty row would break the preview.
            double[] last = fixedRowList.isEmpty()
                ? new double[] { BobMap.WARP_X, BobMap.WARP_Z, BobMap.WARP_YAW }
                : rowValues(fixedRowList.get(fixedRowList.size() - 1));
            addFixedRow(last[0], last[1], last[2]);
            selectFixedRow(fixedRowList.size() - 1);
            redrawMap();
        });
        Button del = miniButton("− fila");
        del.setOnAction(e -> {
            if (fixedRowList.size() <= 1) return;   // never leave the table empty
            int i = Math.min(selectedFixedRow, fixedRowList.size() - 1);
            fixedRows.getChildren().remove(fixedRowList.remove(i).node());
            renumberFixedRows();
            selectFixedRow(Math.min(i, fixedRowList.size() - 1));
            redrawMap();
        });
        Button fill = miniButton("= instancias");
        fill.setOnAction(e -> {
            if (fixedRowList.isEmpty()) addFixedRow(BobMap.WARP_X, BobMap.WARP_Z, BobMap.WARP_YAW);
            while (fixedRowList.size() < instanceCount()) {
                double[] last = rowValues(fixedRowList.get(fixedRowList.size() - 1));
                addFixedRow(last[0], last[1], last[2]);
            }
            redrawMap();
        });

        Label tip = label("Click en el plano → fija X/Z de la fila activa", 10, TEXT2, false);

        VBox v = new VBox(5, head, cols, sp, new HBox(6, add, del, fill), tip);
        v.setStyle("-fx-background-color:transparent;");
        return v;
    }

    private void addFixedRow(double x, double z, double yaw) {
        Label n = label("", 11, TEXT2, false);
        n.setMinWidth(26);
        TextField tx   = coordField(x, 72);
        TextField tz   = coordField(z, 72);
        TextField tyaw = coordField(yaw, 56);

        HBox node = new HBox(4, n, tx, tz, tyaw);
        node.setAlignment(Pos.CENTER_LEFT);
        SpawnRow row = new SpawnRow(node, n, tx, tz, tyaw);
        fixedRowList.add(row);
        fixedRows.getChildren().add(node);

        for (TextField f : new TextField[] { tx, tz, tyaw }) {
            f.textProperty().addListener((obs, o, nv) -> redrawMap());
            f.focusedProperty().addListener((obs, was, is) -> {
                if (is) selectFixedRow(fixedRowList.indexOf(row));
            });
        }
        node.setOnMousePressed(e -> selectFixedRow(fixedRowList.indexOf(row)));

        renumberFixedRows();
        if (fixedRowList.size() == 1) selectFixedRow(0);
    }

    private void renumberFixedRows() {
        for (int i = 0; i < fixedRowList.size(); i++) {
            fixedRowList.get(i).idx().setText("#" + i);
        }
    }

    /** The active row is the one a canvas click writes into; marked with the accent. */
    private void selectFixedRow(int i) {
        selectedFixedRow = Math.max(0, i);
        for (int k = 0; k < fixedRowList.size(); k++) {
            boolean on = (k == selectedFixedRow);
            fixedRowList.get(k).node().setStyle(
                "-fx-background-color:" + (on ? SURF_H : "transparent") + ";" +
                "-fx-background-radius:4; -fx-padding:1 2 1 2;");
        }
    }

    private VBox buildAreaPanel() {
        Label head = label("Rectángulo XZ + semilla", 11, TEXT2, false);

        // Defaults = the starting plain of BoB (walkable, has floor everywhere), so a
        // first run in this mode cannot land every instance in the void.
        areaX0 = coordSpinner(-6800);
        areaZ0 = coordSpinner(4000);
        areaX1 = coordSpinner(-2000);
        areaZ1 = coordSpinner(6800);
        seedSpinner = new Spinner<>(
            new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 999_999, 0, 1));
        seedSpinner.setEditable(true);
        seedSpinner.setPrefWidth(72);
        seedSpinner.setMaxWidth(72);
        seedSpinner.setAccessibleText("Semilla del muestreo aleatorio de spawns");
        seedSpinner.focusedProperty().addListener((o, was, is) -> {
            if (!is) seedSpinner.increment(0);
        });
        seedSpinner.valueProperty().addListener((o, a, b) -> redrawMap());

        areaYField = new TextField("");
        areaYField.getStyleClass().add("coord");
        areaYField.setPromptText("auto");
        areaYField.setPrefWidth(72);
        areaYField.setMaxWidth(72);
        areaYField.setAccessibleText("Altura Y fija; vacío = suelo automático");

        GridPane g = new GridPane();
        g.setHgap(6);
        g.setVgap(5);
        g.addRow(0, label("X0", 11, TEXT2, false), areaX0, label("Z0", 11, TEXT2, false), areaZ0);
        g.addRow(1, label("X1", 11, TEXT2, false), areaX1, label("Z1", 11, TEXT2, false), areaZ1);
        g.addRow(2, label("Seed", 11, TEXT2, false), seedSpinner,
                    label("Y", 11, TEXT2, false), areaYField);

        Label tip = label("Arrastra en el plano para dibujar el área", 10, TEXT2, false);

        VBox v = new VBox(5, head, g, tip);
        v.setStyle("-fx-background-color:transparent;");
        return v;
    }

    private VBox buildPresetPanel() {
        Label head = label("Presets de BoB (round-robin)", 11, TEXT2, false);
        List<BobMap.Preset> ps = BobMap.PRESETS;
        presetChecks = new CheckBox[ps.size()];
        VBox list = new VBox(3);
        for (int i = 0; i < ps.size(); i++) {
            BobMap.Preset p = ps.get(i);
            CheckBox cb = new CheckBox(p.label() + "  (" + (int) p.x() + ", " + (int) p.z() + ")");
            cb.setSelected(true);
            cb.getStyleClass().add("spawn-check");
            cb.selectedProperty().addListener((o, a, b) -> redrawMap());
            presetChecks[i] = cb;
            list.getChildren().add(cb);
        }
        VBox v = new VBox(5, head, list);
        v.setStyle("-fx-background-color:transparent;");
        return v;
    }

    // ── Reading the controls ──────────────────────────────────
    /** Tolerant parse: a half-typed "-" or "" must not blow up the redraw. */
    private static double parseD(TextField f, double fallback) {
        if (f == null) return fallback;
        try {
            return Double.parseDouble(f.getText().trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static double[] rowValues(SpawnRow r) {
        return new double[] { parseD(r.x(), 0), parseD(r.z(), 0), parseD(r.yaw(), 0) };
    }

    private List<double[]> fixedValues() {
        List<double[]> out = new ArrayList<>();
        for (SpawnRow r : fixedRowList) out.add(rowValues(r));
        return out;
    }

    /** {x0, z0, x1, z1} straight from the spinners. */
    private double[] areaValues() {
        return new double[] {
            spinnerVal(areaX0, -6800), spinnerVal(areaZ0, 4000),
            spinnerVal(areaX1, -2000), spinnerVal(areaZ1, 6800)
        };
    }

    private static double spinnerVal(Spinner<Integer> s, double fallback) {
        if (s == null || s.getValue() == null) return fallback;
        return s.getValue();
    }

    private long seedValue() {
        return (long) spinnerVal(seedSpinner, 0);
    }

    private List<BobMap.Preset> selectedPresets() {
        List<BobMap.Preset> out = new ArrayList<>();
        for (int i = 0; i < BobMap.PRESETS.size(); i++) {
            if (presetChecks == null || presetChecks[i].isSelected()) out.add(BobMap.PRESETS.get(i));
        }
        return out;
    }

    /**
     * Where every instance would appear right now: {x, z, yaw} indexed by instance.
     * Mirrors spawn_args_for() in gymnasium_env/runner.py, including the round-robin
     * and the per-instance seed derivation — the preview is useless if it drifts.
     */
    private List<double[]> spawnPoints() {
        List<double[]> out = new ArrayList<>();
        int n = instanceCount();
        switch (spawnModeName()) {
            case SP_FIXED -> {
                List<double[]> rows = fixedValues();
                if (rows.isEmpty()) return out;
                for (int i = 0; i < n; i++) out.add(rows.get(i % rows.size()));
            }
            case SP_AREA -> {
                double[] a = areaValues();
                for (int i = 0; i < n; i++) {
                    out.add(BobMap.sampleArea(a[0], a[1], a[2], a[3], seedValue(), i));
                }
            }
            case SP_PRESET -> {
                List<BobMap.Preset> ps = selectedPresets();
                if (ps.isEmpty()) return out;
                for (int i = 0; i < n; i++) {
                    BobMap.Preset p = ps.get(i % ps.size());
                    out.add(new double[] { p.x(), p.z(), p.yaw() });
                }
            }
            default -> { /* SP_DEFAULT: the game's own warp, nothing to preview */ }
        }
        return out;
    }

    // ── World ↔ canvas ────────────────────────────────────────
    private static double mapScale() {
        return MAP_PX / (BobMap.WORLD_MAX - BobMap.WORLD_MIN);
    }

    private static double sx(double worldX) {
        return (worldX - BobMap.WORLD_MIN) * mapScale();
    }

    /**
     * +Z goes DOWN on screen on purpose: in SM64 the BoB start (z ≈ +6464) is the south
     * plain and the summit (z ≈ -5567) is north, so this puts the mountain at the top,
     * like every map of this level people have ever seen.
     */
    private static double sy(double worldZ) {
        return (worldZ - BobMap.WORLD_MIN) * mapScale();
    }

    private static double worldXAt(double px) {
        return BobMap.WORLD_MIN + px / mapScale();
    }

    private static double worldZAt(double py) {
        return BobMap.WORLD_MIN + py / mapScale();
    }

    private static double clampWorld(double v) {
        return Math.max(BobMap.WORLD_MIN, Math.min(BobMap.WORLD_MAX, v));
    }

    // ── Canvas input ──────────────────────────────────────────
    private double dragStartX, dragStartZ;

    private void onMapPressed(MouseEvent e) {
        double wx = clampWorld(worldXAt(e.getX()));
        double wz = clampWorld(worldZAt(e.getY()));
        String m = spawnModeName();
        if (SP_FIXED.equals(m)) {
            if (fixedRowList.isEmpty()) addFixedRow(wx, wz, 0);
            int i = Math.min(selectedFixedRow, fixedRowList.size() - 1);
            SpawnRow r = fixedRowList.get(i);
            r.x().setText(fmtCoord(wx));
            r.z().setText(fmtCoord(wz));   // each setText triggers its own redraw
        } else if (SP_AREA.equals(m)) {
            dragStartX = wx;
            dragStartZ = wz;
            setSpinner(areaX0, wx); setSpinner(areaZ0, wz);
            setSpinner(areaX1, wx); setSpinner(areaZ1, wz);
            redrawMap();
        }
    }

    private void onMapDragged(MouseEvent e) {
        if (!SP_AREA.equals(spawnModeName())) return;
        setSpinner(areaX0, dragStartX);
        setSpinner(areaZ0, dragStartZ);
        setSpinner(areaX1, clampWorld(worldXAt(e.getX())));
        setSpinner(areaZ1, clampWorld(worldZAt(e.getY())));
        redrawMap();
    }

    private static void setSpinner(Spinner<Integer> s, double v) {
        if (s == null) return;
        int iv = (int) Math.round(v);
        if (s.getValueFactory() instanceof SpinnerValueFactory.IntegerSpinnerValueFactory f) {
            iv = Math.max(f.getMin(), Math.min(f.getMax(), iv));
        }
        s.getValueFactory().setValue(iv);
    }

    // ── Drawing ───────────────────────────────────────────────
    private static final Font TAG_FONT   = Font.font("monospace", FontWeight.BOLD, 9);
    private static final Font INDEX_FONT = Font.font("monospace", FontWeight.BOLD, 9);

    private void redrawMap() {
        if (mapCanvas == null) return;
        GraphicsContext g = mapCanvas.getGraphicsContext2D();
        g.clearRect(0, 0, MAP_PX, MAP_PX);
        g.setFill(Color.web(MAP_BG));
        g.fillRect(0, 0, MAP_PX, MAP_PX);

        // Grid every 1000 world units; the x=0 / z=0 axes a shade brighter.
        g.setLineWidth(1);
        for (double v = BobMap.WORLD_MIN; v <= BobMap.WORLD_MAX; v += BobMap.GRID_STEP) {
            boolean axis = Math.abs(v) < 0.5;
            g.setStroke(Color.web(axis ? MAP_AXIS : BORDER));
            double p = Math.floor(sx(v)) + 0.5;
            g.strokeLine(p, 0, p, MAP_PX);
            g.strokeLine(0, p, MAP_PX, p);
        }

        // Area rectangle under everything else, so markers stay readable on top
        if (SP_AREA.equals(spawnModeName())) {
            double[] a = areaValues();
            double x = sx(Math.min(a[0], a[2])), y = sy(Math.min(a[1], a[3]));
            double w = Math.abs(sx(a[2]) - sx(a[0])), h = Math.abs(sy(a[3]) - sy(a[1]));
            g.setFill(Color.web(ACCENT, 0.12));
            g.fillRect(x, y, w, h);
            g.setStroke(Color.web(ACCENT, 0.7));
            g.strokeRect(x + 0.5, y + 0.5, w, h);
        }

        List<BobMap.Marker> enemies = BobMap.enemiesFor(selectedStarId());
        for (BobMap.Marker m : enemies) drawMarker(g, m);

        drawWarp(g);
        drawSpawns(g);

        rebuildLegend(enemies);
        updateSpawnHint();
    }

    private void drawMarker(GraphicsContext g, BobMap.Marker m) {
        double cx = sx(m.x()), cy = sy(m.z()), r = m.kind().r;
        g.setFill(Color.web(m.kind().hex));
        g.fillOval(cx - r, cy - r, r * 2, r * 2);
        // Thin light outline: chain chomp is almost black and would vanish otherwise.
        g.setStroke(Color.web("#000000", 0.55));
        g.setLineWidth(1);
        g.strokeOval(cx - r, cy - r, r * 2, r * 2);
        if (m.tag() != null) {
            g.setFill(Color.WHITE);
            g.setFont(TAG_FONT);
            g.setTextAlign(TextAlignment.CENTER);
            g.fillText(m.tag(), cx, cy + 3.2);
            g.setTextAlign(TextAlignment.LEFT);
        }
    }

    /** The level's own warp, always drawn in grey as a reference point. */
    private void drawWarp(GraphicsContext g) {
        double cx = sx(BobMap.WARP_X), cy = sy(BobMap.WARP_Z);
        g.setStroke(Color.web(WARP_COL));
        g.setLineWidth(1.4);
        g.strokeLine(cx - 5, cy, cx + 5, cy);
        g.strokeLine(cx, cy - 5, cx, cy + 5);
        g.strokeOval(cx - 4, cy - 4, 8, 8);
    }

    /**
     * One yellow disc per spawn point with the instance indices inside. Instances that
     * share a point (round-robin with fewer points than instances) are merged into a
     * single "0,2" label instead of stacking unreadable circles on the same pixel.
     */
    private void drawSpawns(GraphicsContext g) {
        List<double[]> pts = spawnPoints();
        if (pts.isEmpty()) return;

        List<double[]> uniq = new ArrayList<>();      // {x, z, yaw}
        List<StringBuilder> labels = new ArrayList<>();
        for (int i = 0; i < pts.size(); i++) {
            double[] p = pts.get(i);
            int hit = -1;
            for (int k = 0; k < uniq.size(); k++) {
                if (Math.abs(uniq.get(k)[0] - p[0]) < 1 && Math.abs(uniq.get(k)[1] - p[1]) < 1) {
                    hit = k;
                    break;
                }
            }
            if (hit < 0) {
                uniq.add(p);
                labels.add(new StringBuilder(String.valueOf(i)));
            } else {
                labels.get(hit).append(',').append(i);
            }
        }

        for (int k = 0; k < uniq.size(); k++) {
            double[] p = uniq.get(k);
            double cx = sx(p[0]), cy = sy(p[1]);
            // Yaw tick: SM64 forward is (sin yaw, cos yaw) over (x, z), and +Z is down
            // on this canvas, so the screen vector is (sin, cos) with no sign flip.
            double rad = Math.toRadians(p[2]);
            g.setStroke(Color.web(SPAWN_COL));
            g.setLineWidth(1.6);
            g.strokeLine(cx, cy, cx + Math.sin(rad) * 13, cy + Math.cos(rad) * 13);

            double r = 7;
            g.setFill(Color.web(SPAWN_COL));
            g.fillOval(cx - r, cy - r, r * 2, r * 2);
            g.setStroke(Color.web("#1A1A00"));
            g.setLineWidth(1);
            g.strokeOval(cx - r, cy - r, r * 2, r * 2);

            g.setFill(Color.web("#201A00"));
            g.setFont(INDEX_FONT);
            g.setTextAlign(TextAlignment.CENTER);
            g.fillText(labels.get(k).toString(), cx, cy + 3.2);
            g.setTextAlign(TextAlignment.LEFT);
        }
    }

    /** Legend limited to the kinds actually on screen for this act, 2 columns. */
    private void rebuildLegend(List<BobMap.Marker> enemies) {
        if (legendBox == null) return;
        legendBox.getChildren().clear();
        int i = 0;
        for (BobMap.Kind k : BobMap.Kind.values()) {
            boolean present = false;
            for (BobMap.Marker m : enemies) {
                if (m.kind() == k) { present = true; break; }
            }
            if (!present) continue;
            legendBox.add(legendItem(k.hex, k.label), i % 2, i / 2);
            i++;
        }
        legendBox.add(legendItem(WARP_COL, "warp nivel"), i % 2, i / 2);
        i++;
        legendBox.add(legendItem(SPAWN_COL, "spawn (nº inst)"), i % 2, i / 2);
    }

    private HBox legendItem(String hex, String text) {
        Circle dot = new Circle(3.5, Color.web(hex));
        HBox h = new HBox(5, dot, label(text, 10, TEXT2, false));
        h.setAlignment(Pos.CENTER_LEFT);
        return h;
    }

    private void updateSpawnHint() {
        if (spawnHint == null) return;
        int n = instanceCount();
        String m = spawnModeName();
        if (SP_DEFAULT.equals(m)) {
            spawnHint.setText("sin --spawn-mode: warp del nivel en las " + n + " instancias");
        } else if (SP_FIXED.equals(m)) {
            int k = fixedRowList.size();
            spawnHint.setText("fixed: " + k + (k == 1 ? " punto" : " puntos") + " → " + n
                              + (n == 1 ? " instancia" : " instancias")
                              + (k < n ? " (round-robin)" : ""));
        } else if (SP_AREA.equals(m)) {
            double[] a = areaValues();
            spawnHint.setText("random_area: seed " + seedValue() + " · "
                + (int) Math.abs(a[2] - a[0]) + "×" + (int) Math.abs(a[3] - a[1]) + " uds");
        } else {
            int k = selectedPresets().size();
            spawnHint.setText(k == 0
                ? "preset: ningún preset marcado → se ignoraría el spawn"
                : "preset: " + k + " de " + BobMap.PRESETS.size() + " activos → " + n
                  + (n == 1 ? " instancia" : " instancias"));
        }
    }

    // ── Spawn flags handed to GameLauncher ────────────────────
    private record SpawnCfg(GameLauncher.SpawnMode mode, List<double[]> spawns,
                            double[] area, Double y, Long seed) {}

    /**
     * Translates the UI into runner flags.
     *
     * <p>One wrinkle: --spawn-mode preset has NO way to select a subset (runner.py owns
     * the list). So all four boxes ticked → preset mode (let Python use its own table,
     * which keeps the two in sync by construction); any subset → fixed mode carrying
     * exactly the ticked coordinates. Same resulting spawns either way.
     */
    private SpawnCfg currentSpawnCfg() {
        String m = spawnModeName();
        if (SP_FIXED.equals(m)) {
            List<double[]> out = new ArrayList<>();
            for (double[] r : fixedValues()) {
                out.add(new double[] { r[0], Double.NaN, r[1], r[2] });  // NaN = auto-floor
            }
            return new SpawnCfg(GameLauncher.SpawnMode.FIXED, out, null, null, null);
        }
        if (SP_AREA.equals(m)) {
            double[] a = areaValues();
            String yTxt = areaYField == null ? "" : areaYField.getText().trim();
            Double y = null;
            if (!yTxt.isEmpty()) {
                try { y = Double.valueOf(yTxt); } catch (NumberFormatException ignored) { }
            }
            return new SpawnCfg(GameLauncher.SpawnMode.RANDOM_AREA, List.of(),
                                a, y, seedValue());
        }
        if (SP_PRESET.equals(m)) {
            List<BobMap.Preset> ps = selectedPresets();
            if (ps.size() == BobMap.PRESETS.size()) {
                return new SpawnCfg(GameLauncher.SpawnMode.PRESET, List.of(), null, null, null);
            }
            List<double[]> out = new ArrayList<>();
            for (BobMap.Preset p : ps) {
                out.add(new double[] { p.x(), Double.NaN, p.z(), p.yaw() });
            }
            return new SpawnCfg(GameLauncher.SpawnMode.FIXED, out, null, null, null);
        }
        return new SpawnCfg(GameLauncher.SpawnMode.DEFAULT, List.of(), null, null, null);
    }

    // ── Small styled controls used by the spawn panels ────────
    private Label colHeader(String text, double w) {
        Label l = label(text, 10, TEXT2, false);
        l.setMinWidth(w);
        l.setMaxWidth(w);
        return l;
    }

    private TextField coordField(double value, double w) {
        TextField f = new TextField(fmtCoord(value));
        f.getStyleClass().add("coord");
        f.setPrefWidth(w);
        f.setMinWidth(w);
        f.setMaxWidth(w);
        return f;
    }

    private Spinner<Integer> coordSpinner(int def) {
        Spinner<Integer> s = new Spinner<>(
            new SpinnerValueFactory.IntegerSpinnerValueFactory(
                (int) BobMap.WORLD_MIN, (int) BobMap.WORLD_MAX, def, 100));
        s.setEditable(true);
        s.setPrefWidth(72);
        s.setMaxWidth(72);
        s.focusedProperty().addListener((o, was, is) -> {
            if (!is) s.increment(0);
        });
        s.valueProperty().addListener((o, a, b) -> redrawMap());
        return s;
    }

    private Button miniButton(String text) {
        Button b = new Button(text);
        b.setStyle(miniStyle(false));
        b.setOnMouseEntered(e -> b.setStyle(miniStyle(true)));
        b.setOnMouseExited(e  -> b.setStyle(miniStyle(false)));
        return b;
    }

    private String miniStyle(boolean hover) {
        return "-fx-background-color:" + (hover ? SURF_H : SURFACE) + ";" +
               "-fx-text-fill:" + (hover ? TEXT1 : TEXT2) + ";" +
               "-fx-font-family:monospace; -fx-font-size:10px;" +
               "-fx-border-color:" + BORDER + "; -fx-border-width:1;" +
               "-fx-background-radius:4; -fx-border-radius:4;" +
               "-fx-cursor:hand; -fx-padding:2 7 2 7;" +
               "-fx-focus-color:transparent; -fx-faint-focus-color:transparent;";
    }

    /** Integers print without ".0": the CLI takes both, but humans read these. */
    private static String fmtCoord(double v) {
        if (v == Math.rint(v)) return String.valueOf((long) v);
        return String.format(java.util.Locale.ROOT, "%.1f", v);
    }

    // ── Speed control (spinner, 1×–100×) ───────────────────────
    // Range mirrors the --speed clamp in src/pc/pc_main.c (1.0–100.0). --speed is a
    // sleep-based frame limiter (src/pc/gfx/gfx_dummy.c): asking for more than the CPU
    // can deliver never breaks anything, the instance just runs slower than requested.
    private static final int RL_SPEED_MIN = 1;
    private static final int RL_SPEED_MAX = 100;
    private static final int RL_SPEED_DEF = 5;
    /** Nominal game tick rate the limiter budgets against (1/(30×speed) per frame). */
    private static final int BASE_FPS     = 30;

    private VBox buildRlSpeedBox() {
        Label lbl = label("Game speed", 12, TEXT2, false);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        rlSpeedSpinner = new Spinner<>(
            new SpinnerValueFactory.IntegerSpinnerValueFactory(
                RL_SPEED_MIN, RL_SPEED_MAX, RL_SPEED_DEF));
        rlSpeedSpinner.setEditable(true);
        rlSpeedSpinner.setPrefWidth(80);
        rlSpeedSpinner.setMinWidth(80);
        rlSpeedSpinner.setMaxWidth(80);
        rlSpeedSpinner.setAccessibleText("Multiplicador de velocidad del juego para RL, de 1 a 100");
        // Commit manually typed text when the editor loses focus (same as instancesSpinner)
        rlSpeedSpinner.focusedProperty().addListener((obs, was, is) -> {
            if (!is) rlSpeedSpinner.increment(0);
        });
        rlSpeedSpinner.valueProperty().addListener((obs, o, n) -> updateLoadTexts());

        Label mult = label("×", 13, ACCENT, true);
        HBox top = new HBox(12, lbl, spacer, rlSpeedSpinner, mult);
        top.setAlignment(Pos.CENTER_LEFT);

        loadHint = label("", 11, TEXT2, false);
        HBox hintRow = new HBox(loadHint);
        hintRow.setAlignment(Pos.CENTER_RIGHT);
        VBox.setMargin(hintRow, new Insets(6, 0, 0, 0));

        Label note = label("objetivo, no garantía: si la CPU no da, baja sola", 11, TEXT2, false);
        HBox noteRow = new HBox(note);
        noteRow.setAlignment(Pos.CENTER_RIGHT);
        VBox.setMargin(noteRow, new Insets(2, 0, 0, 0));

        VBox box = new VBox(top, hintRow, noteRow);
        box.setStyle("-fx-background-color:transparent;");
        return box;
    }

    /** Speed multiplier used by the RL routes; always inside the C-side clamp. */
    private int rlSpeed() {
        if (rlSpeedSpinner == null) return RL_SPEED_DEF;
        Integer v = rlSpeedSpinner.getValue();
        if (v == null) return RL_SPEED_DEF;
        return Math.max(RL_SPEED_MIN, Math.min(RL_SPEED_MAX, v));
    }

    /**
     * Aggregate frame target across every instance. It is only a target: --speed is a
     * sleep-based limiter, so when the CPU cannot keep up each instance simply runs
     * slower on its own. Per-instance cost depends on the machine, so no "max
     * recommended speed" is invented here.
     */
    private void updateLoadTexts() {
        if (loadHint == null) return;
        int inst = instanceCount();
        int spd  = rlSpeed();
        loadHint.setText(inst + " inst × " + spd + "× → ~" + (inst * spd * BASE_FPS) + " fps objetivo");
    }

    // ── Instances section ──────────────────────────────────────
    /**
     * Ceiling on parallel instances: cores-1, not cores. The Python runner process (and
     * the OS) needs a core of its own; filling every core with game instances starves
     * the very process that drives them. Math.max(1, ...) keeps the spinner usable on
     * 1–2 core hosts, where cores-1 would be 0 or 1 and a max of 0 is a broken control.
     */
    private static int maxParallelInstances() {
        return Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
    }

    private VBox buildInstancesSection() {
        Label lbl = label("Lanzamientos", 12, TEXT2, false);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        instancesSpinner = new Spinner<>(
            new SpinnerValueFactory.IntegerSpinnerValueFactory(1, maxParallelInstances(), 1));
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

        instancesHint = label("", 11, TEXT2, false);
        HBox hintRow = new HBox(instancesHint);
        hintRow.setAlignment(Pos.CENTER_RIGHT);
        VBox.setMargin(hintRow, new Insets(5, 0, 0, 0));

        VBox box = new VBox(top, hintRow);
        box.setStyle("-fx-background-color:transparent;");
        updateInstanceTexts(instanceCount());   // one single formatter for the hint text
        return box;
    }

    private int instanceCount() {
        if (instancesSpinner == null) return 1;
        Integer v = instancesSpinner.getValue();
        return v == null ? 1 : Math.max(1, v);
    }

    /**
     * Upper bound read from the spinner's own value factory, so the hint can never drift
     * away from what the spinner actually accepts (it used to recompute the expression
     * and showed "máx. 12" on a host where the spinner stopped at 6).
     */
    private int maxInstances() {
        if (instancesSpinner != null
                && instancesSpinner.getValueFactory()
                   instanceof SpinnerValueFactory.IntegerSpinnerValueFactory f) {
            return f.getMax();
        }
        return 1;
    }

    private void updateInstanceTexts(int n) {
        if (instancesHint != null) {
            String suffix = "  (máx. " + maxInstances() + ")";
            instancesHint.setText(n == 1 ? "1 instancia" + suffix : n + " instancias en paralelo" + suffix);
        }
        if (launchHint != null) {
            launchHint.setText(n == 1 ? "Ready to launch." : "Launching " + n + " instances.");
        }
        updateLoadTexts();
        // The map draws one marker per instance, so the instance count is an input to it.
        redrawMap();
    }

    // ── Launch row ─────────────────────────────────────────────
    private HBox buildLaunchRow(Stage stage) {
        Label hint = label("Ready to launch.", 12, TEXT2, false);
        launchHint = hint;

        // Semantics (kept as-is): selected == NO render, so --render is passed to the
        // runner only when the toggle is NOT selected.
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
            if (idx < 0 || idx >= LEVELS.length) idx = 0;
            int starId = selectedStarId();
            int    n   = instanceCount();
            String mode = modeCombo.getSelectionModel().getSelectedItem();
            revealTerminal(stage);

            // Only route left: Java owns ONE python process; python owns the N instances.
            // renderToggle selected == "sin render", so --render goes when NOT selected.
            boolean render = renderToggle == null || !renderToggle.isSelected();
            SpawnCfg sp = currentSpawnCfg();
            runTraining(new GameLauncher.TrainingConfig(
                GameLauncher.DEFAULT_ENV_ID,
                LEVELS[idx].courseId,
                starId,
                rlSpeed(),
                n,
                render,
                MODE_EVAL.equals(mode) ? "eval" : "train",
                RL_EPISODES,
                RL_MAX_STEPS,
                RL_ALGO_ARG,
                "rl_" + System.currentTimeMillis(),
                sp.mode(),
                sp.spawns(),
                sp.area(),
                sp.y(),
                sp.seed()
            ));
        });

        HBox row = new HBox(12, hint, renderToggle, spacer, btn);
        row.setAlignment(Pos.CENTER_LEFT);
        updateInstanceTexts(instanceCount());
        return row;
    }

    // ── Terminal pane ──────────────────────────────────────────
    /**
     * Graceful shutdown: SIGTERM to the whole tree (python + its game children) so
     * every process runs its cleanup handler and no shm/semaphore is leaked in the
     * kernel; only SIGKILL as a last resort. Runs off the FX thread (waits up to 4s).
     */
    private void stopAllProcesses() {
        java.util.List<Process> snapshot;
        synchronized (activeProcesses) {
            snapshot = new java.util.ArrayList<>(activeProcesses);
            activeProcesses.clear();
        }
        if (snapshot.isEmpty()) {
            termAppend("[no active instances]");
            return;
        }
        termAppend("[stopping…]");

        Thread killer = new Thread(() -> {
            terminateTree(snapshot);
            Platform.runLater(() -> {
                termAppend("[stopped all instances]");
                setLaunchHint("stopped");
            });
        });
        killer.setDaemon(true);
        killer.start();
    }

    /** Blocking; never call from the JavaFX thread. */
    private static void terminateTree(java.util.List<Process> procs) {
        for (Process p : procs) {
            p.descendants().forEach(ProcessHandle::destroy);
            p.destroy();
        }
        for (Process p : procs) {
            try {
                if (!p.waitFor(4, java.util.concurrent.TimeUnit.SECONDS)) {
                    p.descendants().forEach(ProcessHandle::destroyForcibly);
                    p.destroyForcibly();
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                p.descendants().forEach(ProcessHandle::destroyForcibly);
                p.destroyForcibly();
            }
        }
    }

    /** Window close / "×": never leave orphan game instances behind. */
    private void shutdownAndExit() {
        java.util.List<Process> snapshot;
        synchronized (activeProcesses) {
            snapshot = new java.util.ArrayList<>(activeProcesses);
            activeProcesses.clear();
        }
        if (snapshot.isEmpty()) {
            Platform.exit();
            return;
        }
        termAppend("[stopping…]");
        Thread killer = new Thread(() -> {
            terminateTree(snapshot);
            Platform.runLater(Platform::exit);
        });
        killer.setDaemon(true);
        killer.start();
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

    // ── RL launch (single python process owns the N instances) ──
    private void runTraining(GameLauncher.TrainingConfig cfg) {
        java.util.List<String> args = GameLauncher.buildTrainingCommand(cfg);
        termAppend("$ " + String.join(" ", args));

        java.io.File binary = GameLauncher.findBinary();
        if (binary == null || !binary.exists()) {
            // Python spawns the game, but failing here gives an immediate, clear error.
            termAppend("[error] binary not found — run: make VERSION=us");
            return;
        }
        if (!binary.canExecute()) binary.setExecutable(true);

        java.io.File root = GameLauncher.projectRoot();
        if (root == null || !root.isDirectory()) {
            termAppend("[error] project root not found");
            return;
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(args);
            pb.directory(root);                                 // so `-m gymnasium_env.runner` resolves
            pb.environment().put("PYTHONUNBUFFERED", "1");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            synchronized (activeProcesses) { activeProcesses.add(p); }
            setLaunchHint(cfg.mode().equals("eval") ? "evaluating…" : "training…");

            Thread reader = new Thread(() -> {
                try (var br = new java.io.BufferedReader(
                        new java.io.InputStreamReader(p.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        final String ln = line;
                        Platform.runLater(() -> termAppendEvent(ln));
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

    // ── Runner event lines: "@@{...json...}" ───────────────────
    // Deliberately NOT a JSON parser: just enough regex to read "ev" / "i" and
    // print the payload without the "@@" marker.
    private static final java.util.regex.Pattern EV_PAT =
        java.util.regex.Pattern.compile("\"ev\"\\s*:\\s*\"([^\"]*)\"");
    private static final java.util.regex.Pattern IDX_PAT =
        java.util.regex.Pattern.compile("\"i\"\\s*:\\s*(\\d+)");
    private static final java.util.regex.Pattern EPISODES_PAT =
        java.util.regex.Pattern.compile("\"episodes\"\\s*:\\s*(\\d+)");

    private void termAppendEvent(String raw) {
        if (raw == null) return;
        if (!raw.startsWith("@@")) { termAppend(raw); return; }

        String payload = raw.substring(2).trim();
        java.util.regex.Matcher m = EV_PAT.matcher(payload);
        String ev = m.find() ? m.group(1) : "event";

        // Pretty-print: drop braces/quotes and the redundant "ev" pair, keep key=value pairs.
        String body = payload;
        if (body.startsWith("{") && body.endsWith("}")) body = body.substring(1, body.length() - 1);
        body = EV_PAT.matcher(body).replaceFirst("");
        body = body.replace("\"", "").replace(":", "=").replace(",", " ").trim();
        termAppend("[" + ev + "]" + (body.isEmpty() ? "" : " " + body));

        switch (ev) {
            case "start"         -> setLaunchHint("starting…");
            case "ready"         -> setLaunchHint("ready");
            case "instance_done" -> setLaunchHint("instance done");
            case "episode" -> {
                java.util.regex.Matcher mi = IDX_PAT.matcher(payload);
                setLaunchHint(mi.find()
                    ? "episode " + mi.group(1) + "/" + RL_EPISODES
                    : "training…");
            }
            case "done"    -> {
                java.util.regex.Matcher me = EPISODES_PAT.matcher(payload);
                setLaunchHint(me.find() ? "done (" + me.group(1) + " episodes)" : "done");
            }
            case "error"   -> setLaunchHint("error");
            default        -> { /* unknown event: already printed above */ }
        }
    }

    private void setLaunchHint(String text) {
        if (launchHint != null) launchHint.setText(text);
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
