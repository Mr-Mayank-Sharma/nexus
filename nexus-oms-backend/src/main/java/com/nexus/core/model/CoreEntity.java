package com.nexus.core.model;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * The four stamp columns every core table carries (the OFBiz and Moqui convention). They record when a row was
 * created and last changed, and feed change detection for sync and exports. Times are UTC, like every
 * LocalDateTime in Nexus. The migrations declare them NOT NULL with a default; the mapping leaves them nullable so
 * seed SQL also runs on test databases whose tables Hibernate creates.
 */
@MappedSuperclass
@Getter
public abstract class CoreEntity {

    @Column(name = "last_updated_stamp")
    private LocalDateTime lastUpdatedStamp;

    @Column(name = "last_updated_tx_stamp")
    private LocalDateTime lastUpdatedTxStamp;

    @Column(name = "created_stamp", updatable = false)
    private LocalDateTime createdStamp;

    @Column(name = "created_tx_stamp", updatable = false)
    private LocalDateTime createdTxStamp;

    @PrePersist
    void stampOnCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdStamp = now;
        createdTxStamp = now;
        lastUpdatedStamp = now;
        lastUpdatedTxStamp = now;
    }

    @PreUpdate
    void stampOnUpdate() {
        LocalDateTime now = LocalDateTime.now();
        lastUpdatedStamp = now;
        lastUpdatedTxStamp = now;
    }
}
