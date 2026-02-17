package org.vinni.cliente.gui;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * author: Vinni 2024
 */
public class PrincipalCli extends javax.swing.JFrame {

    private final int PORT = 12345;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String miNombre;

    public PrincipalCli() {
        initComponents();
    }

    @SuppressWarnings("unchecked")
    private void initComponents() {
        this.setTitle("Cliente ");

        bConectar = new javax.swing.JButton();
        jLabel1 = new javax.swing.JLabel();
        jScrollPane1 = new javax.swing.JScrollPane();
        mensajesTxt = new javax.swing.JTextArea();
        mensajeTxt = new javax.swing.JTextField();
        jLabel2 = new javax.swing.JLabel();
        btEnviar = new javax.swing.JButton();
        nombreTxt = new javax.swing.JTextField();
        jLabel3 = new javax.swing.JLabel();
        destinatarioCmb = new javax.swing.JComboBox<>();
        jLabel4 = new javax.swing.JLabel();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        getContentPane().setLayout(null);

        bConectar.setFont(new java.awt.Font("Segoe UI", 0, 14));
        bConectar.setText("CONECTAR");
        bConectar.addActionListener(evt -> bConectarActionPerformed(evt));
        getContentPane().add(bConectar);
        bConectar.setBounds(340, 10, 130, 30);

        jLabel1.setFont(new java.awt.Font("Tahoma", 1, 14));
        jLabel1.setForeground(new java.awt.Color(204, 0, 0));
        jLabel1.setText("CLIENTE TCP : DFRACK");
        getContentPane().add(jLabel1);
        jLabel1.setBounds(20, 10, 250, 20);

        jLabel3.setFont(new java.awt.Font("Verdana", 0, 12));
        jLabel3.setText("Tu nombre:");
        getContentPane().add(jLabel3);
        jLabel3.setBounds(20, 45, 80, 20);

        nombreTxt.setFont(new java.awt.Font("Verdana", 0, 12));
        getContentPane().add(nombreTxt);
        nombreTxt.setBounds(100, 45, 230, 25);

        jLabel4.setFont(new java.awt.Font("Verdana", 0, 12));
        jLabel4.setText("Enviar a:");
        getContentPane().add(jLabel4);
        jLabel4.setBounds(20, 85, 80, 20);

        destinatarioCmb.setFont(new java.awt.Font("Verdana", 0, 12));
        destinatarioCmb.setEnabled(false);
        getContentPane().add(destinatarioCmb);
        destinatarioCmb.setBounds(100, 85, 230, 25);

        jLabel2.setFont(new java.awt.Font("Verdana", 0, 12));
        jLabel2.setText("Mensaje:");
        getContentPane().add(jLabel2);
        jLabel2.setBounds(20, 125, 80, 20);

        mensajeTxt.setFont(new java.awt.Font("Verdana", 0, 12));
        mensajeTxt.setEnabled(false);
        mensajeTxt.addActionListener(evt -> btEnviarActionPerformed(evt));
        getContentPane().add(mensajeTxt);
        mensajeTxt.setBounds(100, 125, 370, 25);

        btEnviar.setFont(new java.awt.Font("Verdana", 0, 12));
        btEnviar.setText("Enviar");
        btEnviar.setEnabled(false);
        btEnviar.addActionListener(evt -> btEnviarActionPerformed(evt));
        getContentPane().add(btEnviar);
        btEnviar.setBounds(390, 160, 80, 27);

        mensajesTxt.setColumns(20);
        mensajesTxt.setRows(5);
        mensajesTxt.setEditable(false);
        jScrollPane1.setViewportView(mensajesTxt);
        getContentPane().add(jScrollPane1);
        jScrollPane1.setBounds(20, 200, 450, 140);

        setSize(new java.awt.Dimension(510, 400));
        setLocationRelativeTo(null);
    }

    private void bConectarActionPerformed(java.awt.event.ActionEvent evt) {
        conectar();
    }

    private void btEnviarActionPerformed(java.awt.event.ActionEvent evt) {
        enviarMensaje();
    }

    public static void main(String args[]) {
        java.awt.EventQueue.invokeLater(() -> new PrincipalCli().setVisible(true));
    }

    private javax.swing.JButton bConectar;
    private javax.swing.JButton btEnviar;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JTextArea mensajesTxt;
    private JTextField mensajeTxt;
    private JTextField nombreTxt;
    private JComboBox<String> destinatarioCmb;

    private void conectar() {
        miNombre = nombreTxt.getText().trim();

        if (miNombre.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Ingresa tu nombre primero");
            return;
        }

        try {
            socket = new Socket("localhost", PORT);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Enviar nombre al servidor
            out.println(miNombre);

            // Esperar confirmación
            String respuesta = in.readLine();
            if (respuesta != null && respuesta.startsWith("OK:")) {
                mensajesTxt.append(respuesta.substring(3) + "\n");

                // Deshabilitar nombre y habilitar envío
                nombreTxt.setEnabled(false);
                bConectar.setEnabled(false);
                mensajeTxt.setEnabled(true);
                btEnviar.setEnabled(true);
                destinatarioCmb.setEnabled(true);

                // Iniciar hilo para recibir mensajes
                new Thread(() -> {
                    try {
                        String fromServer;
                        while ((fromServer = in.readLine()) != null) {
                            procesarMensaje(fromServer);
                        }
                    } catch (IOException ex) {
                        SwingUtilities.invokeLater(() ->
                                mensajesTxt.append("Conexión perdida\n")
                        );
                    }
                }).start();

            } else if (respuesta != null && respuesta.startsWith("ERROR:")) {
                JOptionPane.showMessageDialog(this, respuesta.substring(6));
                socket.close();
            }

        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "No se pudo conectar al servidor: " + e.getMessage());
        }
    }

    private void procesarMensaje(String mensaje) {
        SwingUtilities.invokeLater(() -> {
            if (mensaje.startsWith("CLIENTES_CONECTADOS:")) {
                // Actualizar lista de destinatarios
                String[] clientes = mensaje.substring(20).split(",");
                destinatarioCmb.removeAllItems();
                for (String cliente : clientes) {
                    if (!cliente.trim().isEmpty()) {
                        destinatarioCmb.addItem(cliente.trim());
                    }
                }
            } else if (mensaje.startsWith("DE:")) {
                // Mensaje recibido de otro cliente
                String[] partes = mensaje.substring(3).split(":", 2);
                String remitente = partes[0];
                String contenido = partes.length > 1 ? partes[1] : "";
                mensajesTxt.append("De " + remitente + ": " + contenido + "\n");
            } else if (mensaje.startsWith("ENVIADO:")) {
                // Confirmación de envío
                String[] partes = mensaje.substring(8).split(":", 2);
                String dest = partes[0];
                String contenido = partes.length > 1 ? partes[1] : "";
                mensajesTxt.append("Enviado a " + dest + ": " + contenido + "\n");
            } else if (mensaje.startsWith("ERROR:")) {
                mensajesTxt.append("Error " + mensaje.substring(6) + "\n");
            } else {
                mensajesTxt.append(mensaje + "\n");
            }
        });
    }

    private void enviarMensaje() {
        if (out == null || destinatarioCmb.getSelectedItem() == null) {
            return;
        }

        String texto = mensajeTxt.getText().trim();
        if (texto.isEmpty()) {
            return;
        }

        String destinatario = (String) destinatarioCmb.getSelectedItem();

        // Enviar en formato "DESTINATARIO:MENSAJE"
        out.println(destinatario + ":" + texto);
        mensajeTxt.setText("");
    }
}