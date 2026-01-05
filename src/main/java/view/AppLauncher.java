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
import javafx.stage.Stage;
import java.time.format.DateTimeFormatter;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
public class AppLauncher extends Application {

    private TextArea areaLog; // Lo hacemos variable de clase para acceder fácil

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Generador MINEDU Pro");

        // --- 1. Contenedor Principal (Raíz) ---
        // VBox organiza los elementos uno debajo del otro
        VBox mainLayout = new VBox(20); // 20px de espacio vertical entre elementos
        mainLayout.setAlignment(Pos.CENTER);
        mainLayout.setPadding(new Insets(30)); // Margen externo grande

        // --- 2. Header (Títulos) ---
        VBox headerBox = new VBox(10);
        headerBox.setAlignment(Pos.CENTER);
        
        
        try {
            // Cargar la imagen desde la carpeta resources/img
            Image imgLogo = new Image(getClass().getResourceAsStream("/logoSvteche/logosvtench.png"));
            ImageView vistaLogo = new ImageView(imgLogo);
            
            // Ajustar tamaño del logo (Juega con este valor: 150, 200, etc.)
            vistaLogo.setFitWidth(180); 
            vistaLogo.setPreserveRatio(true); // Mantiene las proporciones para no deformarlo

            // Agregamos el logo al header
            headerBox.getChildren().add(vistaLogo);
            
            // EXTRA: Poner el logo también como icono de la ventana (barra de tareas)
            primaryStage.getIcons().add(imgLogo);

        } catch (Exception e) {
            System.out.println("Advertencia: No se encontró el logo en /img/logo.png");
        }
        
        
        Label lblTitle = new Label("Generador de Reportes");
        lblTitle.getStyleClass().add("header-title"); // Clase CSS
        
        Label lblSubtitle = new Label("Automatización MINEDU / NOC");
        lblSubtitle.getStyleClass().add("header-subtitle"); // Clase CSS
        
        headerBox.getChildren().addAll(lblTitle, lblSubtitle);

        // --- 3. La "Tarjeta" del Formulario ---
        VBox formCard = new VBox(15); // Espacio interno vertical
        formCard.getStyleClass().add("card-container"); // ASIGNAMOS LA CLASE CSS DE TARJETA
        formCard.setMaxWidth(450); // Ancho máximo para que se vea elegante

        // Usamos un GridPane dentro de la tarjeta para alinear etiquetas y campos
        GridPane gridForm = new GridPane();
        gridForm.setHgap(15); gridForm.setVgap(15);
        gridForm.setAlignment(Pos.CENTER);

        // Componentes
        DatePicker dateFechaIni = new DatePicker();
        dateFechaIni.setPromptText("Seleccionar inicio");
        dateFechaIni.setMaxWidth(Double.MAX_VALUE); // Que ocupe todo el ancho disponible

        DatePicker dateFechaFin = new DatePicker();
        dateFechaFin.setPromptText("Seleccionar fin");
        dateFechaFin.setMaxWidth(Double.MAX_VALUE);

        TextField txtItem = new TextField();
        txtItem.setPromptText("Ej: Código de local");

        // Agregamos al grid con etiquetas
        gridForm.add(new Label("Fecha Inicio:"), 0, 0);
        gridForm.add(dateFechaIni, 1, 0);
        gridForm.add(new Label("Fecha Fin:"), 0, 1);
        gridForm.add(dateFechaFin, 1, 1);
        gridForm.add(new Label("Item / Código:"), 0, 2);
        gridForm.add(txtItem, 1, 2);

        // Botón de Acción (Centrado)
        Button btnGenerar = new Button("INICIAR PROCESO");
        btnGenerar.getStyleClass().add("action-button"); // Clase CSS del botón moderno
        btnGenerar.setMaxWidth(Double.MAX_VALUE); // Botón ancho
        
        // Metemos el grid y el botón dentro de la tarjeta
        formCard.getChildren().addAll(gridForm, btnGenerar);

        // --- 4. Área de Log (Fuera de la tarjeta, abajo) ---
        areaLog = new TextArea();
        areaLog.getStyleClass().add("log-area"); // Clase CSS de terminal
        areaLog.setPrefHeight(120);
        areaLog.setEditable(false);
        areaLog.setWrapText(true);
        areaLog.setMaxWidth(450); // Mismo ancho que la tarjeta
        VBox.setVgrow(areaLog, Priority.ALWAYS); // Que crezca si soba espacio

        // --- 5. Armar el Layout Principal ---
        mainLayout.getChildren().addAll(headerBox, formCard, areaLog);

        // --- Lógica del Botón ---
        btnGenerar.setOnAction(e -> {
            if(dateFechaIni.getValue() == null || dateFechaFin.getValue() == null) {
                log("⚠ Atención: Debe seleccionar ambas fechas.");
                return;
            }
            if(txtItem.getText().isEmpty()) {
                log("⚠ Atención: El campo Ítem/Código está vacío.");
                return;
            }

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            String fIni = dateFechaIni.getValue().format(formatter);
            String fFin = dateFechaFin.getValue().format(formatter);
            String item = txtItem.getText();

            log("🚀 Iniciando proceso para: " + item + " [" + fIni + " - " + fFin + "]");
            btnGenerar.setDisable(true); // Deshabilitar botón mientras procesa
            btnGenerar.setText("PROCESANDO...");

            new Thread(() -> {
                try {
                    // --- TU LLAMADA AL CONTROLADOR ---
                    // report.minedu proceso = new report.minedu();
                    // proceso.ejecutarProceso(fIni, fFin, item);

                    Thread.sleep(2000); // Simulación de trabajo (BORRAR LUEGO)

                    javafx.application.Platform.runLater(() -> {
                        log("✅ ¡Proceso finalizado con éxito!");
                        btnGenerar.setDisable(false);
                        btnGenerar.setText("INICIAR PROCESO");
                    });
                } catch (Exception ex) {
                    javafx.application.Platform.runLater(() -> {
                        log("❌ Error crítico: " + ex.getMessage());
                         btnGenerar.setDisable(false);
                         btnGenerar.setText("INICIAR PROCESO");
                    });
                }
            }).start();
        });

        // Creación de la Escena
        Scene scene = new Scene(mainLayout, 550, 650); // Ventana un poco más alta
        
        // Importante: Cargar el CSS
        try {
            String css = this.getClass().getResource("/estilos.css").toExternalForm();
            scene.getStylesheets().add(css);
        } catch (Exception e) {
            System.out.println("Error cargando CSS: Asegúrate que estilos.css esté en src/main/resources");
        }

        primaryStage.setScene(scene);
        //primaryStage.setResizable(false); // Opcional: evitar que cambien el tamaño
        primaryStage.show();
    }

    // Método auxiliar para escribir en el log más fácil
    private void log(String mensaje) {
        areaLog.appendText(mensaje + "\n");
    }

    public static void main(String[] args) {
        launch(args);
    }
}