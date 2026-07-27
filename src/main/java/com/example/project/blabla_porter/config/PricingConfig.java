package com.example.project.blabla_porter.config;

import org.springframework.stereotype.Component;

@Component
public class PricingConfig {

    // Centralized Pricing Constants in ₹ INR
    private double baseFareInr = 15.0;
    private double perKgRateInr = 10.0;

    // Goods Value Surcharge (Flat percentage of declared value)
    private double valueSurchargeRate = 0.02; // 2% of declared value

    public double getBaseFareInr() {
        return baseFareInr;
    }

    public void setBaseFareInr(double baseFareInr) {
        this.baseFareInr = baseFareInr;
    }

    public double getPerKgRateInr() {
        return perKgRateInr;
    }

    public void setPerKgRateInr(double perKgRateInr) {
        this.perKgRateInr = perKgRateInr;
    }

    public double getPerKmRateInr() {
        return 0.0;
    }

    public double calculateDistanceFare(double distanceKm) {
        if (distanceKm <= 3.0) return 0.0;
        double fare = 0.0;
        if (distanceKm <= 100.0) {
            fare = (distanceKm - 3.0) * 1.5;
        } else if (distanceKm <= 500.0) {
            fare = (97.0 * 1.5) + (distanceKm - 100.0) * 0.5;
        } else {
            fare = (97.0 * 1.5) + (400.0 * 0.5) + (distanceKm - 500.0) * 0.15;
        }
        return fare;
    }

    public double calculateCategorySurcharge(double declaredValue) {
        if (declaredValue <= 0) return 0.0;
        return declaredValue * valueSurchargeRate;
    }

    public String getCategoryTierLabel(double declaredValue) {
        if (declaredValue <= 0) {
            return "No surcharge";
        }
        return "Value Surcharge (2%)";
    }
}
