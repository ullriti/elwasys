package org.kabieror.elwasys.backend.ws;

/**
 * Nachrichtentypen des Terminal-WebSocket-Protokolls (AP4, siehe kb/05-migration-plan.md und
 * kb/03-modules.md für die vollständige Protokoll-Doku). Fachliche Referenz für die
 * Fernwartungs-Nachrichten (Status/Logs/Restart): Common {@code maintenance/*} (Alt-Protokoll,
 * heute Portal-&gt;Client; das neue Protokoll läuft über dieselbe ausgehende
 * Terminal-&gt;Backend-Verbindung, die auch für den Kartenlogin/die Geräteliste genutzt wird).
 *
 * <p>Phase 2 (AP4) implementiert nur das Fundament: Verbindungsaufbau (HELLO/HELLO_ACK),
 * Heartbeat (PING/PONG) und ein Status-Anfrage/Antwort-Paar als Gerüst
 * (STATUS_REQUEST/STATUS_RESPONSE). Log-/Restart-Nachrichten (fachliche Referenz:
 * {@code GetLogRequest}/{@code GetLogResponse}/{@code RestartAppRequest} in
 * {@code Common.maintenance}) folgen inhaltlich erst mit der vollen
 * Fernwartungs-Portierung in Phase 3/4, sind hier aber bereits als reservierte Typen
 * angelegt, damit das Nachrichtenformat nicht erneut brechend geändert werden muss.
 */
public enum TerminalWsMessageType {
    /** Client (Terminal) -&gt; Server: Verbindungsaufbau, Client-Version/Metadaten. */
    HELLO,
    /** Server -&gt; Client: Bestätigung + Standort-/Protokollinformationen. */
    HELLO_ACK,
    /** Beide Richtungen: Heartbeat-Anfrage. */
    PING,
    /** Beide Richtungen: Heartbeat-Antwort. */
    PONG,
    /** Beide Richtungen: Bitte um den aktuellen Terminal-Status (Gerüst, siehe Klassen-Javadoc). */
    STATUS_REQUEST,
    /** Beide Richtungen: Antwort auf {@link #STATUS_REQUEST}. */
    STATUS_RESPONSE,
    /** Reserviert für Phase 3/4 (fachliche Referenz: {@code GetLogRequest}). */
    LOG_REQUEST,
    /** Reserviert für Phase 3/4 (fachliche Referenz: {@code GetLogResponse}). */
    LOG_RESPONSE,
    /** Reserviert für Phase 3/4 (fachliche Referenz: {@code RestartAppRequest}). */
    RESTART_REQUEST,
    /** Server -&gt; Client oder Client -&gt; Server: Protokoll-/Verarbeitungsfehler. */
    ERROR
}
