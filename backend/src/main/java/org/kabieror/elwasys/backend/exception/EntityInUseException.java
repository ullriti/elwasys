package org.kabieror.elwasys.backend.exception;

/**
 * Generische Sperre gegen das Löschen einer Stammdaten-Entität, die noch von einer anderen
 * Entität referenziert wird - Entsprechung der Lösch-Wächter im Alt-Portal (siehe
 * {@code Portal/.../views/ProgramsView#deleteProgram}: ein Programm, das noch mind. einem
 * Gerät zugeordnet ist, wird nicht gelöscht, sondern es erscheint eine Fehlermeldung mit der
 * Anzahl der betroffenen Geräte). Dieselbe Regel gilt sinngemäß für Standorte (referenziert
 * über {@code devices.location_id}, dort ohne {@code ON DELETE}-Klausel in der DB - siehe
 * kb/02-data-model.md) sowie, sinngemäß, für die letzte verbleibende Benutzergruppe (siehe
 * {@code UserGroupService#delete}: eine Gruppe kann nur gelöscht werden, wenn es eine andere
 * gibt, der ihre Benutzer zugewiesen werden können - Nachbildung von
 * {@code Common.UserGroup#delete}).
 */
public class EntityInUseException extends RuntimeException {

    public EntityInUseException(String message) {
        super(message);
    }
}
