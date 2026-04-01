package org.vinni.balanceador;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;


public class LoadBalancer {

    // Puerto público que ven los clientes (no cambia)
    private static final int PUERTO_BALANCEADOR = 12345;

    // Intervalo del health-check en milisegundos
    private static final int HEALTH_CHECK_MS = 2000;

    // Tiempo máximo esperando que un backend responda al health-check
    private static final int HEALTH_TIMEOUT_MS = 1000;

    // ---------------------------------------------------------------
    // Definición de servidores
    // ---------------------------------------------------------------
    private final List<ServerNode> principales = List.of(
            new ServerNode("Principal-1", "localhost", 12346, ServerNode.Tipo.PRINCIPAL),
            new ServerNode("Principal-2", "localhost", 12347, ServerNode.Tipo.PRINCIPAL)
    );

    private final List<ServerNode> respaldos = List.of(
            new ServerNode("Respaldo-1", "localhost", 12348, ServerNode.Tipo.RESPALDO),
            new ServerNode("Respaldo-2", "localhost", 12349, ServerNode.Tipo.RESPALDO)
    );

    private final AtomicInteger turnoRoundRobin = new AtomicInteger(0);
    private final ScheduledExecutorService healthScheduler =
            Executors.newSingleThreadScheduledExecutor();

    // ---------------------------------------------------------------
    // Punto de entrada
    // ---------------------------------------------------------------
    public void iniciar() throws IOException {
        // Arranca el health-check periódico
        healthScheduler.scheduleAtFixedRate(
                this::ejecutarHealthCheck, 0, HEALTH_CHECK_MS, TimeUnit.MILLISECONDS
        );

        try (ServerSocket servidor = new ServerSocket(PUERTO_BALANCEADOR)) {
            log("Balanceador escuchando en puerto " + PUERTO_BALANCEADOR);

            while (true) {
                Socket cliente = servidor.accept();
                new Thread(() -> manejarCliente(cliente)).start();
            }
        }
    }

    // ---------------------------------------------------------------
    // Selección de backend
    // ---------------------------------------------------------------
    private ServerNode seleccionarBackend() {
        List<ServerNode> principalesActivos = principales.stream()
                .filter(ServerNode::estaActivo)
                .toList();

        if (!principalesActivos.isEmpty()) {
            // Round-robin solo entre los activos
            int idx = Math.abs(turnoRoundRobin.getAndIncrement() % principalesActivos.size());
            return principalesActivos.get(idx);
        }

        // Ambos principales caídos → activar respaldos
        List<ServerNode> respaldosActivos = respaldos.stream()
                .filter(ServerNode::estaActivo)
                .toList();

        if (!respaldosActivos.isEmpty()) {
            log("[FAILOVER] Principales caídos. Redirigiendo a servidores de respaldo.");
            int idx = Math.abs(turnoRoundRobin.getAndIncrement() % respaldosActivos.size());
            return respaldosActivos.get(idx);
        }

        return null; // Todos los servidores caídos
    }

    // ---------------------------------------------------------------
    // Manejo de conexión: proxy TCP transparente
    // ---------------------------------------------------------------
    private void manejarCliente(Socket cliente) {
        ServerNode backend = seleccionarBackend();

        if (backend == null) {
            log("[ERROR] No hay servidores disponibles para " + cliente.getRemoteSocketAddress());
            try { cliente.close(); } catch (IOException ignored) {}
            return;
        }

        log("Conectando " + cliente.getRemoteSocketAddress()
                + " → " + backend.getNombre() + " (" + backend.getPuerto() + ")");

        try (Socket servidor = new Socket(backend.getHost(), backend.getPuerto())) {

            // Dos hilos: cliente→servidor y servidor→cliente
            Thread t1 = new Thread(() -> transferir(cliente, servidor, "[C→S] "));
            Thread t2 = new Thread(() -> transferir(servidor, cliente, "[S→C] "));
            t1.start();
            t2.start();
            t1.join();
            t2.join();

        } catch (IOException | InterruptedException e) {
            // Si el backend falla mientras atendía, marcar como caído
            backend.marcarCaido();
            log("[FALLO] " + backend.getNombre() + " falló en mitad de sesión: " + e.getMessage());
        } finally {
            try { cliente.close(); } catch (IOException ignored) {}
        }
    }

    /** Copia bytes de 'origen' a 'destino' hasta que uno cierre. */
    private void transferir(Socket origen, Socket destino, String prefijo) {
        try {
            InputStream in  = origen.getInputStream();
            OutputStream out = destino.getOutputStream();
            byte[] buf = new byte[4096];
            int leidos;
            while ((leidos = in.read(buf)) != -1) {
                out.write(buf, 0, leidos);
                out.flush();
            }
        } catch (IOException ignored) {
            // Desconexión normal o fallo de backend
        } finally {
            try { origen.close();  } catch (IOException ignored) {}
            try { destino.close(); } catch (IOException ignored) {}
        }
    }

    // ---------------------------------------------------------------
    // Health-check
    // ---------------------------------------------------------------
    private void ejecutarHealthCheck() {
        List<ServerNode> todos = new ArrayList<>(principales);
        todos.addAll(respaldos);

        for (ServerNode nodo : todos) {
            boolean alcanzable = probarConexion(nodo.getHost(), nodo.getPuerto());

            if (alcanzable && !nodo.estaActivo()) {
                nodo.marcarActivo();
                log("[RECUPERADO] " + nodo.getNombre() + " volvió en línea.");
            } else if (!alcanzable && nodo.estaActivo()) {
                nodo.marcarCaido();
                log("[CAÍDO] " + nodo.getNombre() + " no responde.");
            }
        }
    }

    private boolean probarConexion(String host, int puerto) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, puerto), HEALTH_TIMEOUT_MS);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private void log(String msg) {
        System.out.println("[LB] " + msg);
    }

    public static void main(String[] args) throws IOException {
        new LoadBalancer().iniciar();
    }
}