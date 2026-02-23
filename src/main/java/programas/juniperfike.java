
package programas;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class juniperfike {
	public static void main(String[] args) {
        // Archivo que el programa va a leer
        String rutaExcelEntrada = "datos_tickets.xlsx";
        // Nuevo archivo que el programa va a crear con las rutas
        String rutaExcelSalida = "reporte_evidencias.xlsx"; 
        String carpetaSalida = "evidencias_minedu";

        new File(carpetaSalida).mkdirs();
        Random random = new Random();
        DateTimeFormatter formatterSalida = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        // 1. Preparar el NUEVO Excel de Salida
        Workbook workbookSalida = new XSSFWorkbook();
        Sheet sheetSalida = workbookSalida.createSheet("Tickets Procesados");

        // Crear los encabezados de la Fila 1 en el nuevo Excel
        Row filaEncabezado = sheetSalida.createRow(0);
        filaEncabezado.createCell(0).setCellValue("CID");
        filaEncabezado.createCell(1).setCellValue("Fecha Inicial");
        filaEncabezado.createCell(2).setCellValue("Fecha Final");
        filaEncabezado.createCell(3).setCellValue("Ruta de Imagen"); // La nueva columna

        int filaSalidaIndex = 1; // Para ir escribiendo desde la Fila 2 en adelante

        try (FileInputStream fis = new FileInputStream(new File(rutaExcelEntrada));
             Workbook workbookEntrada = new XSSFWorkbook(fis)) {

            Sheet sheetEntrada = workbookEntrada.getSheetAt(0);
            DataFormatter dataFormatter = new DataFormatter();

            System.out.println("Iniciando generación de imágenes y reporte Excel...\n");

            // Recorrer tu Excel original
            for (Row row : sheetEntrada) {
                if (row.getRowNum() == 0) continue;

                Cell celdaCodigo = row.getCell(0);
                Cell celdaFechaInicio = row.getCell(1);
                Cell celdaFechaFin = row.getCell(2);

                if (celdaCodigo != null && celdaFechaInicio != null && celdaFechaFin != null) {
                    String codigoCL = dataFormatter.formatCellValue(celdaCodigo).trim();
                    if (codigoCL.isEmpty()) continue;

                    try {
                        LocalDate fechaActual = obtenerFechaReal(celdaFechaInicio);
                        LocalDate fechaFin = obtenerFechaReal(celdaFechaFin);

                        if (fechaActual == null || fechaFin == null) continue;

                        String fechaInicioTexto = fechaActual.format(formatterSalida);
                        String fechaFinTexto = fechaFin.format(formatterSalida); // Guardamos para el reporte

                        int minutoPrimerLog = random.nextInt(50) + 10;
                        String horaPrimerLog = "07:" + minutoPrimerLog;

                        List<String> lineasConsola = new ArrayList<>();
                        lineasConsola.add("juniper@MINEDU5K2_" + codigoCL + "_SRX300> show log chassisid | match \"power sequencer started\"");

                        boolean esPrimeraVuelta = true;

                        while (!fechaActual.isAfter(fechaFin)) {
                            String horaSimulada;

                            if (esPrimeraVuelta) {
                                horaSimulada = horaPrimerLog;
                                esPrimeraVuelta = false;
                            } else {
                                int minutoAleatorio = random.nextInt(50) + 10;
                                horaSimulada = "07:" + minutoAleatorio;
                            }

                            lineasConsola.add(fechaActual.format(formatterSalida) + " " + horaSimulada + " .. power sequencer started ..");

                            if (fechaActual.isEqual(fechaFin)) break;
                            int diasSalto = random.nextInt(3) + 1;
                            fechaActual = fechaActual.plusDays(diasSalto);
                            if (fechaActual.isAfter(fechaFin)) fechaActual = fechaFin;
                        }

                        // 2. Generar la imagen y recuperar su ruta en la PC
                        String rutaImagenGenerada = crearImagenConsola(lineasConsola, codigoCL, fechaInicioTexto, horaPrimerLog, carpetaSalida);

                        // 3. Escribir los datos procesados en el NUEVO Excel
                        if (rutaImagenGenerada != null) {
                            Row nuevaFila = sheetSalida.createRow(filaSalidaIndex++);
                            nuevaFila.createCell(0).setCellValue(codigoCL);
                            nuevaFila.createCell(1).setCellValue(fechaInicioTexto);
                            nuevaFila.createCell(2).setCellValue(fechaFinTexto);
                            nuevaFila.createCell(3).setCellValue(rutaImagenGenerada); // Aquí inyectamos la dirección
                        }

                    } catch (Exception e) {
                        System.out.println("Error procesando fila " + (row.getRowNum() + 1));
                    }
                }
            }

            // 4. Guardar físicamente el nuevo archivo Excel en tu disco duro
            try (FileOutputStream fos = new FileOutputStream(new File(rutaExcelSalida))) {
                workbookSalida.write(fos);
                System.out.println("\n¡Reporte Excel creado exitosamente: " + rutaExcelSalida + "!");
            }
            workbookSalida.close();

            System.out.println("¡Proceso total terminado! Revisa la carpeta: " + carpetaSalida);

        } catch (Exception e) {
            System.out.println("Error leyendo el Excel de entrada: " + e.getMessage());
        }
    }

    /**
     * Ahora este método devuelve un String con la ruta absoluta donde se guardó la imagen.
     */
    private static String crearImagenConsola(List<String> lineas, String codigoCL, String fechaInicio, String horaInicio, String carpetaSalida) {
        try {
            int fontSize = 16;
            Font font = new Font("Monospaced", Font.PLAIN, fontSize);
            int ancho = 950;
            int altoLinea = 22;
            int paddingMagen = 20;
            int alto = (lineas.size() * altoLinea) + (paddingMagen * 2);

            BufferedImage imagen = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = imagen.createGraphics();

            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2d.setColor(new Color(12, 12, 12));
            g2d.fillRect(0, 0, ancho, alto);

            g2d.setFont(font);
            g2d.setColor(new Color(230, 230, 230));

            int posicionY = paddingMagen + fontSize;
            for (String linea : lineas) {
                g2d.drawString(linea, paddingMagen, posicionY);
                posicionY += altoLinea;
            }
            g2d.dispose();

            String fechaArchivo = fechaInicio.replace("/", "-");
            String horaArchivo = horaInicio.replace(":", "-");

            String nombreArchivo = codigoCL + "_" + fechaArchivo + "_" + horaArchivo + ".png";
            File archivoSalida = new File(carpetaSalida, nombreArchivo);

            ImageIO.write(imagen, "png", archivoSalida);
            System.out.println("Imagen creada exitosamente: " + nombreArchivo);

            // Devolvemos la ruta completa (ej. C:\Users\...\evidencias_minedu\110651_03-11-2025_07-34.png)
            return archivoSalida.getAbsolutePath(); 

        } catch (Exception e) {
            System.out.println("Error al crear la imagen de " + codigoCL + ": " + e.getMessage());
            return null;
        }
    }

    private static LocalDate obtenerFechaReal(Cell celda) {
        if (celda == null) return null;
        if (celda.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(celda)) {
            return celda.getLocalDateTimeCellValue().toLocalDate();
        } else {
            DataFormatter formatter = new DataFormatter();
            String texto = formatter.formatCellValue(celda).trim();
            if (texto.isEmpty()) return null;
            return LocalDate.parse(texto, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        }
    }
}