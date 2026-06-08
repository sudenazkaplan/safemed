package com.safemed.gateway.service;

import com.safemed.gateway.dto.AuthRequestDTO;
import com.safemed.gateway.dto.AuthResponseDTO;

public interface AuthService {

    AuthResponseDTO register(AuthRequestDTO dto);

    AuthResponseDTO login(AuthRequestDTO dto);
}
