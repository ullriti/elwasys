package org.kabieror.elwasys.backend.ws;

import java.util.Map;
import java.util.UUID;

/**
 * Nachrichtenumschlag des Terminal-WebSocket-Protokolls (AP4, siehe kb/03-modules.md für die
 * vollständige Protokoll-Doku): JSON, mit explizitem Typ- und Versionsfeld, damit das Format
 * künftig erweitert werden kann, ohne bestehende Clients zu brechen (neue, unbekannte Felder
 * werden von {@link TerminalWebSocketHandler} ignoriert statt einen Fehler auszulösen).
 *
 * <pre>{@code
 * {
 *   "v": 1,
 *   "type": "HELLO",
 *   "id": "3f2a...-uuid",
 *   "payload": { "clientVersion": "0.4.0" }
 * }
 * }</pre>
 *
 * @param v       Protokollversion, siehe {@link #PROTOCOL_VERSION}
 * @param type    Nachrichtentyp, siehe {@link TerminalWsMessageType}
 * @param id      Korrelations-Id (vom Absender vergeben; eine Antwort trägt dieselbe Id wie
 *                die auslösende Anfrage, damit der Absender Anfrage/Antwort zuordnen kann)
 * @param payload typspezifische Nutzdaten, oder {@code null}/leer, wenn der Typ keine
 *                Nutzdaten trägt
 */
public record TerminalWsMessage(int v, TerminalWsMessageType type, String id, Map<String, Object> payload) {

    public static final int PROTOCOL_VERSION = 1;

    public static TerminalWsMessage of(TerminalWsMessageType type, Map<String, Object> payload) {
        return new TerminalWsMessage(PROTOCOL_VERSION, type, UUID.randomUUID().toString(), payload);
    }

    public static TerminalWsMessage inReplyTo(TerminalWsMessage request, TerminalWsMessageType type,
            Map<String, Object> payload) {
        String correlationId = request == null ? null : request.id();
        return new TerminalWsMessage(PROTOCOL_VERSION, type, correlationId, payload);
    }
}
