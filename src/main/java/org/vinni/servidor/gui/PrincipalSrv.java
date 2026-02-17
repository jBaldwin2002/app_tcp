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

    // Lista de writers de todos los clientes conectados
    private final Map<String, PrintWriter> clientesConectados = new ConcurrentHashMap<>();

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
        iniciarServidor();
    }

    /**
     * Envía mensaje a un cliente específico
     */
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

    /**
     * Notifica a todos sobre cambios en la lista de clientes
     */
    private void notificarCambioClientes() {
        for (String cliente : clientesConectados.keySet()) {
            enviarListaClientes(cliente);
        }
    }

    private void manejarCliente(Socket clientSocket) {
        PrintWriter out = null;
        String nombreCliente = null;

        try {
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(clientSocket.getInputStream())
            );
            out = new PrintWriter(clientSocket.getOutputStream(), true);

            // Primer mensaje del cliente debe ser su nombre
            nombreCliente = in.readLine();

            if (nombreCliente == null || nombreCliente.trim().isEmpty()) {
                out.println("ERROR:Nombre inválido");
                clientSocket.close();
                return;
            }

            // Verificar si el nombre ya existe
            if (clientesConectados.containsKey(nombreCliente)) {
                out.println("ERROR:Nombre ya existe");
                clientSocket.close();
                return;
            }

            // Registrar cliente
            clientesConectados.put(nombreCliente, out);
            out.println("OK:Conectado como " + nombreCliente);
            log("Cliente conectado: " + nombreCliente);

            // Notificar a todos sobre el nuevo cliente
            notificarCambioClientes();

            String linea;
            while ((linea = in.readLine()) != null) {
                // Formato esperado: "DESTINATARIO:MENSAJE"
                if (linea.contains(":")) {
                    String[] partes = linea.split(":", 2);
                    String destinatario = partes[0].trim();
                    String mensaje = partes.length > 1 ? partes[1] : "";

                    log(nombreCliente + " -> " + destinatario + ": " + mensaje);

                    if (clientesConectados.containsKey(destinatario)) {
                        // Enviar mensaje al destinatario
                        enviarA(destinatario, "DE:" + nombreCliente + ":" + mensaje);
                        // Confirmar al remitente
                        enviarA(nombreCliente, "ENVIADO:" + destinatario + ":" + mensaje);
                    } else {
                        enviarA(nombreCliente, "ERROR:Cliente '" + destinatario + "' no encontrado");
                    }
                } else {
                    log("Formato inválido de " + nombreCliente + ": " + linea);
                }
            }

        } catch (IOException e) {
            log("Error con cliente " + nombreCliente + ": " + e.getMessage());
        } finally {
            // Desconectar cliente
            if (nombreCliente != null) {
                clientesConectados.remove(nombreCliente);
                log("Cliente desconectado: " + nombreCliente + ". Clientes activos: " + clientesConectados.size());
                notificarCambioClientes();
            }
            try { clientSocket.close(); } catch (IOException ignored) {}
        }
    }

    private void log(String mensaje) {
        SwingUtilities.invokeLater(() -> mensajesTxt.append(mensaje + "\n"));
    }

    private void iniciarServidor() {
        bIniciar.setEnabled(false);
        new Thread(() -> {
            try {
                InetAddress addr = InetAddress.getLocalHost();
                serverSocket = new ServerSocket(PORT);
                log("Servidor TCP en ejecución: " + addr + ", Puerto " + serverSocket.getLocalPort());

                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    new Thread(() -> manejarCliente(clientSocket)).start();
                }
            } catch (IOException ex) {
                log("Error en el servidor: " + ex.getMessage());
            }
        }).start();
    }

    public static void main(String args[]) {
        java.awt.EventQueue.invokeLater(() -> new PrincipalSrv().setVisible(true));
    }

    private javax.swing.JButton bIniciar;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JTextArea mensajesTxt;
    private javax.swing.JScrollPane jScrollPane1;
}