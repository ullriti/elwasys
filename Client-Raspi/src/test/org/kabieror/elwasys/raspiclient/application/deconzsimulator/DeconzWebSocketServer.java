package org.kabieror.elwasys.raspiclient.application.deconzsimulator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A minimal RFC 6455 WebSocket <em>server</em> used to fake the deCONZ event
 * stream in the client E2E test harness (see {@link DeconzSimulator}).
 * <p>
 * The real deCONZ gateway pushes device/sensor change events to connected
 * clients over a plain (unencrypted, unauthenticated) WebSocket; the
 * production client ({@code DeconzEventListener}, using Spring's
 * {@code StandardWebSocketClient} / the Tomcat WebSocket client
 * implementation pulled in transitively via {@code
 * spring-boot-starter-websocket}) only ever <em>receives</em> messages on
 * this connection - it never sends any application message of its own. That
 * means this fake server only has to implement:
 * <ol>
 *     <li>the opening HTTP handshake (RFC 6455 section 4.2.2), and</li>
 *     <li>writing unmasked text frames to the client.</li>
 * </ol>
 * Frames received from the client (there shouldn't be any beyond a possible
 * close frame) are simply drained and ignored.
 */
class DeconzWebSocketServer {

    private static final String WEBSOCKET_MAGIC = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final ServerSocket serverSocket;
    private final List<OutputStream> clients = new CopyOnWriteArrayList<>();
    private volatile boolean running = false;

    DeconzWebSocketServer() throws IOException {
        // Bind eagerly (port 0 = let the OS choose a free ephemeral port) so
        // the port is known and stable as soon as the simulator is
        // constructed - the deCONZ "GET config" response (which reports this
        // port to the client) may be served before start() is called.
        this.serverSocket = new ServerSocket(0);
    }

    int getPort() {
        return serverSocket.getLocalPort();
    }

    void start() {
        running = true;
        final Thread t = new Thread(this::acceptLoop, "deconz-sim-ws-accept");
        t.setDaemon(true);
        t.start();
    }

    void stop() {
        running = false;
        try {
            serverSocket.close();
        } catch (final IOException e) {
            logger.debug("Error closing the simulated deCONZ WebSocket server socket.", e);
        }
        for (final OutputStream os : clients) {
            try {
                os.close();
            } catch (final IOException ignored) {
                // best effort
            }
        }
        clients.clear();
    }

    /**
     * Sends a text frame to every currently connected client (there is
     * normally exactly one: the client-under-test).
     */
    void broadcastText(String message) {
        final byte[] frame = buildTextFrame(message.getBytes(StandardCharsets.UTF_8));
        for (final OutputStream os : clients) {
            try {
                synchronized (os) {
                    os.write(frame);
                    os.flush();
                }
            } catch (final IOException e) {
                clients.remove(os);
            }
        }
    }

    private void acceptLoop() {
        while (running) {
            try {
                final Socket socket = serverSocket.accept();
                handleClient(socket);
            } catch (final IOException e) {
                if (running) {
                    logger.warn("Error accepting a connection to the simulated deCONZ WebSocket server.", e);
                }
                break;
            }
        }
    }

    private void handleClient(Socket socket) {
        final Thread t = new Thread(() -> {
            OutputStream out = null;
            try {
                final InputStream in = socket.getInputStream();
                final String key = readHandshakeKey(in);
                if (key == null) {
                    socket.close();
                    return;
                }
                out = socket.getOutputStream();
                writeHandshakeResponse(out, key);
                clients.add(out);
                logger.info("A client connected to the simulated deCONZ WebSocket server.");

                // Drain (and ignore) anything the client sends. The real
                // DeconzEventListener never sends application messages on
                // this connection; this loop only exists to notice when the
                // client closes the socket.
                final byte[] buf = new byte[1024];
                while (running && in.read(buf) != -1) {
                    // ignored
                }
            } catch (final IOException e) {
                logger.debug("Simulated deCONZ WebSocket connection ended.", e);
            } finally {
                if (out != null) {
                    clients.remove(out);
                }
                try {
                    socket.close();
                } catch (final IOException ignored) {
                    // best effort
                }
            }
        }, "deconz-sim-ws-client");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Reads HTTP request lines from {@code in} until the blank line that
     * terminates the header block, returning the value of the
     * {@code Sec-WebSocket-Key} header (or {@code null} if the connection
     * closed before a complete header block / the key was found).
     */
    private static String readHandshakeKey(InputStream in) throws IOException {
        String key = null;
        String line;
        boolean sawAnyLine = false;
        while ((line = readLine(in)) != null) {
            sawAnyLine = true;
            if (line.isEmpty()) {
                break;
            }
            final int colon = line.indexOf(':');
            if (colon > 0) {
                final String name = line.substring(0, colon).trim();
                if (name.equalsIgnoreCase("Sec-WebSocket-Key")) {
                    key = line.substring(colon + 1).trim();
                }
            }
        }
        return sawAnyLine ? key : null;
    }

    /**
     * Reads a single CRLF (or bare LF) terminated line from {@code in},
     * without over-reading into the following WebSocket frame bytes -
     * {@link java.io.BufferedReader} would risk exactly that, since it
     * pulls a whole buffer's worth of bytes ahead from the underlying
     * stream.
     *
     * @return the line (without the line terminator), or {@code null} if the
     * stream ended before any byte of a new line was read.
     */
    private static String readLine(InputStream in) throws IOException {
        final ByteArrayOutputStream line = new ByteArrayOutputStream();
        int b;
        boolean any = false;
        while ((b = in.read()) != -1) {
            any = true;
            if (b == '\n') {
                break;
            }
            if (b != '\r') {
                line.write(b);
            }
        }
        if (!any) {
            return null;
        }
        return line.toString(StandardCharsets.US_ASCII);
    }

    private static void writeHandshakeResponse(OutputStream out, String key) throws IOException {
        final String accept = computeAcceptKey(key);
        final String response = "HTTP/1.1 101 Switching Protocols\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Accept: " + accept + "\r\n" +
                "\r\n";
        out.write(response.getBytes(StandardCharsets.US_ASCII));
        out.flush();
    }

    private static String computeAcceptKey(String key) throws IOException {
        try {
            final MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            final byte[] hash = sha1.digest((key + WEBSOCKET_MAGIC).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (final NoSuchAlgorithmException e) {
            throw new IOException("SHA-1 is not available.", e);
        }
    }

    /**
     * Builds a single, unmasked, final text frame (server-to-client frames
     * must not be masked per RFC 6455).
     */
    private static byte[] buildTextFrame(byte[] payload) {
        final ByteArrayOutputStream frame = new ByteArrayOutputStream();
        frame.write(0x81); // FIN=1, opcode=1 (text)
        final int len = payload.length;
        if (len <= 125) {
            frame.write(len);
        } else if (len <= 0xFFFF) {
            frame.write(126);
            frame.write((len >>> 8) & 0xFF);
            frame.write(len & 0xFF);
        } else {
            frame.write(127);
            for (int i = 7; i >= 0; i--) {
                frame.write((int) ((((long) len) >>> (8 * i)) & 0xFF));
            }
        }
        frame.write(payload, 0, payload.length);
        return frame.toByteArray();
    }
}
