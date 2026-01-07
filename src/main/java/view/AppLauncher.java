package view;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import java.io.File;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

public class AppLauncher extends Application {

    private TextArea areaLog;
    private File archivoSeleccionado;

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Generador MINEDU Pro");

        // Layout Principal: Un VBox que contiene el Header y el TabPane
        VBox rootLayout = new VBox(10);
        rootLayout.setPadding(new Insets(20));
        rootLayout.setAlignment(Pos.TOP_CENTER);

        // --- 1. HEADER (Fijo para todas las pestañas) ---
        VBox headerBox = createHeader(primaryStage);

        // --- 2. CONTENEDOR DE PESTAÑAS (TabPane) ---
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        VBox.setVgrow(tabPane, Priority.ALWAYS); // Hace que las pestañas ocupen el espacio disponible

        // Crear las 3 pestañas
        Tab tabDatos = new Tab("Obtener Datos", createSeccionDatos(primaryStage));
     // Busca esta línea en tu método start y cámbiala:
        Tab tabFotos = new Tab("Conteo de Imágenes", createSeccionFotos(primaryStage));
        Tab tabInforme = new Tab("Generar Informe", createSeccionInforme(primaryStage));

        tabPane.getTabs().addAll(tabDatos, tabFotos, tabInforme);

        // --- 3. ÁREA DE LOG (Común abajo) ---
        areaLog = new TextArea();
        areaLog.setPrefHeight(100);
        areaLog.setEditable(false);
        areaLog.getStyleClass().add("log-area");

        rootLayout.getChildren().addAll(headerBox, tabPane, areaLog);

        Scene scene = new Scene(rootLayout, 550, 780);
        try {
            scene.getStylesheets().add(getClass().getResource("/estilos.css").toExternalForm());
        } catch (Exception e) {}

        primaryStage.setScene(scene);
        primaryStage.show();
    }

    // --- SECCIÓN 1: OBTENER DATOS (Tu código original) ---
    private VBox createSeccionDatos(Stage stage) {
        VBox container = new VBox(15);
        container.setPadding(new Insets(20));
        container.setAlignment(Pos.CENTER);

        VBox formCard = new VBox(15);
        formCard.getStyleClass().add("card-container");

        Button btnSubirExcel = new Button("📁 CARGAR BASE EXCEL");
        btnSubirExcel.setMaxWidth(Double.MAX_VALUE);
        btnSubirExcel.setStyle("-fx-background-color: #2c3e50; -fx-text-fill: white;");

        Label lblArchivoStatus = new Label("Ningún archivo seleccionado");

        GridPane gridForm = new GridPane();
        gridForm.setHgap(10); gridForm.setVgap(10);
        gridForm.add(new Label("Fecha Inicio:"), 0, 0);
        gridForm.add(new DatePicker(), 1, 0);
        gridForm.add(new Label("Fecha Fin:"), 0, 1);
        gridForm.add(new DatePicker(), 1, 1);
        gridForm.add(new Label("Item / Código:"), 0, 2);
        gridForm.add(new TextField(), 1, 2);

        Button btnGenerar = new Button("INICIAR PROCESO");
        btnGenerar.getStyleClass().add("action-button");
        btnGenerar.setMaxWidth(Double.MAX_VALUE);

        formCard.getChildren().addAll(btnSubirExcel, lblArchivoStatus, new Separator(), gridForm, btnGenerar);
        container.getChildren().add(formCard);
        
        return container;
    }

    // --- SECCIÓN 2: CONTEO DE IMÁGENES (Nueva) ---
    private VBox createSeccionFotos(Stage stage) { // Añadimos stage como parámetro
        VBox container = new VBox(20);
        container.setPadding(new Insets(30));
        container.setAlignment(Pos.CENTER);

        Label lblInfo = new Label("Módulo de Conteo de Imágenes");
        lblInfo.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        // --- NUEVO: CARGAR EXCEL PARA CONTEO ---
        Button btnCargarExcelConteo = new Button("📁 CARGAR EXCEL PARA CONTEO");
        btnCargarExcelConteo.setMaxWidth(300);
        btnCargarExcelConteo.setStyle("-fx-background-color: #34495e; -fx-text-fill: white;");

        Label lblStatusConteo = new Label("No se ha cargado base para conteo");
        lblStatusConteo.setStyle("-fx-font-size: 11px; -fx-text-fill: #7f8c8d;");

        // Evento para el nuevo botón
        btnCargarExcelConteo.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Seleccionar Excel de Conteo");
            fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Archivos Excel", "*.xlsx", "*.xls")
            );
            File file = fileChooser.showOpenDialog(stage);
            if (file != null) {
                lblStatusConteo.setText("📂 Base: " + file.getName());
                log("✔ Base de conteo cargada: " + file.getAbsolutePath());
            }
        });

        // Tu botón original de carpeta
        Button btnSeleccionarCarpeta = new Button("📷 SELECCIONAR CARPETA DE FOTOS");
        btnSeleccionarCarpeta.setMaxWidth(300);

        // Agregamos todo al contenedor
        container.getChildren().addAll(
            lblInfo, 
            new Separator(), 
            btnCargarExcelConteo, 
            lblStatusConteo, 
            btnSeleccionarCarpeta
        );
        
        return container;
    }

    // --- SECCIÓN 3: GENERAR INFORME (Nueva) ---
    private VBox createSeccionInforme(Stage stage) {
        VBox container = new VBox(15);
        container.setPadding(new Insets(20));
        container.setAlignment(Pos.CENTER);

        Button btnDescargar = new Button("📥 DESCARGAR RESULTADO FINAL (Word)");
        btnDescargar.getStyleClass().add("action-button");
        btnDescargar.setPrefHeight(50);

        container.getChildren().add(btnDescargar);
        return container;
    }

    // Helper para el Header
    private VBox createHeader(Stage primaryStage) {
        VBox headerBox = new VBox(10);
        headerBox.setAlignment(Pos.CENTER);
        try {
            Image imgLogo = new Image(getClass().getResourceAsStream("/logoSvteche/logosvtench.png"));
            ImageView vistaLogo = new ImageView(imgLogo);
            vistaLogo.setFitWidth(150);
            vistaLogo.setPreserveRatio(true);
            headerBox.getChildren().add(vistaLogo);
            primaryStage.getIcons().add(imgLogo);
        } catch (Exception e) {}
        
        Label lblTitle = new Label("Generador de Reportes");
        lblTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        headerBox.getChildren().add(lblTitle);
        return headerBox;
    }

    private void log(String mensaje) {
        if (areaLog != null) areaLog.appendText(mensaje + "\n");
    }

    public static void main(String[] args) {
        launch(args);
    }
}