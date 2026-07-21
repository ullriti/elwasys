package org.kabieror.elwasys.backend.events;

/**
 * Das Guthaben eines Benutzers hat sich geändert (Einzahlung, Auszahlung oder Bezahlung einer
 * Programmausführung) - veröffentlicht von {@link
 * org.kabieror.elwasys.backend.service.CreditService}. {@code userId} ist die Id des
 * betroffenen Benutzers (nie eines virtuellen Benutzers - siehe
 * {@link org.kabieror.elwasys.backend.service.CreditService#payExecution}, das für virtuelle
 * Benutzer gar keine Buchung anlegt).
 */
public record CreditChangedEvent(Integer userId) implements DomainEvent {
}
