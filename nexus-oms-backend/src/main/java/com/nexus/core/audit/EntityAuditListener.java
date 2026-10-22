package com.nexus.core.audit;

import com.nexus.core.CoreTransactional;
import org.springframework.beans.factory.annotation.Qualifier;
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
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
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
    private static final int MAX_VALUE_LENGTH = 4000;
    private static final int MAX_USER_ID_LENGTH = 40;

    private final EntityManagerFactory entityManagerFactory;
    private final JdbcTemplate jdbc;
    private final SequenceService sequenceService;
    /** The audited field names of each entity class, worked out once per class. */
    private final Map<Class<?>, Set<String>> auditedFieldsByClass = new ConcurrentHashMap<>();

    public EntityAuditListener(@Qualifier(CoreTransactional.ENTITY_MANAGER_FACTORY) EntityManagerFactory entityManagerFactory,
                               @Qualifier(CoreTransactional.JDBC) JdbcTemplate jdbc, SequenceService sequenceService) {
        this.entityManagerFactory = entityManagerFactory;
        this.jdbc = jdbc;
        this.sequenceService = sequenceService;
    }

    @PostConstruct
    void register() {
        EventListenerRegistry registry = entityManagerFactory.unwrap(SessionFactoryImplementor.class).getServiceRegistry()
                .getService(EventListenerRegistry.class);
        registry.appendListeners(EventType.POST_INSERT, this);
        registry.appendListeners(EventType.POST_UPDATE, this);
    }

    /** A new row: every audited field with a value is logged with no old value. */
    @Override
    public void onPostInsert(PostInsertEvent event) {
        Set<String> auditedFields = auditedFields(event.getEntity().getClass());
        if (auditedFields.isEmpty()) {
            return;
        }
        String[] propertyNames = event.getPersister().getPropertyNames();
        Object[] values = event.getState();
        for (int index = 0; index < propertyNames.length; index++) {
            if (auditedFields.contains(propertyNames[index]) && values[index] != null) {
                writeAuditRow(event.getEntity(), propertyNames[index], null, values[index]);
            }
        }
    }

    /** A changed row: every audited field whose value changed is logged with its old and new value. */
    @Override
    public void onPostUpdate(PostUpdateEvent event) {
        Set<String> auditedFields = auditedFields(event.getEntity().getClass());
        if (auditedFields.isEmpty() || event.getOldState() == null) {
            return;
        }
        String[] propertyNames = event.getPersister().getPropertyNames();
        Object[] oldValues = event.getOldState();
        Object[] newValues = event.getState();
        for (int index = 0; index < propertyNames.length; index++) {
            if (auditedFields.contains(propertyNames[index]) && !Objects.equals(oldValues[index], newValues[index])) {
                writeAuditRow(event.getEntity(), propertyNames[index], oldValues[index], newValues[index]);
            }
        }
    }

    @Override
    public boolean requiresPostCommitHandling(EntityPersister persister) {
        return false;
    }

    /** One audit row. The key goes in three columns: first part, second part, and the rest joined with "::". */
    private void writeAuditRow(Object entity, String fieldName, Object oldValue, Object newValue) {
        List<String> keyParts = primaryKeyParts(entity);
        String primaryValue = keyParts.isEmpty() ? "" : keyParts.get(0);
        String secondaryValue = keyParts.size() > 1 ? keyParts.get(1) : null;
        String restOfKey = keyParts.size() > 2 ? String.join("::", keyParts.subList(2, keyParts.size())) : null;
        jdbc.update("INSERT INTO entity_audit_log (audit_history_seq_id, changed_entity_name, changed_field_name, "
                        + "pk_primary_value, pk_secondary_value, pk_rest_combined_value, old_value_text, new_value_text, "
                        + "changed_date, changed_by_user_id, " + STAMP_COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, " + STAMP_VALUES + ")",
                sequenceService.nextId("EntityAuditLog"), entity.getClass().getSimpleName(), fieldName, primaryValue, secondaryValue, restOfKey,
                asText(oldValue), asText(newValue), LocalDateTime.now(), signedInUser());
    }

    private Set<String> auditedFields(Class<?> entityClass) {
        return auditedFieldsByClass.computeIfAbsent(entityClass, type -> {
            Set<String> fieldNames = new HashSet<>();
            for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
                for (Field field : current.getDeclaredFields()) {
                    if (field.isAnnotationPresent(AuditedField.class)) {
                        fieldNames.add(field.getName());
                    }
                }
            }
            return fieldNames;
        });
    }

    /** The values of the entity's @Id fields, in declaration order. */
    private static List<String> primaryKeyParts(Object entity) {
        List<String> parts = new ArrayList<>();
        for (Class<?> current = entity.getClass(); current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (!field.isAnnotationPresent(Id.class)) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    parts.add(String.valueOf(field.get(entity)));
                } catch (IllegalAccessException unreadable) {
                    parts.add("?");
                }
            }
        }
        return parts;
    }

    private static String asText(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return text.length() > MAX_VALUE_LENGTH ? text.substring(0, MAX_VALUE_LENGTH) : text;
    }

    private static String signedInUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        String name = authentication.getName();
        return name.length() > MAX_USER_ID_LENGTH ? name.substring(0, MAX_USER_ID_LENGTH) : name;
    }
}
