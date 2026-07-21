package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_proof_of_delivery")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NxProofOfDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "pickup_order_id", nullable = false)
    private UUID pickupOrderId;

    @Column(name = "order_number", nullable = false)
    private String orderNumber;

    @Column(name = "collected_by_name", nullable = false)
    private String collectedByName;

    @Column(name = "collected_by_id_doc")
    private String collectedByIdDoc;

    @Column(name = "collector_signature", nullable = false)
    private String collectorSignature;

    @Column(name = "associate_name", nullable = false)
    private String associateName;

    @Column(name = "associate_signature")
    private String associateSignature;

    @Column(name = "collection_notes")
    private String collectionNotes;

    @Column(name = "items_handed_over", nullable = false)
    private Integer itemsHandedOver;

    @Column(name = "photo_path")
    private String photoPath;

    @Column(name = "collected_at", nullable = false)
    private LocalDateTime collectedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (collectedAt == null) collectedAt = LocalDateTime.now();
    }
}
