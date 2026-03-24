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
import java.time.LocalDateTime; // CAMBIO: Usar LocalDateTime en lugar de LocalDate
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class juniperfike {
    public static void main(String[] args) {
        String rutaExcelEntrada = "datos_tickets.xlsx";
        String rutaExcelSalida = "reporte_evidencias.xlsx"; 
        String carpetaSalida = "evidencias_minedu";

        new File(carpetaSalida).mkdirs();
        Random random = new Random();
        
        // CAMBIO: El formateador de salida ahora incluye horas, minutos y segundos
        DateTimeFormatter formatterSalida = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

        Workbook workbookSalida = new XSSFWorkbook();
        Sheet sheetSalida = workbookSalida.createSheet("Tickets Procesados");

        Row filaEncabezado = sheetSalida.createRow(0);
        filaEncabezado.createCell(0).setCellValue("CID");
        filaEncabezado.createCell(1).setCellValue("Fecha Inicial");
        filaEncabezado.createCell(2).setCellValue("Fecha Final");
        filaEncabezado.createCell(3).setCellValue("Ruta de Imagen");

        int filaSalidaIndex = 1;

        try (FileInputStream fis = new FileInputStream(new File(rutaExcelEntrada));
             Workbook workbookEntrada = new XSSFWorkbook(fis)) {

            Sheet sheetEntrada = workbookEntrada.getSheetAt(0);
            DataFormatter dataFormatter = new DataFormatter();

            System.out.println("Iniciando generación de imágenes y reporte Excel...\n");

            for (Row row : sheetEntrada) {
                if (row.getRowNum() == 0) continue;

                Cell celdaCodigo = row.getCell(0);
                Cell celdaFechaInicio = row.getCell(1);
                Cell celdaFechaFin = row.getCell(2);

                if (celdaCodigo != null && celdaFechaInicio != null && celdaFechaFin != null) {
                    String codigoCL = dataFormatter.formatCellValue(celdaCodigo).trim();
                    if (codigoCL.isEmpty()) continue;

                    try {
                        // CAMBIO: Ahora obtenemos fechas con su hora exacta
                        LocalDateTime fechaInicio = obtenerFechaReal(celdaFechaInicio);
                        LocalDateTime fechaFin = obtenerFechaReal(celdaFechaFin);

                        if (fechaInicio == null || fechaFin == null) {
                            System.out.println("Fecha inválida en fila " + (row.getRowNum() + 1));
                            continue;
                        }

                        String fechaInicioTexto = fechaInicio.format(formatterSalida);
                        String fechaFinTexto = fechaFin.format(formatterSalida);

                        List<String> lineasConsola = new ArrayList<>();
                        lineasConsola.add("juniper@MINEDU5K2_" + codigoCL + "_SRX300> show log chassisid | match \"power sequencer started\"");

                        // CAMBIO: Iniciamos en la hora exacta del Excel
                        LocalDateTime tiempoActual = fechaInicio;

                        // Bucle de tiempo
                        while (!tiempoActual.isAfter(fechaFin)) {
                            // Imprimimos la línea con el tiempo actual exacto
                            lineasConsola.add(tiempoActual.format(formatterSalida) + " .. power sequencer started ..");

                            if (tiempoActual.isEqual(fechaFin)) break;
                            
                            // CAMBIO: En vez de saltar días, saltamos minutos (ej. entre 15 y 45 minutos)
                            // Puedes ajustar este salto a tu gusto
                            int minutosSalto = random.nextInt(30) + 15;
                            tiempoActual = tiempoActual.plusMinutes(minutosSalto);

                            // Si al sumar minutos nos pasamos de la fecha/hora final, nos ajustamos exactamente al final
                            if (tiempoActual.isAfter(fechaFin)) {
                                tiempoActual = fechaFin;
                            }
                        }

                        // Generar la imagen
                        String rutaImagenGenerada = crearImagenConsola(lineasConsola, codigoCL, fechaInicio, carpetaSalida);

                        // Escribir los datos procesados en el NUEVO Excel
                        if (rutaImagenGenerada != null) {
                            Row nuevaFila = sheetSalida.createRow(filaSalidaIndex++);
                            nuevaFila.createCell(0).setCellValue(codigoCL);
                            nuevaFila.createCell(1).setCellValue(fechaInicioTexto);
                            nuevaFila.createCell(2).setCellValue(fechaFinTexto);
                            nuevaFila.createCell(3).setCellValue(rutaImagenGenerada);
                        }

                    } catch (Exception e) {
                        System.out.println("Error procesando fila " + (row.getRowNum() + 1) + ": " + e.getMessage());
                    }
                }
            }

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
     * CAMBIO: He simplificado los parámetros para recibir directamente el LocalDateTime
     */
    private static String crearImagenConsola(List<String> lineas, String codigoCL, LocalDateTime fechaInicio, String carpetaSalida) {
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

            // CAMBIO: Formato seguro para nombres de archivos (sin dos puntos ':')
            DateTimeFormatter formatterArchivo = DateTimeFormatter.ofPattern("dd-MM-yyyy_HH-mm-ss");
            String nombreArchivo = codigoCL + "_" + fechaInicio.format(formatterArchivo) + ".png";
            File archivoSalida = new File(carpetaSalida, nombreArchivo);

            ImageIO.write(imagen, "png", archivoSalida);
            System.out.println("Imagen creada exitosamente: " + nombreArchivo);

            return archivoSalida.getAbsolutePath(); 

        } catch (Exception e) {
            System.out.println("Error al crear la imagen de " + codigoCL + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * CAMBIO: Ahora usa LocalDateTime y soporta el formato de tu Excel ("yyyy-MM-dd HH:mm:ss")
     */
    private static LocalDateTime obtenerFechaReal(Cell celda) {
        if (celda == null) return null;
        if (celda.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(celda)) {
            return celda.getLocalDateTimeCellValue(); // Mantiene la hora
        } else {
            DataFormatter formatter = new DataFormatter();
            String texto = formatter.formatCellValue(celda).trim();
            if (texto.isEmpty()) return null;
            
            try {
                // Intenta parsear el formato específico de tu ejemplo
                return LocalDateTime.parse(texto, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            } catch (Exception e) {
                try {
                    // Formato alternativo por si alguna celda lo tiene diferente
                    return LocalDateTime.parse(texto, DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
                } catch (Exception ex) {
                    System.out.println("No se pudo parsear la fecha: " + texto);
                    return null;
                }
            }
        }
    }
}