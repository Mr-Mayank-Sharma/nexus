package com.nexus.oms.dto;

import java.util.List;
import java.util.UUID;

public record RfTask(
        UUID picklistId,
        String picklistName,
        String status,
        String priority,
        int totalItems,
        int pickedItems,
        List<RfTaskItem> items) {}
