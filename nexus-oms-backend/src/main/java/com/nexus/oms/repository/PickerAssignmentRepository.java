package com.nexus.oms.repository;

import com.nexus.oms.entity.NxPickerAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface PickerAssignmentRepository extends JpaRepository<NxPickerAssignment, UUID> {
    List<NxPickerAssignment> findByPickerIdAndStatus(UUID pickerId, String status);
    List<NxPickerAssignment> findByNodeId(UUID nodeId);

    @Query("SELECT a FROM NxPickerAssignment a WHERE a.nodeId = :nodeId AND a.status IN ('ASSIGNED', 'IN_PROGRESS') ORDER BY a.priority ASC, a.assignedAt ASC")
    List<NxPickerAssignment> findActiveAssignmentsByNode(@Param("nodeId") UUID nodeId);

    @Query("SELECT a FROM NxPickerAssignment a WHERE a.pickerId = :pickerId AND a.status IN ('ASSIGNED', 'IN_PROGRESS') ORDER BY a.assignedAt ASC")
    List<NxPickerAssignment> findActiveAssignmentsByPicker(@Param("pickerId") UUID pickerId);

    @Query("SELECT a FROM NxPickerAssignment a WHERE a.nodeId = :nodeId AND a.status = 'ASSIGNED' ORDER BY a.priority ASC, a.assignedAt ASC LIMIT 1")
    NxPickerAssignment findNextUnassigned(@Param("nodeId") UUID nodeId);
}
