package com.example.project.blabla_porter.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RazorpayOrderResponse {
    private String keyId;
    private String orderId;
    private Double amount;
    private String currency;
    private String goodsDescription;
    private String senderName;
    private String senderMobile;
}
