package com.nexus.oms.repository;

import com.nexus.oms.entity.PurchaseRequestItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface PurchaseRequestItemRepository extends JpaRepository<PurchaseRequestItem, UUID> {

    List<PurchaseRequestItem> findByRequestId(UUID requestId);
}
