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
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Scheduled jobs as data (Moqui's {@code service_job}): each job names a handler bean and a cron expression, and can be
 * paused without a release. Every few seconds the runner starts the jobs that are due.
 * <p>
 * A job runs on one server at a time: the run takes the job's {@code service_job_run_lock} row until it finishes or the
 * lock expires ({@code expire_lock_time} minutes, for a server that died mid-run). Every run is a
 * {@code service_job_run} with its results or its error. Off with {@code nexus.core.jobs.enabled=false} (the tests
 * turn it off).
 * <p>
 * Each step is its own new transaction ({@code REQUIRES_NEW}), so a failing job never rolls back the record of its run.
 */
@Service("coreJobService")
public class JobService {

    private static final Logger log = LoggerFactory.getLogger(JobService.class);

    static final long DEFAULT_LOCK_MINUTES = 30;
    private static final int MAX_ERROR_LENGTH = 4000;

    private final EntityManager entityManager;
    private final SequenceService sequenceService;
    private final ApplicationContext applicationContext;
    private final TransactionTemplate newTransaction;
    private final boolean enabled;
    private final int keepDays;
    private final String hostName;
    private volatile LocalDateTime lastPrunedAt = LocalDateTime.MIN;

    public JobService(EntityManager entityManager, SequenceService sequenceService, ApplicationContext applicationContext,
                      PlatformTransactionManager transactionManager, @Value("${nexus.core.jobs.enabled:true}") boolean enabled,
                      @Value("${nexus.core.jobs.keep-days:7}") int keepDays) {
        this.entityManager = entityManager;
        this.sequenceService = sequenceService;
        this.applicationContext = applicationContext;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.enabled = enabled;
        this.keepDays = keepDays;
        this.hostName = localHostName();
    }

    public record Run(String jobRunId, String jobName, boolean hasError, String results, String errors) {
    }

    public record JobView(String jobName, String description, String cronExpression, boolean paused, String lastRunTime, String lockedBy) {
    }

    // ── the runner ──

    /** Every few seconds: prune old runs once an hour, then start every job that is due. */
    @Scheduled(fixedDelayString = "${nexus.core.jobs.tick-ms:5000}", initialDelayString = "${nexus.core.jobs.initial-delay-ms:30000}")
    public void tick() {
        if (!enabled) {
            return;
        }
        LocalDateTime now = CoreClock.now();
        if (lastPrunedAt.isBefore(now.minusHours(1))) {
            lastPrunedAt = now;
            prune(now.minusDays(keepDays));
        }
        for (String jobName : due(now)) {
            try {
                runJob(jobName, null);
            } catch (RuntimeException couldNotStart) {
                log.error("Job {} could not start: {}", jobName, couldNotStart.getMessage(), couldNotStart);
            }
        }
    }

    /** Deletes run history older than the cutoff (a job every 10 seconds writes 8,640 runs a day). Returns how many. */
    public int prune(LocalDateTime before) {
        Integer deleted = newTransaction.execute(status -> entityManager.createQuery("DELETE FROM ServiceJobRun r WHERE r.startTime < :before")
                .setParameter("before", before)
                .executeUpdate());
        return deleted == null ? 0 : deleted;
    }

    /** Jobs not paused, inside their dates, whose next cron time after the last run has come; by priority. */
    public List<String> due(LocalDateTime now) {
        return newTransaction.execute(status -> entityManager.createQuery("SELECT j FROM ServiceJob j WHERE (j.paused IS NULL OR j.paused = false) "
                        + "ORDER BY j.priority, j.jobName", ServiceJob.class)
                .getResultList()
                .stream()
                .filter(job -> isDue(job, now))
                .map(ServiceJob::getJobName)
                .toList());
    }

    boolean isDue(ServiceJob job, LocalDateTime now) {
        boolean notStartedYet = job.getFromDate() != null && job.getFromDate().isAfter(now);
        boolean alreadyEnded = job.getThruDate() != null && !job.getThruDate().isAfter(now);
        boolean noSchedule = job.getCronExpression() == null || job.getCronExpression().isBlank();
        if (notStartedYet || alreadyEnded || noSchedule) {
            return false;
        }
        ServiceJobRunLock lock = entityManager.find(ServiceJobRunLock.class, job.getJobName());
        if (isHeld(lock, now)) {
            return false;   // running now
        }
        LocalDateTime lastRun = lock == null ? null : lock.getLastRunTime();
        if (lastRun == null) {
            return true;   // never ran
        }
        LocalDateTime nextRun = CronExpression.parse(job.getCronExpression()).next(lastRun.atOffset(ZoneOffset.UTC).toLocalDateTime());
        return nextRun != null && !nextRun.isAfter(now);
    }

    /**
     * Runs a job now (a person asked, or it is due), unless another server holds it.
     * <ol>
     *   <li>take the lock and record the run (one transaction);</li>
     *   <li>run the handler bean the job names;</li>
     *   <li>record the result or error and free the lock (another transaction).</li>
     * </ol>
     *
     * @return the run, or empty when the job was already running
     */
    public Optional<Run> runJob(String jobName, String userId) {
        LocalDateTime startedAt = CoreClock.now();
        String runId = newTransaction.execute(status -> startRun(jobName, userId, startedAt));
        if (runId == null) {
            return Optional.empty();
        }

        String results = null;
        String errors = null;
        try {
            String handlerBeanName = newTransaction.execute(status -> entityManager.find(ServiceJob.class, jobName).getServiceName());
            Map<String, String> parameters = newTransaction.execute(status -> parameters(jobName));
            results = applicationContext.getBean(handlerBeanName, CoreJobHandler.class).run(parameters);
        } catch (Exception failure) {
            errors = failure.getClass().getSimpleName() + ": " + failure.getMessage();
            log.error("Job {} failed: {}", jobName, failure.getMessage(), failure);
        }

        String finalResults = results;
        String finalErrors = errors;
        newTransaction.executeWithoutResult(status -> finishRun(jobName, runId, startedAt, finalResults, finalErrors));
        return Optional.of(new Run(runId, jobName, errors != null, results, errors));
    }

    /** Takes the job's lock and records a new run; null when another run holds the lock. */
    private String startRun(String jobName, String userId, LocalDateTime startedAt) {
        ServiceJob job = entityManager.find(ServiceJob.class, jobName);
        if (job == null) {
            throw new IllegalArgumentException("Unknown job " + jobName);
        }
        ServiceJobRunLock lock = entityManager.find(ServiceJobRunLock.class, jobName, LockModeType.PESSIMISTIC_WRITE);
        if (lock == null) {
            lock = new ServiceJobRunLock();
            lock.setJobName(jobName);
            entityManager.persist(lock);
        } else if (isHeld(lock, startedAt)) {
            return null;   // running elsewhere
        }

        ServiceJobRun run = new ServiceJobRun();
        run.setJobRunId(sequenceService.nextId("ServiceJobRun"));
        run.setJobName(jobName);
        run.setUserId(userId);
        run.setParameters(String.valueOf(parameters(jobName)));
        run.setHostName(hostName);
        run.setRunThread(Thread.currentThread().getName());
        run.setStartTime(startedAt);
        entityManager.persist(run);

        boolean hasOwnExpiry = job.getExpireLockTime() != null && job.getExpireLockTime() > 0;
        long lockMinutes = hasOwnExpiry ? job.getExpireLockTime() : DEFAULT_LOCK_MINUTES;
        lock.setJobRunId(run.getJobRunId());
        lock.setLockedBy(hostName);
        lock.setLockedUntil(startedAt.plusMinutes(lockMinutes));
        return run.getJobRunId();
    }

    private void finishRun(String jobName, String runId, LocalDateTime startedAt, String results, String errors) {
        ServiceJobRun run = entityManager.find(ServiceJobRun.class, runId);
        run.setEndTime(CoreClock.now());
        run.setResults(results);
        run.setHasError(errors != null);
        run.setErrors(errors == null || errors.length() <= MAX_ERROR_LENGTH ? errors : errors.substring(0, MAX_ERROR_LENGTH));
        ServiceJobRunLock lock = entityManager.find(ServiceJobRunLock.class, jobName, LockModeType.PESSIMISTIC_WRITE);
        lock.setLockedUntil(null);
        lock.setLastRunTime(startedAt);
    }

    // ── managing jobs ──

    public void setPaused(String jobName, boolean paused) {
        newTransaction.executeWithoutResult(status -> loadJob(jobName).setPaused(paused));
    }

    /** Changes when a job runs; the expression is checked first (six fields: second minute hour day month weekday). */
    public void setSchedule(String jobName, String cronExpression) {
        if (!CronExpression.isValidExpression(cronExpression)) {
            throw new IllegalArgumentException("'" + cronExpression + "' is not a schedule; use six fields, for example '0 */5 * * * *' for every 5 minutes");
        }
        newTransaction.executeWithoutResult(status -> loadJob(jobName).setCronExpression(cronExpression));
    }

    /** Every job with its schedule, its last run, and the server running it now (null when it isn't running). */
    public List<JobView> jobs() {
        return newTransaction.execute(status -> entityManager.createQuery("SELECT j FROM ServiceJob j ORDER BY j.jobName", ServiceJob.class)
                .getResultList()
                .stream()
                .map(this::toView)
                .toList());
    }

    /** A job's latest runs, newest first. */
    public List<Run> runs(String jobName, int limit) {
        return newTransaction.execute(status -> entityManager.createQuery("SELECT r FROM ServiceJobRun r WHERE r.jobName = :jobName ORDER BY r.startTime DESC",
                        ServiceJobRun.class)
                .setParameter("jobName", jobName)
                .setMaxResults(limit)
                .getResultList()
                .stream()
                .map(run -> new Run(run.getJobRunId(), run.getJobName(), Boolean.TRUE.equals(run.getHasError()), run.getResults(), run.getErrors()))
                .toList());
    }

    // ── internals ──

    private JobView toView(ServiceJob job) {
        ServiceJobRunLock lock = entityManager.find(ServiceJobRunLock.class, job.getJobName());
        boolean running = isHeld(lock, CoreClock.now());
        String lastRunTime = lock == null || lock.getLastRunTime() == null ? null : lock.getLastRunTime().toString();
        return new JobView(job.getJobName(), job.getDescription(), job.getCronExpression(), Boolean.TRUE.equals(job.getPaused()), lastRunTime,
                running ? lock.getLockedBy() : null);
    }

    private static boolean isHeld(ServiceJobRunLock lock, LocalDateTime at) {
        return lock != null && lock.getLockedUntil() != null && lock.getLockedUntil().isAfter(at);
    }

    private ServiceJob loadJob(String jobName) {
        ServiceJob job = entityManager.find(ServiceJob.class, jobName);
        if (job == null) {
            throw new IllegalArgumentException("Unknown job " + jobName);
        }
        return job;
    }

    /** The job's parameters by name. */
    private Map<String, String> parameters(String jobName) {
        Map<String, String> valueByName = new LinkedHashMap<>();
        entityManager.createQuery("SELECT p FROM ServiceJobParameter p WHERE p.jobName = :jobName ORDER BY p.parameterName", ServiceJobParameter.class)
                .setParameter("jobName", jobName)
                .getResultList()
                .forEach(parameter -> valueByName.put(parameter.getParameterName(), parameter.getParameterValue()));
        return valueByName;
    }

    private static String localHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException noName) {
            return "unknown";
        }
    }
}
