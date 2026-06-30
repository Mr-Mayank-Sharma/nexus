package com.nexus.oms.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiActionHistoryDto {
    private String id;
    private String actionType;
    private String label;
    private String status;
    private String actor;
    private String details;
    private LocalDateTime timestamp;
}
