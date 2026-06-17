package com.miniESB.controller;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.miniESB.dto.auth.AuthResponse;
import com.miniESB.dto.auth.LoginRequest;
import com.miniESB.dto.auth.RegisterRequest;
import com.miniESB.domain.entity.User;
import com.miniESB.domain.enums.UserRole;
import com.miniESB.repository.UserRepository;
import com.miniESB.security.JwtService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Auth", description = "Authentication and registration")
@ConditionalOnProperty(name = "engine.mode", havingValue = "false", matchIfMissing = true)
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthenticationManager authManager;
    private final UserDetailsService userDetailsService;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthController(AuthenticationManager authManager,
                          UserDetailsService userDetailsService,
                          JwtService jwtService,
                          UserRepository userRepository,
                          PasswordEncoder passwordEncoder) {
        this.authManager = authManager;
        this.userDetailsService = userDetailsService;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Operation(summary = "Login", description = "Authenticates a user and returns a JWT token.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Login successful, token returned"),
        @ApiResponse(responseCode = "401", description = "Invalid credentials")
    })
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        authManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password())
        );
        var userDetails = userDetailsService.loadUserByUsername(request.username());
        String token = jwtService.generateToken(userDetails);
        return ResponseEntity.ok(Map.of("message", "Hello " + request.username(), "token", token));
    }

    @Operation(summary = "Register", description = "Creates a new user account and returns a JWT token.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Account created successfully"),
        @ApiResponse(responseCode = "409", description = "Username already exists")
    })
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        User user = new User();
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(UserRole.valueOf(request.role().toUpperCase()));
        userRepository.save(user);
        var userDetails = userDetailsService.loadUserByUsername(request.username());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new AuthResponse(jwtService.generateToken(userDetails)));
    }
}