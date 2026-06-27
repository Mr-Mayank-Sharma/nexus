package com.nexus.oms.repository;

import com.nexus.oms.entity.NxCarrierZone;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface CarrierZoneRepository extends JpaRepository<NxCarrierZone, UUID> {
    List<NxCarrierZone> findByCarrierCodeAndZipPrefixAndIsDestinationTrue(String carrierCode, String zipPrefix);
}
