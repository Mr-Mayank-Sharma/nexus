package com.nexus.oms.repository;

import com.nexus.oms.entity.NxManifestShipment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ManifestShipmentRepository extends JpaRepository<NxManifestShipment, UUID> {

    List<NxManifestShipment> findByManifestId(UUID manifestId);

    void deleteByManifestId(UUID manifestId);
}
