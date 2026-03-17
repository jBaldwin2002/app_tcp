package org.vinni.servidor.gui;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Author: Vinni
 */
public class PrincipalSrv extends javax.swing.JFrame {

    private final int PORT = 12345;
    private ServerSocket serverSocket;
    private boolean servidorCorriendo = false;

    // Lista de clientes conectados (Guardamos el Socket entero para poder cerrarlo forzosamente)
    private final Map<String, Socket> socketsConectados = new ConcurrentHashMap<>();
    private final Map<String, PrintWriter> clientesConectados = new ConcurrentHashMap<>();

    private static final String[] extensionesProhividas = {".exe", ".bat"};
    private static final long tamanhoMin = 1024;
    private static final long tamanhoMax = 1024 * 1024 * 5;

    public PrincipalSrv() {
        initComponents();
    }

    @SuppressWarnings("unchecked")
    private void initComponents() {
        this.setTitle("Servidor ...");

        bAccion = new javax.swing.JButton();
        jLabel1 = new javax.swing.JLabel();
        mensajesTxt = new JTextArea();
        jScrollPane1 = new javax.swing.JScrollPane();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        getContentPane().setLayout(null);

        bAccion.setFont(new java.awt.Font("Segoe UI", 1, 14));
        bAccion.setText("INICIAR SERVIDOR");
        bAccion.addActionListener(evt -> toggleServidor());
        getContentPane().add(bAccion);
        bAccion.setBounds(100, 90, 250, 40);

        jLabel1.setFont(new java.awt.Font("Tahoma", 1, 14));
        jLabel1.setForeground(new java.awt.Color(204, 0, 0));
        jLabel1.setText("SERVIDOR TCP : HOEL");
        getContentPane().add(jLabel1);
        jLabel1.setBounds(150, 10, 160, 17);

        mensajesTxt.setColumns(25);
        mensajesTxt.setRows(5);
        mensajesTxt.setEditable(false);

        jScrollPane1.setViewportView(mensajesTxt);
        getContentPane().add(jScrollPane1);
        jScrollPane1.setBounds(20, 160, 410, 100);

        setSize(new java.awt.Dimension(491, 310));
        setLocationRelativeTo(null);
    }

    private void toggleServidor() {
        if (!servidorCorriendo) {
            iniciarServidor();
        } else {
            detenerServidorManual();
        }
    }

    private void iniciarServidor() {
        servidorCorriendo = true;
        bAccion.setText("DETENER SERVIDOR");
        bAccion.setForeground(java.awt.Color.RED);

        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(PORT);
                InetAddress addr = InetAddress.getLocalHost();
                log("Servidor TCP en ejecución: " + addr + ", Puerto " + serverSocket.getLocalPort());

                while (servidorCorriendo) {
                    Socket clientSocket = serverSocket.accept();
                    new Thread(() -> manejarCliente(clientSocket)).start();
                }
            } catch (IOException ex) {
                if (servidorCorriendo) {
                    log("[ERROR] Fallo en el servidor: " + ex.getMessage());
                } else {
                    log("--> El servidor ha sido detenido manualmente.");
                }
            } finally {
                detenerServidorManual();
            }
        }).start();
    }

    private void detenerServidorManual() {
        servidorCorriendo = false;

        // 1. Cerrar el ServerSocket para no aceptar más conexiones
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ignored) {}

        // 2. Desconectar a todos los clientes a la fuerza para que activen su política de reconexión
        for (Socket socket : socketsConectados.values()) {
            try {
                socket.close();
            } catch (IOException ignored) {}
        }

        socketsConectados.clear();
        clientesConectados.clear();

        SwingUtilities.invokeLater(() -> {
            bAccion.setText("INICIAR SERVIDOR");
            bAccion.setForeground(java.awt.Color.BLACK);
        });

        log("Servidor apagado. Todas las conexiones fueron cerradas.");
    }

    // ... (El resto de los métodos enviarA, enviarListaClientes, notificarCambioClientes, broadcast, broadcastArchivo, validarArchivo se mantienen exactamente igual que en la versión anterior) ...

    private void enviarA(String destinatario, String mensaje) {
        PrintWriter writer = clientesConectados.get(destinatario);
        if (writer != null) {
            writer.println(mensaje);
        }
    }

    private void enviarListaClientes(String nombreCliente) {
        StringBuilder lista = new StringBuilder("CLIENTES_CONECTADOS:");
        for (String cliente : clientesConectados.keySet()) {
            if (!cliente.equals(nombreCliente)) {
                lista.append(cliente).append(",");
            }
        }
        enviarA(nombreCliente, lista.toString());
    }

    private void notificarCambioClientes() {
        for (String cliente : clientesConectados.keySet()) {
            enviarListaClientes(cliente);
        }
    }

    private void broadcast(String remitente, String mensaje) {
        for (Map.Entry<String, PrintWriter> entry : clientesConectados.entrySet()) {
            if (!entry.getKey().equals(remitente)) {
                entry.getValue().println("DE:" + remitente + ":[TODOS] " + mensaje);
            }
        }
    }

    private void broadcastArchivo(String remitente, String nombreArchivo, long tamanoBytes, String base64Data) {
        for (Map.Entry<String, PrintWriter> entry : clientesConectados.entrySet()) {
            if (!entry.getKey().equals(remitente)) {
                entry.getValue().println(
                        "FILE_RECV:" + remitente + ":" + nombreArchivo + ":" + tamanoBytes + ":" + base64Data
                );
            }
        }
    }

    private String validarArchivo(String nombreArchivo, long tamanoBytes) {
        String nombreLower = nombreArchivo.toLowerCase();
        for (String ext : extensionesProhividas ) {
            if (nombreLower.endsWith(ext)) {
                return "Archivo rechazado: extension '" + ext + "' no permitida";
            }
        }
        if (tamanoBytes < tamanhoMin) {
            return "Archivo rechazado: tamaño minimo es 1 KB (recibido: " + tamanoBytes + " bytes)";
        }
        if (tamanoBytes > tamanhoMax) {
            return "Archivo rechazado: tamaño maximo es 5 MB (recibido: " + (tamanoBytes / 1024 / 1024) + " MB)";
        }
        return null;
    }

    private void manejarCliente(Socket clientSocket) {
        PrintWriter out = null;
        String nombreCliente = null;

        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            out = new PrintWriter(clientSocket.getOutputStream(), true);

            nombreCliente = in.readLine();

            if (nombreCliente == null || nombreCliente.trim().isEmpty()) {
                out.println("ERROR:Nombre invalido");
                clientSocket.close();
                return;
            }

            if (clientesConectados.containsKey(nombreCliente)) {
                out.println("ERROR:Nombre ya existe");
                clientSocket.close();
                return;
            }

            clientesConectados.put(nombreCliente, out);
            socketsConectados.put(nombreCliente, clientSocket); // se guarda el socket
            out.println("OK:Conectado como " + nombreCliente);
            log("Cliente conectado: " + nombreCliente);
            notificarCambioClientes();

            String linea;
            while ((linea = in.readLine()) != null) {
                if (linea.startsWith("FILE_SEND:")) {
                    String[] partes = linea.split(":", 5);
                    if (partes.length < 5) {
                        enviarA(nombreCliente, "ERROR:Formato de archivo invalido");
                        continue;
                    }
                    String destinatario = partes[1].trim();
                    String nombreArchivo = partes[2].trim();
                    long tamanoBytes = Long.parseLong(partes[3].trim());
                    String base64Data = partes[4];

                    String errorValidacion = validarArchivo(nombreArchivo, tamanoBytes);
                    if (errorValidacion != null) {
                        enviarA(nombreCliente, "ERROR:" + errorValidacion);
                        continue;
                    }

                    if (destinatario.equals("TODOS")) {
                        broadcastArchivo(nombreCliente, nombreArchivo, tamanoBytes, base64Data);
                        enviarA(nombreCliente, "ARCHIVO:TODOS:" + nombreArchivo + ":" + tamanoBytes);
                    } else {
                        if (!clientesConectados.containsKey(destinatario)) {
                            enviarA(nombreCliente, "ERROR:Cliente '" + destinatario + "' no encontrado");
                            continue;
                        }
                        enviarA(destinatario, "FILE_RECV:" + nombreCliente + ":" + nombreArchivo + ":" + tamanoBytes + ":" + base64Data);
                        enviarA(nombreCliente, "FILE_OK:" + destinatario + ":" + nombreArchivo + ":" + tamanoBytes);
                    }

                } else if (linea.startsWith("TODOS:")) {
                    String mensaje = linea.substring(6);
                    log(nombreCliente + " -> [TODOS]: " + mensaje);
                    broadcast(nombreCliente, mensaje);
                    enviarA(nombreCliente, "BROADCAST_OK:" + mensaje);

                } else if (linea.contains(":")) {
                    String[] partes = linea.split(":", 2);
                    String destinatario = partes[0].trim();
                    String mensaje = partes.length > 1 ? partes[1] : "";

                    log(nombreCliente + " -> " + destinatario + ": " + mensaje);
                    if (clientesConectados.containsKey(destinatario)) {
                        enviarA(destinatario, "DE:" + nombreCliente + ":" + mensaje);
                        enviarA(nombreCliente, "ENVIADO:" + destinatario + ":" + mensaje);
                    } else {
                        enviarA(nombreCliente, "ERROR:Cliente '" + destinatario + "' no encontrado");
                    }
                }
            }
        } catch (Exception e) {
            // El error salta aquí si el cliente se desconecta abruptamente o si el servidor cierra el socket.
        } finally {
            if (nombreCliente != null) {
                clientesConectados.remove(nombreCliente);
                socketsConectados.remove(nombreCliente);
                if (servidorCorriendo) {
                    log("Cliente desconectado: " + nombreCliente + ". Activos: " + clientesConectados.size());
                    notificarCambioClientes();
                }
            }
            try { clientSocket.close(); } catch (Exception ignored) {}
        }
    }

    private void log(String mensaje) {
        SwingUtilities.invokeLater(() -> mensajesTxt.append(mensaje + "\n"));
    }

    public static void main(String args[]) {
        java.awt.EventQueue.invokeLater(() -> new PrincipalSrv().setVisible(true));
    }

    private javax.swing.JButton bAccion;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JTextArea mensajesTxt;
    private javax.swing.JScrollPane jScrollPane1;
}