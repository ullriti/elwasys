package org.kabieror.elwasys.raspiclient.ws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kabieror.elwasys.raspiclient.offline.OfflineIncidentOutbox;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * Regressionstest des Leerlauf-Wächters (siehe {@code TerminalWebSocketClient#checkLiveness}):
 * am 2026-09-29 blieb auf beiden Terminals die TCP-Verbindung {@code ESTABLISHED}, das Backend
 * hatte die Session wegen ausbleibender PONGs geschlossen, aber das Schließen erreichte das
 * Terminal nie - kein {@code afterConnectionClosed}, kein Reconnect, 11 Tage Stille.
 * <p>
 * Ohne Netz und ohne Sleeps: Uhrzeit wird übergeben, die Session ist ein Stub. Der geplante
 * Reconnect würde erst nach 5 s laufen und wird per {@code stop()} vorher verworfen.
 */
class TerminalWebSocketClientLivenessTest {

    @TempDir
    Path dir;

    private TerminalWebSocketClient client;

    @BeforeEach
    void setUp() {
        client = new TerminalWebSocketClient(null, "https://backend.invalid/", "token", "uid",
                new OfflineIncidentOutbox(dir.resolve("incidents.jsonl")));
    }

    @AfterEach
    void tearDown() {
        client.stop();
    }

    @Test
    void aConnectionThatKeepsHearingFromTheBackendIsLeftAlone() {
        StubSession s = new StubSession();
        client.afterConnectionEstablished(s.proxy);

        client.checkLiveness(System.nanoTime() + TerminalWebSocketClient.IDLE_TIMEOUT.minusSeconds(5).toNanos());

        assertSame(s.proxy, sessionOf(client), "session kept");
        assertFalse(client.isReconnectPending(), "no reconnect");
        assertEquals(0, s.closes.get());
    }

    @Test
    void aSilentConnectionIsDeclaredDeadClosedAndReconnectedWithoutAnyCloseCallback() throws Exception {
        StubSession s = new StubSession();
        client.afterConnectionEstablished(s.proxy);

        // Ausfallbild: es kommt NIE ein afterConnectionClosed - nur Stille.
        client.checkLiveness(System.nanoTime() + TerminalWebSocketClient.IDLE_TIMEOUT.plusSeconds(5).toNanos());

        assertEquals(null, sessionOf(client), "dead session dropped");
        assertTrue(client.isReconnectPending(), "reconnect scheduled");
        assertTrue(s.closed.await(5, TimeUnit.SECONDS), "dead session is closed best effort");
    }

    @Test
    void aMessageFromTheBackendResetsTheIdleClock() {
        StubSession s = new StubSession();
        client.afterConnectionEstablished(s.proxy);

        client.handleTextMessage(s.proxy, new TextMessage("{\"type\":\"PING\",\"id\":\"1\",\"payload\":{}}"));

        // Vom Verbindungsaufbau aus waere das laengst tot - das PING hat die Uhr zurueckgesetzt.
        client.checkLiveness(System.nanoTime() + TerminalWebSocketClient.IDLE_TIMEOUT.minusSeconds(1).toNanos());
        assertSame(s.proxy, sessionOf(client));
        assertFalse(client.isReconnectPending());
    }

    @Test
    void theLateCloseOfASupersededSessionDoesNotTriggerAnotherReconnect() {
        StubSession old = new StubSession();
        client.afterConnectionEstablished(old.proxy);
        client.checkLiveness(System.nanoTime() + TerminalWebSocketClient.IDLE_TIMEOUT.plusSeconds(5).toNanos());

        // Der Reconnect hat inzwischen eine neue Verbindung geliefert (in Produktion setzt
        // openConnection() beim Abschluss das Reconnect-Flag zurueck - hier nachgestellt).
        StubSession fresh = new StubSession();
        client.afterConnectionEstablished(fresh.proxy);
        ((java.util.concurrent.atomic.AtomicBoolean) fieldOf(client, "isReconnectRunning")).set(false);

        // Die alte Session meldet ihr Ende erst jetzt.
        client.afterConnectionClosed(old.proxy, CloseStatus.GOING_AWAY);

        assertSame(fresh.proxy, sessionOf(client), "the current session survives");
        assertFalse(client.isReconnectPending(), "no second reconnect");
    }

    @Test
    void aLateMessageOfASupersededSessionDoesNotMaskTheSilenceOfTheCurrentOne() {
        StubSession old = new StubSession();
        StubSession current = new StubSession();
        client.afterConnectionEstablished(current.proxy);

        // Eine abgeloeste Session liefert noch Frames - die laufende bleibt stumm.
        client.handleTextMessage(old.proxy, new TextMessage("{\"type\":\"PING\",\"id\":\"1\",\"payload\":{}}"));

        client.checkLiveness(System.nanoTime() + TerminalWebSocketClient.IDLE_TIMEOUT.plusSeconds(5).toNanos());
        assertEquals(null, sessionOf(client), "the silent current session is still declared dead");
        assertTrue(client.isReconnectPending());
    }

    @Test
    void theWatchdogDoesNothingAfterStop() {
        StubSession s = new StubSession();
        client.afterConnectionEstablished(s.proxy);
        client.stop();

        client.checkLiveness(System.nanoTime() + TerminalWebSocketClient.IDLE_TIMEOUT.plusSeconds(5).toNanos());

        assertFalse(client.isReconnectPending(), "no reconnect after stop");
    }

    @Test
    void aWatchdogDoesNothingBeforeAnyConnectionExists() {
        client.checkLiveness(System.nanoTime() + 3_600_000_000_000L);
        assertFalse(client.isReconnectPending());
    }

    private static Object fieldOf(TerminalWebSocketClient c, String name) {
        try {
            Field f = TerminalWebSocketClient.class.getDeclaredField(name);
            f.setAccessible(true);
            return f.get(c);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static WebSocketSession sessionOf(TerminalWebSocketClient c) {
        return (WebSocketSession) fieldOf(c, "session");
    }

    /** Minimaler Session-Stub: offen, schluckt Sendungen, zählt Close-Aufrufe. */
    private static final class StubSession {
        final AtomicInteger closes = new AtomicInteger();
        final CountDownLatch closed = new CountDownLatch(1);
        final WebSocketSession proxy = (WebSocketSession) Proxy.newProxyInstance(
                WebSocketSession.class.getClassLoader(), new Class<?>[] {WebSocketSession.class},
                (p, method, args) -> switch (method.getName()) {
                    case "isOpen" -> true;
                    case "close" -> {
                        closes.incrementAndGet();
                        closed.countDown();
                        yield null;
                    }
                    case "equals" -> p == args[0];
                    case "hashCode" -> System.identityHashCode(p);
                    case "toString" -> "StubSession";
                    default -> null;
                });
    }
}
