package view;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class AppLauncher extends Application {

    private TextArea areaLog;
    private double xOffset = 0;
    private double yOffset = 0;

    @Override
    public void start(Stage primaryStage) {
        // 1. ELIMINAR BARRA DE WINDOWS PARA LOOK MODERNO
        primaryStage.initStyle(StageStyle.UNDECORATED);

        // Contenedor principal sin espacio entre el header y el contenido
        VBox root = new VBox(0);
        root.getStyleClass().add("root");

        // --- BARRA DE TÍTULO PERSONALIZADA (ESTILO MODERNO) ---
        HBox customHeader = new HBox();
        customHeader.getStyleClass().add("custom-header");
        customHeader.setAlignment(Pos.CENTER_RIGHT);
        customHeader.setPrefHeight(35);
        
        // Título de la app en la barra (opcional, alineado a la izquierda)
        Label windowTitle = new Label(" SVTECH // Protocol - Luis Rubio");
        windowTitle.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px; -fx-padding: 0 0 0 10;");
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Botones de control
        Button btnMin = new Button("♥"); // Em dash para mejor estética
        btnMin.getStyleClass().add("window-button");
        btnMin.setOnAction(e -> primaryStage.setIconified(true));

        Button btnClose = new Button("♦"); // Multiplication X para look pro
        btnClose.getStyleClass().addAll("window-button", "close-button");
        btnClose.setOnAction(e -> System.exit(0));

        customHeader.getChildren().addAll(windowTitle, spacer, btnMin, btnClose);

        // Lógica para arrastrar la ventana desde la barra personalizada
        customHeader.setOnMousePressed(event -> {
            xOffset = event.getSceneX();
            yOffset = event.getSceneY();
        });
        customHeader.setOnMouseDragged(event -> {
            primaryStage.setX(event.getScreenX() - xOffset);
            primaryStage.setY(event.getScreenY() - yOffset);
        });

        // --- CONTENIDO DE LA APLICACIÓN ---
        VBox mainContent = new VBox(20);
        mainContent.setPadding(new Insets(20));

        // Header original del diseño
        Label lblTitle = new Label("SVTECH // MINEDU PROTOCOL");
        lblTitle.getStyleClass().add("header-title");
        Label lblSub = new Label("Developed for Bitel Perú");
        lblSub.setStyle("-fx-text-fill: #94a3b8;");

        // TabPane (Pestañas)
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Tab t1 = new Tab("CREATE REPORT-GRFICAS", createSeccionDatos()); t1.getStyleClass().add("tab-blue");
        Tab t2 = new Tab("CONTEO DE GRAFICAS", createSeccionFotos()); t2.getStyleClass().add("tab-yellow");
        Tab t3 = new Tab("CREATE REPORT WORD", createSeccionJiraCompleta()); t3.getStyleClass().add("tab-magenta");
        Tab t4 = new Tab("CREATE TICKET JIRA", createSeccionInforme()); t4.getStyleClass().add("tab-orange");
        Tab t5 = new Tab("EXPORT ALL TICKET JIRA", createSeccionInforme()); t5.getStyleClass().add("tab-red");
        Tab t6 = new Tab("UPDATE TICKET JIRA", createSeccionInforme()); t6.getStyleClass().add("tab-green");
        tabPane.getTabs().addAll(t1, t2, t3, t4, t5, t6);

        // Log Consola
        VBox logBox = new VBox(8);
        Label lblLog = new Label("SYSTEM LOG");
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
            System.err.println("Error: No se encontró estilos.css en la carpeta resources.");
        }

        primaryStage.setScene(scene);
        primaryStage.show();
        
        log("Initiating secure connection to Jira API...");
    }

    // --- SECCIONES DE CONTENIDO ---
    private HBox createSeccionJiraCompleta() {
        HBox container = new HBox(30);
        container.setPadding(new Insets(20));
        container.setAlignment(Pos.CENTER);

        VBox cardLeft = new VBox(15);
        cardLeft.getStyleClass().addAll("card-base", "card-cyan");
        cardLeft.setPrefWidth(480);

        GridPane grid = new GridPane();
        grid.setVgap(12); grid.setHgap(15);
        String[] fields = {"PROJECT KEY:", "SUMMARY:", "ISSUE TYPE:", "DESCRIPTION:", "LOMP:"};
        for(int i=0; i<fields.length; i++) {
            grid.add(new Label(fields[i]), 0, i);
            TextField tf = new TextField();
            tf.setPrefWidth(300);
            grid.add(tf, 1, i);
        }
        cardLeft.getChildren().addAll(new Label("CREATE JIRA TICKET"), new Separator(), grid);

        VBox cardRight = new VBox(25);
        cardRight.getStyleClass().addAll("card-base", "card-magenta");
        cardRight.setPrefWidth(450);
        cardRight.setAlignment(Pos.CENTER);

        Button b1 = new Button("UPLOAD DATA MANIFEST >>");
        b1.getStyleClass().add("btn-blue"); b1.setPrefHeight(45); b1.setMaxWidth(Double.MAX_VALUE);

        Button b2 = new Button("FETCH TEMPLATE");
        b2.getStyleClass().add("btn-hollow"); b2.setPrefWidth(200);

        Button b3 = new Button("EXECUTE JIRA PROTOCOL >>");
        b3.getStyleClass().add("btn-orange"); b3.setPrefHeight(45); b3.setMaxWidth(Double.MAX_VALUE);

        cardRight.getChildren().addAll(new Label("EXECUTION PANEL"), b1, b2, b3);
        container.getChildren().addAll(cardLeft, cardRight);
        return container;
    }

    private VBox createSeccionDatos() {
        VBox card = new VBox(20);
        card.getStyleClass().addAll("card-base", "card-cyan");
        card.setMaxWidth(600);
        card.setAlignment(Pos.CENTER);

        GridPane grid = new GridPane();
        grid.setHgap(20); grid.setVgap(15);
        grid.setAlignment(Pos.CENTER);
        grid.add(new Label("Fecha Inicio:"), 0, 0); grid.add(new DatePicker(), 1, 0);
        grid.add(new Label("Fecha Fin:"), 0, 1); grid.add(new DatePicker(), 1, 1);
        grid.add(new Label("Item / Código:"), 0, 2); grid.add(new TextField(), 1, 2);

        Button btn = new Button("INICIAR EXTRACCIÓN PROTOCOL");
        btn.getStyleClass().add("btn-blue"); btn.setPrefWidth(400);

        card.getChildren().addAll(new Label("PARÁMETROS DE EXTRACCIÓN"), grid, btn);
        VBox container = new VBox(card);
        container.setAlignment(Pos.CENTER);
        container.setPadding(new Insets(30));
        return container;
    }

    private VBox createSeccionFotos() { 
        VBox v = new VBox(new Label("Módulo de Conteo de Imágenes Activo...")); 
        v.setAlignment(Pos.CENTER); v.setPadding(new Insets(50));
        return v;
    }
    
    private VBox createSeccionInforme() { 
        VBox v = new VBox(new Label("Generador de Reportes Word Listo...")); 
        v.setAlignment(Pos.CENTER); v.setPadding(new Insets(50));
        return v;
    }

    private void log(String msg) {
        areaLog.appendText("[SYSTEM]: " + msg + "\n");
    }

    public static void main(String[] args) { launch(args); }
}