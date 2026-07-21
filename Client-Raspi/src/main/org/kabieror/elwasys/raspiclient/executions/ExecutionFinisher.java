package org.kabieror.elwasys.raspiclient.executions;

import java.io.IOException;
import java.nio.charset.Charset;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.concurrent.ScheduledFuture;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.apache.commons.mail.EmailException;
import org.kabieror.elwasys.common.Execution;
import org.kabieror.elwasys.raspiclient.application.ElwaManager;
import org.kabieror.elwasys.raspiclient.devices.DevicePowerState;
import org.kabieror.elwasys.raspiclient.devices.IDevicePowerManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import net.pushover.client.MessagePriority;
import net.pushover.client.PushoverException;
import net.pushover.client.PushoverMessage;
import net.pushover.client.PushoverRestClient;
import net.pushover.client.Status;

/**
 * Diese Klasse führt die bei der Beendigung einer Programmausführung notwendigen Operationen aus
 *
 * @author Oliver Kabierschke
 */
class ExecutionFinisher implements Runnable {
    /**
     *
     */
    private final ExecutionManager executionManager;

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final Gson gson = new Gson();

    private final HttpClient httpClient = HttpClient.newHttpClient();

    private final Object lock = new Object();

    private final Execution e;

    private ScheduledFuture<?> future;

    private Boolean executed = false;

    private boolean aborted = false;

    private IDevicePowerManager devicePowerManager;

    ExecutionFinisher(ExecutionManager executionManager, Execution e, IDevicePowerManager devicePowerManager) {
        this.executionManager = executionManager;
        this.e = e;
        this.devicePowerManager = devicePowerManager;
    }

    @Override
    public void run() {
        synchronized (this.lock) {
            synchronized (e.getDevice()) {
                if (this.executed) {
                    return;
                }
                try {
                    this.executeAction();
                    this.executed = true;
                } catch (final Exception e) {
                    this.logger.error("Execution finisher failed.", e);
                    for (final IExecutionErrorListener l : this.executionManager.errorListeners) {
                        l.onExecutionFailed(this.e, e);
                    }
                }
            }
        }
    }

    void abort() {
        this.aborted = true;
        this.run();
    }

    /**
     * Versucht das erneute Ausführen der Fertigstellung einer
     * Programmausführung
     */
    void retry() throws SQLException, IOException, InterruptedException, FhemException {
        this.executeAction();
    }

    private void executeAction() throws SQLException, IOException, InterruptedException, FhemException {
        this.logger.info("[" + this.e.getDevice().getName() + "] Stopping execution " + this.e.getId());
        this.logger.info("[" + this.e.getDevice().getName() + "] User: " + this.e.getUser().getName());
        this.logger.info("[" + this.e.getDevice().getName() + "] Total time: " + this.e.getElapsedTimeString());

        // Breche geplante Ausführung ab, falls nicht von dieser
        // gestartet
        if (this.future != null) {
            this.future.cancel(false);
        }

        // Breche geplante automatische Stops ab
        if (this.executionManager.plannedStops.containsKey(this.e)) {
            this.executionManager.plannedStops.get(this.e).cancel(false);
        }

        // Schalte den Strom der Maschine aus
        try {
            this.devicePowerManager.setDevicePowerState(this.e.getDevice(), DevicePowerState.OFF);
        } catch (final IOException | InterruptedException | FhemException e1) {
            this.logger.error("[" + this.e.getDevice().getName() + "] Could not power off the device.", e1);
            throw e1;
        }

        // Informiere Ausführung über dessen Ende
        try {
            this.e.stop();
        } catch (final SQLException e1) {
            this.logger.error("[" + this.e.getDevice().getName() + "] Could not stop the execution.", e1);
            throw e1;
        }

        // Informiere Gerät über Ende der Ausführung
        this.e.getDevice().onExecutionEnded();

        // Veranlasse Benutzer zum Zahlen
        try {
            this.e.getUser().payExecution(this.e);
        } catch (final SQLException e1) {
            this.logger.error("[" + this.e.getDevice().getName() + "] User " + this.e.getUser().getName() +
                    " could not pay the execution.", e1);
            throw e1;
        }

        // Ausführung aus der Liste entfernen
        if (this.executionManager.executionFinishers.containsKey(this.e)) {
            this.executionManager.executionFinishers.remove(this.e);
        }

        if (this.executionManager.plannedStops.containsKey(this.e)) {
            this.executionManager.plannedStops.remove(this.e);
        }

        // Informiere alle Listener über das Ende der Programmausfürung
        for (final IExecutionFinishedListener l : this.executionManager.finishListeners) {
            l.onExecutionFinished(this.e);
        }

        // Bereite Benachrichtigungs-Email vor
        String notificationTitle;
        String notificationMessageShort;
        String notificationMessageLong;
        if (this.aborted) {
            notificationTitle = "Waschvorgang abgebrochen!";
            notificationMessageShort =
                    "Der Waschvorgang auf " + this.e.getDevice().getName() + " wurde abgebrochen.";
            notificationMessageLong = "Hallo " + this.e.getUser().getName() + ",\n\n dein Waschvorgang auf " +
                    this.e.getDevice().getName() + " wurde gerade abgebrochen.\n" + "Uhrzeit: " +
                    LocalDateTime.now().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)) +
                    "\n\n--\nelwasys";
        } else {
            notificationTitle = this.e.getDevice().getName() + " ist fertig!";
            notificationMessageShort =
                    this.e.getDevice().getName() + " ist fertig. Bitte entferne die Wäsche umgehend.";
            notificationMessageLong =
                    "Hallo " + this.e.getUser().getName() + ",\n\n" + this.e.getDevice().getName() +
                            " ist gerade fertig.\n" + "Uhrzeit: " +
                            LocalDateTime.now().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)) +
                            "\n" + "Bitte entferne die Wäsche umgehend.\n\n--\nelwasys";
        }
        notificationTitle = new String(notificationTitle.getBytes(), Charset.defaultCharset());
        notificationMessageLong = new String(notificationMessageLong.getBytes(), Charset.defaultCharset());
        notificationMessageShort = new String(notificationMessageShort.getBytes(), Charset.defaultCharset());

        // Sende Benachrichtigungs-Email
        if (this.e.getUser().getEmailNotification()) {
            try {
                ElwaManager.instance.getUtilities()
                        .sendEmail(notificationTitle, notificationMessageLong, this.e.getUser());
                this.logger.debug("Sent notification to " + this.e.getUser().getEmail());
            } catch (final EmailException e1) {
                this.logger.error("Could not send the notification mail.", e1);
            }
        } else {
            this.logger.debug("User is not to be notified.");
        }

        // Sende Pushover-Benachrichtigung
        if (this.e.getUser().getPushoverUserKey() != null && !this.e.getUser().getPushoverUserKey().isEmpty()) {
            try {
                final PushoverRestClient client = new PushoverRestClient();
                final Status result = client.pushMessage(PushoverMessage
                        .builderWithApiToken(ElwaManager.instance.getConfigurationManager().getPushoverApiToken())
                        .setUserId(this.e.getUser().getPushoverUserKey()).setMessage(notificationMessageShort)
                        .setPriority(MessagePriority.HIGH).setTitle(notificationTitle)
                        .setUrl("http://waschportal.hilaren.de").setTitleForURL("Waschportal").build());
                this.logger.debug("Sent push notification. Status: " + result.getStatus());
            } catch (final PushoverException e1) {
                this.logger.error("Could not send push notification.", e1);
            }
        }

        // Sende elwaApp-Pushbenachrichtigung
        if (this.e.getUser().isPushEnabled()
                && this.e.getUser().getPushIonicId() != null
                && !this.e.getUser().getPushIonicId().isEmpty()) {
            try {
                JsonObject notification = new JsonObject();
                notification.addProperty("title", notificationTitle);
                notification.addProperty("message", notificationMessageShort);
                JsonArray userIds = new JsonArray();
                userIds.add(this.e.getUser().getPushIonicId());
                JsonObject requestBody = new JsonObject();
                requestBody.add("user_ids", userIds);
                requestBody.addProperty("profile", "dev");
                requestBody.add("notification", notification);

                HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.ionic.io/push/notifications"))
                        .header("PROFILE_TAG", "dev")
                        .header("Authorization", "Bearer " + ElwaManager.instance.getConfigurationManager().getIonicApiToken())
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(this.gson.toJson(requestBody)))
                        .build();
                HttpResponse<String> jsonResponse = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (jsonResponse.statusCode() > 299) {
                    this.logger.error("Could not send ionic notification. Status: " + jsonResponse.statusCode() +
                            "\n" + jsonResponse.body());
                } else {
                    this.logger.debug("Sent ionic notification. " + jsonResponse.statusCode());
                }
            } catch (final IOException | InterruptedException e) {
                this.logger.error("Could not send ionic notification.", e);
            }
        }

    }

    void setScheduledFuture(ScheduledFuture<?> future) {
        this.future = future;
    }
}