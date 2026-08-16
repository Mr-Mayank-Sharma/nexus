package com.nexus.oms.dto;

import java.util.UUID;

public record RfTaskItem(
        UUID itemId,
        String sku,
        String productName,
        int quantity,
        int pickedQuantity,
        String fromLocation,
        String status) {}
