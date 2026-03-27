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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class JuniperRealNoFake {
    public static void main(String[] args) {
        String rutaExcelEntrada = "datos_tickets.xlsx";
        String rutaExcelSalida = "reporte_evidencias.xlsx"; 
        String carpetaSalida = "evidencias_minedu";

        new File(carpetaSalida).mkdirs();
        
        // Formatos de fecha
        DateTimeFormatter formatterJuniper = DateTimeFormatter.ofPattern("MMM dd HH:mm:ss", Locale.ENGLISH);
        DateTimeFormatter formatterSalidaExcel = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

        try (FileInputStream fis = new FileInputStream(new File(rutaExcelEntrada));
             Workbook workbookEntrada = new XSSFWorkbook(fis);
             Workbook workbookSalida = new XSSFWorkbook()) {

            Sheet sheetEntrada = workbookEntrada.getSheetAt(0);
            Sheet sheetSalida = workbookSalida.createSheet("Tickets Procesados");
            DataFormatter dataFormatter = new DataFormatter();

            // Encabezados
            Row filaEncabezado = sheetSalida.createRow(0);
            String[] headers = {"CID", "Fecha Inicial", "Fecha Final (Solución)", "Ruta de Imagen"};
            for (int i = 0; i < headers.length; i++) filaEncabezado.createCell(i).setCellValue(headers[i]);

            int filaSalidaIndex = 1;

            for (Row row : sheetEntrada) {
                String codigoCL = dataFormatter.formatCellValue(row.getCell(0)).trim();
                String fechaInicialStr = dataFormatter.formatCellValue(row.getCell(1)).trim();
                String fechaFinalStr = dataFormatter.formatCellValue(row.getCell(2)).trim();

                if (codigoCL.isEmpty() && fechaFinalStr.isEmpty()) continue;
                if (row.getRowNum() == 0 && (codigoCL.equalsIgnoreCase("CID") || codigoCL.toLowerCase().contains("código"))) continue;

                LocalDateTime fechaFin = obtenerFechaReal(row.getCell(2)); 

                Row nuevaFila = sheetSalida.createRow(filaSalidaIndex++);
                nuevaFila.createCell(0).setCellValue(codigoCL);
                nuevaFila.createCell(1).setCellValue(fechaInicialStr);

                if (!codigoCL.isEmpty() && fechaFin != null) {
                    // LÓGICA DE LAS 2 FECHAS
                    LocalDateTime fechaCincoMinAntes = fechaFin.minusMinutes(5);
                    
                    List<String> lineasConsola = new ArrayList<>();
                    lineasConsola.add("juniper@MINEDU5K2_" + codigoCL + "_SRX300> show log chassisd | match \"power sequencer started\"");
                    lineasConsola.add(fechaCincoMinAntes.format(formatterJuniper) + "  .. power sequencer started ..");
                    lineasConsola.add(fechaFin.format(formatterJuniper) + "  .. power sequencer started ..");

                    String rutaImagen = crearImagenConsola(lineasConsola, codigoCL, fechaFin, carpetaSalida);

                    nuevaFila.createCell(2).setCellValue(fechaFin.format(formatterSalidaExcel));
                    nuevaFila.createCell(3).setCellValue(rutaImagen);
                } else {
                    nuevaFila.createCell(2).setCellValue(fechaFinalStr.isEmpty() ? "SIN FECHA" : fechaFinalStr);
                    nuevaFila.createCell(3).setCellValue("ERROR: Revisar formato de fecha");
                }
            }

            try (FileOutputStream fos = new FileOutputStream(new File(rutaExcelSalida))) {
                workbookSalida.write(fos);
            }
            System.out.println("Proceso terminado. Registros procesados: " + (filaSalidaIndex - 1));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String crearImagenConsola(List<String> lineas, String codigoCL, LocalDateTime fecha, String carpetaSalida) {
        try {
            int fontSize = 16;
            Font font = new Font("Consolas", Font.PLAIN, fontSize);
            int ancho = 920; 
            int altoLinea = 28;
            int padding = 25;
            int alto = (lineas.size() * altoLinea) + (padding * 2);

            BufferedImage imagen = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = imagen.createGraphics();
            
            // Suavizado de texto
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            
            // Fondo oscuro estilo terminal
            g2d.setColor(new Color(15, 15, 15)); 
            g2d.fillRect(0, 0, ancho, alto);
            
            g2d.setFont(font);
            int y = padding + fontSize;

            for (int i = 0; i < lineas.size(); i++) {
                // La primera línea (el comando) suele ser un poco más brillante o blanca
                if (i == 0) {
                    g2d.setColor(new Color(240, 240, 240)); 
                } else {
                    g2d.setColor(new Color(190, 190, 190)); // Los logs son grisáceos
                }
                g2d.drawString(lineas.get(i), padding, y);
                y += altoLinea;
            }
            g2d.dispose();

            DateTimeFormatter fmtNombre = DateTimeFormatter.ofPattern("dd-MM-yyyy_HH-mm-ss");
            String nombreArchivo = "EVIDENCIA_" + codigoCL + "_" + fecha.format(fmtNombre) + ".png";
            
            File file = new File(carpetaSalida, nombreArchivo);
            ImageIO.write(imagen, "png", file);
            return file.getAbsolutePath();
        } catch (Exception e) {
            return "Error al generar imagen";
        }
    }

    private static LocalDateTime obtenerFechaReal(Cell celda) {
        if (celda == null) return null;
        try {
            if (celda.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(celda)) {
                return celda.getLocalDateTimeCellValue();
            }
            String val = new DataFormatter().formatCellValue(celda).trim();
            if (val.isEmpty()) return null;
            
            // Intentos de parseo manual
            String[] formatos = {
            		"yyyy-MM-dd HH:mm:ss", 
                    "dd/MM/yyyy HH:mm:ss", 
                    "d/M/yyyy H:mm:ss", 
                    "dd-MM-yyyy HH:mm:ss",
                    "dd/MM/yyyy HH:mm",  // <-- Añadido: Formato exacto de tu Excel
                    "d/M/yyyy H:mm",     // <-- Añadido
                    "yyyy-MM-dd HH:mm",  // <-- Añadido
                    "dd-MM-yyyy HH:mm"   // <-- Añadido
            };

            for (String f : formatos) {
                try {
                    return LocalDateTime.parse(val, DateTimeFormatter.ofPattern(f));
                } catch (Exception ignored) {}
            }
            return null;
        } catch (Exception e) {
            return null; 
        }
    }
}