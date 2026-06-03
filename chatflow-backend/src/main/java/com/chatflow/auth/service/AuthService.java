package com.chatflow.auth.service;

import com.chatflow.auth.dto.AuthResponse;
import com.chatflow.auth.dto.LoginRequest;
import com.chatflow.auth.dto.RegisterRequest;
import com.chatflow.auth.security.AuthenticatedUser;
import com.chatflow.auth.security.JwtService;
import com.chatflow.user.entity.User;
import com.chatflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username is not available");
        }

        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .build();

        User saved = userRepository.save(user);
        log.debug("Registered user id={} username={}", saved.getId(), saved.getUsername());

        String token = jwtService.generateToken(saved.getId());
        return AuthResponse.builder()
                .token(token)
                .userId(saved.getId())
                .username(saved.getUsername())
                .build();
    }


    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));

        AuthenticatedUser principal = (AuthenticatedUser) authentication.getPrincipal();
        log.debug("Login successful for user id={}", principal.id());

        String token = jwtService.generateToken(principal.id());
        return AuthResponse.builder()
                .token(token)
                .userId(principal.id())
                .username(principal.username())
                .build();
    }
}