package com.nexus.oms.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.SerializationFeature;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Platform guards: every scheduled job is cluster-locked; timestamps serialise as UTC. */
class SchedulingGuardTest {

    @Test
    void everyScheduledJobHasAUniqueSchedulerLock() throws Exception {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Component.class));
        List<String> missing = new ArrayList<>();
        Set<String> names = new HashSet<>();
        int jobs = 0;
        for (BeanDefinition bd : scanner.findCandidateComponents("com.nexus.oms")) {
            for (Method m : Class.forName(bd.getBeanClassName()).getDeclaredMethods()) {
                if (!m.isAnnotationPresent(Scheduled.class)) continue;
                jobs++;
                SchedulerLock lock = m.getAnnotation(SchedulerLock.class);
                if (lock == null) {
                    missing.add(m.getDeclaringClass().getSimpleName() + "." + m.getName());
                } else {
                    assertTrue(names.add(lock.name()), "duplicate lock name " + lock.name());
                }
            }
        }
        assertTrue(jobs >= 15, "expected the scheduled jobs to be found, got " + jobs);
        assertTrue(missing.isEmpty(), "@Scheduled without @SchedulerLock runs on every replica: " + missing);
    }

    @Test
    void localDateTimeIsWrittenAsUtcInstant() throws Exception {
        ObjectMapper mapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .addModule(JacksonConfig.utcLocalDateTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
        LocalDateTime t = LocalDateTime.of(2026, 10, 9, 6, 30, 15);

        String json = mapper.writeValueAsString(t);

        assertEquals("\"2026-10-09T06:30:15Z\"", json);
        assertEquals(t, mapper.readValue(json, LocalDateTime.class), "values with Z still read back");
        assertEquals(t, mapper.readValue("\"2026-10-09T06:30:15\"", LocalDateTime.class), "and without Z");
    }
}
