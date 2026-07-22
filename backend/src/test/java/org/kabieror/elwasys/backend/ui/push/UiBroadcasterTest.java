package org.kabieror.elwasys.backend.ui.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.Command;
import com.vaadin.flow.shared.Registration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.kabieror.elwasys.backend.events.DeviceChangedEvent;
import org.kabieror.elwasys.backend.events.DomainEvent;
import org.kabieror.elwasys.backend.events.LocationChangedEvent;

/**
 * Reiner Unit-Test (kein Spring-Kontext) für {@link UiBroadcaster} - deckt die beiden in AP5
 * geforderten Verhalten ab: "Broadcaster verteilt an registrierte Listener" und "Abmelden
 * funktioniert" (siehe docs/kb/05-migration-plan.md, "Live-Updates zwischen Sessions"). Das Testen
 * von {@code @TransactionalEventListener} selbst (Springs eigener Mechanismus, AFTER_COMMIT)
 * ist NICHT Gegenstand dieses Tests - das prüft {@code DomainEventsIT} mit einem echten
 * Spring-Kontext. {@link UI#access(Command)} wird mit Mockito synchron ausgeführt simuliert -
 * ein echtes {@link UI} bräuchte eine gesperrte {@code VaadinSession}, die es außerhalb eines
 * echten Servlet-Containers nicht gibt (bewusst vermieden, siehe docs/kb/05-migration-plan.md,
 * Lizenz-Befund: kein Test rendert eine echte Vaadin-Route).
 */
class UiBroadcasterTest {

    /**
     * Baut ein Mockito-{@link UI}, dessen {@link UI#access(Command)} das übergebene
     * {@link Command} SYNCHRON auf dem aufrufenden Thread ausführt - genügt für diesen Test,
     * weil es hier nicht um Threading/Session-Locking geht, sondern nur darum, dass der
     * Broadcaster den richtigen Listener mit dem richtigen Ereignis aufruft.
     */
    private static UI mockUi() {
        UI ui = mock(UI.class);
        when(ui.access(any(Command.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, Command.class).execute();
            Future<Void> future = CompletableFuture.completedFuture(null);
            return future;
        });
        return ui;
    }

    @Test
    void deliversAnEventToARegisteredListener() {
        UiBroadcaster broadcaster = new UiBroadcaster();
        UI ui = mockUi();
        List<DomainEvent> received = new ArrayList<>();

        broadcaster.register(ui, received::add);

        DeviceChangedEvent event = new DeviceChangedEvent(42);
        broadcaster.onDomainEvent(event);

        assertThat(received).containsExactly(event);
    }

    @Test
    void deliversAnEventToAllRegisteredListeners() {
        UiBroadcaster broadcaster = new UiBroadcaster();
        List<DomainEvent> receivedByFirst = new ArrayList<>();
        List<DomainEvent> receivedBySecond = new ArrayList<>();

        broadcaster.register(mockUi(), receivedByFirst::add);
        broadcaster.register(mockUi(), receivedBySecond::add);

        DeviceChangedEvent event = new DeviceChangedEvent(7);
        broadcaster.onDomainEvent(event);

        assertThat(receivedByFirst).containsExactly(event);
        assertThat(receivedBySecond).containsExactly(event);
    }

    @Test
    void unregisteringStopsFurtherDelivery() {
        UiBroadcaster broadcaster = new UiBroadcaster();
        UI ui = mockUi();
        List<DomainEvent> received = new ArrayList<>();

        Registration registration = broadcaster.register(ui, received::add);
        assertThat(broadcaster.subscriberCount()).isEqualTo(1);

        registration.remove();
        assertThat(broadcaster.subscriberCount()).isEqualTo(0);

        broadcaster.onDomainEvent(new LocationChangedEvent(1));

        assertThat(received).isEmpty();
    }

    @Test
    void unregisteringOnlyRemovesTheGivenListener() {
        UiBroadcaster broadcaster = new UiBroadcaster();
        List<DomainEvent> receivedByFirst = new ArrayList<>();
        List<DomainEvent> receivedBySecond = new ArrayList<>();

        Registration firstRegistration = broadcaster.register(mockUi(), receivedByFirst::add);
        broadcaster.register(mockUi(), receivedBySecond::add);

        firstRegistration.remove();

        DeviceChangedEvent event = new DeviceChangedEvent(3);
        broadcaster.onDomainEvent(event);

        assertThat(receivedByFirst).isEmpty();
        assertThat(receivedBySecond).containsExactly(event);
    }
}
