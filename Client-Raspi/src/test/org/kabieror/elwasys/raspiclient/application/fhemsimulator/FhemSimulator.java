package org.kabieror.elwasys.raspiclient.application.fhemsimulator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Diese Klasse simuliert einen Fhem-Server.
 *
 * @author Oliver Kabierschke
 */
public class FhemSimulator {

    public static int communicationTimeout = 2000;
    static ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private static FhemSimulator instance;
    Logger logger = LoggerFactory.getLogger(this.getClass());
    private boolean shutdown = false;
    private ServerSocket serverSocket;

    private BlockingQueue<String> eventsQueue = new LinkedBlockingDeque<>();

    private Map<String, SimulatedDevice> devices = new HashMap<>();

    public FhemSimulator() {
        devices.put("wm1sw", new SwitchDevice());
        devices.put("wm2sw", new SwitchDevice());
        devices.put("wm3sw", new SwitchDevice());
        devices.put("wm4sw", new SwitchDevice());
    }

    /**
     * Starts the simulator programmatically on the given port. Intended for use
     * from tests (E2E) that need a fake fhem gateway. Returns once the server
     * socket is listening.
     */
    public void start(int port) throws IOException {
        this.serverSocket = new ServerSocket(port);
        this.startListen();
    }

    /**
     * Stops the simulator and releases the server socket.
     */
    public void stop() {
        this.shutdown = true;
        if (this.serverSocket != null) {
            try {
                this.serverSocket.close();
            } catch (IOException e) {
                this.logger.warn("Could not close the fhem simulator server socket.", e);
            }
        }
    }

    public static void main(String[] args) {
        instance = new FhemSimulator();
        try {
            instance.serverSocket = new ServerSocket(7072);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        instance.startListen();


        // Wait for commands
        System.out.println("Commands: block [dev], unblock [dev], set [dev] [state]");
        final BufferedReader userReader = new BufferedReader(new InputStreamReader(System.in));
        while (!Thread.interrupted()) {
            System.out.print("> ");
            String input;
            try {
                input = userReader.readLine();
            } catch (final IOException e) {
                e.printStackTrace();
                break;
            }

            String[] parts = input.split(" ");

            if (input.equals("quit")) {
                break;
            } else if (parts.length < 1) {
                System.out.println("Unknown command");
            } else if (parts[0].equals("block")) {
                // Set device unswitchable
                if (parts.length < 2) {
                    System.out.println("Missing device name to block device");
                    continue;
                }
                if (!instance.devices.containsKey(parts[1])) {
                    System.out.println("Unknown device");
                    continue;
                }
                if (instance.devices.get(parts[1]) instanceof SwitchDevice) {
                    SwitchDevice switchDevice = (SwitchDevice) instance.devices.get(parts[1]);
                    switchDevice.isSwitchable = false;
                } else {
                    System.out.println("Device is not switchable");
                }
            } else if (parts[0].equals("unblock")) {
                // Set device switchable
                if (parts.length < 2) {
                    System.out.println("Missing device name to block device");
                    continue;
                }
                if (!instance.devices.containsKey(parts[1])) {
                    System.out.println("Unknown device");
                    continue;
                }
                if (instance.devices.get(parts[1]) instanceof SwitchDevice) {
                    SwitchDevice switchDevice = (SwitchDevice) instance.devices.get(parts[1]);
                    switchDevice.isSwitchable = true;
                } else {
                    System.out.println("Device is not switchable");
                }
            } else if (parts[0].equals("set")) {
                // Set device state
                if (parts.length < 3) {
                    System.out.println("Missing parameters");
                    continue;
                }

                if (!instance.devices.containsKey(parts[1])) {
                    System.out.println("Unknown device");
                    continue;
                }
                if (instance.devices.get(parts[1]) instanceof SwitchDevice) {
                    SwitchDevice switchDevice = (SwitchDevice) instance.devices.get(parts[1]);

                    switch (parts[2]) {
                        case "on":
                            switchDevice.switchOn();
                            break;
                        case "off":
                            switchDevice.switchOff();
                            break;
                        default:
                            System.out.println(String.format("Unknown state '%1s'", parts[2]));
                            break;
                    }
                } else {
                    System.out.println("Device is not switchable");
                }
            } else if (parts[0].equals("powerevent")) {
                if (parts.length < 3) {
                    System.out.println("Missing parameters");
                    continue;
                }
                try {
                    instance.eventsQueue.put(" " + parts[1] + " power: " + parts[2]);
                } catch (InterruptedException e) {
                    System.err.println("Interrupted before event could be delivered.");
                }
            } else {
                System.out.println("Unknown command");
            }
        }

        try {
            instance.serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void startListen() {
        Thread t = new Thread(() -> {
            while (true) {
                try {
                    Socket clientSocker = serverSocket.accept();
                    this.startHandleConnection(clientSocker);
                } catch (IOException e) {
                    if (!this.shutdown) {
                        e.printStackTrace();
                    }
                    break;
                }
            }
        });
        t.setName("ListenThread");
        t.start();
    }

    private void startHandleConnection(Socket socket) {
        Thread t = new Thread(() -> {
            logger.info("Incoming connection from " + socket.getInetAddress().getHostAddress());
            final BufferedReader in;
            final PrintWriter out;

            Thread eventThread = null;
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
            while (socket.isConnected()) {
                String command;
                try {
                    command = in.readLine();
                } catch (IOException e) {
                    e.printStackTrace();
                    break;
                }
                if (command == null) {
                    try {
                        socket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                }
                if (command.isEmpty()) {
                    continue;
                }
                String[] commandParts = command.trim().split(" ");
                if (commandParts.length == 2 && commandParts[0].equals("inform")) {
                    switch (commandParts[1]) {
                        case "on":
                            // Starte Event-Benachrichtigung
                            if (eventThread == null) {
                                eventThread = new Thread(() -> {
                                    while (!Thread.interrupted()) {
                                        try {
                                            out.println(eventsQueue.take());
                                        } catch (InterruptedException e) {
                                            this.logger.debug("Events informer thread ending");
                                            break;
                                        }
                                    }
                                });
                                eventThread.setName("EventThread");
                                eventThread.start();
                            }
                            break;
                        case "off":
                            if (eventThread != null) {
                                eventThread.interrupt();
                            }
                            break;
                        default:
                            out.println(String.format("Unknown state '%1s'", commandParts[1]));
                            break;
                    }
                } else if (commandParts.length == 4 && commandParts[0].equals("get")) {
                    // Get parameter
                    String devName = commandParts[1];
                    if (!this.devices.containsKey(devName)) {
                        out.println(String.format("unknown device '%1s'", devName));
                        continue;
                    }

                    SimulatedDevice dev = this.devices.get(devName);
                    String paramName = commandParts[3];
                    String value = dev.getParameterValue(paramName);
                    if (value == null) {
                        out.println(String.format("unknown param '%1s'", paramName));
                        continue;
                    }
                    out.println(value);
                } else if (commandParts.length == 3 && commandParts[0].equals("set")) {
                    // Set state
                    String devName = commandParts[1];
                    if (!this.devices.containsKey(devName)) {
                        out.println(String.format("unknown device '%1s'", devName));
                        continue;
                    }

                    SimulatedDevice simDev = this.devices.get(devName);
                    if (!(simDev instanceof SwitchDevice)) {
                        out.println("this device cannot be switched");
                        continue;
                    }
                    SwitchDevice sw = (SwitchDevice) simDev;

                    String newState = commandParts[2];
                    switch (newState) {
                        case "on":
                            sw.switchOn();
                            break;
                        case "off":
                            sw.switchOff();
                            break;
                        default:
                            out.println(String.format("unknown state '%1s'", newState));
                            continue;
                    }
                } else if (commandParts.length == 1 && commandParts[0].equals("version")) {
                    out.println("# $Id: fhem.pl 6913 2014-11-08 10:32:44Z rudolfkoenig $");
                    out.println("# $Id: 10_CUL_HM.pm 6863 2014-11-02 09:04:57Z martinp876 $");
                    out.println("# $Id: 01_FHEMWEB.pm 6884 2014-11-04 22:03:52Z rudolfkoenig $");
                    out.println("# $Id: 92_FileLog.pm 6769 2014-10-15 17:03:30Z rudolfkoenig $");
                    out.println("# $Id: 00_HMLAN.pm 6471 2014-08-27 12:32:38Z martinp876 $");
                    out.println("# $Id: 99_SUNRISE_EL.pm 6765 2014-10-14 18:24:29Z rudolfkoenig $");
                    out.println("# $Id: 98_SVG.pm 6756 2014-10-12 13:13:26Z rudolfkoenig $");
                    out.println("# $Id: 99_Utils.pm 6660 2014-10-03 06:35:43Z rudolfkoenig $");
                    out.println("# $Id: 98_autocreate.pm 6505 2014-09-06 12:24:48Z rudolfkoenig $");
                    out.println("# $Id: 91_eventTypes.pm 6792 2014-10-19 16:03:13Z rudolfkoenig $");
                    out.println("# $Id: 91_notify.pm 6371 2014-08-07 05:33:37Z rudolfkoenig $");
                    out.println("# $Id: 98_telnet.pm 6611 2014-09-24 07:48:32Z rudolfkoenig $");
                } else {
                    out.println("unknown command");
                    System.out.println("Received unknown command: " + command);
                }
            }
            System.out.println("Closing connection from " + socket.getInetAddress().getHostAddress());
            if (eventThread != null) {
                eventThread.interrupt();
            }
        });
        t.setName("ClientConnectionThread");
        t.start();
    }
}
