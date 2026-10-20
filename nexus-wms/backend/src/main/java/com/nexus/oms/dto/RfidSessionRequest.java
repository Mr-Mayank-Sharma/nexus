package com.nexus.oms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * T-08: Request to open an RFID scan session.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RfidSessionRequest {

    private UUID locationId;
    private String sessionType;   // RECEIVING | CYCLE_COUNT | FULFILLMENT
    private String mode;          // FULL_REGISTRY | DECODE_TO_UPC
}
