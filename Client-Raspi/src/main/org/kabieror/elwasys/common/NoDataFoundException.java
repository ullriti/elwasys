package org.kabieror.elwasys.common;

/**
 * Diese Ausnahme wird geworfen, wenn angeforderte Daten nicht in der Datenbank
 * gefunden werden k�nnen
 * 
 * @author Oliver Kabierschke
 *
 */
public class NoDataFoundException extends Exception {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    public NoDataFoundException(String message) {
        super(message);
    }
}
