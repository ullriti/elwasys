package org.kabieror.elwasys.backend.notification;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.kabieror.elwasys.backend.support.AbstractBackendIT;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Beweist, dass der Benachrichtigungsdienst im vollen Spring-Kontext (ohne
 * {@code ELWASYS_NOTIFICATIONS_ENABLED} in der Umgebung, siehe application.yml) per Default
 * deaktiviert ist - kritisch, damit ein produktiv laufendes Backend niemals ungewollt
 * parallel zum Alt-Code Benachrichtigungen verschickt (siehe kb/05-migration-plan.md, AP5,
 * "Rahmenbedingungen"). Die eigentlichen Versand-/Nicht-Versand-Nachweise pro Kanal stehen
 * in {@link NotificationServiceEmailTest} und {@link NotificationServicePushoverTest}.
 */
class NotificationsPropertiesDefaultTest extends AbstractBackendIT {

    @Autowired
    private NotificationsProperties properties;

    @Test
    void notificationsAreDisabledByDefault() {
        assertThat(this.properties.isEnabled()).isFalse();
    }
}
