/**
 *
 */
package org.kabieror.elwasys.common;

/**
 * Diese Ausnahme tritt ein, wenn ein Client sich auf einen Ort registrieren
 * möchte, an dem bereits ein anderer Client arbeitet.
 *
 * @author Oliver Kabierschke
 *
 */
public class LocationOccupiedException extends Exception {

    /**
     *
     */
    private static final long serialVersionUID = 1L;

    private final String uid;

    public LocationOccupiedException(String uid) {
        this.uid = uid;
    }

    /**
     * Die Identifikation des besetzenden Clients.
     * 
     * @return
     */
    public String getUid() {
        return this.uid;
    }
}
