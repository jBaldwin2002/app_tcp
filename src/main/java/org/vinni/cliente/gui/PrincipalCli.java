package org.vinni.cliente.gui;

import javax.swing.*;
import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Random;

/**
 * author: Vinni 2024
 */
public class PrincipalCli extends javax.swing.JFrame {

    private final int PORT = 12345;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String miNombre;

    private static final String[] extensionesProhividas = {".exe", ".bat"};
    private static final long tamanhoMin = 1024;
    private static final long tamanhoMax = 1024 * 1024 * 5;

    public PrincipalCli() {
        initComponents();
        cargarIdAutomatico();
    }

    private void cargarIdAutomatico(){
        Random random = new Random();
        int tercer = random.nextInt(256);
        int cuarto = random.nextInt(254) + 1;
        String ipAleatoria = "192.168." + tercer + "." + cuarto;
        nombreTxt.setText(ipAleatoria);
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
        btEnviarArchivo = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        getContentPane().setLayout(null);

        // Título
        jLabel1.setFont(new java.awt.Font("Tahoma", 1, 14));
        jLabel1.setForeground(new java.awt.Color(204, 0, 0));
        jLabel1.setText("CLIENTE TCP : DFRACK");
        getContentPane().add(jLabel1);
        jLabel1.setBounds(20, 10, 250, 20);

        // Boton conectar
        bConectar.setFont(new java.awt.Font("Segoe UI", 0, 14));
        bConectar.setText("CONECTAR");
        bConectar.addActionListener(evt -> conectar());
        getContentPane().add(bConectar);
        bConectar.setBounds(340, 10, 130, 30);

        // ID
        jLabel3.setFont(new java.awt.Font("Verdana", 0, 12));
        jLabel3.setText("ID:");
        getContentPane().add(jLabel3);
        jLabel3.setBounds(20, 45, 80, 20);

        nombreTxt.setFont(new java.awt.Font("Verdana", 0, 12));
        nombreTxt.setEditable(false);
        nombreTxt.setBackground(new java.awt.Color(230, 230, 230));
        getContentPane().add(nombreTxt);
        nombreTxt.setBounds(100, 45, 230, 25);

        // Destinatario
        jLabel4.setFont(new java.awt.Font("Verdana", 0, 12));
        jLabel4.setText("Enviar a:");
        getContentPane().add(jLabel4);
        jLabel4.setBounds(20, 85, 80, 20);

        destinatarioCmb.setFont(new java.awt.Font("Verdana", 0, 12));
        destinatarioCmb.setEnabled(false);
        getContentPane().add(destinatarioCmb);
        destinatarioCmb.setBounds(100, 85, 230, 25);

        // Mensaje
        jLabel2.setFont(new java.awt.Font("Verdana", 0, 12));
        jLabel2.setText("Mensaje:");
        getContentPane().add(jLabel2);
        jLabel2.setBounds(20, 125, 80, 20);

        mensajeTxt.setFont(new java.awt.Font("Verdana", 0, 12));
        mensajeTxt.setEnabled(false);
        mensajeTxt.addActionListener(evt -> enviarMensaje());
        getContentPane().add(mensajeTxt);
        mensajeTxt.setBounds(100, 125, 270, 25);

        // Boton enviar mensaje
        btEnviar.setFont(new java.awt.Font("Verdana", 0, 12));
        btEnviar.setText("Enviar");
        btEnviar.setEnabled(false);
        btEnviar.addActionListener(evt -> enviarMensaje());
        getContentPane().add(btEnviar);
        btEnviar.setBounds(380, 125, 90, 25);

        // Boton enviar archivo
        btEnviarArchivo.setFont(new java.awt.Font("Verdana", 0, 11));
        btEnviarArchivo.setText("Enviar Archivo");
        btEnviarArchivo.setEnabled(false);
        btEnviarArchivo.addActionListener(evt -> seleccionarYEnviarArchivo());
        getContentPane().add(btEnviarArchivo);
        btEnviarArchivo.setBounds(100, 160, 150, 27);

        // Area de mensajes
        mensajesTxt.setColumns(20);
        mensajesTxt.setRows(5);
        mensajesTxt.setEditable(false);
        jScrollPane1.setViewportView(mensajesTxt);
        getContentPane().add(jScrollPane1);
        jScrollPane1.setBounds(20, 205, 450, 160);

        setSize(new java.awt.Dimension(510, 420));
        setLocationRelativeTo(null);
    }


    private void conectar() {
        miNombre = nombreTxt.getText().trim();
        if (miNombre.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Error al generar el ID automatico");
            return;
        }

        try {
            socket = new Socket("localhost", PORT);
            out = new PrintWriter(socket.getOutputStream(), true);
            in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            out.println(miNombre);

            String respuesta = in.readLine();
            if (respuesta != null && respuesta.startsWith("OK:")) {
                log(respuesta.substring(3));
                bConectar.setEnabled(false);
                mensajeTxt.setEnabled(true);
                btEnviar.setEnabled(true);
                btEnviarArchivo.setEnabled(true);
                destinatarioCmb.setEnabled(true);

                new Thread(() -> {
                    try {
                        String fromServer;
                        while ((fromServer = in.readLine()) != null) {
                            procesarMensaje(fromServer);
                        }
                    } catch (IOException ex) {
                        SwingUtilities.invokeLater(() -> log("Conexion perdida"));
                    }
                }).start();

            } else if (respuesta != null && respuesta.startsWith("ERROR:")) {
                if (respuesta.contains("ya existe")) {
                    socket.close();
                    cargarIdAutomatico();
                    JOptionPane.showMessageDialog(this,
                            "ID en uso, se genero uno nuevo: " + nombreTxt.getText()
                                    + "\nIntenta conectar de nuevo.");
                } else {
                    JOptionPane.showMessageDialog(this, respuesta.substring(6));
                    socket.close();
                }
            }

        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                    "No se pudo conectar al servidor: " + e.getMessage());
        }
    }

    private void procesarMensaje(String mensaje) {
        SwingUtilities.invokeLater(() -> {

            // Lista de clientes conectados
            if (mensaje.startsWith("CLIENTES_CONECTADOS:")) {
                String[] clientes = mensaje.substring(20).split(",");
                destinatarioCmb.removeAllItems();
                for (String cliente : clientes) {
                    if (!cliente.trim().isEmpty()) {
                        destinatarioCmb.addItem(cliente.trim());
                    }
                }

                // Recepcion de archivo
                // Formato: FILE_RECV:remitente:nombreArchivo:tamanoBytes:base64Data
            } else if (mensaje.startsWith("FILE_RECV:")) {
                String[] partes = mensaje.split(":", 5);
                if (partes.length < 5) {
                    log("[ERROR] Archivo recibido con formato invalido");
                    return;
                }
                String remitente = partes[1];
                String nombreArchivo = partes[2];
                long   tamanoBytes = Long.parseLong(partes[3]);
                String base64Data = partes[4];

                log("[ARCHIVO] De " + remitente + ": " + nombreArchivo
                        + " (" + (tamanoBytes / 1024) + " KB) — Elige donde guardar...");

                // Donde guardar
                JFileChooser chooser = new JFileChooser();
                chooser.setDialogTitle("Guardar archivo de " + remitente);
                chooser.setSelectedFile(new File(nombreArchivo));
                chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

                int resultado = chooser.showSaveDialog(this);
                if (resultado == JFileChooser.APPROVE_OPTION) {
                    File destino = chooser.getSelectedFile();
                    try {
                        byte[] datos = Base64.getDecoder().decode(base64Data);
                        Files.write(destino.toPath(), datos);
                        log("[ARCHIVO] Guardado en: " + destino.getAbsolutePath());
                    } catch (IOException e) {
                        log("[ERROR] No se pudo guardar: " + e.getMessage());
                    }
                } else {
                    log("[ARCHIVO] Guardado cancelado");
                }

            } else if (mensaje.startsWith("FILE_OK:")) {
                String[] partes = mensaje.split(":", 4);
                String dest = partes.length > 1 ? partes[1] : "?";
                String nombreArchivo = partes.length > 2 ? partes[2] : "?";
                long   tamano = partes.length > 3 ? Long.parseLong(partes[3]) : 0;
                log("[ARCHIVO] Enviado a " + dest + ": " + nombreArchivo
                        + " (" + (tamano / 1024) + " KB)");

                //Mensaje de texto normal
            } else if (mensaje.startsWith("DE:")) {
                String[] partes = mensaje.substring(3).split(":", 2);
                String remitente = partes[0];
                String contenido = partes.length > 1 ? partes[1] : "";
                log("De " + remitente + ": " + contenido);

            } else if (mensaje.startsWith("ENVIADO:")) {
                String[] partes = mensaje.substring(8).split(":", 2);
                String dest = partes[0];
                String contenido = partes.length > 1 ? partes[1] : "";
                log("Enviado a " + dest + ": " + contenido);

            } else if (mensaje.startsWith("ERROR:")) {
                log("Error: " + mensaje.substring(6));

            } else {
                log(mensaje);
            }
        });
    }


    private void seleccionarYEnviarArchivo() {
        if (out == null || destinatarioCmb.getSelectedItem() == null) return;

        String destinatario = (String) destinatarioCmb.getSelectedItem();

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Seleccionar archivo para enviar a " + destinatario);
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

        int resultado = chooser.showOpenDialog(this);
        if (resultado != JFileChooser.APPROVE_OPTION) return;

        File archivo = chooser.getSelectedFile();

        // Validacion de extension
        String nombreLower = archivo.getName().toLowerCase();
        for (String ext : extensionesProhividas) {
            if (nombreLower.endsWith(ext)) {
                JOptionPane.showMessageDialog(this,
                        "No se permiten archivos con extension '" + ext + "'",
                        "Extension no permitida", JOptionPane.WARNING_MESSAGE);
                return;
            }
        }


        long tamanoBytes = archivo.length();
        if (tamanoBytes < tamanhoMin) {
            JOptionPane.showMessageDialog(this,
                    "El archivo es demasiado pequeño.\n"
                            + "Tamaño mínimo: 1 KB\n"
                            + "Tamaño del archivo: " + tamanoBytes + " bytes",
                    "Archivo muy pequeño", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (tamanoBytes > tamanhoMax) {
            JOptionPane.showMessageDialog(this,
                    "El archivo supera el límite permitido.\n"
                            + "Tamaño maximo: 5 MB\n"
                            + "Tamaño del archivo: "
                            + String.format("%.2f", tamanoBytes / 1024.0 / 1024.0) + " MB",
                    "Archivo muy grande", JOptionPane.WARNING_MESSAGE);
            return;
        }

        //Leer, codificar y enviar en hilo aparte
        btEnviarArchivo.setEnabled(false);
        log("[ARCHIVO] Enviando " + archivo.getName() + " a " + destinatario + "...");

        final File archivoFinal = archivo;
        new Thread(() -> {
            try {
                byte[] datos  = Files.readAllBytes(archivoFinal.toPath());
                String base64 = Base64.getEncoder().encodeToString(datos);

                // Formato: FILE_SEND:destinatario:nombreArchivo:tamanoBytes:base64Data
                out.println("FILE_SEND:" + destinatario + ":"
                        + archivoFinal.getName() + ":" + tamanoBytes + ":" + base64);

            } catch (IOException e) {
                SwingUtilities.invokeLater(() ->
                        log("[ERROR] No se pudo leer el archivo: " + e.getMessage()));
            } finally {
                SwingUtilities.invokeLater(() -> btEnviarArchivo.setEnabled(true));
            }
        }).start();
    }


    private void enviarMensaje() {
        if (out == null || destinatarioCmb.getSelectedItem() == null) return;
        String texto = mensajeTxt.getText().trim();
        if (texto.isEmpty()) return;
        String destinatario = (String) destinatarioCmb.getSelectedItem();
        out.println(destinatario + ":" + texto);
        mensajeTxt.setText("");
    }

    private void log(String mensaje) {
        mensajesTxt.append(mensaje + "\n");
    }

    public static void main(String args[]) {
        java.awt.EventQueue.invokeLater(() -> new PrincipalCli().setVisible(true));
    }

    private javax.swing.JButton bConectar;
    private javax.swing.JButton btEnviar;
    private javax.swing.JButton btEnviarArchivo;
    private javax.swing.JLabel jLabel1, jLabel2, jLabel3, jLabel4;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JTextArea mensajesTxt;
    private javax.swing.JTextField mensajeTxt;
    private javax.swing.JTextField nombreTxt;
    private javax.swing.JComboBox<String> destinatarioCmb;
}