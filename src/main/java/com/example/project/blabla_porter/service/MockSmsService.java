package com.example.project.blabla_porter.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "blabla.sms.provider", havingValue = "MOCK", matchIfMissing = true)
public class MockSmsService implements SmsService {

    @Override
    public void sendSms(String toPhoneNumber, String message) {
        System.out.println("==========================================================================");
        System.out.println("  [MOCK SMS SEND]");
        System.out.println("  To: " + toPhoneNumber);
        System.out.println("  Body: " + message);
        System.out.println("==========================================================================");
    }
}
