package com.miniESB.serviceImpl;

import com.miniESB.config.EmailService;
import com.miniESB.domain.entity.User;
import com.miniESB.domain.enums.UserRole;
import com.miniESB.dto.user.CreateUserRequest;
import com.miniESB.dto.user.UpdateUserRequest;
import com.miniESB.dto.user.UserResponse;
import com.miniESB.exception.ResourceNotFoundException;
import com.miniESB.repository.UserRepository;
import com.miniESB.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserServiceImpl")
class UserServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private EmailService emailService;

    @InjectMocks
    private UserServiceImpl userService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setUsername("dev1");
        user.setPasswordHash("$2a$hashedpassword");
        user.setRole(UserRole.DEVELOPER);
        user.setEmail("dev1@example.com");
    }

    // =========================================================================
    // createUser()
    // =========================================================================

    @Nested
    @DisplayName("createUser()")
    class CreateUser {

        @Test
        @DisplayName("creates and returns user when username is unique")
        void createUser_success() {
            CreateUserRequest req = new CreateUserRequest("dev1", "pass123", "DEVELOPER", "dev1@example.com");
            when(userRepository.existsByUsername("dev1")).thenReturn(false);
            when(passwordEncoder.encode("pass123")).thenReturn("$2a$hashed");
            when(userRepository.save(any(User.class))).thenReturn(user);

            UserResponse result = userService.createUser(req);

            assertThat(result.username()).isEqualTo("dev1");
            assertThat(result.role()).isEqualTo("DEVELOPER");
        }

        @Test
        @DisplayName("throws DataIntegrityViolationException when username already exists")
        void createUser_duplicateUsername() {
            CreateUserRequest req = new CreateUserRequest("dev1", "pass123", "DEVELOPER", "dev1@example.com");
            when(userRepository.existsByUsername("dev1")).thenReturn(true);

            assertThatThrownBy(() -> userService.createUser(req))
                    .isInstanceOf(DataIntegrityViolationException.class);

            verify(userRepository, never()).save(any());
        }
    }

    // =========================================================================
    // updateUser()
    // =========================================================================

    @Nested
    @DisplayName("updateUser()")
    class UpdateUser {

        @Test
        @DisplayName("updates provided fields only")
        void updateUser_partialUpdate() {
            UpdateUserRequest req = new UpdateUserRequest("newDev", null, null, "new@example.com");
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(userRepository.save(any())).thenReturn(user);

            userService.updateUser(1L, req);

            assertThat(user.getUsername()).isEqualTo("newDev");
            assertThat(user.getEmail()).isEqualTo("new@example.com");
            verify(passwordEncoder, never()).encode(any());
        }

        @Test
        @DisplayName("encodes new password when provided")
        void updateUser_withNewPassword() {
            UpdateUserRequest req = new UpdateUserRequest(null, "newPass", null, null);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(passwordEncoder.encode("newPass")).thenReturn("$2a$newHash");
            when(userRepository.save(any())).thenReturn(user);

            userService.updateUser(1L, req);

            verify(passwordEncoder).encode("newPass");
            assertThat(user.getPasswordHash()).isEqualTo("$2a$newHash");
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when user not found")
        void updateUser_notFound() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.updateUser(99L, new UpdateUserRequest(null, null, null, null)))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // =========================================================================
    // deleteUser()
    // =========================================================================

    @Nested
    @DisplayName("deleteUser()")
    class DeleteUser {

        @Test
        @DisplayName("deletes user when it exists")
        void deleteUser_success() {
            when(userRepository.existsById(1L)).thenReturn(true);

            userService.deleteUser(1L);

            verify(userRepository).deleteById(1L);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when user not found")
        void deleteUser_notFound() {
            when(userRepository.existsById(99L)).thenReturn(false);

            assertThatThrownBy(() -> userService.deleteUser(99L))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(userRepository, never()).deleteById(any());
        }
    }

    // =========================================================================
    // assignRole()
    // =========================================================================

    @Nested
    @DisplayName("assignRole()")
    class AssignRole {

        @Test
        @DisplayName("assigns new role and returns updated user")
        void assignRole_success() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(userRepository.save(any())).thenReturn(user);

            UserResponse result = userService.assignRole(1L, "ADMIN");

            assertThat(user.getRole()).isEqualTo(UserRole.ADMIN);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when user not found")
        void assignRole_notFound() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.assignRole(99L, "ADMIN"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // =========================================================================
    // getAllUsers()
    // =========================================================================

    @Nested
    @DisplayName("getAllUsers()")
    class GetAllUsers {

        @Test
        @DisplayName("returns all users as DTOs")
        void getAllUsers_returnsList() {
            when(userRepository.findAll()).thenReturn(List.of(user));

            List<UserResponse> result = userService.getAllUsers();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).username()).isEqualTo("dev1");
        }
    }

    // =========================================================================
    // resetPassword()
    // =========================================================================

    @Nested
    @DisplayName("resetPassword()")
    class ResetPassword {

        @Test
        @DisplayName("resets password when old password matches")
        void resetPassword_success() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("oldPass", "$2a$hashedpassword")).thenReturn(true);
            when(passwordEncoder.encode("newPass")).thenReturn("$2a$newHash");
            when(userRepository.save(any())).thenReturn(user);

            userService.resetPassword(1L, "oldPass", "newPass");

            assertThat(user.getPasswordHash()).isEqualTo("$2a$newHash");
        }

        @Test
        @DisplayName("throws IllegalArgumentException when old password does not match")
        void resetPassword_wrongOldPassword() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("wrong", "$2a$hashedpassword")).thenReturn(false);

            assertThatThrownBy(() -> userService.resetPassword(1L, "wrong", "newPass"))
                    .isInstanceOf(IllegalArgumentException.class);

            verify(userRepository, never()).save(any());
        }
    }

    // =========================================================================
    // forgotPassword()
    // =========================================================================

    @Nested
    @DisplayName("forgotPassword()")
    class ForgotPassword {

        @Test
        @DisplayName("sets reset code, expiry and sends email")
        void forgotPassword_success() {
            when(userRepository.findByUsername("dev1")).thenReturn(Optional.of(user));
            when(userRepository.save(any())).thenReturn(user);

            userService.forgotPassword("dev1");

            verify(emailService).sendResetPasswordEmail(eq("dev1@example.com"), anyString());
            assertThat(user.getResetCode()).isNotBlank();
            assertThat(user.getResetCodeExpiration()).isAfter(LocalDateTime.now());
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when user not found")
        void forgotPassword_userNotFound() {
            when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.forgotPassword("ghost"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("throws IllegalStateException when no email is associated")
        void forgotPassword_noEmail() {
            user.setEmail(null);
            when(userRepository.findByUsername("dev1")).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> userService.forgotPassword("dev1"))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    // =========================================================================
    // verifyCode()
    // =========================================================================

    @Nested
    @DisplayName("verifyCode()")
    class VerifyCode {

        @Test
        @DisplayName("returns true when code matches and is not expired")
        void verifyCode_validCode() {
            user.setResetCode("123456");
            user.setResetCodeExpiration(LocalDateTime.now().plusMinutes(5));
            when(userRepository.findByUsername("dev1")).thenReturn(Optional.of(user));

            assertThat(userService.verifyCode("dev1", "123456")).isTrue();
        }

        @Test
        @DisplayName("returns false when code does not match")
        void verifyCode_wrongCode() {
            user.setResetCode("123456");
            user.setResetCodeExpiration(LocalDateTime.now().plusMinutes(5));
            when(userRepository.findByUsername("dev1")).thenReturn(Optional.of(user));

            assertThat(userService.verifyCode("dev1", "000000")).isFalse();
        }

        @Test
        @DisplayName("returns false when code is expired")
        void verifyCode_expiredCode() {
            user.setResetCode("123456");
            user.setResetCodeExpiration(LocalDateTime.now().minusMinutes(1));
            when(userRepository.findByUsername("dev1")).thenReturn(Optional.of(user));

            assertThat(userService.verifyCode("dev1", "123456")).isFalse();
        }

        @Test
        @DisplayName("returns false when no reset code is set")
        void verifyCode_noCode() {
            user.setResetCode(null);
            user.setResetCodeExpiration(null);
            when(userRepository.findByUsername("dev1")).thenReturn(Optional.of(user));

            assertThat(userService.verifyCode("dev1", "123456")).isFalse();
        }
    }

    // =========================================================================
    // resetPasswordByCode()
    // =========================================================================

    @Nested
    @DisplayName("resetPasswordByCode()")
    class ResetPasswordByCode {

        @Test
        @DisplayName("sets new password and clears reset code")
        void resetPasswordByCode_success() {
            user.setResetCode("123456");
            user.setResetCodeExpiration(LocalDateTime.now().plusMinutes(5));
            when(userRepository.findByUsername("dev1")).thenReturn(Optional.of(user));
            when(passwordEncoder.encode("newPass")).thenReturn("$2a$newHash");
            when(userRepository.save(any())).thenReturn(user);

            userService.resetPasswordByCode("dev1", "newPass");

            assertThat(user.getPasswordHash()).isEqualTo("$2a$newHash");
            assertThat(user.getResetCode()).isNull();
            assertThat(user.getResetCodeExpiration()).isNull();
        }

        @Test
        @DisplayName("throws IllegalStateException when code is expired")
        void resetPasswordByCode_expiredCode() {
            user.setResetCode("123456");
            user.setResetCodeExpiration(LocalDateTime.now().minusMinutes(1));
            when(userRepository.findByUsername("dev1")).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> userService.resetPasswordByCode("dev1", "newPass"))
                    .isInstanceOf(IllegalStateException.class);

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws IllegalStateException when no code is present")
        void resetPasswordByCode_noCode() {
            user.setResetCode(null);
            user.setResetCodeExpiration(null);
            when(userRepository.findByUsername("dev1")).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> userService.resetPasswordByCode("dev1", "newPass"))
                    .isInstanceOf(IllegalStateException.class);
        }
    }
}