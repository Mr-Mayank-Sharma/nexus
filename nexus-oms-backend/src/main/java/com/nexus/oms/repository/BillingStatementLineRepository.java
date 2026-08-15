package com.nexus.oms.repository;

import com.nexus.oms.entity.NxBillingStatementLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BillingStatementLineRepository extends JpaRepository<NxBillingStatementLine, UUID> {

    List<NxBillingStatementLine> findByStatementId(UUID statementId);

    void deleteByStatementId(UUID statementId);
}
