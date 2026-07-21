package org.kabieror.elwasys.backend.events;

/**
 * Gemeinsame Basis aller fachlichen Ereignisse, die die Services (Stammdaten-CRUD,
 * Guthaben-Buchungen, Execution-Lebenszyklus) nach jeder erfolgreichen Änderung veröffentlichen
 * (Phase 3 AP5, siehe kb/05-migration-plan.md). Fachlicher Nachfolger der
 * {@code Portal/.../events/I*UpdatedEventListener}-Interfaces des Alt-Portals - dort allerdings
 * nur ein reiner Same-Session-Callback (ein Dialog/Fenster benachrichtigt sein aufrufendes
 * Fenster direkt, kein Vaadin-{@code @Push}, siehe {@link org.kabieror.elwasys.backend.ui.push.UiBroadcaster}
 * Javadoc für den Befund). Diese Ereignisse werden dagegen über
 * {@link org.springframework.context.ApplicationEventPublisher} publiziert und über
 * {@link org.kabieror.elwasys.backend.ui.push.UiBroadcaster} an ALLE offenen Vaadin-Sessions
 * verteilt - echte Cross-Session-Live-Updates.
 *
 * <p><b>Wichtig</b>: die Auslösung liegt bewusst in der Service-Schicht (nicht in der UI), damit
 * auch Änderungen, die über die REST-API von Terminals hereinkommen (z.B.
 * {@link org.kabieror.elwasys.backend.api.ExecutionController}, das
 * {@link org.kabieror.elwasys.backend.service.ExecutionService}/
 * {@link org.kabieror.elwasys.backend.service.CreditService} aufruft), dieselben Ereignisse
 * feuern wie eine Änderung über das Portal-UI.
 *
 * <p>{@code sealed}, damit die vollständige Ereignis-Inventur an einer Stelle sichtbar bleibt -
 * neue Ereignisarten werden hier bewusst explizit ergänzt statt implizit irgendwo im Code zu
 * entstehen.
 */
public sealed interface DomainEvent permits UserChangedEvent, UserGroupChangedEvent, DeviceChangedEvent,
        ProgramChangedEvent, LocationChangedEvent, CreditChangedEvent, ExecutionChangedEvent {
}
