package view;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.FileChooser; // Importante
import javafx.stage.Stage;
import java.io.File;
import java.time.format.DateTimeFormatter;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

public class AppLauncher extends Application {

    private TextArea areaLog;
    private File archivoSeleccionado; // Para guardar la referencia del Excel subido

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Generador MINEDU Pro");

        VBox mainLayout = new VBox(20);
        mainLayout.setAlignment(Pos.CENTER);
        mainLayout.setPadding(new Insets(30));

        // --- 1. Header ---
        VBox headerBox = new VBox(10);
        headerBox.setAlignment(Pos.CENTER);
        
        try {
            Image imgLogo = new Image(getClass().getResourceAsStream("/logoSvteche/logosvtench.png"));
            ImageView vistaLogo = new ImageView(imgLogo);
            vistaLogo.setFitWidth(180); 
            vistaLogo.setPreserveRatio(true);
            headerBox.getChildren().add(vistaLogo);
            primaryStage.getIcons().add(imgLogo);
        } catch (Exception e) {
            System.out.println("Advertencia: No se encontró el logo.");
        }
        
        Label lblTitle = new Label("Generador de Reportes");
        lblTitle.getStyleClass().add("header-title");
        headerBox.getChildren().add(lblTitle);

        // --- 2. Tarjeta del Formulario ---
        VBox formCard = new VBox(15);
        formCard.getStyleClass().add("card-container");
        formCard.setMaxWidth(450);

        // Botón Subir Excel
        Button btnSubirExcel = new Button("📁 CARGAR BASE EXCEL");
        btnSubirExcel.setMaxWidth(Double.MAX_VALUE);
        btnSubirExcel.setStyle("-fx-background-color: #2c3e50; -fx-text-fill: white;"); // Estilo rápido

        Label lblArchivoStatus = new Label("Ningún archivo seleccionado");
        lblArchivoStatus.setStyle("-fx-font-size: 11px; -fx-text-fill: gray;");

        GridPane gridForm = new GridPane();
        gridForm.setHgap(15); gridForm.setVgap(15);
        gridForm.setAlignment(Pos.CENTER);

        DatePicker dateFechaIni = new DatePicker();
        dateFechaIni.setMaxWidth(Double.MAX_VALUE);
        DatePicker dateFechaFin = new DatePicker();
        dateFechaFin.setMaxWidth(Double.MAX_VALUE);
        TextField txtItem = new TextField();

        gridForm.add(new Label("Fecha Inicio:"), 0, 0);
        gridForm.add(dateFechaIni, 1, 0);
        gridForm.add(new Label("Fecha Fin:"), 0, 1);
        gridForm.add(dateFechaFin, 1, 1);
        gridForm.add(new Label("Item / Código:"), 0, 2);
        gridForm.add(txtItem, 1, 2);

        Button btnGenerar = new Button("INICIAR PROCESO");
        btnGenerar.getStyleClass().add("action-button");
        btnGenerar.setMaxWidth(Double.MAX_VALUE);
        
        formCard.getChildren().addAll(btnSubirExcel, lblArchivoStatus, new Separator(), gridForm, btnGenerar);

        // --- 3. Área de Log ---
        areaLog = new TextArea();
        areaLog.getStyleClass().add("log-area");
        areaLog.setPrefHeight(120);
        areaLog.setEditable(false);
        areaLog.setMaxWidth(450);
        VBox.setVgrow(areaLog, Priority.ALWAYS);

        // --- 4. Botón Descargar (Abajo) ---
        Button btnDescargar = new Button("📥 DESCARGAR RESULTADO (EXCEL)");
        btnDescargar.setMaxWidth(450);
        btnDescargar.setDisable(true); // Deshabilitado hasta que termine el proceso

        mainLayout.getChildren().addAll(headerBox, formCard, areaLog, btnDescargar);

        // --- LÓGICA DE BOTONES ---

        // Evento: Subir Excel
        btnSubirExcel.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Seleccionar Base de Datos Excel");
            fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Archivos Excel", "*.xlsx", "*.xls")
            );
            archivoSeleccionado = fileChooser.showOpenDialog(primaryStage);
            
            if (archivoSeleccionado != null) {
                lblArchivoStatus.setText("📂 Archivo: " + archivoSeleccionado.getName());
                log("✔ Excel cargado: " + archivoSeleccionado.getAbsolutePath());
            }
        });

        // Evento: Generar Proceso
        btnGenerar.setOnAction(e -> {
            if(dateFechaIni.getValue() == null || dateFechaFin.getValue() == null || txtItem.getText().isEmpty()) {
                log("⚠ Atención: Complete todos los campos.");
                return;
            }

            log("🚀 Iniciando proceso...");
            btnGenerar.setDisable(true);
            btnGenerar.setText("PROCESANDO...");

            new Thread(() -> {
                try {
                    Thread.sleep(2500); // Simulación
                    javafx.application.Platform.runLater(() -> {
                        log("✅ ¡Proceso finalizado!");
                        btnGenerar.setDisable(false);
                        btnGenerar.setText("INICIAR PROCESO");
                        btnDescargar.setDisable(false); // Habilitar descarga
                    });
                } catch (Exception ex) {
                    javafx.application.Platform.runLater(() -> log("❌ Error: " + ex.getMessage()));
                }
            }).start();
        });

        // Evento: Descargar Excel
        btnDescargar.setOnAction(e -> {
            FileChooser saveChooser = new FileChooser();
            saveChooser.setTitle("Guardar Reporte");
            saveChooser.setInitialFileName("Reporte_MINEDU.xlsx");
            saveChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel", "*.xlsx"));
            
            File saveFile = saveChooser.showSaveDialog(primaryStage);
            if (saveFile != null) {
                log("💾 Reporte guardado en: " + saveFile.getAbsolutePath());
            }
        });

        Scene scene = new Scene(mainLayout, 550, 750); // Aumentamos un poco el alto
        try {
            scene.getStylesheets().add(getClass().getResource("/estilos.css").toExternalForm());
        } catch (Exception e) {}

        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void log(String mensaje) {
        areaLog.appendText(mensaje + "\n");
    }

    public static void main(String[] args) {
        launch(args);
    }
}