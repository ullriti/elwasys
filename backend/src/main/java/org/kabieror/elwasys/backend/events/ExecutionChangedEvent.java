package org.kabieror.elwasys.backend.events;

/**
 * Eine Programmausführung wurde angelegt, gestartet, beendet, abgebrochen, zurückgesetzt oder
 * gelöscht - veröffentlicht von {@link org.kabieror.elwasys.backend.service.ExecutionService}.
 * Wird sowohl vom Portal-UI (z.B. {@code ExpiredExecutionsDialog}) als auch - über {@link
 * org.kabieror.elwasys.backend.api.ExecutionController} - von Terminals über die REST-API
 * ausgelöst; da die Ereignis-Auslösung in der Service-Schicht liegt, ist das für dieses
 * Ereignis nicht unterscheidbar (und muss es auch nicht sein).
 *
 * @param executionId die Id der betroffenen Ausführung
 * @param deviceId    die Id des betroffenen Geräts (für das Admin-Dashboard, das je Gerät
 *                    gezielt nachladen kann statt die gesamte Seite neu aufzubauen, siehe
 *                    {@code AdminDashboardView})
 * @param userId      die Id des betroffenen Benutzers (kann ein virtueller Benutzer sein, siehe
 *                    {@code UserEntity})
 */
public record ExecutionChangedEvent(Integer executionId, Integer deviceId, Integer userId) implements DomainEvent {
}
