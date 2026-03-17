package org.vinni.cliente.gui;

import javax.swing.*;
import java.io.*;
import java.net.InetSocketAddress;
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

    // Políticas de Reconexión y Timeouts
    private final int MAX_REINTENTOS = 5;
    private final int TIEMPO_REINTENTO_MS = 3000; // 3 segundos
    private final int CONNECTION_TIMEOUT_MS = 2000; // 2 segundos de timeout para el connect

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

        jLabel1.setFont(new java.awt.Font("Tahoma", 1, 14));
        jLabel1.setForeground(new java.awt.Color(204, 0, 0));
        jLabel1.setText("CLIENTE TCP : DFRACK");
        getContentPane().add(jLabel1);
        jLabel1.setBounds(20, 10, 250, 20);

        bConectar.setFont(new java.awt.Font("Segoe UI", 0, 14));
        bConectar.setText("CONECTAR");
        bConectar.addActionListener(evt -> iniciarConexion());
        getContentPane().add(bConectar);
        bConectar.setBounds(340, 10, 130, 30);

        jLabel3.setFont(new java.awt.Font("Verdana", 0, 12));
        jLabel3.setText("ID:");
        getContentPane().add(jLabel3);
        jLabel3.setBounds(20, 45, 80, 20);

        nombreTxt.setFont(new java.awt.Font("Verdana", 0, 12));
        nombreTxt.setEditable(false);
        nombreTxt.setBackground(new java.awt.Color(230, 230, 230));
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
        mensajeTxt.addActionListener(evt -> enviarMensaje());
        getContentPane().add(mensajeTxt);
        mensajeTxt.setBounds(100, 125, 270, 25);

        btEnviar.setFont(new java.awt.Font("Verdana", 0, 12));
        btEnviar.setText("Enviar");
        btEnviar.setEnabled(false);
        btEnviar.addActionListener(evt -> enviarMensaje());
        getContentPane().add(btEnviar);
        btEnviar.setBounds(380, 125, 90, 25);

        btEnviarArchivo.setFont(new java.awt.Font("Verdana", 0, 11));
        btEnviarArchivo.setText("Enviar Archivo");
        btEnviarArchivo.setEnabled(false);
        btEnviarArchivo.addActionListener(evt -> seleccionarYEnviarArchivo());
        getContentPane().add(btEnviarArchivo);
        btEnviarArchivo.setBounds(100, 160, 150, 27);

        mensajesTxt.setColumns(20);
        mensajesTxt.setRows(5);
        mensajesTxt.setEditable(false);
        jScrollPane1.setViewportView(mensajesTxt);
        getContentPane().add(jScrollPane1);
        jScrollPane1.setBounds(20, 205, 450, 160);

        setSize(new java.awt.Dimension(510, 420));
        setLocationRelativeTo(null);
    }

    private void cambiarEstadoUI(boolean conectado) {
        SwingUtilities.invokeLater(() -> {
            bConectar.setEnabled(!conectado);
            mensajeTxt.setEnabled(conectado);
            btEnviar.setEnabled(conectado);
            btEnviarArchivo.setEnabled(conectado);
            destinatarioCmb.setEnabled(conectado);
            if (!conectado) {
                destinatarioCmb.removeAllItems();
            }
        });
    }

    private void iniciarConexion() {
        miNombre = nombreTxt.getText().trim();
        if (miNombre.isEmpty()) return;

        bConectar.setEnabled(false); // Evitar doble clic
        log("Iniciando conexión...");
        ejecutarPoliticaDeReintentos();
    }

    /**
     * Hilo asíncrono para manejar Timeout y Reintentos
     */
    private void ejecutarPoliticaDeReintentos() {
        new Thread(() -> {
            int intentoActual = 1;
            boolean conexionExitosa = false;

            while (intentoActual <= MAX_REINTENTOS && !conexionExitosa) {
                try {
                    log("Intento " + intentoActual + " de " + MAX_REINTENTOS + " ");

                    socket = new Socket();
                    // Timeout de conexión aquí
                    socket.connect(new InetSocketAddress("localhost", PORT), CONNECTION_TIMEOUT_MS);

                    out = new PrintWriter(socket.getOutputStream(), true);
                    in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                    out.println(miNombre);
                    String respuesta = in.readLine();

                    if (respuesta != null && respuesta.startsWith("OK:")) {
                        conexionExitosa = true;
                        log(respuesta.substring(3));
                        cambiarEstadoUI(true);
                        iniciarHiloEscucha(); // Arrancar escucha de mensajes
                    } else if (respuesta != null && respuesta.startsWith("ERROR:Nombre ya existe")) {
                        // Si el nombre existe, se cambia y se interrumpe la política (no es error de red)
                        socket.close();
                        SwingUtilities.invokeLater(() -> {
                            cargarIdAutomatico();
                            JOptionPane.showMessageDialog(this, "ID en uso, se generó uno nuevo. Intenta conectar de nuevo.");
                            bConectar.setEnabled(true);
                        });
                        return; // Rompe el hilo de reintentos
                    }

                } catch (IOException e) {
                    log("Fallo intento " + intentoActual + ": Servidor no disponible.");

                    if (intentoActual < MAX_REINTENTOS) {
                        try {
                            log("Esperando " + (TIEMPO_REINTENTO_MS / 1000) + " segundos antes de reintentar...");
                            Thread.sleep(TIEMPO_REINTENTO_MS);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
                intentoActual++;
            }

            if (!conexionExitosa) {
                log("--> Política agotada: No se pudo conectar tras " + MAX_REINTENTOS + " intentos.");
                SwingUtilities.invokeLater(() -> bConectar.setEnabled(true));
            }
        }).start();
    }

    private void iniciarHiloEscucha() {
        new Thread(() -> {
            try {
                String fromServer;
                while ((fromServer = in.readLine()) != null) {
                    procesarMensaje(fromServer);
                }
            } catch (IOException ex) {
                // Si el readline falla, se perdió la conexión con el servidor activo.
                log("¡Conexión perdida con el servidor!");
                cambiarEstadoUI(false);

                // Disparar reconexión automática
                log("--> Iniciando politica de RECONEXION automatica...");
                ejecutarPoliticaDeReintentos();
            }
        }).start();
    }

    private void procesarMensaje(String mensaje) {
        SwingUtilities.invokeLater(() -> {
            if (mensaje.startsWith("CLIENTES_CONECTADOS:")) {
                String[] clientes = mensaje.substring(20).split(",");
                destinatarioCmb.removeAllItems();
                destinatarioCmb.addItem("TODOS");
                for (String cliente : clientes) {
                    if (!cliente.trim().isEmpty()) {
                        destinatarioCmb.addItem(cliente.trim());
                    }
                }
            } else if (mensaje.startsWith("FILE_RECV:")) {
                String[] partes = mensaje.split(":", 5);
                if (partes.length < 5) return;
                String remitente = partes[1];
                String nombreArchivo = partes[2];
                long tamanoBytes = Long.parseLong(partes[3]);
                String base64Data = partes[4];

                log("[ARCHIVO] De " + remitente + ": " + nombreArchivo + " (" + (tamanoBytes / 1024) + " KB) — Elige donde guardar...");

                JFileChooser chooser = new JFileChooser();
                chooser.setSelectedFile(new File(nombreArchivo));
                if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                    try {
                        byte[] datos = Base64.getDecoder().decode(base64Data);
                        Files.write(chooser.getSelectedFile().toPath(), datos);
                        log("[ARCHIVO] Guardado en: " + chooser.getSelectedFile().getAbsolutePath());
                    } catch (IOException e) {
                        log("[ERROR] No se pudo guardar: " + e.getMessage());
                    }
                }
            } else if (mensaje.startsWith("FILE_OK:")) {
                String[] partes = mensaje.split(":", 4);
                log("[ARCHIVO] Enviado a " + partes[1] + ": " + partes[2]);
            } else if (mensaje.startsWith("DE:")) {
                String[] partes = mensaje.substring(3).split(":", 2);
                log("De " + partes[0] + ": " + (partes.length > 1 ? partes[1] : ""));
            } else if (mensaje.startsWith("ENVIADO:")) {
                String[] partes = mensaje.substring(8).split(":", 2);
                log("Enviado a " + partes[0] + ": " + (partes.length > 1 ? partes[1] : ""));
            } else {
                log(mensaje);
            }
        });
    }

    private void seleccionarYEnviarArchivo() {
        if (out == null || destinatarioCmb.getSelectedItem() == null) return;
        String destinatario = (String) destinatarioCmb.getSelectedItem();

        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File archivo = chooser.getSelectedFile();
        String nombreLower = archivo.getName().toLowerCase();

        for (String ext : extensionesProhividas) {
            if (nombreLower.endsWith(ext)) {
                JOptionPane.showMessageDialog(this, "No se permiten archivos con extension '" + ext + "'");
                return;
            }
        }

        long tamanoBytes = archivo.length();
        if (tamanoBytes < tamanhoMin || tamanoBytes > tamanhoMax) {
            JOptionPane.showMessageDialog(this, "Tamaño de archivo inválido. (Min: 1KB, Max: 5MB)");
            return;
        }

        btEnviarArchivo.setEnabled(false);
        log("[ARCHIVO] Enviando " + archivo.getName() + " a " + destinatario + "...");

        new Thread(() -> {
            try {
                byte[] datos = Files.readAllBytes(archivo.toPath());
                String base64 = Base64.getEncoder().encodeToString(datos);
                out.println("FILE_SEND:" + destinatario + ":" + archivo.getName() + ":" + tamanoBytes + ":" + base64);
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> log("[ERROR] No se pudo leer: " + e.getMessage()));
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
        out.println((destinatario.equals("TODOS") ? "TODOS:" : destinatario + ":") + texto);
        mensajeTxt.setText("");
    }

    private void log(String mensaje) {
        SwingUtilities.invokeLater(() -> mensajesTxt.append(mensaje + "\n"));
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