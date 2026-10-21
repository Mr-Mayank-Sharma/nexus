package com.nexus.gateway.email;

import com.nexus.gateway.account.Accounts;
import com.nexus.gateway.log.RequestLog;
import jakarta.mail.internet.MimeMessage;
import org.springframework.jdbc.core.JdbcTemplate;
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

    private final JdbcTemplate jdbc;
    private final Accounts accounts;
    private final RequestLog log;
    private final List<Sent> testOutbox = Collections.synchronizedList(new ArrayList<>());

    public EmailService(JdbcTemplate jdbc, Accounts accounts, RequestLog log) {
        this.jdbc = jdbc;
        this.accounts = accounts;
        this.log = log;
    }

    public record Sent(String messageId, String clientId, String storeId, String to, String subject, String html) {
    }

    public void putTemplate(String clientId, String storeId, String templateId, String subject, String bodyHtml) {
        if (templateId == null || templateId.isBlank() || subject == null || bodyHtml == null) throw new IllegalArgumentException("A template needs an id, a subject and a body");
        jdbc.update("DELETE FROM email_template WHERE client_id = ? AND template_id = ? AND " + (storeId == null ? "store_id IS NULL" : "store_id = ?"),
                storeId == null ? new Object[]{clientId, templateId} : new Object[]{clientId, templateId, storeId});
        jdbc.update("INSERT INTO email_template (template_row_id, client_id, store_id, template_id, subject, body_html) VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID().toString(), clientId, storeId, templateId, subject, bodyHtml);
    }

    /** Renders the store's template (or the client's) with the data and sends it. Returns the message id. */
    public String send(String clientId, String storeId, String templateId, String to, Map<String, Object> data) {
        if (to == null || !to.contains("@")) throw new IllegalArgumentException("'" + to + "' is not an email address");
        List<Map<String, Object>> t = jdbc.queryForList("SELECT subject, body_html FROM email_template WHERE client_id = ? AND template_id = ? "
                + "AND (store_id = ? OR store_id IS NULL) ORDER BY CASE WHEN store_id IS NULL THEN 1 ELSE 0 END", clientId, templateId, storeId);
        if (t.isEmpty()) throw new IllegalStateException("No email template '" + templateId + "' is set up" + (storeId == null ? "" : " for store " + storeId));
        String subject = render((String) t.get(0).get("subject"), data, false);
        String html = render((String) t.get(0).get("body_html"), data, true);
        String provider = providerFor(clientId, storeId);
        Accounts.Account acct = accounts.resolve(clientId, storeId, provider);
        return log.timed(clientId, storeId, "EMAIL", provider, templateId + " to " + to, () -> deliver(acct, clientId, storeId, to, subject, html));
    }

    /** Messages TEST_EMAIL accounts kept, newest last. */
    public List<Sent> testOutbox(String clientId) {
        synchronized (testOutbox) {
            return testOutbox.stream().filter(s -> s.clientId().equals(clientId)).toList();
        }
    }

    /** Fills {{name}} from the data; in HTML the values are escaped, so data can't inject markup. Unknown names stay empty. */
    static String render(String template, Map<String, Object> data, boolean html) {
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            Object v = data == null ? null : data.get(m.group(1));
            String s = v == null ? "" : String.valueOf(v);
            m.appendReplacement(out, Matcher.quoteReplacement(html ? HtmlUtils.htmlEscape(s) : s));
        }
        m.appendTail(out);
        return out.toString();
    }

    private String providerFor(String clientId, String storeId) {
        List<String> p = jdbc.queryForList("SELECT provider FROM provider_account WHERE client_id = ? AND active = TRUE AND provider IN ('SMTP', 'TEST_EMAIL') "
                + "AND (store_id = ? OR store_id IS NULL) ORDER BY CASE WHEN store_id IS NULL THEN 1 ELSE 0 END", String.class, clientId, storeId);
        if (p.isEmpty()) throw new IllegalStateException("No email account is set up" + (storeId == null ? "" : " for store " + storeId));
        return p.get(0);
    }

    private String deliver(Accounts.Account a, String clientId, String storeId, String to, String subject, String html) {
        String id = UUID.randomUUID().toString();
        if ("TEST_EMAIL".equals(a.provider())) {
            testOutbox.add(new Sent(id, clientId, storeId, to, subject, html));
            return id;
        }
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(a.setting("host"));
        sender.setPort(Integer.parseInt(a.settings().getOrDefault("port", "587")));
        sender.setUsername(a.settings().get("username"));
        sender.setPassword(a.secrets().get("password"));
        Properties p = sender.getJavaMailProperties();
        p.put("mail.smtp.auth", String.valueOf(a.settings().get("username") != null));
        p.put("mail.smtp.starttls.enable", a.settings().getOrDefault("starttls", "true"));
        p.put("mail.smtp.connectiontimeout", "10000");
        p.put("mail.smtp.timeout", "20000");
        try {
            MimeMessage msg = sender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(msg, true, "UTF-8");
            h.setFrom(a.setting("from"));
            h.setTo(to);
            h.setSubject(subject);
            h.setText(html, true);
            msg.setHeader("X-Nexus-Message-Id", id);
            sender.send(msg);
            return id;
        } catch (Exception e) {
            throw new IllegalStateException("The email could not be sent: " + e.getMessage(), e);
        }
    }
}
