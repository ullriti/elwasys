package org.kabieror.elwasys.raspiclient.ws;

import java.util.Map;
import java.util.UUID;

/**
 * Nachrichtenumschlag des Terminal-WebSocket-Protokolls (Phase 4 AP5, Client-seitiges
 * Gegenstück zu {@code backend.ws.TerminalWsMessage} - siehe kb/03-modules.md). Feldnamen
 * ({@code v}/{@code type}/{@code id}/{@code payload}) sind bewusst identisch zum
 * Jackson-serialisierten Backend-Record gehalten, damit Gson (hier verwendet, siehe
 * {@link TerminalWebSocketClient}) die Nachrichten ohne benutzerdefinierte (De-)Serialisierer
 * lesen/schreiben kann.
 */
public final class TerminalWsMessage {

    public static final int PROTOCOL_VERSION = 1;

    private int v = PROTOCOL_VERSION;
    private TerminalWsMessageType type;
    private String id;
    private Map<String, Object> payload;

    /** Für Gson (Deserialisierung eingehender Nachrichten). */
    public TerminalWsMessage() {
    }

    public TerminalWsMessage(int v, TerminalWsMessageType type, String id, Map<String, Object> payload) {
        this.v = v;
        this.type = type;
        this.id = id;
        this.payload = payload;
    }

    /**
     * Baut eine neue, vom Terminal initiierte Nachricht mit einer frisch erzeugten
     * Korrelations-Id (siehe {@code backend.ws.TerminalWsMessage#of}).
     */
    public static TerminalWsMessage of(TerminalWsMessageType type, Map<String, Object> payload) {
        return new TerminalWsMessage(PROTOCOL_VERSION, type, UUID.randomUUID().toString(), payload);
    }

    /**
     * Baut eine Antwort auf {@code request}, die dieselbe Korrelations-Id trägt (die
     * Backend-Gegenstelle ordnet Anfrage/Antwort ausschließlich über dieses Feld zu, siehe
     * {@code TerminalMaintenanceService#completeIfPending}).
     */
    public static TerminalWsMessage inReplyTo(TerminalWsMessage request, TerminalWsMessageType type,
            Map<String, Object> payload) {
        String correlationId = request == null ? null : request.getId();
        return new TerminalWsMessage(PROTOCOL_VERSION, type, correlationId, payload);
    }

    public int getV() {
        return this.v;
    }

    public TerminalWsMessageType getType() {
        return this.type;
    }

    public String getId() {
        return this.id;
    }

    public Map<String, Object> getPayload() {
        return this.payload;
    }
}
