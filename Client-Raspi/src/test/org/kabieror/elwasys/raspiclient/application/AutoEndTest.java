package org.kabieror.elwasys.raspiclient.application;

import org.kabieror.elwasys.raspiclient.model.ClientExecution;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

public class AutoEndTest {
    /**
     * Diese Klasse erwartet eine Eingabe von der Konsole, die als
     * Leistungsmessung an den ExecutionManager weiter gereicht wird. Die
     * Eingabe wird als Double-Wert gelesen.
     *
     * @throws InterruptedException
     */
    public static void main(String[] args) {
        final Thread mainThread = new Thread(() -> Main.main(new String[] { "-dry" }));
        mainThread.start();

        final BufferedReader userReader = new BufferedReader(new InputStreamReader(System.in));
        while (!Thread.interrupted()) {
            String input;
            try {
                input = userReader.readLine();
            } catch (final IOException e) {
                e.printStackTrace();
                break;
            }
            if (!input.matches("^\\d+(\\.\\d+)?$")) {
                break;
            }

            final Double value = Double.parseDouble(input);
            final List<ClientExecution> executions = ElwaManager.instance.getExecutionManager().getRunningExecutions();
            if (executions.isEmpty()) {
                System.out.println("Keine laufende Ausführung.");
            } else {
                ElwaManager.instance.getExecutionManager()
                        .onPowerMeasurementAvailable(executions.get(0), value);
            }
        }
        mainThread.interrupt();
        try {
            mainThread.join();
        } catch (final InterruptedException e) {
            e.printStackTrace();
        }
    }
}
