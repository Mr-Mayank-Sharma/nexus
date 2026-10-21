package com.nexus.core.job;

import com.nexus.core.model.service.ServiceJob;
import com.nexus.core.model.service.ServiceJobParameter;
import com.nexus.core.model.service.ServiceJobRun;
import com.nexus.core.model.service.ServiceJobRunLock;
import com.nexus.core.service.CoreClock;
import com.nexus.core.service.SequenceService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.InetAddress;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Scheduled jobs as data (Moqui's {@code service_job}): each job names a handler bean and a cron expression, and can be
 * paused without a release. Every few seconds the runner starts the jobs that are due. A job runs on one server at a
 * time: the run takes the job's {@code service_job_run_lock} row until it finishes or the lock expires
 * ({@code expire_lock_time} minutes, for a server that died mid-run). Every run is a {@code service_job_run} with its
 * results or its error. Off with {@code nexus.core.jobs.enabled=false} (the tests turn it off).
 */
@Service("coreJobService")
public class JobService {

    private static final Logger log = LoggerFactory.getLogger(JobService.class);

    private final EntityManager em;
    private final SequenceService sequences;
    private final ApplicationContext context;
    private final TransactionTemplate tx;
    private final boolean enabled;
    private final int keepDays;
    private final String host;
    private volatile LocalDateTime lastPrune = LocalDateTime.MIN;

    public JobService(EntityManager em, SequenceService sequences, ApplicationContext context, PlatformTransactionManager tm,
                      @Value("${nexus.core.jobs.enabled:true}") boolean enabled, @Value("${nexus.core.jobs.keep-days:7}") int keepDays) {
        this.em = em;
        this.sequences = sequences;
        this.context = context;
        this.tx = new TransactionTemplate(tm);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.enabled = enabled;
        this.keepDays = keepDays;
        this.host = hostName();
    }

    public record Run(String jobRunId, String jobName, boolean hasError, String results, String errors) {
    }

    @Scheduled(fixedDelayString = "${nexus.core.jobs.tick-ms:5000}", initialDelayString = "${nexus.core.jobs.initial-delay-ms:30000}")
    public void tick() {
        if (!enabled) return;
        LocalDateTime now = CoreClock.now();
        if (lastPrune.isBefore(now.minusHours(1))) {
            lastPrune = now;
            prune(now.minusDays(keepDays));
        }
        for (String job : due(now)) {
            try {
                runJob(job, null);
            } catch (RuntimeException e) {
                log.error("Job {} could not start: {}", job, e.getMessage(), e);
            }
        }
    }

    /** Deletes run history older than the cutoff (a job every 10 seconds writes 8,640 runs a day). */
    public int prune(LocalDateTime before) {
        Integer n = tx.execute(s -> em.createQuery("DELETE FROM ServiceJobRun r WHERE r.startTime < :b").setParameter("b", before).executeUpdate());
        return n == null ? 0 : n;
    }

    /** Jobs not paused, inside their dates, whose next cron time after the last run has come. */
    public List<String> due(LocalDateTime now) {
        return tx.execute(s -> em.createQuery("SELECT j FROM ServiceJob j WHERE (j.paused IS NULL OR j.paused = false) ORDER BY j.priority, j.jobName",
                ServiceJob.class).getResultList().stream().filter(j -> isDue(j, now)).map(ServiceJob::getJobName).toList());
    }

    boolean isDue(ServiceJob j, LocalDateTime now) {
        if (j.getFromDate() != null && j.getFromDate().isAfter(now)) return false;
        if (j.getThruDate() != null && !j.getThruDate().isAfter(now)) return false;
        if (j.getCronExpression() == null || j.getCronExpression().isBlank()) return false;
        ServiceJobRunLock lock = em.find(ServiceJobRunLock.class, j.getJobName());
        if (lock != null && lock.getLockedUntil() != null && lock.getLockedUntil().isAfter(now)) return false;
        LocalDateTime last = lock == null ? null : lock.getLastRunTime();
        if (last == null) return true;
        LocalDateTime next = CronExpression.parse(j.getCronExpression()).next(last.atOffset(ZoneOffset.UTC).toLocalDateTime());
        return next != null && !next.isAfter(now);
    }

    /** Runs a job now (a person asked, or it is due), unless another server holds it. Empty when it was already running. */
    public Optional<Run> runJob(String jobName, String userId) {
        LocalDateTime start = CoreClock.now();
        String runId = tx.execute(s -> {
            ServiceJob j = em.find(ServiceJob.class, jobName);
            if (j == null) throw new IllegalArgumentException("Unknown job " + jobName);
            ServiceJobRunLock lock = em.find(ServiceJobRunLock.class, jobName, LockModeType.PESSIMISTIC_WRITE);
            if (lock == null) {
                lock = new ServiceJobRunLock();
                lock.setJobName(jobName);
                em.persist(lock);
            } else if (lock.getLockedUntil() != null && lock.getLockedUntil().isAfter(start)) {
                return null;   // running elsewhere
            }
            ServiceJobRun run = new ServiceJobRun();
            run.setJobRunId(sequences.nextId("ServiceJobRun"));
            run.setJobName(jobName);
            run.setUserId(userId);
            run.setParameters(String.valueOf(parameters(jobName)));
            run.setHostName(host);
            run.setRunThread(Thread.currentThread().getName());
            run.setStartTime(start);
            em.persist(run);
            long minutes = j.getExpireLockTime() == null || j.getExpireLockTime() <= 0 ? 30 : j.getExpireLockTime();
            lock.setJobRunId(run.getJobRunId());
            lock.setLockedBy(host);
            lock.setLockedUntil(start.plusMinutes(minutes));
            return run.getJobRunId();
        });
        if (runId == null) return Optional.empty();

        String results = null, errors = null;
        try {
            String bean = tx.execute(s -> em.find(ServiceJob.class, jobName).getServiceName());
            results = context.getBean(bean, CoreJobHandler.class).run(tx.execute(s -> parameters(jobName)));
        } catch (Exception e) {
            errors = e.getClass().getSimpleName() + ": " + e.getMessage();
            log.error("Job {} failed: {}", jobName, e.getMessage(), e);
        }
        String r = results, err = errors;
        tx.executeWithoutResult(s -> {
            ServiceJobRun run = em.find(ServiceJobRun.class, runId);
            run.setEndTime(CoreClock.now());
            run.setResults(r);
            run.setHasError(err != null);
            run.setErrors(err == null ? null : err.length() > 4000 ? err.substring(0, 4000) : err);
            ServiceJobRunLock lock = em.find(ServiceJobRunLock.class, jobName, LockModeType.PESSIMISTIC_WRITE);
            lock.setLockedUntil(null);
            lock.setLastRunTime(start);
        });
        return Optional.of(new Run(runId, jobName, errors != null, results, errors));
    }

    public void setPaused(String jobName, boolean paused) {
        tx.executeWithoutResult(s -> {
            ServiceJob j = em.find(ServiceJob.class, jobName);
            if (j == null) throw new IllegalArgumentException("Unknown job " + jobName);
            j.setPaused(paused);
        });
    }

    /** Changes when a job runs; the expression is checked first (six fields: second minute hour day month weekday). */
    public void setSchedule(String jobName, String cronExpression) {
        if (!CronExpression.isValidExpression(cronExpression)) {
            throw new IllegalArgumentException("'" + cronExpression + "' is not a schedule; use six fields, for example '0 */5 * * * *' for every 5 minutes");
        }
        tx.executeWithoutResult(s -> {
            ServiceJob j = em.find(ServiceJob.class, jobName);
            if (j == null) throw new IllegalArgumentException("Unknown job " + jobName);
            j.setCronExpression(cronExpression);
        });
    }

    public record JobView(String jobName, String description, String cronExpression, boolean paused, String lastRunTime, String lockedBy) {
    }

    public List<JobView> jobs() {
        return tx.execute(s -> em.createQuery("SELECT j FROM ServiceJob j ORDER BY j.jobName", ServiceJob.class).getResultList().stream().map(j -> {
            ServiceJobRunLock l = em.find(ServiceJobRunLock.class, j.getJobName());
            boolean running = l != null && l.getLockedUntil() != null && l.getLockedUntil().isAfter(CoreClock.now());
            return new JobView(j.getJobName(), j.getDescription(), j.getCronExpression(), Boolean.TRUE.equals(j.getPaused()),
                    l == null || l.getLastRunTime() == null ? null : l.getLastRunTime().toString(), running ? l.getLockedBy() : null);
        }).toList());
    }

    public List<Run> runs(String jobName, int limit) {
        return tx.execute(s -> em.createQuery("SELECT r FROM ServiceJobRun r WHERE r.jobName = :j ORDER BY r.startTime DESC", ServiceJobRun.class)
                .setParameter("j", jobName).setMaxResults(limit).getResultList().stream()
                .map(r -> new Run(r.getJobRunId(), r.getJobName(), Boolean.TRUE.equals(r.getHasError()), r.getResults(), r.getErrors())).toList());
    }

    private Map<String, String> parameters(String jobName) {
        Map<String, String> m = new LinkedHashMap<>();
        em.createQuery("SELECT p FROM ServiceJobParameter p WHERE p.jobName = :j ORDER BY p.parameterName", ServiceJobParameter.class)
                .setParameter("j", jobName).getResultList().forEach(p -> m.put(p.getParameterName(), p.getParameterValue()));
        return m;
    }

    private static String hostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
