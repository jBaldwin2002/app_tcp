package org.vinni.balanceador;

import java.io.*;
import java.net.*;
import java.util.LinkedList;


public class BalanceadorTCP {

    private static final int PUERTO_ESCUCHA = 12345;

    // Usamos una LinkedList para poder mover los servidores caídos al final
    private static final LinkedList<InfoServidor> servidores = new LinkedList<>();

    static {
        // Añadimos los 3 servidores iniciales a la cola
        servidores.add(new InfoServidor("localhost", 12346));
        servidores.add(new InfoServidor("localhost", 12347));
        servidores.add(new InfoServidor("localhost", 12348));
    }

    // Clase auxiliar para guardar IP y Puerto
    private static class InfoServidor {
        String ip;
        int puerto;
        InfoServidor(String ip, int puerto) {
            this.ip = ip;
            this.puerto = puerto;
        }
    }

    public static void main(String[] args) {
        System.out.println("[BALANCEADOR] Iniciando en el puerto " + PUERTO_ESCUCHA + "...");

        try (ServerSocket serverSocket = new ServerSocket(PUERTO_ESCUCHA)) {
            while (true) {
                Socket socketCliente = serverSocket.accept();
                System.out.println("\n[BALANCEADOR] Nuevo cliente desde: " + socketCliente.getInetAddress());
                new Thread(() -> manejarCliente(socketCliente)).start();
            }
        } catch (IOException e) {
            System.err.println("[BALANCEADOR ERROR] " + e.getMessage());
        }
    }

    private static void manejarCliente(Socket socketCliente) {
        Socket socketServidor = null;
        boolean conectado = false;

        // Intentamos conectar a los servidores disponibles en la cola actual
        for (int i = 0; i < servidores.size(); i++) {
            InfoServidor objetivo;

            // Sincronizamos para evitar que dos clientes modifiquen la lista al mismo tiempo
            synchronized (servidores) {
                objetivo = servidores.get(0); // Siempre intentamos con el primero de la fila
            }

            try {
                System.out.println("[BALANCEADOR] Intentando conectar al principal actual (Puerto " + objetivo.puerto + ")...");
                socketServidor = new Socket(objetivo.ip, objetivo.puerto);
                conectado = true;
                System.out.println("[BALANCEADOR] OK! Trafico enrutado al Puerto " + objetivo.puerto);
                break; // Conexión exitosa, salimos del bucle

            } catch (IOException e) {
                System.out.println("[BALANCEADOR] FALLO: Puerto " + objetivo.puerto + " no responde.");

                synchronized (servidores) {
                    // Si el servidor falló, lo quitamos del primer lugar y lo mandamos al final
                    if (servidores.get(0) == objetivo) {
                        servidores.remove(0);
                        servidores.add(objetivo);
                        System.out.println("[BALANCEADOR] -> Servidor movido al final de la fila de respaldos.");
                    }
                }
            }
        }

        if (!conectado) {
            System.out.println("[BALANCEADOR] ERROR CRITICO: Ningun servidor esta disponible.");
            try { socketCliente.close(); } catch (IOException ignored) {}
            return;
        }

        iniciarPuenteTCP(socketCliente, socketServidor);
    }

    private static void iniciarPuenteTCP(Socket cliente, Socket servidor) {
        new Thread(() -> {
            try { transferirBytes(cliente.getInputStream(), servidor.getOutputStream()); } catch (IOException ignored) {}
            cerrarConexiones(cliente, servidor);
        }).start();

        new Thread(() -> {
            try { transferirBytes(servidor.getInputStream(), cliente.getOutputStream()); } catch (IOException ignored) {}
            cerrarConexiones(cliente, servidor);
        }).start();
    }

    private static void transferirBytes(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[8192];
        int bytesLeidos;
        while ((bytesLeidos = in.read(buffer)) != -1) {
            out.write(buffer, 0, bytesLeidos);
            out.flush();
        }
    }

    private static void cerrarConexiones(Socket c, Socket s) {
        try { if (c != null && !c.isClosed()) c.close(); } catch (IOException ignored) {}
        try { if (s != null && !s.isClosed()) s.close(); } catch (IOException ignored) {}
    }
}