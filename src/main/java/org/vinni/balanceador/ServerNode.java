package org.vinni.balanceador;

import java.util.concurrent.atomic.AtomicBoolean;


public class ServerNode {

    public enum Tipo { PRINCIPAL, RESPALDO }

    private final String nombre;
    private final String host;
    private final int puerto;
    private final Tipo tipo;
    private final AtomicBoolean activo = new AtomicBoolean(true);

    public ServerNode(String nombre, String host, int puerto, Tipo tipo) {
        this.nombre = nombre;
        this.host   = host;
        this.puerto = puerto;
        this.tipo   = tipo;
    }

    public boolean estaActivo()  { return activo.get(); }
    public void marcarActivo()   { activo.set(true);  }
    public void marcarCaido()    { activo.set(false); }

    public String getNombre() { return nombre; }
    public String getHost()   { return host;   }
    public int    getPuerto() { return puerto; }
    public Tipo   getTipo()   { return tipo;   }
}