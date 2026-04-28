package com.ashraf.munichyoungsterevents.service;

import com.ashraf.munichyoungsterevents.dto.AuthLoginRequestDTO;
import com.ashraf.munichyoungsterevents.dto.AuthRegisterRequestDTO;
import com.ashraf.munichyoungsterevents.dto.AuthResponseDTO;
import com.ashraf.munichyoungsterevents.entity.Attendee;
import com.ashraf.munichyoungsterevents.entity.Role;
import com.ashraf.munichyoungsterevents.entity.User;
import com.ashraf.munichyoungsterevents.exception.ConflictException;
import com.ashraf.munichyoungsterevents.exception.NotFoundException;
import com.ashraf.munichyoungsterevents.repository.AttendeeRepository;
import com.ashraf.munichyoungsterevents.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.transaction.Transactional;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;

@Service
public class AuthServiceImpl implements AuthService{

    private final UserRepository userRepository;
    private final AttendeeRepository attendeeRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    public AuthServiceImpl(UserRepository userRepository, AttendeeRepository attendeeRepository, PasswordEncoder passwordEncoder, AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.attendeeRepository = attendeeRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
    }

    @Override
    @Transactional
    public AuthResponseDTO register(AuthRegisterRequestDTO request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Email already registered");
        }
        if (attendeeRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Attendee email already exists");
        }

        User user = new User();
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(Role.ATTENDEE);
        user.setEnabled(true);
        User savedUser = userRepository.save(user);

        // Registration creates an attendee profile linked to the newly created account.
        Attendee attendee = new Attendee();
        attendee.setFirstName(request.getFirstName());
        attendee.setLastName(request.getLastName());
        attendee.setEmail(request.getEmail());
        attendee.setUser(savedUser);
        Attendee savedAttendee = attendeeRepository.save(attendee);
        savedUser.setAttendee(savedAttendee);

        return toResponse(savedUser);
    }

    @Override
    public AuthResponseDTO login(AuthLoginRequestDTO request, HttpServletRequest httpRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        // Persist the authenticated principal into the HTTP session so subsequent requests
        // are authenticated via JSESSIONID without resending credentials.
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);

        HttpSession session = httpRequest.getSession(true);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new NotFoundException("User not found after login"));

        return toResponse(user);
    }

    @Override
    public AuthResponseDTO me(Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new NotFoundException("Authenticated user not found"));

        return toResponse(user);
    }

    private AuthResponseDTO toResponse(User user) {
        String firstName = user.getAttendee() != null ? user.getAttendee().getFirstName() : null;
        String lastName = user.getAttendee() != null ? user.getAttendee().getLastName() : null;
        Long attendeeId = user.getAttendee() != null ? user.getAttendee().getId() : null;
        return new AuthResponseDTO(
                user.getId(),
                attendeeId,
                user.getEmail(),
                user.getRole(),
                firstName,
                lastName
        );
    }

}
