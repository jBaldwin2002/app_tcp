package org.vinni.watchdog;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class WatchdogMulti {
    private static final int DELAY_REINICIO_MS = 3000;
    private static final String classpath = System.getProperty("java.class.path");

    record NodoServidor(String nombre, String clase, int puerto, String flagParada) {}

    private static final List<NodoServidor> NODOS = List.of(
            new NodoServidor("Principal-1", "org.vinni.servidor.gui.PrincipalSrv",  12346, "servidor1.stop"),
            new NodoServidor("Principal-2", "org.vinni.servidor.gui.PrincipalSrv",  12347, "servidor2.stop"),
            new NodoServidor("Respaldo-1",  "org.vinni.servidor.gui.PrincipalSrv",  12348, "respaldo1.stop"),
            new NodoServidor("Respaldo-2",  "org.vinni.servidor.gui.PrincipalSrv",  12349, "respaldo2.stop"),
            new NodoServidor("Balanceador", "org.vinni.balanceador.LoadBalancer",       0, "balanceador.stop")
    );

    public static void main(String[] args) {
        limpiarFlags();
        System.out.println("[WATCHDOG] Iniciando clúster con " + NODOS.size() + " nodos.");

        for (NodoServidor nodo : NODOS) {
            Thread hilo = new Thread(() -> supervisarNodo(nodo));
            hilo.setName("watchdog-" + nodo.nombre());
            hilo.setDaemon(false);
            hilo.start();
        }
    }

    private static void supervisarNodo(NodoServidor nodo) {
        while (true) {
            try {
                System.out.println("[" + nodo.nombre() + "] Lanzando...");

                ProcessBuilder pb = new ProcessBuilder(
                        "java",
                        nodo.puerto() > 0 ? "-DPORT=" + nodo.puerto() : "-DPORT=0",
                        "-DFLAG_PARADA=" + nodo.flagParada(),
                        "-cp", classpath,
                        nodo.clase()
                );
                pb.inheritIO();
                pb.directory(new File("."));

                Process proceso = pb.start();
                int codigo = proceso.waitFor();

                System.out.println("[" + nodo.nombre() + "] Terminó (código " + codigo + ").");

                File flag = new File(nodo.flagParada());
                if (flag.exists()) {
                    flag.delete();
                    System.out.println("[" + nodo.nombre() + "] Parada voluntaria. No se reinicia.");
                    break;
                }

                System.out.println("[" + nodo.nombre() + "] Caída inesperada. Reiniciando en "
                        + DELAY_REINICIO_MS / 1000 + "s...");
                Thread.sleep(DELAY_REINICIO_MS);

            } catch (IOException e) {
                System.err.println("[" + nodo.nombre() + "] No se pudo lanzar: " + e.getMessage());
                try { Thread.sleep(DELAY_REINICIO_MS); } catch (InterruptedException ie) { break; }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private static void limpiarFlags() {
        for (NodoServidor n : NODOS) {
            File f = new File(n.flagParada());
            if (f.exists()) { f.delete(); System.out.println("[WATCHDOG] Flag residual eliminado: " + n.flagParada()); }
        }
    }
}