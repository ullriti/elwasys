package org.kabieror.elwasys.raspiclient.application;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.ServerSocket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ein simulierbarer "Backend nicht erreichbar"-Zustand für die Offline-E2E-Tests (Phase 4
 * AP6, siehe kb/05-migration-plan.md "Konzeptskizze: Offline-Buchungen am Terminal" und
 * kb/08-test-plan.md). Ein minimaler TCP-Weiterleitungs-Proxy (reines JDK, analog
 * {@code application.fhemsimulator.FhemSimulator}): der Client zeigt mit {@code backend.url}
 * auf diesen Proxy statt direkt auf das gemeinsam genutzte Test-Backend
 * ({@link TestBackend#url()}); solange {@link #isOnline()} gilt, werden alle Bytes 1:1 in
 * beide Richtungen durchgereicht (das Backend bemerkt vom Proxy nichts). Nach
 * {@link #goOffline()} werden NEUE Verbindungen sofort geschlossen (der Client sieht ein
 * "connection reset", genau wie bei einem echten Netz-/Backend-Ausfall) UND bereits offene
 * Verbindungen (inkl. eines vom {@code java.net.http}-Verbindungspool des Terminals
 * warmgehaltenen Keep-Alive-Sockets) werden aktiv geschlossen, damit ein Test nicht durch
 * eine zufällig noch funktionierende alte Verbindung flakt.
 */
class BackendProxy {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final String targetHost;
    private final int targetPort;

    private ServerSocket serverSocket;
    private ExecutorService executor;
    private final AtomicBoolean online = new AtomicBoolean(true);
    private final Set<Socket> openSockets = ConcurrentHashMap.newKeySet();

    BackendProxy(String targetHost, int targetPort) {
        this.targetHost = targetHost;
        this.targetPort = targetPort;
    }

    /**
     * Startet den Proxy auf einem freien lokalen Port und liefert dessen Basis-URL (z. B.
     * {@code http://localhost:54321/}) - für {@code backend.url} in der Client-Konfiguration.
     */
    String start() throws IOException {
        this.serverSocket = new ServerSocket(0);
        this.executor = Executors.newCachedThreadPool();
        this.executor.submit(this::acceptLoop);
        int port = this.serverSocket.getLocalPort();
        this.logger.info("BackendProxy listening on localhost:{} -> {}:{}", port, this.targetHost, this.targetPort);
        return "http://localhost:" + port + "/";
    }

    boolean isOnline() {
        return this.online.get();
    }

    /**
     * Simuliert einen Backend-Ausfall: neue Verbindungen werden sofort verweigert, bereits
     * offene aktiv geschlossen.
     */
    void goOffline() {
        this.online.set(false);
        for (Socket s : this.openSockets) {
            closeQuietly(s);
        }
        this.openSockets.clear();
        this.logger.info("BackendProxy: simulating backend outage (offline).");
    }

    /**
     * Beendet die Simulation - neue Verbindungen werden wieder normal weitergeleitet.
     */
    void goOnline() {
        this.online.set(true);
        this.logger.info("BackendProxy: backend reachable again (online).");
    }

    void stop() {
        this.online.set(false);
        try {
            if (this.serverSocket != null) {
                this.serverSocket.close();
            }
        } catch (IOException ignored) {
            // best effort
        }
        for (Socket s : this.openSockets) {
            closeQuietly(s);
        }
        if (this.executor != null) {
            this.executor.shutdownNow();
        }
    }

    private void acceptLoop() {
        while (!this.serverSocket.isClosed()) {
            Socket client;
            try {
                client = this.serverSocket.accept();
            } catch (IOException e) {
                return; // socket closed - stop() was called
            }
            if (!this.online.get()) {
                closeQuietly(client);
                continue;
            }
            this.openSockets.add(client);
            this.executor.submit(() -> handleConnection(client));
        }
    }

    private void handleConnection(Socket client) {
        Socket target = null;
        try {
            target = new Socket(this.targetHost, this.targetPort);
            this.openSockets.add(target);
            Socket finalTarget = target;
            Thread pumpToTarget = new Thread(() -> pump(client, finalTarget));
            Thread pumpToClient = new Thread(() -> pump(finalTarget, client));
            pumpToTarget.setName("BackendProxy-c2t");
            pumpToClient.setName("BackendProxy-t2c");
            pumpToTarget.start();
            pumpToClient.start();
            pumpToTarget.join();
            pumpToClient.join();
        } catch (IOException | InterruptedException e) {
            if (Thread.currentThread().isInterrupted()) {
                Thread.currentThread().interrupt();
            }
            // Erwartet, sobald goOffline()/stop() die Sockets schliesst - kein Fehler.
        } finally {
            closeQuietly(client);
            if (target != null) {
                closeQuietly(target);
            }
        }
    }

    private void pump(Socket from, Socket to) {
        try {
            InputStream in = from.getInputStream();
            OutputStream out = to.getOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                out.flush();
            }
        } catch (IOException e) {
            // Erwartet bei Verbindungsende/-abbruch.
        } finally {
            closeQuietly(to);
        }
    }

    private void closeQuietly(Socket s) {
        this.openSockets.remove(s);
        try {
            s.close();
        } catch (IOException ignored) {
            // best effort
        }
    }
}
