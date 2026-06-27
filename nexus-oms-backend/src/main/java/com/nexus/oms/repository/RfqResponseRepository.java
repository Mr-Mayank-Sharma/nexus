package com.nexus.oms.repository;

import com.nexus.oms.entity.RfqResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface RfqResponseRepository extends JpaRepository<RfqResponse, UUID> {

    List<RfqResponse> findByRfqId(UUID rfqId);

    List<RfqResponse> findBySupplierId(UUID supplierId);

    List<RfqResponse> findByRfqIdAndStatus(UUID rfqId, String status);
}
