package com.nexus.oms.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MergeOrdersRequest {

    @NotEmpty
    private List<UUID> orderIds;

    @NotNull
    private UUID targetOrderId;
}
