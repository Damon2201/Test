package com.example.project.blabla_porter.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FareBreakdownDTO {
    private double baseFareInr;
    private double estimatedDistanceKm;
    private double distanceFareInr;
    private double declaredValueInr;
    private String categoryTierLabel;
    private double categorySurchargeInr;
    private double weightKg;
    private double weightFareInr;
    private double totalFareInr;
}
