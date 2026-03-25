package org.vinni.watchdog;

import java.io.File;
import java.io.IOException;

/**
 * Author: Vinni
 * Guardián del servidor TCP.
 * Funciona directamente desde IntelliJ sin necesitar empaquetar JARs.
 * Lanza el servidor y lo relanza automáticamente si muere de forma inesperada.
 * Si detecta el archivo "servidor.stop", interpreta que fue parada voluntaria
 * y NO reinicia el proceso.
 *
 * Orden de ejecución:
 * 1. Correr Watchdog.java (este archivo)
 * 2. El servidor aparece automáticamente
 * 3. Correr los clientes (PrincipalCli)
 */
public class Watchdog {

    // Clase main del servidor
    private static final String CLASE_SERVIDOR = "org.vinni.servidor.gui.PrincipalSrv";

    // Archivo bandera que PrincipalSrv crea cuando el usuario detiene el servidor voluntariamente
    private static final String FLAG_PARADA_VOLUNTARIA = "servidor.stop";

    // Segundos de espera antes de relanzar tras una caída inesperada
    private static final int DELAY_REINICIO_MS = 3000;

    public static void main(String[] args) {
        System.out.println("[WATCHDOG] Guardian iniciado.");
        System.out.println("[WATCHDOG] Monitoreando clase: " + CLASE_SERVIDOR);
        limpiarFlagSiExiste();

        // Reutiliza el classpath exacto con el que corre el Watchdog en IntelliJ
        String classpath = System.getProperty("java.class.path");

        while (true) {
            try {
                System.out.println("[WATCHDOG] Lanzando servidor...");

                ProcessBuilder pb = new ProcessBuilder(
                        "java",
                        "-cp", classpath,       // mismo classpath de IntelliJ
                        CLASE_SERVIDOR          // clase main del servidor
                );
                pb.inheritIO();                 // el servidor imprime en la misma consola
                pb.directory(new File("."));    // directorio de trabajo = raíz del proyecto

                Process proceso = pb.start();
                int exitCode = proceso.waitFor(); // bloquea hasta que el servidor termine

                System.out.println("[WATCHDOG] Servidor terminado (código: " + exitCode + ").");

                // ¿Fue parada voluntaria (botón DETENER)?
                File flag = new File(FLAG_PARADA_VOLUNTARIA);
                if (flag.exists()) {
                    flag.delete();
                    System.out.println("[WATCHDOG] Parada voluntaria. El Watchdog también se cierra.");
                    break;
                }

                // Caída inesperada: X, Task Manager, crash
                System.out.println("[WATCHDOG] Caída inesperada. Reiniciando en "
                        + (DELAY_REINICIO_MS / 1000) + " segundos...");
                Thread.sleep(DELAY_REINICIO_MS);

            } catch (IOException e) {
                System.err.println("[WATCHDOG ERROR] No se pudo lanzar el servidor: " + e.getMessage());
                System.err.println("[WATCHDOG] Reintentando en " + (DELAY_REINICIO_MS / 1000) + " segundos...");
                try { Thread.sleep(DELAY_REINICIO_MS); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("[WATCHDOG] Watchdog interrumpido. Saliendo.");
                break;
            }
        }

        System.out.println("[WATCHDOG] Guardian finalizado.");
    }

    private static void limpiarFlagSiExiste() {
        File flag = new File(FLAG_PARADA_VOLUNTARIA);
        if (flag.exists()) {
            flag.delete();
            System.out.println("[WATCHDOG] Flag residual eliminado.");
        }
    }
}