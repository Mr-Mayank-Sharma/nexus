package com.nexus.gateway.api;

import com.nexus.gateway.account.Accounts;
import com.nexus.gateway.email.EmailService;
import com.nexus.gateway.log.RequestLog;
import com.nexus.gateway.security.Clients;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** For the people who run the gateway (header X-Admin-Token): clients, keys, accounts, templates, health. */
@RestController
@RequestMapping("/admin")
public class AdminApi {

    private final Clients clients;
    private final Accounts accounts;
    private final EmailService email;
    private final RequestLog log;

    public AdminApi(Clients clients, Accounts accounts, EmailService email, RequestLog log) {
        this.clients = clients;
        this.accounts = accounts;
        this.email = email;
        this.log = log;
    }

    public record NewClient(String name) {
    }

    public record AccountBody(String storeId, String provider, String environment, Map<String, String> settings, Map<String, String> secrets) {
    }

    public record TemplateBody(String storeId, String templateId, String subject, String bodyHtml) {
    }

    /** The API key is in this answer only; keep it in the client installation. */
    @PostMapping("/clients")
    public Clients.NewClient create(@RequestBody NewClient c) {
        return clients.create(c.name());
    }

    @PostMapping("/clients/{clientId}/rotate-key")
    public Map<String, String> rotate(@PathVariable String clientId) {
        return Map.of("apiKey", clients.rotate(clientId));
    }

    @PutMapping("/clients/{clientId}/accounts")
    public Map<String, String> account(@PathVariable String clientId, @RequestBody AccountBody a) {
        return Map.of("accountId", accounts.put(clientId, a.storeId(), a.provider(), a.environment(), a.settings(), a.secrets()));
    }

    @GetMapping("/clients/{clientId}/accounts")
    public List<Accounts.AccountView> accounts(@PathVariable String clientId) {
        return accounts.list(clientId);
    }

    @PutMapping("/clients/{clientId}/templates")
    public Map<String, String> template(@PathVariable String clientId, @RequestBody TemplateBody t) {
        email.putTemplate(clientId, t.storeId(), t.templateId(), t.subject(), t.bodyHtml());
        return Map.of("templateId", t.templateId());
    }

    @GetMapping("/clients/{clientId}/test-outbox")
    public List<EmailService.Sent> outbox(@PathVariable String clientId) {
        return email.testOutbox(clientId);
    }

    @GetMapping("/requests")
    public List<Map<String, Object>> requests(@RequestParam(required = false) String clientId, @RequestParam(defaultValue = "100") int limit) {
        return log.recent(clientId, Math.min(limit, 1000));
    }

    @GetMapping("/health")
    public List<Map<String, Object>> health(@RequestParam(defaultValue = "24") int hours) {
        return log.health(hours);
    }
}
