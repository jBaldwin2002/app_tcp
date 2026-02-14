package org.vinni.servidor.gui;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * Author: Vinni
 */
public class PrincipalSrv extends javax.swing.JFrame {

    private final int PORT = 12345;
    private ServerSocket serverSocket;

    // Lista de writers de todos los clientes conectados
    private final List<PrintWriter> clientesConectados = new ArrayList<>();

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
     * Envía un mensaje a TODOS los clientes conectados (broadcast)
     */
    private synchronized void broadcast(String mensaje) {
        // Iteramos con una copia para evitar ConcurrentModificationException
        for (PrintWriter writer : new ArrayList<>(clientesConectados)) {
            writer.println(mensaje);
        }
    }

    private void manejarCliente(Socket clientSocket) {
        PrintWriter out = null;
        try {
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(clientSocket.getInputStream())
            );
            out = new PrintWriter(clientSocket.getOutputStream(), true);

            // Registrar este cliente en la lista
            synchronized (clientesConectados) {
                clientesConectados.add(out);
            }

            String clienteInfo = clientSocket.getInetAddress() + ":" + clientSocket.getPort();
            log("Cliente conectado: " + clienteInfo);

            String linea;
            while ((linea = in.readLine()) != null) {
                final String mensajeRecibido = "Cliente [" + clienteInfo + "]: " + linea;
                log(mensajeRecibido);

                // Reenviar el mensaje a TODOS los clientes (broadcast)
                broadcast(mensajeRecibido);
            }

        } catch (IOException e) {
            log("Error con cliente: " + e.getMessage());
        } finally {
            // Al desconectarse, quitarlo de la lista
            if (out != null) {
                synchronized (clientesConectados) {
                    clientesConectados.remove(out);
                }
            }
            try { clientSocket.close(); } catch (IOException ignored) {}
            log("Un cliente se desconectó. Clientes activos: " + clientesConectados.size());
        }
    }

    /** Escribe en el JTextArea de forma segura desde cualquier hilo */
    private void log(String mensaje) {
        SwingUtilities.invokeLater(() -> mensajesTxt.append(mensaje + "\n"));
    }

    private void iniciarServidor() {
        bIniciar.setEnabled(false); // Evitar iniciar dos veces
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

    // Variables declaration
    private javax.swing.JButton bIniciar;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JTextArea mensajesTxt;
    private javax.swing.JScrollPane jScrollPane1;
}
