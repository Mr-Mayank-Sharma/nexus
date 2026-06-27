package com.nexus.oms.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SplitOrderRequest {

    @NotEmpty
    private List<SplitGroup> groups;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SplitGroup {
        @NotEmpty
        private List<String> itemIds;

        @NotNull
        private Integer priority;
    }
}
