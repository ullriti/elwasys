package org.kabieror.elwasys.backend.events;

/**
 * Ein Programm wurde angelegt, bearbeitet oder gelöscht - veröffentlicht von {@link
 * org.kabieror.elwasys.backend.service.ProgramService}.
 */
public record ProgramChangedEvent(Integer programId) implements DomainEvent {
}
