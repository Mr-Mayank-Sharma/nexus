package com.nexus.core.audit;

import com.nexus.core.service.SequenceService;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Id;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.event.spi.PostInsertEvent;
import org.hibernate.event.spi.PostInsertEventListener;
import org.hibernate.event.spi.PostUpdateEvent;
import org.hibernate.event.spi.PostUpdateEventListener;
import org.hibernate.persister.entity.EntityPersister;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Writes {@code entity_audit_log} rows for fields marked {@link AuditedField}, on insert and on every change. It works
 * on both databases because it is application code, not a trigger. Rows are written on the same connection and
 * transaction as the change, so a rolled-back change leaves no audit row.
 */
// Eager: the app starts beans lazily, and a listener nobody asks for would never register.
@Component("coreEntityAuditListener")
@Lazy(false)
public class EntityAuditListener implements PostInsertEventListener, PostUpdateEventListener {

    static final String STAMP_COLUMNS = "last_updated_stamp, last_updated_tx_stamp, created_stamp, created_tx_stamp";
    static final String STAMP_VALUES = "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP";

    private final EntityManagerFactory emf;
    private final JdbcTemplate jdbc;
    private final SequenceService sequences;
    private final Map<Class<?>, Set<String>> auditedByClass = new ConcurrentHashMap<>();

    public EntityAuditListener(EntityManagerFactory emf, JdbcTemplate jdbc, SequenceService sequences) {
        this.emf = emf;
        this.jdbc = jdbc;
        this.sequences = sequences;
    }

    @PostConstruct
    void register() {
        EventListenerRegistry registry = emf.unwrap(SessionFactoryImplementor.class).getServiceRegistry()
                .getService(EventListenerRegistry.class);
        registry.appendListeners(EventType.POST_INSERT, this);
        registry.appendListeners(EventType.POST_UPDATE, this);
    }

    @Override
    public void onPostInsert(PostInsertEvent event) {
        Set<String> audited = audited(event.getEntity().getClass());
        if (audited.isEmpty()) return;
        String[] names = event.getPersister().getPropertyNames();
        Object[] state = event.getState();
        for (int i = 0; i < names.length; i++) {
            if (audited.contains(names[i]) && state[i] != null) {
                write(event.getEntity(), names[i], null, state[i]);
            }
        }
    }

    @Override
    public void onPostUpdate(PostUpdateEvent event) {
        Set<String> audited = audited(event.getEntity().getClass());
        if (audited.isEmpty() || event.getOldState() == null) return;
        String[] names = event.getPersister().getPropertyNames();
        for (int i = 0; i < names.length; i++) {
            if (audited.contains(names[i]) && !Objects.equals(event.getOldState()[i], event.getState()[i])) {
                write(event.getEntity(), names[i], event.getOldState()[i], event.getState()[i]);
            }
        }
    }

    @Override
    public boolean requiresPostCommitHandling(EntityPersister persister) {
        return false;
    }

    private void write(Object entity, String field, Object oldValue, Object newValue) {
        List<String> pk = primaryKey(entity);
        jdbc.update("INSERT INTO entity_audit_log (audit_history_seq_id, changed_entity_name, changed_field_name, "
                        + "pk_primary_value, pk_secondary_value, pk_rest_combined_value, old_value_text, new_value_text, "
                        + "changed_date, changed_by_user_id, " + STAMP_COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, " + STAMP_VALUES + ")",
                sequences.nextId("EntityAuditLog"), entity.getClass().getSimpleName(), field,
                pk.isEmpty() ? "" : pk.get(0), pk.size() > 1 ? pk.get(1) : null,
                pk.size() > 2 ? String.join("::", pk.subList(2, pk.size())) : null,
                text(oldValue), text(newValue), LocalDateTime.now(), currentUser());
    }

    private Set<String> audited(Class<?> type) {
        return auditedByClass.computeIfAbsent(type, t -> {
            Set<String> names = new java.util.HashSet<>();
            for (Class<?> c = t; c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (f.isAnnotationPresent(AuditedField.class)) names.add(f.getName());
                }
            }
            return names;
        });
    }

    private static List<String> primaryKey(Object entity) {
        List<String> values = new ArrayList<>();
        for (Class<?> c = entity.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.isAnnotationPresent(Id.class)) {
                    try {
                        f.setAccessible(true);
                        values.add(String.valueOf(f.get(entity)));
                    } catch (IllegalAccessException e) {
                        values.add("?");
                    }
                }
            }
        }
        return values;
    }

    private static String text(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v);
        return s.length() > 4000 ? s.substring(0, 4000) : s;
    }

    private static String currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null || auth.getName() == null ? null : auth.getName().length() > 40 ? auth.getName().substring(0, 40) : auth.getName();
    }
}
