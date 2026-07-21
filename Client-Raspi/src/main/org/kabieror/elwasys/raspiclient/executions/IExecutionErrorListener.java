package org.kabieror.elwasys.raspiclient.executions;

import org.kabieror.elwasys.raspiclient.model.ClientExecution;

/**
 * Diese Schnittstelle wird beim Ende einer Ausführung angewendet
 * 
 * @author Oliver Kabierschke
 *
 */
public interface IExecutionErrorListener {
    /**
     * Wird aufgerufen, sobald eine Ausführung fehlgeschlagen ist.
     * 
     * @param execution
     *            Die beendete Ausführung
     * @param exception
     *            Die Ausnahme, welche bei der Ausführung aufgetreten ist.
     */
    void onExecutionFailed(ClientExecution execution, Exception exception);
}
