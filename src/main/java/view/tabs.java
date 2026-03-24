package view;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import java.io.File;

public class tabs extends Application {

    private TextArea areaLog;
    private double xOffset = 0;
    private double yOffset = 0;
    private File excelSeleccionado;
    private Stage primaryStage; // Referencia al Stage principal

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage; // Guardamos la referencia
        primaryStage.initStyle(StageStyle.UNDECORATED);

        VBox root = new VBox(0);
        root.getStyleClass().add("root");

        // --- BARRA DE TÍTULO ---
        HBox customHeader = new HBox();
        customHeader.getStyleClass().add("custom-header");
        customHeader.setAlignment(Pos.CENTER_RIGHT);
        customHeader.setPrefHeight(35);

        Label windowTitle = new Label(" 🌐 MINEDU Protocol - Luis Rubio");
        windowTitle.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px; -fx-padding: 0 0 0 10;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button btnMin = new Button("➖");
        btnMin.getStyleClass().add("window-button");
        btnMin.setOnAction(e -> primaryStage.setIconified(true));

        Button btnClose = new Button("❌");
        btnClose.getStyleClass().addAll("window-button", "close-button");
        btnClose.setOnAction(e -> System.exit(0));

        customHeader.getChildren().addAll(windowTitle, spacer, btnMin, btnClose);

        customHeader.setOnMousePressed(event -> {
            xOffset = event.getSceneX();
            yOffset = event.getSceneY();
        });
        customHeader.setOnMouseDragged(event -> {
            primaryStage.setX(event.getScreenX() - xOffset);
            primaryStage.setY(event.getScreenY() - yOffset);
        });

        // --- CONTENIDO ---
        VBox mainContent = new VBox(20);
        mainContent.setPadding(new Insets(20));

        Label lblTitle = new Label("🚀 MINEDU 5k");
        lblTitle.getStyleClass().add("header-title");
        Label lblSub = new Label("Developed for Bitel Perú | Network Operations");
        lblSub.setStyle("-fx-text-fill: #94a3b8;");

        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // Pestañas con Emojis
        Tab t1 = new Tab("📊 GRÁFICAS", createSeccionDatosGraficas());
        t1.getStyleClass().add("tab-blue");

        Tab t2 = new Tab("📝 WORD REPORT", createSeccionFotosWord());
        t2.getStyleClass().add("tab-yellow");

        Tab t3 = new Tab("🔄 STATUS", SttatusJiraCompleta());
        t3.getStyleClass().add("tab-magenta");

        Tab t4 = new Tab("➕ CREATE JIRA", CreateJira("NUEVO TICKET JIRA"));
        t4.getStyleClass().add("tab-orange");

        Tab t5 = new Tab("📥 EXPORT", ExportJira("EXPORTAR TODO"));
        t5.getStyleClass().add("tab-red");

        Tab t6 = new Tab("🆙 UPDATE", UpdateGenerica("ACTUALIZAR JIRA"));
        t6.getStyleClass().add("tab-green");

        tabPane.getTabs().addAll(t1, t2, t3, t4, t5, t6);

        // Log Consola
        VBox logBox = new VBox(8);
        Label lblLog = new Label("💻 SYSTEM LOG");
        areaLog = new TextArea();
        areaLog.setPrefHeight(150);
        areaLog.getStyleClass().add("log-area");
        areaLog.setEditable(false);
        logBox.getChildren().addAll(lblLog, areaLog);

        mainContent.getChildren().addAll(lblTitle, lblSub, tabPane, logBox);
        root.getChildren().addAll(customHeader, mainContent);

        Scene scene = new Scene(root, 1150, 800);
        try {
            scene.getStylesheets().add(getClass().getResource("/estilos.css").toExternalForm());
        } catch (Exception e) {
            System.err.println("Error: No se encontró estilos.css");
        }

        primaryStage.setScene(scene);
        primaryStage.show();

        log("Welcome, initiating secure connection to Jira API...");
    }

    // ==================== PESTAÑAS CON EXCEL UPLOAD ====================

    /**
     * Pestaña STATUS: permite subir un Excel y ejecutar acción simulada.
     */
    private Node SttatusJiraCompleta() {
        return createTabWithExcelUpload("📋 STATUS JIRA", "⚙️ VERIFICAR ESTADO");
    }

    /**
     * Pestaña CREATE JIRA: permite subir un Excel y crear ticket.
     */
    private Node CreateJira(String titulo) {
        return createTabWithExcelUpload("➕ " + titulo, "🚀 CREAR TICKET DESDE EXCEL");
    }

    /**
     * Pestaña EXPORT: permite subir un Excel y exportar datos.
     */
    private Node ExportJira(String titulo) {
        return createTabWithExcelUpload("📥 " + titulo, "📤 EXPORTAR INFORMACIÓN");
    }

    /**
     * Pestaña UPDATE: permite subir un Excel y actualizar Jira.
     */
    private Node UpdateGenerica(String titulo) {
        return createTabWithExcelUpload("🆙 " + titulo, "🔄 ACTUALIZAR DESDE EXCEL");
    }

    // ==================== COMPONENTE REUTILIZABLE ====================

    /**
     * Construye una tarjeta con título, selector de Excel y botón de acción.
     */
    private VBox createTabWithExcelUpload(String tituloTarjeta, String textoBoton) {
        VBox v = new VBox(40);
        v.getStyleClass().addAll("card-base", "card-cyan"); // color genérico
        v.setMaxWidth(600);
        v.setAlignment(Pos.CENTER);

        // Título
        Label lblTitulo = new Label(tituloTarjeta);
        lblTitulo.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        // Grid para el upload
        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(15);
        grid.setAlignment(Pos.CENTER);

        Label lblSubir = new Label("📁 SUBIR EXCEL:");
        Button btnSubirExcel = new Button("📂 Seleccionar Archivo");
        btnSubirExcel.setPrefWidth(220);
        btnSubirExcel.getStyleClass().add("btn-hollow");

        // Acción del botón de subida
        btnSubirExcel.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Abrir Excel de Protocolo");
            fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Excel Files", "*.xlsx", "*.xls")
            );
            File file = fileChooser.showOpenDialog(primaryStage);
            if (file != null) {
                excelSeleccionado = file;
                btnSubirExcel.setText("✅ " + file.getName());
                btnSubirExcel.setStyle("-fx-border-color: #22c55e; -fx-text-fill: #22c55e;");
                log("Archivo Excel cargado: " + file.getAbsolutePath());
            }
        });

        grid.add(lblSubir, 0, 0);
        grid.add(btnSubirExcel, 1, 0);

        // Botón de acción principal
        Button btnExec = new Button(textoBoton);
        btnExec.getStyleClass().add("btn-blue");
        btnExec.setPrefWidth(400);
        btnExec.setOnAction(e -> {
            if (excelSeleccionado == null) {
                log("❌ ERROR: No se ha seleccionado ningún archivo Excel.");
            } else {
                log("⏳ Ejecutando: " + textoBoton + " con archivo " + excelSeleccionado.getName());
                // Aquí iría la lógica específica de cada pestaña
            }
        });

        v.getChildren().addAll(lblTitulo, grid, btnExec);
        return wrap(v);
    }

    // ==================== PESTAÑAS ORIGINALES (sin cambios) ====================

    private VBox createSeccionFotosWord() {
        VBox v = new VBox(40);
        v.getStyleClass().addAll("card-base", "card-yellow");
        v.setMaxWidth(600);
        v.setAlignment(Pos.CENTER);

        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(15);
        grid.setAlignment(Pos.CENTER);

        grid.add(new Label("📁 SUBIR EXCEL:"), 0, 0);
        Button btnSubirExcel = new Button("📂 Seleccionar Archivo");
        btnSubirExcel.setPrefWidth(220);
        btnSubirExcel.getStyleClass().add("btn-hollow");

        btnSubirExcel.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Abrir Excel de Protocolo");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Files", "*.xlsx", "*.xls"));
            File file = fileChooser.showOpenDialog(primaryStage);
            if (file != null) {
                excelSeleccionado = file;
                btnSubirExcel.setText("✅ " + file.getName());
                btnSubirExcel.setStyle("-fx-border-color: #22c55e; -fx-text-fill: #22c55e;");
                log("Archivo Excel cargado: " + file.getAbsolutePath());
            }
        });

        grid.add(btnSubirExcel, 1, 0);
        grid.add(new Label("🖼️ RUTA IMG: "), 0, 1);
        grid.add(new TextField(), 1, 1);
        grid.add(new Label("🆔 ITEM: "), 0, 2);
        grid.add(new TextField(), 1, 2);

        Button btnExec = new Button("⚙️ INICIAR EXTRACCIÓN PROTOCOL");
        btnExec.getStyleClass().add("btn-blue");
        btnExec.setPrefWidth(400);
        btnExec.setOnAction(e -> log(excelSeleccionado == null ? "❌ ERROR: Falta Excel" : "⏳ Procesando..."));

        v.getChildren().addAll(new Label("CREACIÓN DE INFORME"), grid, btnExec);
        return wrap(v);
    }

    private VBox createSeccionDatosGraficas() {
        VBox card = new VBox(40);
        card.getStyleClass().addAll("card-base", "card-cyan");
        card.setMaxWidth(600);
        card.setAlignment(Pos.CENTER);

        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(15);
        grid.setAlignment(Pos.CENTER);
        grid.add(new Label("📅 Fecha Inicio:"), 0, 0);
        grid.add(new DatePicker(), 1, 0);
        grid.add(new Label("📅 Fecha Fin:"), 0, 1);
        grid.add(new DatePicker(), 1, 1);
        grid.add(new Label("🔍 Item / Código:"), 0, 2);
        grid.add(new TextField(), 1, 2);

        Button btn = new Button("📈 GENERAR GRÁFICAS");
        btn.getStyleClass().add("btn-blue");
        btn.setPrefWidth(400);

        card.getChildren().addAll(new Label("PARÁMETROS DE PERÍODO"), grid, btn);
        return wrap(card);
    }

    private HBox createSeccionJiraCompleta() {
        HBox container = new HBox(30);
        container.setPadding(new Insets(20));
        container.setAlignment(Pos.CENTER);

        VBox cardLeft = new VBox(15);
        cardLeft.getStyleClass().addAll("card-base", "card-magenta");
        cardLeft.setPrefWidth(480);

        GridPane grid = new GridPane();
        grid.setVgap(12);
        grid.setHgap(15);
        String[] fields = { "PROJECT KEY", "SUMMARY", "ISSUE TYPE", "DESCRIPTION", "LOMP" };
        for (int i = 0; i < fields.length; i++) {
            grid.add(new Label("🔹 " + fields[i] + ":"), 0, i);
            grid.add(new TextField(), 1, i);
        }
        cardLeft.getChildren().addAll(new Label("📝 CREATE JIRA TICKET"), new Separator(), grid);

        VBox cardRight = new VBox(25);
        cardRight.getStyleClass().addAll("card-base", "card-magenta");
        cardRight.setPrefWidth(450);
        cardRight.setAlignment(Pos.CENTER);

        Button b1 = new Button("📥 DOWNLOAD EXCEL >>");
        b1.getStyleClass().add("btn-blue");
        b1.setPrefHeight(45);
        b1.setMaxWidth(Double.MAX_VALUE);

        Button b3 = new Button("⚡ EXECUTE JIRA UPDATE >>");
        b3.getStyleClass().add("btn-orange");
        b3.setPrefHeight(45);
        b3.setMaxWidth(Double.MAX_VALUE);

        cardRight.getChildren().addAll(new Label("🎮 CONTROL PANEL"), b1, b3);
        container.getChildren().addAll(cardLeft, cardRight);
        return container;
    }

    // ==================== UTILIDADES ====================

    private VBox wrap(VBox v) {
        VBox c = new VBox(v);
        c.setAlignment(Pos.CENTER);
        c.setPadding(new Insets(30));
        return c;
    }

    private void log(String msg) {
        areaLog.appendText("[" + java.time.LocalTime.now().toString().substring(0, 8) + "] " + msg + "\n");
    }

    public static void main(String[] args) {
        launch(args);
    }
}