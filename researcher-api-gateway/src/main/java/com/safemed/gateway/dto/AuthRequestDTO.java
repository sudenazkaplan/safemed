package com.safemed.gateway.dto;

import lombok.Data;

// used for both register and login (institution ignored on login)
@Data
public class AuthRequestDTO {
    private String username;
    private String password;
    private String institution;
}
