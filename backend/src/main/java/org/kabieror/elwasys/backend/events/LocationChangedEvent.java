package org.kabieror.elwasys.backend.events;

/**
 * Ein Standort wurde angelegt, bearbeitet oder gelöscht - veröffentlicht von {@link
 * org.kabieror.elwasys.backend.service.LocationService}.
 */
public record LocationChangedEvent(Integer locationId) implements DomainEvent {
}
