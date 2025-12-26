package controller;
import java.util.Scanner;
public class Controller {
public String ElegirITEM() throws InterruptedException {
	//pregunaren la consola que intem se hará el informe
	
	Scanner sc = new Scanner(System.in); // Crear objeto Scanner

	System.out.println("Que ITEM Desea Crear el Informe? :3 pero primero yapeame 3 soles por cada informe (PON SOLACMENTE EL NUMERO DEL ITEM)");
	String Item = sc.nextLine(); // Leer una línea completa
	System.out.println("/n Usted Eligio ITEM "+Item+" si es correcto escribe 'y' ");
	String t = sc.nextLine(); // Leer una línea completa

	sc.close(); // C el Scanner
	if(t.equalsIgnoreCase("y")) {
		
		return Item;	
	 

	}else {
		System.exit(0);
	}

	return Item;
	
}
public String ElegirFecha() {
	
	Scanner i = new Scanner(System.in); // Crear objeto Scanner
	System.out.println("aun no se resibe el yape.. apurate sino el fantasma te va llevar");
	System.out.println("De que mes desea? (ponerlo como numero el mes (Enero=1, Febrero =2 ...)");
	String i1 = i.nextLine(); // Leer una línea completa
	//System.out.println("De que dia desea? (ponerlo como numero 1 al 31");
	//String i2= i.nextLine(); // Leer una línea completa
	System.out.println("De que año? (2025,2026 ...)");
	String i2= i.nextLine(); // Leer una línea completa
	
	i.close();
	return i1+"_"+i2;
}
}
