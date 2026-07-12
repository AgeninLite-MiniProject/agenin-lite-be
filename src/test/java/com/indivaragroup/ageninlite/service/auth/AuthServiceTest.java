package com.indivaragroup.ageninlite.service.auth;

import com.indivaragroup.ageninlite.common.exception.AppException;
import com.indivaragroup.ageninlite.common.exception.code.AuthErrorCode;
import com.indivaragroup.ageninlite.dto.auth.LoginRequestDto;
import com.indivaragroup.ageninlite.dto.auth.LoginResponseDto;
import com.indivaragroup.ageninlite.dto.auth.RegisterRequestDto;
import com.indivaragroup.ageninlite.dto.auth.RegisterResponseDto;
import com.indivaragroup.ageninlite.dto.auth.RefreshRequestDto;
import com.indivaragroup.ageninlite.dto.auth.RefreshResponseDto;
import com.indivaragroup.ageninlite.entity.AuthRefreshToken;
import com.indivaragroup.ageninlite.entity.MstUser;
import com.indivaragroup.ageninlite.repository.auth.RefreshTokenRepository;
import com.indivaragroup.ageninlite.repository.auth.UserRepository;
import com.indivaragroup.ageninlite.security.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.impl.DefaultClaims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private AuthService authService;

    private RegisterRequestDto registerRequest;
    private LoginRequestDto loginRequest;
    private RefreshRequestDto refreshRequest;
    private MstUser inviter;
    private MstUser existingUser;

    @BeforeEach
    void setUp() {
        registerRequest = new RegisterRequestDto();
        registerRequest.setName("Budi");
        registerRequest.setPhoneNumber("+628123");
        registerRequest.setPassword("password123");
        registerRequest.setEmail("budi@mail.com");

        loginRequest = new LoginRequestDto();
        loginRequest.setPhoneNumber("+628123");
        loginRequest.setPassword("password123");

        refreshRequest = new RefreshRequestDto();
        refreshRequest.setRefreshToken("raw_refresh_token");

        inviter = new MstUser();
        inviter.setUserId(UUID.randomUUID());
        inviter.setReferralCode("AGN-INV1");
        inviter.setDeleted(false);

        existingUser = new MstUser();
        existingUser.setUserId(UUID.randomUUID());
        existingUser.setPhoneNumber("+628123");
        existingUser.setPasswordHash("hashedPassword");
        existingUser.setRole("AGENT");
        existingUser.setDeleted(false);
    }

    // --- TEST REGISTER ---

    @Test
    void register_Success_WithoutReferral() {
        when(userRepository.existsByPhoneNumber(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any(MstUser.class))).thenAnswer(i -> i.getArguments()[0]);

        RegisterResponseDto response = authService.register(registerRequest);

        assertNotNull(response);
        assertEquals("Budi", response.getName());
        verify(userRepository).save(any(MstUser.class));
    }

    @Test
    void register_Success_WithReferral() {
        registerRequest.setReferralCode("AGN-INV1");
        when(userRepository.existsByPhoneNumber(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.findByReferralCode("AGN-INV1")).thenReturn(Optional.of(inviter));
        when(userRepository.countByReferredBy(inviter.getUserId())).thenReturn(5);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any(MstUser.class))).thenAnswer(i -> i.getArguments()[0]);

        RegisterResponseDto response = authService.register(registerRequest);

        assertNotNull(response);
        verify(userRepository).save(any(MstUser.class));
    }

    @Test
    void register_Failed_PhoneExists() {
        when(userRepository.existsByPhoneNumber(anyString())).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> authService.register(registerRequest));
        assertEquals(AuthErrorCode.AUTH_0001, ex.getErrorCode());
    }

    @Test
    void register_Failed_EmailExists() {
        when(userRepository.existsByPhoneNumber(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> authService.register(registerRequest));
        assertEquals(AuthErrorCode.AUTH_0004, ex.getErrorCode());
    }

    @Test
    void register_Failed_ReferralNotFound() {
        registerRequest.setReferralCode("INVALID");
        when(userRepository.existsByPhoneNumber(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.findByReferralCode("INVALID")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> authService.register(registerRequest));
        assertEquals(AuthErrorCode.AUTH_0006, ex.getErrorCode());
    }

    @Test
    void register_Failed_ReferralDeleted() {
        registerRequest.setReferralCode("AGN-INV1");
        inviter.setDeleted(true);
        when(userRepository.existsByPhoneNumber(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.findByReferralCode("AGN-INV1")).thenReturn(Optional.of(inviter));

        AppException ex = assertThrows(AppException.class, () -> authService.register(registerRequest));
        assertEquals(AuthErrorCode.AUTH_0008, ex.getErrorCode());
    }

    @Test
    void register_Failed_ReferralMaxDownlines() {
        registerRequest.setReferralCode("AGN-INV1");
        when(userRepository.existsByPhoneNumber(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.findByReferralCode("AGN-INV1")).thenReturn(Optional.of(inviter));
        when(userRepository.countByReferredBy(inviter.getUserId())).thenReturn(10); // Max

        AppException ex = assertThrows(AppException.class, () -> authService.register(registerRequest));
        assertEquals(AuthErrorCode.AUTH_0007, ex.getErrorCode());
    }

    // --- TEST LOGIN ---

    @Test
    void login_Success() {
        when(userRepository.findByPhoneNumber(anyString())).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("password123", "hashedPassword")).thenReturn(true);
        when(jwtUtil.generateToken(any(), any(), any())).thenReturn("access_jwt");
        when(jwtUtil.generateRefreshToken(any(), any())).thenReturn("refresh_jwt");
        when(passwordEncoder.encode(anyString())).thenReturn("hashed_refresh");

        LoginResponseDto response = authService.login(loginRequest);

        assertNotNull(response);
        assertEquals("access_jwt", response.getAccessToken());
        assertEquals("refresh_jwt", response.getRefreshToken());
        verify(refreshTokenRepository).save(any(AuthRefreshToken.class));
    }

    @Test
    void login_Failed_PhoneNotFound() {
        when(userRepository.findByPhoneNumber(anyString())).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> authService.login(loginRequest));
        assertEquals(AuthErrorCode.AUTH_0010, ex.getErrorCode());
    }

    @Test
    void login_Failed_WrongPassword() {
        when(userRepository.findByPhoneNumber(anyString())).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        AppException ex = assertThrows(AppException.class, () -> authService.login(loginRequest));
        assertEquals(AuthErrorCode.AUTH_0010, ex.getErrorCode());
    }

    @Test
    void login_Failed_UserDeleted() {
        existingUser.setDeleted(true);
        when(userRepository.findByPhoneNumber(anyString())).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("password123", "hashedPassword")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> authService.login(loginRequest));
        assertEquals(AuthErrorCode.AUTH_0011, ex.getErrorCode());
    }

    @Test
    void register_Success_WithNullEmailAndReferral() {
        registerRequest.setEmail(null);
        registerRequest.setReferralCode(null);

        when(userRepository.existsByPhoneNumber(anyString())).thenReturn(false);

        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any(MstUser.class))).thenAnswer(i -> i.getArguments()[0]);

        RegisterResponseDto response = authService.register(registerRequest);

        assertNotNull(response);
        verify(userRepository).save(any(MstUser.class));
        verify(userRepository, never()).existsByEmail(anyString());
    }

    @Test
    void register_Success_WithEmptyEmailAndReferral() {
        registerRequest.setEmail("");
        registerRequest.setReferralCode("");

        when(userRepository.existsByPhoneNumber(anyString())).thenReturn(false);

        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any(MstUser.class))).thenAnswer(i -> i.getArguments()[0]);

        RegisterResponseDto response = authService.register(registerRequest);

        assertNotNull(response);
        verify(userRepository).save(any(MstUser.class));
        verify(userRepository, never()).existsByEmail(anyString());
    }

    // --- TEST REFRESH ---

    @Test
    void refresh_Success() {
        String tokenId = UUID.randomUUID().toString();
        Claims claims = new DefaultClaims(Map.of(
                "tokenId", tokenId,
                "sub", existingUser.getUserId().toString()
        ));

        AuthRefreshToken storedToken = AuthRefreshToken.builder()
                .tokenId(tokenId)
                .tokenHash("storedHash")
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build();

        when(jwtUtil.isTokenValid(anyString())).thenReturn(true);
        when(jwtUtil.extractAllClaims(anyString())).thenReturn(claims);
        when(refreshTokenRepository.findByTokenId(tokenId)).thenReturn(Optional.of(storedToken));
        when(passwordEncoder.matches(anyString(), eq("storedHash"))).thenReturn(true);
        when(userRepository.findById(existingUser.getUserId())).thenReturn(Optional.of(existingUser));
        
        when(jwtUtil.generateToken(any(), any(), any())).thenReturn("new_access_jwt");
        when(jwtUtil.generateRefreshToken(any(), any())).thenReturn("new_refresh_jwt");
        when(passwordEncoder.encode(anyString())).thenReturn("new_hashed_refresh");

        RefreshResponseDto response = authService.refresh(refreshRequest);

        assertNotNull(response);
        assertEquals("new_access_jwt", response.getAccessToken());
        assertEquals("new_refresh_jwt", response.getRefreshToken());
        assertNotNull(storedToken.getRevokedAt()); // Check if old token was revoked
        verify(refreshTokenRepository, times(2)).save(any(AuthRefreshToken.class)); // save old & new
    }

    @Test
    void refresh_Failed_InvalidToken() {
        when(jwtUtil.isTokenValid(anyString())).thenReturn(false);

        AppException ex = assertThrows(AppException.class, () -> authService.refresh(refreshRequest));
        assertEquals(AuthErrorCode.AUTH_0030, ex.getErrorCode());
    }

    @Test
    void refresh_Failed_TokenIdMissing() {
        Claims claims = new DefaultClaims(Map.of(
                "sub", existingUser.getUserId().toString()
        ));

        when(jwtUtil.isTokenValid(anyString())).thenReturn(true);
        when(jwtUtil.extractAllClaims(anyString())).thenReturn(claims);

        AppException ex = assertThrows(AppException.class, () -> authService.refresh(refreshRequest));
        assertEquals(AuthErrorCode.AUTH_0030, ex.getErrorCode());
    }

    @Test
    void refresh_Failed_TokenExpiredInDb() {
        String tokenId = UUID.randomUUID().toString();
        Claims claims = new DefaultClaims(Map.of(
                "tokenId", tokenId,
                "sub", existingUser.getUserId().toString()
        ));

        AuthRefreshToken storedToken = AuthRefreshToken.builder()
                .tokenId(tokenId)
                .tokenHash("storedHash")
                .expiresAt(LocalDateTime.now().minusDays(1)) // expired!
                .build();

        when(jwtUtil.isTokenValid(anyString())).thenReturn(true);
        when(jwtUtil.extractAllClaims(anyString())).thenReturn(claims);
        when(refreshTokenRepository.findByTokenId(tokenId)).thenReturn(Optional.of(storedToken));

        AppException ex = assertThrows(AppException.class, () -> authService.refresh(refreshRequest));
        assertEquals(AuthErrorCode.AUTH_0030, ex.getErrorCode());
    }

    @Test
    void refresh_Failed_ExtractClaimsThrowsException() {
        when(jwtUtil.isTokenValid(anyString())).thenReturn(true);
        when(jwtUtil.extractAllClaims(anyString())).thenThrow(new RuntimeException("Parsing error"));

        AppException ex = assertThrows(AppException.class, () -> authService.refresh(refreshRequest));
        assertEquals(AuthErrorCode.AUTH_0030, ex.getErrorCode());
    }

    @Test
    void refresh_Failed_TokenIdNotFoundInDb() {
        String tokenId = UUID.randomUUID().toString();
        Claims claims = new DefaultClaims(Map.of(
                "tokenId", tokenId,
                "sub", existingUser.getUserId().toString()
        ));

        when(jwtUtil.isTokenValid(anyString())).thenReturn(true);
        when(jwtUtil.extractAllClaims(anyString())).thenReturn(claims);
        when(refreshTokenRepository.findByTokenId(tokenId)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> authService.refresh(refreshRequest));
        assertEquals(AuthErrorCode.AUTH_0030, ex.getErrorCode());
    }

    @Test
    void refresh_Failed_HashMismatch() {
        String tokenId = UUID.randomUUID().toString();
        Claims claims = new DefaultClaims(Map.of(
                "tokenId", tokenId,
                "sub", existingUser.getUserId().toString()
        ));

        AuthRefreshToken storedToken = AuthRefreshToken.builder()
                .tokenId(tokenId)
                .tokenHash("storedHash")
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build();

        when(jwtUtil.isTokenValid(anyString())).thenReturn(true);
        when(jwtUtil.extractAllClaims(anyString())).thenReturn(claims);
        when(refreshTokenRepository.findByTokenId(tokenId)).thenReturn(Optional.of(storedToken));
        when(passwordEncoder.matches(anyString(), eq("storedHash"))).thenReturn(false);

        AppException ex = assertThrows(AppException.class, () -> authService.refresh(refreshRequest));
        assertEquals(AuthErrorCode.AUTH_0030, ex.getErrorCode());
    }

    @Test
    void refresh_Failed_UserNotFound() {
        String tokenId = UUID.randomUUID().toString();
        Claims claims = new DefaultClaims(Map.of(
                "tokenId", tokenId,
                "sub", existingUser.getUserId().toString()
        ));

        AuthRefreshToken storedToken = AuthRefreshToken.builder()
                .tokenId(tokenId)
                .tokenHash("storedHash")
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build();

        when(jwtUtil.isTokenValid(anyString())).thenReturn(true);
        when(jwtUtil.extractAllClaims(anyString())).thenReturn(claims);
        when(refreshTokenRepository.findByTokenId(tokenId)).thenReturn(Optional.of(storedToken));
        when(passwordEncoder.matches(anyString(), eq("storedHash"))).thenReturn(true);
        when(userRepository.findById(existingUser.getUserId())).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> authService.refresh(refreshRequest));
        assertEquals(AuthErrorCode.AUTH_0030, ex.getErrorCode());
    }

    @Test
    void refresh_Failed_UserDeleted() {
        String tokenId = UUID.randomUUID().toString();
        Claims claims = new DefaultClaims(Map.of(
                "tokenId", tokenId,
                "sub", existingUser.getUserId().toString()
        ));

        AuthRefreshToken storedToken = AuthRefreshToken.builder()
                .tokenId(tokenId)
                .tokenHash("storedHash")
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build();

        existingUser.setDeleted(true);

        when(jwtUtil.isTokenValid(anyString())).thenReturn(true);
        when(jwtUtil.extractAllClaims(anyString())).thenReturn(claims);
        when(refreshTokenRepository.findByTokenId(tokenId)).thenReturn(Optional.of(storedToken));
        when(passwordEncoder.matches(anyString(), eq("storedHash"))).thenReturn(true);
        when(userRepository.findById(existingUser.getUserId())).thenReturn(Optional.of(existingUser));

        AppException ex = assertThrows(AppException.class, () -> authService.refresh(refreshRequest));
        assertEquals(AuthErrorCode.AUTH_0011, ex.getErrorCode());
    }

    @Test
    void refresh_Failed_TokenRevokedInDb() {
        String tokenId = UUID.randomUUID().toString();
        Claims claims = new DefaultClaims(Map.of(
                "tokenId", tokenId,
                "sub", existingUser.getUserId().toString()
        ));

        AuthRefreshToken storedToken = AuthRefreshToken.builder()
                .tokenId(tokenId)
                .tokenHash("storedHash")
                .expiresAt(LocalDateTime.now().plusDays(1))
                .revokedAt(LocalDateTime.now().minusDays(1)) // revoked!
                .build();

        when(jwtUtil.isTokenValid(anyString())).thenReturn(true);
        when(jwtUtil.extractAllClaims(anyString())).thenReturn(claims);
        when(refreshTokenRepository.findByTokenId(tokenId)).thenReturn(Optional.of(storedToken));

        AppException ex = assertThrows(AppException.class, () -> authService.refresh(refreshRequest));
        assertEquals(AuthErrorCode.AUTH_0030, ex.getErrorCode());
    }
}