package com.nexus.oms.service;

import com.nexus.oms.entity.NxEngineeredStandard;
import com.nexus.oms.entity.NxLaborEntry;
import com.nexus.oms.entity.NxShiftSchedule;
import com.nexus.oms.entity.WarehouseStaff;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.EngineeredStandardRepository;
import com.nexus.oms.repository.LaborEntryRepository;
import com.nexus.oms.repository.ShiftScheduleRepository;
import com.nexus.oms.repository.WarehouseStaffRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class LaborService {

    private static final double BASE_HOURLY_RATE = 20.0;

    private final LaborEntryRepository laborEntryRepository;
    private final ShiftScheduleRepository shiftScheduleRepository;
    private final EngineeredStandardRepository engineeredStandardRepository;
    private final WarehouseStaffRepository warehouseStaffRepository;

    public LaborService(LaborEntryRepository laborEntryRepository,
                        ShiftScheduleRepository shiftScheduleRepository,
                        EngineeredStandardRepository engineeredStandardRepository,
                        WarehouseStaffRepository warehouseStaffRepository) {
        this.laborEntryRepository = laborEntryRepository;
        this.shiftScheduleRepository = shiftScheduleRepository;
        this.engineeredStandardRepository = engineeredStandardRepository;
        this.warehouseStaffRepository = warehouseStaffRepository;
    }

    @Transactional
    public NxLaborEntry clockIn(UUID staffId, UUID warehouseId) {
        WarehouseStaff staff = warehouseStaffRepository.findById(staffId)
                .orElseThrow(() -> new ResourceNotFoundException("WarehouseStaff", staffId));

        NxLaborEntry entry = NxLaborEntry.builder()
                .tenantId(staff.getTenantId())
                .warehouseId(warehouseId)
                .staffId(staffId)
                .employeeCode(staff.getEmployeeCode())
                .firstName(staff.getFirstName())
                .lastName(staff.getLastName())
                .status("ON_BREAK")
                .shift(staff.getShift())
                .clockedInAt(LocalDateTime.now())
                .build();

        return laborEntryRepository.save(entry);
    }

    @Transactional
    public NxLaborEntry clockOut(UUID laborEntryId) {
        NxLaborEntry entry = findById(laborEntryId);

        if ("CLOCKED_OUT".equals(entry.getStatus())) {
            throw new BadRequestException("Labor entry is already clocked out");
        }

        entry.setStatus("CLOCKED_OUT");
        entry.setClockedOutAt(LocalDateTime.now());

        if (entry.getClockedInAt() != null) {
            long totalMinutes = Duration.between(entry.getClockedInAt(), entry.getClockedOutAt()).toMinutes();
            entry.setTotalWorkMinutes((int) (totalMinutes - entry.getTotalBreakMinutes()));
        }

        updateProductivityScore(entry);
        return laborEntryRepository.save(entry);
    }

    @Transactional
    public NxLaborEntry startBreak(UUID laborEntryId) {
        NxLaborEntry entry = findById(laborEntryId);

        if ("CLOCKED_OUT".equals(entry.getStatus())) {
            throw new BadRequestException("Cannot start break: worker is clocked out");
        }
        if ("ON_BREAK".equals(entry.getStatus())) {
            throw new BadRequestException("Worker is already on break");
        }

        entry.setStatus("ON_BREAK");
        entry.setBreakStartedAt(LocalDateTime.now());
        return laborEntryRepository.save(entry);
    }

    @Transactional
    public NxLaborEntry endBreak(UUID laborEntryId) {
        NxLaborEntry entry = findById(laborEntryId);

        if (!"ON_BREAK".equals(entry.getStatus())) {
            throw new BadRequestException("Worker is not on break");
        }

        entry.setStatus("ACTIVE");
        entry.setBreakEndedAt(LocalDateTime.now());

        if (entry.getBreakStartedAt() != null) {
            long breakMinutes = Duration.between(entry.getBreakStartedAt(), entry.getBreakEndedAt()).toMinutes();
            entry.setTotalBreakMinutes(entry.getTotalBreakMinutes() + (int) breakMinutes);
        }

        return laborEntryRepository.save(entry);
    }

    @Transactional
    public NxLaborEntry updateProgress(UUID laborEntryId, Map<String, Object> progressData) {
        NxLaborEntry entry = findById(laborEntryId);

        if ("CLOCKED_OUT".equals(entry.getStatus())) {
            throw new BadRequestException("Cannot update progress: worker is clocked out");
        }

        if (progressData.containsKey("linesPicked")) {
            entry.setLinesPicked(toInt(progressData.get("linesPicked")));
        }
        if (progressData.containsKey("linesPacked")) {
            entry.setLinesPacked(toInt(progressData.get("linesPacked")));
        }
        if (progressData.containsKey("unitsReceived")) {
            entry.setUnitsReceived(toInt(progressData.get("unitsReceived")));
        }
        if (progressData.containsKey("unitsShipped")) {
            entry.setUnitsShipped(toInt(progressData.get("unitsShipped")));
        }
        if (progressData.containsKey("errorCount")) {
            entry.setErrorCount(toInt(progressData.get("errorCount")));
        }

        updateProductivityScore(entry);
        return laborEntryRepository.save(entry);
    }

    @Transactional
    public NxLaborEntry assignTask(UUID laborEntryId, String taskType, UUID waveId) {
        NxLaborEntry entry = findById(laborEntryId);

        if ("CLOCKED_OUT".equals(entry.getStatus())) {
            throw new BadRequestException("Cannot assign task: worker is clocked out");
        }

        entry.setTaskType(taskType);
        entry.setCurrentWaveId(waveId);
        entry.setStatus("ACTIVE");
        return laborEntryRepository.save(entry);
    }

    public List<Map<String, Object>> getActiveWorkers(UUID warehouseId) {
        List<NxLaborEntry> entries = laborEntryRepository.findByWarehouseIdAndStatus(warehouseId, "ACTIVE");
        List<NxLaborEntry> onBreak = laborEntryRepository.findByWarehouseIdAndStatus(warehouseId, "ON_BREAK");
        List<NxLaborEntry> idle = laborEntryRepository.findByWarehouseIdAndStatus(warehouseId, "IDLE");

        List<NxLaborEntry> allActive = new ArrayList<>();
        allActive.addAll(entries);
        allActive.addAll(onBreak);
        allActive.addAll(idle);

        return allActive.stream().map(this::toWorkerSummary).collect(Collectors.toList());
    }

    public Map<String, Object> getLaborStats(UUID warehouseId, LocalDate date) {
        String dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE);
        List<NxLaborEntry> entries = laborEntryRepository.findByWarehouseIdAndShiftDate(warehouseId, dateStr);

        int totalWorkers = entries.size();
        long activeWorkers = entries.stream()
                .filter(e -> !"CLOCKED_OUT".equals(e.getStatus()))
                .count();
        double avgProductivity = entries.stream()
                .mapToDouble(e -> e.getProductivityScore() != null ? e.getProductivityScore() : 0.0)
                .average().orElse(0.0);
        int totalLinesPicked = entries.stream().mapToInt(e -> e.getLinesPicked() != null ? e.getLinesPicked() : 0).sum();
        int totalLinesPacked = entries.stream().mapToInt(e -> e.getLinesPacked() != null ? e.getLinesPacked() : 0).sum();
        int totalUnitsReceived = entries.stream().mapToInt(e -> e.getUnitsReceived() != null ? e.getUnitsReceived() : 0).sum();
        int totalUnitsShipped = entries.stream().mapToInt(e -> e.getUnitsShipped() != null ? e.getUnitsShipped() : 0).sum();

        double laborUtilization = totalWorkers > 0 ? (activeWorkers * 100.0 / totalWorkers) : 0.0;

        String avgEfficiency = calculateAvgEfficiencyRating(entries);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalWorkers", totalWorkers);
        stats.put("activeWorkers", activeWorkers);
        stats.put("avgProductivity", Math.round(avgProductivity * 100.0) / 100.0);
        stats.put("totalLinesPicked", totalLinesPicked);
        stats.put("totalLinesPacked", totalLinesPacked);
        stats.put("totalUnitsReceived", totalUnitsReceived);
        stats.put("totalUnitsShipped", totalUnitsShipped);
        stats.put("laborUtilization", Math.round(laborUtilization * 100.0) / 100.0);
        stats.put("avgEfficiencyRating", avgEfficiency);
        return stats;
    }

    public List<Map<String, Object>> getEfficiencyByWorker(UUID warehouseId, LocalDate date) {
        String dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE);
        List<NxLaborEntry> entries = laborEntryRepository.findByWarehouseIdAndShiftDate(warehouseId, dateStr);

        return entries.stream().map(e -> {
            Map<String, Object> worker = new LinkedHashMap<>();
            worker.put("staffId", e.getStaffId());
            worker.put("name", e.getFirstName() + " " + e.getLastName());
            worker.put("employeeCode", e.getEmployeeCode());
            worker.put("productivityScore", e.getProductivityScore());
            worker.put("efficiencyRating", e.getEfficiencyRating());
            worker.put("linesPicked", e.getLinesPicked());
            worker.put("linesPacked", e.getLinesPacked());
            worker.put("unitsReceived", e.getUnitsReceived());
            worker.put("unitsShipped", e.getUnitsShipped());
            worker.put("errorCount", e.getErrorCount());
            worker.put("status", e.getStatus());
            return worker;
        }).collect(Collectors.toList());
    }

    public List<Map<String, Object>> getEfficiencyByShift(UUID warehouseId, LocalDate date) {
        String dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE);
        List<NxLaborEntry> entries = laborEntryRepository.findByWarehouseIdAndShiftDate(warehouseId, dateStr);

        return entries.stream()
                .filter(e -> e.getShift() != null)
                .collect(Collectors.groupingBy(NxLaborEntry::getShift, LinkedHashMap::new, Collectors.toList()))
                .entrySet().stream()
                .map(entry -> buildShiftAggregate(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> getEfficiencyByTaskType(UUID warehouseId, LocalDate date) {
        String dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE);
        List<NxLaborEntry> entries = laborEntryRepository.findByWarehouseIdAndShiftDate(warehouseId, dateStr);

        return entries.stream()
                .filter(e -> e.getTaskType() != null)
                .collect(Collectors.groupingBy(NxLaborEntry::getTaskType, LinkedHashMap::new, Collectors.toList()))
                .entrySet().stream()
                .map(entry -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("taskType", entry.getKey());
                    result.put("count", entry.getValue().size());
                    double avgProd = entry.getValue().stream()
                            .mapToDouble(e -> e.getProductivityScore() != null ? e.getProductivityScore() : 0.0)
                            .average().orElse(0.0);
                    result.put("avgProductivity", Math.round(avgProd * 100.0) / 100.0);
                    return result;
                })
                .collect(Collectors.toList());
    }

    public List<NxShiftSchedule> getShiftSchedules(UUID warehouseId, LocalDate date) {
        return shiftScheduleRepository.findByWarehouseIdAndShiftDate(warehouseId, date);
    }

    @Transactional
    public NxShiftSchedule createShiftSchedule(NxShiftSchedule schedule) {
        if (schedule.getStatus() == null) {
            schedule.setStatus("SCHEDULED");
        }
        return shiftScheduleRepository.save(schedule);
    }

    public List<NxEngineeredStandard> getEngineeredStandards(UUID warehouseId) {
        return engineeredStandardRepository.findByWarehouseIdAndIsActive(warehouseId, true);
    }

    @Transactional
    public NxEngineeredStandard createEngineeredStandard(NxEngineeredStandard standard) {
        if (standard.getIsActive() == null) {
            standard.setIsActive(true);
        }
        return engineeredStandardRepository.save(standard);
    }

    public Map<String, Object> calculateIncentivePay(UUID laborEntryId) {
        NxLaborEntry entry = findById(laborEntryId);

        if (entry.getTaskType() == null) {
            throw new BadRequestException("No task assigned to this labor entry");
        }

        Optional<NxEngineeredStandard> standardOpt = engineeredStandardRepository
                .findByWarehouseIdAndTaskType(entry.getWarehouseId(), entry.getTaskType())
                .stream()
                .findFirst();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("laborEntryId", laborEntryId);
        result.put("staffName", entry.getFirstName() + " " + entry.getLastName());
        result.put("employeeCode", entry.getEmployeeCode());
        result.put("taskType", entry.getTaskType());
        result.put("productivityScore", entry.getProductivityScore());

        if (standardOpt.isEmpty()) {
            result.put("basePay", BASE_HOURLY_RATE);
            result.put("incentiveMultiplier", 1.0);
            result.put("totalPay", BASE_HOURLY_RATE);
            result.put("message", "No engineered standard found for task type: " + entry.getTaskType());
            return result;
        }

        NxEngineeredStandard standard = standardOpt.get();
        double productivity = entry.getProductivityScore() != null ? entry.getProductivityScore() : 0.0;
        double standardValue = standard.getStandardValue();

        result.put("standardValue", standardValue);
        result.put("uom", standard.getUom());

        double basePay = BASE_HOURLY_RATE;
        double incentiveMultiplier = 1.0;

        if (standardValue > 0 && productivity > standardValue) {
            incentiveMultiplier = 1 + ((productivity - standardValue) / standardValue) * 0.5;
        }

        double totalPay = basePay * incentiveMultiplier;

        result.put("basePay", basePay);
        result.put("incentiveMultiplier", Math.round(incentiveMultiplier * 100.0) / 100.0);
        result.put("totalPay", Math.round(totalPay * 100.0) / 100.0);

        return result;
    }

    private NxLaborEntry findById(UUID id) {
        return laborEntryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("NxLaborEntry", id));
    }

    private void updateProductivityScore(NxLaborEntry entry) {
        int totalOutput = (entry.getLinesPicked() != null ? entry.getLinesPicked() : 0)
                + (entry.getLinesPacked() != null ? entry.getLinesPacked() : 0)
                + (entry.getUnitsReceived() != null ? entry.getUnitsReceived() : 0)
                + (entry.getUnitsShipped() != null ? entry.getUnitsShipped() : 0);

        int errors = entry.getErrorCount() != null ? entry.getErrorCount() : 0;
        int workMinutes = entry.getTotalWorkMinutes() != null && entry.getTotalWorkMinutes() > 0
                ? entry.getTotalWorkMinutes() : 1;

        double rawRate = (double) totalOutput / workMinutes * 60;
        double errorPenalty = errors * 2.0;
        double score = Math.max(0, Math.min(100, rawRate - errorPenalty));

        entry.setProductivityScore(Math.round(score * 100.0) / 100.0);
        entry.setEfficiencyRating(ratingFromScore(score));
    }

    private String ratingFromScore(double score) {
        if (score >= 90) return "EXCELLENT";
        if (score >= 75) return "GOOD";
        if (score >= 55) return "AVERAGE";
        if (score >= 35) return "BELOW_AVERAGE";
        return "POOR";
    }

    private String calculateAvgEfficiencyRating(List<NxLaborEntry> entries) {
        double avg = entries.stream()
                .mapToDouble(e -> e.getProductivityScore() != null ? e.getProductivityScore() : 0.0)
                .average().orElse(0.0);
        return ratingFromScore(avg);
    }

    private Map<String, Object> toWorkerSummary(NxLaborEntry entry) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("id", entry.getId());
        summary.put("staffId", entry.getStaffId());
        summary.put("name", entry.getFirstName() + " " + entry.getLastName());
        summary.put("employeeCode", entry.getEmployeeCode());
        summary.put("status", entry.getStatus());
        summary.put("taskType", entry.getTaskType());
        summary.put("currentTask", entry.getCurrentTask());
        summary.put("currentWaveId", entry.getCurrentWaveId());
        summary.put("linesPicked", entry.getLinesPicked());
        summary.put("linesPacked", entry.getLinesPacked());
        summary.put("unitsReceived", entry.getUnitsReceived());
        summary.put("unitsShipped", entry.getUnitsShipped());
        summary.put("productivityScore", entry.getProductivityScore());
        summary.put("efficiencyRating", entry.getEfficiencyRating());
        summary.put("errorCount", entry.getErrorCount());
        summary.put("totalWorkMinutes", entry.getTotalWorkMinutes());
        summary.put("totalBreakMinutes", entry.getTotalBreakMinutes());
        summary.put("clockedInAt", entry.getClockedInAt());
        return summary;
    }

    private Map<String, Object> buildShiftAggregate(String shiftType, List<NxLaborEntry> entries) {
        Map<String, Object> aggregate = new LinkedHashMap<>();
        aggregate.put("shift", shiftType);
        aggregate.put("workerCount", entries.size());

        double avgProd = entries.stream()
                .mapToDouble(e -> e.getProductivityScore() != null ? e.getProductivityScore() : 0.0)
                .average().orElse(0.0);
        aggregate.put("avgProductivity", Math.round(avgProd * 100.0) / 100.0);

        int totalPicked = entries.stream().mapToInt(e -> e.getLinesPicked() != null ? e.getLinesPicked() : 0).sum();
        int totalPacked = entries.stream().mapToInt(e -> e.getLinesPacked() != null ? e.getLinesPacked() : 0).sum();
        int totalReceived = entries.stream().mapToInt(e -> e.getUnitsReceived() != null ? e.getUnitsReceived() : 0).sum();
        int totalShipped = entries.stream().mapToInt(e -> e.getUnitsShipped() != null ? e.getUnitsShipped() : 0).sum();

        aggregate.put("totalLinesPicked", totalPicked);
        aggregate.put("totalLinesPacked", totalPacked);
        aggregate.put("totalUnitsReceived", totalReceived);
        aggregate.put("totalUnitsShipped", totalShipped);
        aggregate.put("efficiencyRating", ratingFromScore(avgProd));

        return aggregate;
    }

    private int toInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String str) {
            return Integer.parseInt(str);
        }
        return 0;
    }
}
