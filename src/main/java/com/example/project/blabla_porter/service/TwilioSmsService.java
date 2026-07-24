package com.example.project.blabla_porter.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Service
@ConditionalOnProperty(name = "blabla.sms.provider", havingValue = "TWILIO")
public class TwilioSmsService implements SmsService {

    @Value("${blabla.twilio.account-sid}")
    private String accountSid;

    @Value("${blabla.twilio.auth-token}")
    private String authToken;

    @Value("${blabla.twilio.phone-number}")
    private String fromPhoneNumber;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public void sendSms(String toPhoneNumber, String message) {
        if (accountSid == null || accountSid.isBlank() ||
            authToken == null || authToken.isBlank() ||
            fromPhoneNumber == null || fromPhoneNumber.isBlank()) {
            System.err.println("Twilio SMS credentials are not fully configured. Skipping send.");
            return;
        }

        // Format to international phone number format if needed (e.g. 9876543210 -> +919876543210)
        String formattedTo = toPhoneNumber.trim();
        if (!formattedTo.startsWith("+")) {
            if (formattedTo.length() == 10) {
                formattedTo = "+91" + formattedTo;
            } else if (formattedTo.length() == 12 && formattedTo.startsWith("91")) {
                formattedTo = "+" + formattedTo;
            }
        }

        try {
            String url = "https://api.twilio.com/2010-04-01/Accounts/" + accountSid + "/Messages.json";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.setBasicAuth(accountSid, authToken);

            MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
            map.add("To", formattedTo);
            map.add("From", fromPhoneNumber);
            map.add("Body", message);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);
            restTemplate.postForEntity(url, request, String.class);
            System.out.println("Twilio SMS sent successfully to: " + formattedTo);
        } catch (Exception e) {
            System.err.println("Failed to send Twilio SMS to " + formattedTo + ": " + e.getMessage());
        }
    }
}
