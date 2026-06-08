package com.safemed.gateway.dto;

import lombok.Data;

// researcher registers a callback url for notifications
@Data
public class WebhookRegisterDTO {
    private String callbackUrl;
}
