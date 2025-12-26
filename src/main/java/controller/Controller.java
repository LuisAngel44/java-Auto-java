package controller;
import java.util.Scanner;

public class Controller {
    // 1. Creamos un ÚNICO Scanner para toda la clase
    // Lo ponemos fuera de los métodos para que todos lo compartan
    private Scanner sc = new Scanner(System.in);

    public String ElegirITEM() throws InterruptedException {
    	  System.out.println("\n INICIO DEL PROGRAMA ");
    	  System.out.println("-------------------------------------------- ");
    	
        System.out.println("Que ITEM Desea Crear el Informe? ");
        String Item = sc.nextLine(); 
        
        System.out.println("\nUsted Eligio ITEM " + Item + " si es correcto escribe 'y' ");
        String t = sc.nextLine(); 

        // 2. ELIMINADO: sc.close(); <-- Esto causaba el error
        
        if(t.equalsIgnoreCase("y")) {
            System.out.println("Item elegido: Excel_w_ITEM_" + Item + ".xlsx");
        } else {
            System.out.println("Saliendo del programa...");
            System.exit(0);
        }
        return Item;
    }

    public String ElegirFechaIni() {
        // No creamos un nuevo Scanner, usamos el de la clase
        System.out.println("\nFECHA DE INICIO");
        System.out.println("------------------------------------------");
        
        System.out.println("De que mes desea? (numero del 01 al 12)");
        String i1 = sc.nextLine(); 
        
        System.out.println("De que dia desea? (numero del 01 al 31)");
        String i3 = sc.nextLine(); 
        
        System.out.println("De que año? (2025, 2026...)");
        String i2 = sc.nextLine(); 
        
        String fechaCompleta = i3 + "/" + i1 + "/" + i2;
        System.out.println("FECHA DE INICIO: " + fechaCompleta);
        
        return fechaCompleta;
    }

    public String ElegirFechaFinal() {
        System.out.println("\nFECHA DE FINAL");
        System.out.println("------------------------------------------");
        
        System.out.println("De que mes desea? (numero del 1 al 12)");
        String i1 = sc.nextLine(); 
        
        System.out.println("De que dia desea? (numero del 1 al 31)");
        String i3 = sc.nextLine(); 
        
        System.out.println("De que año? (2025, 2026...)");
        String i2 = sc.nextLine(); 
        
        String fechaCompleta = i3 + "/" + i1 + "/" + i2;
        System.out.println("FECHA DE FINAL: " + fechaCompleta);
        
        return fechaCompleta;
    }
}
