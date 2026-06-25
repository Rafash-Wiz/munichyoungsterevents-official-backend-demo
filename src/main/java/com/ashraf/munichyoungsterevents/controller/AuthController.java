package com.ashraf.munichyoungsterevents.controller;

import com.ashraf.munichyoungsterevents.dto.AuthLoginRequestDTO;
import com.ashraf.munichyoungsterevents.dto.AuthRegisterRequestDTO;
import com.ashraf.munichyoungsterevents.dto.AuthResponseDTO;
import com.ashraf.munichyoungsterevents.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public AuthResponseDTO register(@Valid @RequestBody AuthRegisterRequestDTO request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponseDTO login(@Valid @RequestBody AuthLoginRequestDTO request) {
        return authService.login(request);
    }

    @PostMapping("/logout")
    public void logout(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
    }

    @GetMapping("/me")
    public AuthResponseDTO me(Authentication authentication) {
        return authService.me(authentication);
    }


}
