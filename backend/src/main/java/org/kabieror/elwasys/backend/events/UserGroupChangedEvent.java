package org.kabieror.elwasys.backend.events;

/**
 * Eine Benutzergruppe wurde angelegt, bearbeitet, gelöscht, oder ihre zugelassenen
 * Standorte/Geräte/Programme wurden geändert - veröffentlicht von {@link
 * org.kabieror.elwasys.backend.service.UserGroupService}.
 */
public record UserGroupChangedEvent(Integer userGroupId) implements DomainEvent {
}
