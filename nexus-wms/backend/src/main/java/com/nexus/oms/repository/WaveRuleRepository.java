package com.nexus.oms.repository;

import com.nexus.oms.entity.NxWaveRule;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface WaveRuleRepository extends JpaRepository<NxWaveRule, UUID> {

    List<NxWaveRule> findByWaveId(UUID waveId);

    List<NxWaveRule> findByWaveIdAndIsActive(UUID waveId, Boolean isActive);
}
