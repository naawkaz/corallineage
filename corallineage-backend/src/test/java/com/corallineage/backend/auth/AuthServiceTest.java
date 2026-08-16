package com.corallineage.backend.auth;

import com.corallineage.backend.exception.ConflictException;
import com.corallineage.backend.exception.NotFoundException;
import com.corallineage.backend.exception.UnauthorizedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Spy
    private PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Spy
    private JwtService jwtService = new JwtService("unit-test-secret-key-at-least-32-characters-long", 86400000L);

    @Spy
    private UserMapper userMapper;

    @InjectMocks
    private AuthService authService;

    private RegisterRequest registerRequest() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("new_researcher");
        request.setPassword("secretpass");
        request.setDisplayName("New Researcher");
        return request;
    }

    private User user() {
        User user = new User();
        user.setId(1L);
        user.setUsername("researcher_01");
        user.setDisplayName("researcher_01");
        user.setPasswordHash(new BCryptPasswordEncoder().encode("correct-password"));
        return user;
    }

    @Test
    void register_success_savesUserAndReturnsToken() {
        when(userRepository.existsByUsername("new_researcher")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(2L);
            return u;
        });

        AuthResponse response = authService.register(registerRequest());

        assertThat(response.getToken()).isNotBlank();
        assertThat(response.getUser().getUsername()).isEqualTo("new_researcher");
        assertThat(response.getUser().getDisplayName()).isEqualTo("New Researcher");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void register_defaultsDisplayNameToUsername_whenNotSupplied() {
        RegisterRequest request = registerRequest();
        request.setDisplayName(null);
        when(userRepository.existsByUsername("new_researcher")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(2L);
            return u;
        });

        AuthResponse response = authService.register(request);

        assertThat(response.getUser().getDisplayName()).isEqualTo("new_researcher");
    }

    @Test
    void register_duplicateUsername_throwsConflict() {
        when(userRepository.existsByUsername("new_researcher")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest()))
                .isInstanceOf(ConflictException.class);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void login_success_returnsToken() {
        when(userRepository.findByUsername("researcher_01")).thenReturn(Optional.of(user()));
        LoginRequest request = new LoginRequest();
        request.setUsername("researcher_01");
        request.setPassword("correct-password");

        AuthResponse response = authService.login(request);

        assertThat(response.getToken()).isNotBlank();
        assertThat(response.getUser().getUsername()).isEqualTo("researcher_01");
    }

    @Test
    void login_wrongPassword_throwsUnauthorized() {
        when(userRepository.findByUsername("researcher_01")).thenReturn(Optional.of(user()));
        LoginRequest request = new LoginRequest();
        request.setUsername("researcher_01");
        request.setPassword("wrong-password");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void login_unknownUsername_throwsUnauthorized() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());
        LoginRequest request = new LoginRequest();
        request.setUsername("ghost");
        request.setPassword("whatever");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void getCurrentUser_success_returnsUser() {
        when(userRepository.findByUsername("researcher_01")).thenReturn(Optional.of(user()));

        UserResponse response = authService.getCurrentUser("researcher_01");

        assertThat(response.getUsername()).isEqualTo("researcher_01");
    }

    @Test
    void getCurrentUser_notFound_throwsNotFound() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.getCurrentUser("ghost"))
                .isInstanceOf(NotFoundException.class);
    }
}
