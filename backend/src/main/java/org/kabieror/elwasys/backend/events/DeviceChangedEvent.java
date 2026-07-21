package org.kabieror.elwasys.backend.events;

/**
 * Ein Gerät wurde angelegt, bearbeitet oder gelöscht - veröffentlicht von {@link
 * org.kabieror.elwasys.backend.service.DeviceService}.
 */
public record DeviceChangedEvent(Integer deviceId) implements DomainEvent {
}
