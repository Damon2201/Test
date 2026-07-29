package com.example.project.blabla_porter.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PendingRatingInfo {
    private Long targetId;
    private String type; // "parcel" or "ride"
    private String description;
    private Long counterpartyId;
    private String counterpartyName;
    private String counterpartyRole;
}
