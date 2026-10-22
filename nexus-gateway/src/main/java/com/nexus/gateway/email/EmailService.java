package com.nexus.gateway.email;

import com.nexus.gateway.account.Accounts;
import com.nexus.gateway.log.RequestLog;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Customer emails: a template per client (and optionally per store) with {{name}} placeholders, sent through the
 * client's email account. SMTP covers SES, SendGrid, Postmark and Mailtrap; TEST_EMAIL keeps messages in memory so an
 * installation can test its notifications without sending anything.
 */
@Service
public class EmailService {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_.]+)\\s*}}");
    static final String TEST_EMAIL = "TEST_EMAIL";
    static final String DEFAULT_SMTP_PORT = "587";
    static final String CONNECT_TIMEOUT_MILLIS = "10000";
    static final String SEND_TIMEOUT_MILLIS = "20000";

    private final EmailTemplates emailTemplates;
    private final Accounts accounts;
    private final RequestLog requestLog;
    /** What TEST_EMAIL accounts "sent", oldest first. */
    private final List<Sent> testOutbox = Collections.synchronizedList(new ArrayList<>());

    public EmailService(EmailTemplates emailTemplates, Accounts accounts, RequestLog requestLog) {
        this.emailTemplates = emailTemplates;
        this.accounts = accounts;
        this.requestLog = requestLog;
    }

    public record Sent(String messageId, String clientId, String storeId, String to, String subject, String html) {
    }

    /** Sets a template (for one store when {@code storeId} is given, else for the client), replacing the earlier one. */
    public void putTemplate(String clientId, String storeId, String templateId, String subject, String bodyHtml) {
        if (templateId == null || templateId.isBlank() || subject == null || bodyHtml == null) {
            throw new IllegalArgumentException("A template needs an id, a subject and a body");
        }
        emailTemplates.put(clientId, storeId, templateId, subject, bodyHtml);
    }

    /** Renders the store's template (or the client's) with the data and sends it. Returns the message id. */
    public String send(String clientId, String storeId, String templateId, String to, Map<String, Object> data) {
        if (to == null || !to.contains("@")) {
            throw new IllegalArgumentException("'" + to + "' is not an email address");
        }
        String forStore = storeId == null ? "" : " for store " + storeId;
        EmailTemplates.Template template = emailTemplates.find(clientId, storeId, templateId)
                .orElseThrow(() -> new IllegalStateException("No email template '" + templateId + "' is set up" + forStore));
        String subject = render(template.subject(), data, false);
        String html = render(template.bodyHtml(), data, true);
        String provider = emailTemplates.emailProvider(clientId, storeId)
                .orElseThrow(() -> new IllegalStateException("No email account is set up" + forStore));
        Accounts.Account account = accounts.resolve(clientId, storeId, provider);
        return requestLog.timed(clientId, storeId, "EMAIL", provider, templateId + " to " + to,
                () -> deliver(account, clientId, storeId, to, subject, html));
    }

    /** Messages TEST_EMAIL accounts kept for this client, oldest first. */
    public List<Sent> testOutbox(String clientId) {
        synchronized (testOutbox) {
            return testOutbox.stream().filter(sent -> sent.clientId().equals(clientId)).toList();
        }
    }

    /** Fills {{name}} from the data; in HTML the values are escaped, so data can't inject markup. Unknown names stay empty. */
    static String render(String template, Map<String, Object> data, boolean html) {
        Matcher placeholder = PLACEHOLDER.matcher(template);
        StringBuilder rendered = new StringBuilder();
        while (placeholder.find()) {
            Object value = data == null ? null : data.get(placeholder.group(1));
            String text = value == null ? "" : String.valueOf(value);
            placeholder.appendReplacement(rendered, Matcher.quoteReplacement(html ? HtmlUtils.htmlEscape(text) : text));
        }
        placeholder.appendTail(rendered);
        return rendered.toString();
    }

    /** Sends through the account: kept in the test outbox for TEST_EMAIL, over SMTP otherwise. Returns the message id. */
    private String deliver(Accounts.Account account, String clientId, String storeId, String to, String subject, String html) {
        String messageId = UUID.randomUUID().toString();
        if (TEST_EMAIL.equals(account.provider())) {
            testOutbox.add(new Sent(messageId, clientId, storeId, to, subject, html));
            return messageId;
        }
        JavaMailSenderImpl sender = smtpSender(account);
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(account.setting("from"));
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            message.setHeader("X-Nexus-Message-Id", messageId);
            sender.send(message);
            return messageId;
        } catch (Exception failure) {
            throw new IllegalStateException("The email could not be sent: " + failure.getMessage(), failure);
        }
    }

    /** An SMTP sender from the account's settings (host, port, username, starttls) and its password secret. */
    private static JavaMailSenderImpl smtpSender(Accounts.Account account) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        String username = account.settings().get("username");
        sender.setHost(account.setting("host"));
        sender.setPort(Integer.parseInt(account.settings().getOrDefault("port", DEFAULT_SMTP_PORT)));
        sender.setUsername(username);
        sender.setPassword(account.secrets().get("password"));
        Properties properties = sender.getJavaMailProperties();
        properties.put("mail.smtp.auth", String.valueOf(username != null));
        properties.put("mail.smtp.starttls.enable", account.settings().getOrDefault("starttls", "true"));
        properties.put("mail.smtp.connectiontimeout", CONNECT_TIMEOUT_MILLIS);
        properties.put("mail.smtp.timeout", SEND_TIMEOUT_MILLIS);
        return sender;
    }
}
