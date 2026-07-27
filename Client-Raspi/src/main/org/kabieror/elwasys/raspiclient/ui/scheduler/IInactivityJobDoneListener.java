package org.kabieror.elwasys.raspiclient.ui.scheduler;

/**
 * Wird über das Ende eines Inaktivitäts-Auftrags benachrichtigt (siehe {@link InactivityJob}).
 *
 * @author Oliver Kabierschke
 */
public interface IInactivityJobDoneListener {
    /**
     * Wird aufgerufen, sobald ein Job nach Inaktivität ausgeführt wurde.
     *
     * @param future Die Statusanzeige des Jobs.
     */
    void onInactivityJobDone(InactivityFuture future);
}
