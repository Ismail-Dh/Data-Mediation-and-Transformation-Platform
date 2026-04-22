package com.miniESB.service.impl;

import com.miniESB.domain.entity.User;
import com.miniESB.domain.enums.UserRole;
import com.miniESB.dto.user.CreateUserRequest;
import com.miniESB.dto.user.UpdateUserRequest;
import com.miniESB.dto.user.UserResponse;
import com.miniESB.exception.ResourceNotFoundException;
import com.miniESB.repository.UserRepository;
import com.miniESB.config.EmailService;
import com.miniESB.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Autowired
    public UserServiceImpl(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           EmailService emailService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    // ─── Existing methods ────────────────────────────────────────────────────

    @Override
    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new DataIntegrityViolationException("Username already exists");
        }
        User user = new User();
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(UserRole.valueOf(request.role()));
        user.setEmail(request.email());
        user = userRepository.save(user);
        return toResponse(user);
    }

    @Override
    @Transactional
    public UserResponse updateUser(Long id, UpdateUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (request.username() != null) user.setUsername(request.username());
        if (request.password() != null) user.setPasswordHash(passwordEncoder.encode(request.password()));
        if (request.role() != null) user.setRole(UserRole.valueOf(request.role()));
        if (request.email() != null) user.setEmail(request.email());
        user = userRepository.save(user);
        return toResponse(user);
    }

    @Override
    @Transactional
    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new ResourceNotFoundException("User not found");
        }
        userRepository.deleteById(id);
    }

    @Override
    @Transactional
    public UserResponse assignRole(Long id, String role) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        user.setRole(UserRole.valueOf(role));
        user = userRepository.save(user);
        return toResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public UserResponse resetPassword(Long id, String oldPassword, String newPassword) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Ancien mot de passe incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user = userRepository.save(user);
        return toResponse(user);
    }

    // ─── Forgot password flow ────────────────────────────────────────────────

    /**
     * Step 1 — Generate a 6-digit code, persist it with a 10-minute expiry,
     *           and send it to the user's registered email address.
     */
    @Override
    @Transactional
    public void forgotPassword(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Aucun utilisateur avec ce nom d'utilisateur"));

        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new IllegalStateException("Aucune adresse email associée à ce compte");
        }

        String code = generateCode();
        user.setResetCode(code);
        user.setResetCodeExpiration(LocalDateTime.now().plusMinutes(10));
        userRepository.save(user);

        emailService.sendResetPasswordEmail(user.getEmail(), code);
    }

    /**
     * Step 2 — Verify the code provided by the user.
     * Returns {@code false} (rather than throwing) so the controller can
     * return a clean boolean response to the client.
     */
    @Override
    @Transactional(readOnly = true)
    public boolean verifyCode(String username, String code) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé"));

        if (user.getResetCode() == null || user.getResetCodeExpiration() == null) {
            return false;
        }

        if (user.getResetCodeExpiration().isBefore(LocalDateTime.now())) {
            return false; // code expired
        }

        return user.getResetCode().equals(code);
    }

    /**
     * Step 3 — Set the new password and clear the reset code so it cannot
     *           be reused.  Must be called only after {@link #verifyCode}
     *           returns {@code true}.
     */
    @Override
    @Transactional
    public void resetPasswordByCode(String username, String newPassword) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé"));

        // Guard: refuse if no valid code is present (prevents skipping verifyCode)
        if (user.getResetCode() == null || user.getResetCodeExpiration() == null
                || user.getResetCodeExpiration().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("Code invalide ou expiré — veuillez recommencer");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setResetCode(null);
        user.setResetCodeExpiration(null);
        userRepository.save(user);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private String generateCode() {
        return String.valueOf((int) (Math.random() * 900_000) + 100_000);
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getRole().name(),
                user.getEmail()
        );
    }
}