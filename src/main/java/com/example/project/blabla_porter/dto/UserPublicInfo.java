package com.example.project.blabla_porter.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserPublicInfo {
    private Long id;
    private String fullName;
    private String mobileNumber;
    private Double averageRating;
    private Integer totalRatingsCount;
}
