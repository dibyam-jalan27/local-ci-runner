package com.localci.model;

import java.util.List;

/**
 * Represents the notification configuration for a pipeline.
 *
 * YAML structure:
 * 
 * <pre>
 *   notify:
 *     on: ["failure", "success", "always"]
 *     slack:
 *       webhookUrl: "https://hooks.slack.com/..."
 *       messageTemplate: "Pipeline {pipeline} {status}"
 *     email:
 *       smtpHost: "smtp.gmail.com"
 *       smtpPort: 587
 *       username: "ci@example.com"
 *       password: "secret"
 *       recipient: "team@example.com"
 *     webhook:
 *       url: "https://example.com/webhook"
 * </pre>
 */
public class NotifyConfig {

    private List<String> on; // ["failure", "success", "always"]
    private SlackConfig slack;
    private EmailConfig email;
    private WebhookConfig webhook;

    public NotifyConfig() {
    }

    // ── Getters ──────────────────────────────────────────

    public List<String> getOn() {
        return on;
    }

    public SlackConfig getSlack() {
        return slack;
    }

    public EmailConfig getEmail() {
        return email;
    }

    public WebhookConfig getWebhook() {
        return webhook;
    }

    // ── Setters ──────────────────────────────────────────

    public void setOn(List<String> on) {
        this.on = on;
    }

    public void setSlack(SlackConfig slack) {
        this.slack = slack;
    }

    public void setEmail(EmailConfig email) {
        this.email = email;
    }

    public void setWebhook(WebhookConfig webhook) {
        this.webhook = webhook;
    }

    // ══════════════════════════════════════════════════════
    // Inner config classes
    // ══════════════════════════════════════════════════════

    public static class SlackConfig {
        private String webhookUrl;
        private String messageTemplate;

        public SlackConfig() {
        }

        public String getWebhookUrl() {
            return webhookUrl;
        }

        public void setWebhookUrl(String webhookUrl) {
            this.webhookUrl = webhookUrl;
        }

        public String getMessageTemplate() {
            return messageTemplate;
        }

        public void setMessageTemplate(String messageTemplate) {
            this.messageTemplate = messageTemplate;
        }
    }

    public static class EmailConfig {
        private String smtpHost;
        private int smtpPort;
        private String username;
        private String password;
        private String recipient;

        public EmailConfig() {
        }

        public String getSmtpHost() {
            return smtpHost;
        }

        public void setSmtpHost(String smtpHost) {
            this.smtpHost = smtpHost;
        }

        public int getSmtpPort() {
            return smtpPort;
        }

        public void setSmtpPort(int smtpPort) {
            this.smtpPort = smtpPort;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getRecipient() {
            return recipient;
        }

        public void setRecipient(String recipient) {
            this.recipient = recipient;
        }
    }

    public static class WebhookConfig {
        private String url;

        public WebhookConfig() {
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }
    }
}
