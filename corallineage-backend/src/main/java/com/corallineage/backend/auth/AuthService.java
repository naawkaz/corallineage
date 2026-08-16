package com.corallineage.backend.auth;

import com.corallineage.backend.exception.ConflictException;
import com.corallineage.backend.exception.NotFoundException;
import com.corallineage.backend.exception.UnauthorizedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserMapper userMapper;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                        JwtService jwtService, UserMapper userMapper) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.userMapper = userMapper;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String username = request.getUsername().trim();
        if (userRepository.existsByUsername(username)) {
            throw new ConflictException("Username already taken: " + username);
        }

        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setDisplayName(
                request.getDisplayName() == null || request.getDisplayName().isBlank()
                        ? username
                        : request.getDisplayName().trim());
        User saved = userRepository.save(user);

        return new AuthResponse(jwtService.generateToken(saved.getUsername()), userMapper.toResponse(saved));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername().trim())
                .orElseThrow(() -> new UnauthorizedException("Invalid username or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid username or password");
        }

        return new AuthResponse(jwtService.generateToken(user.getUsername()), userMapper.toResponse(user));
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User not found: " + username));
        return userMapper.toResponse(user);
    }
}
