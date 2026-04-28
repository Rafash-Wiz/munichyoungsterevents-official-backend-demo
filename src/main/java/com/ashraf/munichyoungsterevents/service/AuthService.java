package com.ashraf.munichyoungsterevents.service;

import com.ashraf.munichyoungsterevents.dto.AuthLoginRequestDTO;
import com.ashraf.munichyoungsterevents.dto.AuthRegisterRequestDTO;
import com.ashraf.munichyoungsterevents.dto.AuthResponseDTO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;

public interface AuthService {

    AuthResponseDTO register(AuthRegisterRequestDTO request);

    AuthResponseDTO login(AuthLoginRequestDTO request, HttpServletRequest httpRequest);

    AuthResponseDTO me(Authentication authentication);
}
