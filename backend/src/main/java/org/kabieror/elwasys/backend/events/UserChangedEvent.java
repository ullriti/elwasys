package org.kabieror.elwasys.backend.events;

/**
 * Ein Benutzer wurde angelegt, bearbeitet, (weich) gelöscht oder hat seine
 * Selbstbedienungs-Einstellungen geändert - veröffentlicht von {@link
 * org.kabieror.elwasys.backend.service.UserService}. {@code userId} ist die Id des
 * betroffenen Benutzers.
 */
public record UserChangedEvent(Integer userId) implements DomainEvent {
}
