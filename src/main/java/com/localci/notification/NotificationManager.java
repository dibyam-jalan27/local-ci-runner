package com.localci.notification;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.localci.model.NotifyConfig;
import com.localci.model.RunReport;
import com.localci.tui.TerminalUI;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Properties;

/**
 * Sends pipeline run notifications via Slack, email, or generic webhooks.
 */
public class NotificationManager {

    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final TerminalUI ui;

    public NotificationManager(TerminalUI ui) {
        this.ui = ui;
    }

    /**
     * Sends notifications based on the notify config and pipeline result.
     */
    public void notify(NotifyConfig config, RunReport report, boolean pipelinePassed) {
        if (config == null)
            return;

        // Check if we should notify based on the "on" condition
        List<String> conditions = config.getOn();
        if (conditions == null || conditions.isEmpty()) {
            conditions = List.of("always");
        }

        boolean shouldNotify = false;
        for (String cond : conditions) {
            if ("always".equalsIgnoreCase(cond)) {
                shouldNotify = true;
                break;
            }
            if ("success".equalsIgnoreCase(cond) && pipelinePassed) {
                shouldNotify = true;
                break;
            }
            if ("failure".equalsIgnoreCase(cond) && !pipelinePassed) {
                shouldNotify = true;
                break;
            }
        }

        if (!shouldNotify) {
            log("Notification skipped (condition not met).");
            return;
        }

        // Slack
        if (config.getSlack() != null) {
            sendSlack(config.getSlack(), report);
        }

        // Email
        if (config.getEmail() != null) {
            sendEmail(config.getEmail(), report);
        }

        // Webhook
        if (config.getWebhook() != null) {
            sendWebhook(config.getWebhook(), report);
        }
    }

    // ── Slack ────────────────────────────────────────────

    private void sendSlack(NotifyConfig.SlackConfig slack, RunReport report) {
        try {
            String template = slack.getMessageTemplate();
            if (template == null || template.isBlank()) {
                template = "Pipeline *{pipeline}* finished with status: *{status}* (Duration: {duration})";
            }

            String message = template
                    .replace("{pipeline}", report.getPipelineName() != null ? report.getPipelineName() : "unknown")
                    .replace("{status}", report.getStatus() != null ? report.getStatus() : "unknown")
                    .replace("{duration}", formatDuration(report.getTotalDurationMs()));

            String jsonBody = "{\"text\": \"" + escapeJson(message) + "\"}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(slack.getWebhookUrl()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = HTTP.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                log("✓ Slack notification sent.");
            } else {
                log("[WARN] Slack notification failed (HTTP " + response.statusCode() + ")");
            }
        } catch (Exception e) {
            log("[WARN] Slack notification error: " + e.getMessage());
        }
    }

    // ── Email ────────────────────────────────────────────

    private void sendEmail(NotifyConfig.EmailConfig email, RunReport report) {
        try {
            Properties props = new Properties();
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.host", email.getSmtpHost());
            props.put("mail.smtp.port", String.valueOf(email.getSmtpPort()));

            jakarta.mail.Session session = jakarta.mail.Session.getInstance(props,
                    new jakarta.mail.Authenticator() {
                        @Override
                        protected jakarta.mail.PasswordAuthentication getPasswordAuthentication() {
                            return new jakarta.mail.PasswordAuthentication(
                                    email.getUsername(), email.getPassword());
                        }
                    });

            jakarta.mail.internet.MimeMessage msg = new jakarta.mail.internet.MimeMessage(session);
            msg.setFrom(new jakarta.mail.internet.InternetAddress(email.getUsername()));
            msg.setRecipients(jakarta.mail.Message.RecipientType.TO,
                    jakarta.mail.internet.InternetAddress.parse(email.getRecipient()));
            msg.setSubject("Pipeline Report: " + report.getPipelineName()
                    + " — " + report.getStatus());

            String body = "Pipeline: " + report.getPipelineName() + "\n"
                    + "Status: " + report.getStatus() + "\n"
                    + "Duration: " + formatDuration(report.getTotalDurationMs()) + "\n"
                    + "Start: " + report.getStartTime() + "\n"
                    + "End: " + report.getEndTime() + "\n";

            msg.setText(body);
            jakarta.mail.Transport.send(msg);

            log("✓ Email notification sent to " + email.getRecipient());
        } catch (Exception e) {
            log("[WARN] Email notification error: " + e.getMessage());
        }
    }

    // ── Generic Webhook ─────────────────────────────────

    private void sendWebhook(NotifyConfig.WebhookConfig webhook, RunReport report) {
        try {
            String jsonBody = GSON.toJson(report);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhook.getUrl()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = HTTP.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log("✓ Webhook notification sent to " + webhook.getUrl());
            } else {
                log("[WARN] Webhook notification failed (HTTP " + response.statusCode() + ")");
            }
        } catch (Exception e) {
            log("[WARN] Webhook notification error: " + e.getMessage());
        }
    }

    // ── Helpers ──────────────────────────────────────────

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "");
    }

    private String formatDuration(long ms) {
        if (ms < 1000)
            return ms + "ms";
        if (ms < 60_000)
            return String.format("%.1fs", ms / 1000.0);
        return String.format("%dm %ds", ms / 60_000, (ms % 60_000) / 1000);
    }

    private void log(String message) {
        if (ui != null) {
            ui.appendLog(message);
        } else {
            System.out.println("[NOTIFY] " + message);
        }
    }
}
