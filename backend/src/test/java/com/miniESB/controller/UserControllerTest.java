package com.miniESB.controller;


import com.miniESB.dto.user.CreateUserRequest;
import com.miniESB.dto.user.ResetPasswordRequest;
import com.miniESB.dto.user.UpdateUserRequest;
import com.miniESB.dto.user.UserResponse;
import com.miniESB.exception.ResourceNotFoundException;
import com.miniESB.service.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserController — unit tests")
class UserControllerTest {

    @Mock  private UserService userService;
    @InjectMocks private UserController userController;

    private final UserResponse userDto =
            new UserResponse(1L, "dev1", "DEVELOPER", "dev1@example.com");

    @Nested @DisplayName("createUser()")
    class CreateUser {

        @Test @DisplayName("returns 201 with created user")
        void createUser_returns201() {
            CreateUserRequest req = new CreateUserRequest(
                    "dev1", "pass123", "DEVELOPER", "dev1@example.com");
            when(userService.createUser(req)).thenReturn(userDto);

            ResponseEntity<UserResponse> response = userController.createUser(req);

            assertThat(response.getStatusCode().value()).isEqualTo(201);
            assertThat(response.getBody().username()).isEqualTo("dev1");
        }
    }

    @Nested @DisplayName("updateUser()")
    class UpdateUser {

        @Test @DisplayName("returns 200 with updated user")
        void updateUser_returns200() {
            UpdateUserRequest req = new UpdateUserRequest("newDev", null, null, null);
            when(userService.updateUser(1L, req)).thenReturn(userDto);

            ResponseEntity<UserResponse> response = userController.updateUser(1L, req);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
        }

        @Test @DisplayName("propagates ResourceNotFoundException when user not found")
        void updateUser_notFound() {
            UpdateUserRequest req = new UpdateUserRequest(null, null, null, null);
            when(userService.updateUser(99L, req))
                    .thenThrow(new ResourceNotFoundException("User not found"));

            assertThatThrownBy(() -> userController.updateUser(99L, req))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested @DisplayName("deleteUser()")
    class DeleteUser {

        @Test @DisplayName("returns 204 on successful delete")
        void deleteUser_returns204() {
            doNothing().when(userService).deleteUser(1L);

            ResponseEntity<Void> response = userController.deleteUser(1L);

            assertThat(response.getStatusCode().value()).isEqualTo(204);
        }

        @Test @DisplayName("propagates ResourceNotFoundException when user not found")
        void deleteUser_notFound() {
            doThrow(new ResourceNotFoundException("User not found")).when(userService).deleteUser(99L);

            assertThatThrownBy(() -> userController.deleteUser(99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested @DisplayName("assignRole()")
    class AssignRole {

        @Test @DisplayName("returns 200 with updated role")
        void assignRole_returns200() {
            UserResponse adminDto = new UserResponse(1L, "dev1", "ADMIN", "dev1@example.com");
            when(userService.assignRole(1L, "ADMIN")).thenReturn(adminDto);

            ResponseEntity<UserResponse> response = userController.assignRole(1L, "ADMIN");

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody().role()).isEqualTo("ADMIN");
        }
    }

    @Nested @DisplayName("getAllUsers()")
    class GetAllUsers {

        @Test @DisplayName("returns 200 with list of users")
        void getAllUsers_returns200() {
            when(userService.getAllUsers()).thenReturn(List.of(userDto));

            ResponseEntity<List<UserResponse>> response = userController.getAllUsers();

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).hasSize(1);
        }
    }

    @Nested @DisplayName("resetPassword()")
    class ResetPassword {

        @Test @DisplayName("returns 200 on successful password reset")
        void resetPassword_returns200() {
            ResetPasswordRequest req = new ResetPasswordRequest("oldPass", "newPass");
            when(userService.resetPassword(1L, "oldPass", "newPass")).thenReturn(userDto);

            ResponseEntity<UserResponse> response = userController.resetPassword(1L, req);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
        }

        @Test @DisplayName("propagates IllegalArgumentException when old password is wrong")
        void resetPassword_wrongOldPassword() {
            ResetPasswordRequest req = new ResetPasswordRequest("wrong", "newPass");
            when(userService.resetPassword(1L, "wrong", "newPass"))
                    .thenThrow(new IllegalArgumentException("Ancien mot de passe incorrect"));

            assertThatThrownBy(() -> userController.resetPassword(1L, req))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested @DisplayName("forgotPassword()")
    class ForgotPassword {

        @Test @DisplayName("returns 200 when reset code sent")
        void forgotPassword_returns200() {
            doNothing().when(userService).forgotPassword("dev1");

            ResponseEntity<String> response = userController.forgotPassword("dev1");

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).contains("email");
            verify(userService).forgotPassword("dev1");
        }

        @Test @DisplayName("propagates ResourceNotFoundException when user not found")
        void forgotPassword_userNotFound() {
            doThrow(new ResourceNotFoundException("not found")).when(userService).forgotPassword("ghost");

            assertThatThrownBy(() -> userController.forgotPassword("ghost"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested @DisplayName("verifyCode()")
    class VerifyCode {

        @Test @DisplayName("returns 200 with true when code is valid")
        void verifyCode_validCode() {
            when(userService.verifyCode("dev1", "123456")).thenReturn(true);

            ResponseEntity<Boolean> response = userController.verifyCode("dev1", "123456");

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).isTrue();
        }

        @Test @DisplayName("returns 200 with false when code is invalid")
        void verifyCode_invalidCode() {
            when(userService.verifyCode("dev1", "000000")).thenReturn(false);

            ResponseEntity<Boolean> response = userController.verifyCode("dev1", "000000");

            assertThat(response.getBody()).isFalse();
        }
    }

    @Nested @DisplayName("resetPasswordByCode()")
    class ResetPasswordByCode {

        @Test @DisplayName("returns 200 on successful reset")
        void resetPasswordByCode_returns200() {
            doNothing().when(userService).resetPasswordByCode("dev1", "newPass");

            ResponseEntity<String> response = userController.resetPasswordByCode("dev1", "newPass");

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).contains("updated");
        }

        @Test @DisplayName("propagates IllegalStateException when code expired")
        void resetPasswordByCode_expiredCode() {
            doThrow(new IllegalStateException("Code invalide ou expiré"))
                    .when(userService).resetPasswordByCode("dev1", "newPass");

            assertThatThrownBy(() -> userController.resetPasswordByCode("dev1", "newPass"))
                    .isInstanceOf(IllegalStateException.class);
        }
    }
}