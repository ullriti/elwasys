package org.kabieror.elwasys.raspiclient.application.deconzsimulator;

/**
 * In-memory state of a single simulated deCONZ "lights" resource (a switched
 * socket, from the client's point of view).
 */
class SimulatedLight {

    final String name;

    volatile boolean on = false;

    SimulatedLight(String name) {
        this.name = name;
    }
}
