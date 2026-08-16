package com.nexus.oms.repository;

import com.nexus.oms.entity.NxAsnLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NxAsnLineRepository extends JpaRepository<NxAsnLine, UUID> {
    java.util.List<NxAsnLine> findByAsnId(UUID asnId);
}
