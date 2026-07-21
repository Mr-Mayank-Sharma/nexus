package com.nexus.oms.repository;

import com.nexus.oms.entity.NxProofOfDelivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface ProofOfDeliveryRepository extends JpaRepository<NxProofOfDelivery, UUID> {
    NxProofOfDelivery findByPickupOrderId(UUID pickupOrderId);
}
