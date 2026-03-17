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

    // Políticas de reinicio
    private final int MAX_REINICIOS = 5;
    private final int ESPERA_REINICIO_MS = 5000;

    // Lista de writers de todos los clientes conectados
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

        bIniciar = new javax.swing.JButton();
        jLabel1 = new javax.swing.JLabel();
        mensajesTxt = new JTextArea();
        jScrollPane1 = new javax.swing.JScrollPane();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        getContentPane().setLayout(null);

        bIniciar.setFont(new java.awt.Font("Segoe UI", 0, 18));
        bIniciar.setText("INICIAR SERVIDOR");
        bIniciar.addActionListener(evt -> bIniciarActionPerformed(evt));
        getContentPane().add(bIniciar);
        bIniciar.setBounds(100, 90, 250, 40);

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

    private void bIniciarActionPerformed(java.awt.event.ActionEvent evt) {
        if (!servidorCorriendo) {
            iniciarServidorConPoliticas();
        }
    }

    /**
     * Aplica las políticas de reinicio si el socket falla
     */
    private void iniciarServidorConPoliticas() {
        bIniciar.setEnabled(false);
        servidorCorriendo = true;

        new Thread(() -> {
            int intentosReinicio = 0;

            while (intentosReinicio < MAX_REINICIOS && servidorCorriendo) {
                try {
                    serverSocket = new ServerSocket(PORT);
                    InetAddress addr = InetAddress.getLocalHost();
                    log("Servidor TCP en ejecución: " + addr + ", Puerto " + serverSocket.getLocalPort());
                    intentosReinicio = 0; // Se reinician los intentos si la conexión es exitosa

                    while (servidorCorriendo) {
                        Socket clientSocket = serverSocket.accept();
                        new Thread(() -> manejarCliente(clientSocket)).start();
                    }

                } catch (IOException ex) {
                    intentosReinicio++;
                    log("[ERROR FATAL] Fallo en el servidor: " + ex.getMessage());

                    if (intentosReinicio < MAX_REINICIOS) {
                        log("--> Aplicando Política: Reiniciando servidor en " + (ESPERA_REINICIO_MS / 1000) + " seg... (Intento " + intentosReinicio + " de " + MAX_REINICIOS + ")");
                        try {
                            Thread.sleep(ESPERA_REINICIO_MS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    } else {
                        log("--> Política Agotada: No se pudo reiniciar el servidor tras " + MAX_REINICIOS + " intentos.");
                        servidorCorriendo = false;
                        SwingUtilities.invokeLater(() -> bIniciar.setEnabled(true));
                    }
                }
            }
        }).start();
    }

    private void enviarA(String destinatario, String mensaje) {
        PrintWriter writer = clientesConectados.get(destinatario);
        if (writer != null) {
            writer.println(mensaje);
        }
    }
    /**
     * Envía la lista de clientes conectados a un cliente específico
     */
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
                        log("[ARCHIVO] " + nombreCliente + " -> [TODOS] | " + nombreArchivo);
                    } else {
                        if (!clientesConectados.containsKey(destinatario)) {
                            enviarA(nombreCliente, "ERROR:Cliente '" + destinatario + "' no encontrado");
                            continue;
                        }
                        enviarA(destinatario, "FILE_RECV:" + nombreCliente + ":" + nombreArchivo + ":" + tamanoBytes + ":" + base64Data);
                        enviarA(nombreCliente, "FILE_OK:" + destinatario + ":" + nombreArchivo + ":" + tamanoBytes);
                        log("[ARCHIVO] " + nombreCliente + " -> " + destinatario + " | " + nombreArchivo);
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
            log("Conexión perdida con cliente " + nombreCliente);
        } finally {
            if (nombreCliente != null) {
                clientesConectados.remove(nombreCliente);
                log("Cliente desconectado: " + nombreCliente + ". Activos: " + clientesConectados.size());
                notificarCambioClientes();
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

    private javax.swing.JButton bIniciar;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JTextArea mensajesTxt;
    private javax.swing.JScrollPane jScrollPane1;
}