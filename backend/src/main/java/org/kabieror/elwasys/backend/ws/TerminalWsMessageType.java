package org.kabieror.elwasys.backend.ws;

/**
 * Nachrichtentypen des Terminal-WebSocket-Protokolls (AP4, siehe kb/05-migration-plan.md und
 * kb/03-modules.md für die vollständige Protokoll-Doku). Fachliche Referenz für die
 * Fernwartungs-Nachrichten (Status/Logs/Restart): Common {@code maintenance/*} (Alt-Protokoll,
 * heute Portal-&gt;Client; das neue Protokoll läuft über dieselbe ausgehende
 * Terminal-&gt;Backend-Verbindung, die auch für den Kartenlogin/die Geräteliste genutzt wird).
 *
 * <p>Phase 2 (AP4) implementierte nur das Fundament: Verbindungsaufbau (HELLO/HELLO_ACK),
 * Heartbeat (PING/PONG) und ein Status-Anfrage/Antwort-Paar als Gerüst
 * (STATUS_REQUEST/STATUS_RESPONSE); LOG_REQUEST/LOG_RESPONSE/RESTART_REQUEST waren dort noch
 * rein reservierte Typen (jede Anfrage dieser Typen wurde serverseitig mit
 * {@code ERROR{reason:"not-implemented"}} beantwortet).
 *
 * <p><b>Phase 3 AP4</b> (siehe kb/05-migration-plan.md, Roadmap-Punkt "Fernwartung"):
 * {@code LOG_REQUEST}/{@code RESTART_REQUEST} werden jetzt tatsächlich verwendet - allerdings
 * PORTAL-INITIIERT (Server fragt das Terminal, nicht umgekehrt), vermittelt über
 * {@link TerminalMaintenanceService}. {@code LOG_RESPONSE} (fachliche Referenz
 * {@code GetLogResponse}, Payload {@code {"lines": [...]}}) sowie das neue
 * {@code RESTART_RESPONSE} (Bestätigung des Terminals, dass ein Neustart entgegengenommen
 * wurde - im Alt-Protokoll gab es dafür keine Entsprechung, {@code RestartAppRequest} wurde
 * dort "fire-and-forget" verschickt) werden vom {@link TerminalWebSocketHandler} an
 * wartende Anfragen des {@link TerminalMaintenanceService} zurückgeroutet. Ein tatsächlicher
 * Terminal-seitiger Handler für {@code LOG_REQUEST}/{@code RESTART_REQUEST} existiert noch
 * nicht (Alt-Clients sprechen bis Phase 4 weiterhin das Alt-TCP-Protokoll, siehe
 * kb/05-migration-plan.md) - eine Anfrage an ein nicht verbundenes Terminal schlägt daher
 * bewusst SOFORT serverseitig fehl ({@link TerminalMaintenanceService}), ohne eine
 * Nachricht zu senden.
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
    /** Server -&gt; Client (seit Phase 3 AP4): Bitte um den aktuellen Log-Inhalt. */
    LOG_REQUEST,
    /** Client -&gt; Server (seit Phase 3 AP4): Antwort auf {@link #LOG_REQUEST}, Payload {@code {"lines": [...]}}. */
    LOG_RESPONSE,
    /** Server -&gt; Client (seit Phase 3 AP4): Bitte, die Anwendung neu zu starten. */
    RESTART_REQUEST,
    /** Client -&gt; Server (seit Phase 3 AP4, neu): Bestätigung von {@link #RESTART_REQUEST}. */
    RESTART_RESPONSE,
    /** Server -&gt; Client oder Client -&gt; Server: Protokoll-/Verarbeitungsfehler. */
    ERROR
}
