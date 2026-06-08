package com.safemed.gateway.service;

import com.safemed.gateway.dto.AuthRequestDTO;
import com.safemed.gateway.dto.AuthResponseDTO;
import com.safemed.gateway.exception.BadRequestException;
import com.safemed.gateway.model.User;
import com.safemed.gateway.repository.UserRepository;
import com.safemed.gateway.security.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthServiceImpl implements AuthService {

    // every new signup is a researcher by default
    private static final String DEFAULT_ROLE = "RESEARCHER";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthServiceImpl(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public AuthResponseDTO register(AuthRequestDTO dto) {
        // username must be unique
        if (userRepository.findByUsername(dto.getUsername()).isPresent()) {
            throw new BadRequestException("Username already taken");
        }

        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword())); // never store plain text
        user.setInstitution(dto.getInstitution());
        user.setRole(DEFAULT_ROLE);

        User saved = userRepository.save(user);
        String token = jwtUtil.generateToken(saved.getUsername(), saved.getRole());
        return new AuthResponseDTO(token, saved.getUsername(), saved.getRole());
    }

    @Override
    public AuthResponseDTO login(AuthRequestDTO dto) {
        // same message for both cases so we don't leak which one failed
        User user = userRepository.findByUsername(dto.getUsername())
                .orElseThrow(() -> new BadRequestException("Invalid username or password"));

        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BadRequestException("Invalid username or password");
        }

        String token = jwtUtil.generateToken(user.getUsername(), user.getRole());
        return new AuthResponseDTO(token, user.getUsername(), user.getRole());
    }
}
