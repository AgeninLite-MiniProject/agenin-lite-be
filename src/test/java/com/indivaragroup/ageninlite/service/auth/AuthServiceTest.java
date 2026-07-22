package com.indivaragroup.ageninlite.service.auth;

import com.indivaragroup.ageninlite.common.exception.AppException;
import com.indivaragroup.ageninlite.common.exception.code.AuthErrorCode;
import com.indivaragroup.ageninlite.dto.auth.LoginRequestDto;
import com.indivaragroup.ageninlite.dto.auth.LoginResponseDto;
import com.indivaragroup.ageninlite.dto.auth.RegisterRequestDto;
import com.indivaragroup.ageninlite.dto.auth.RegisterResponseDto;
import com.indivaragroup.ageninlite.dto.auth.RefreshRequestDto;
import com.indivaragroup.ageninlite.dto.auth.RefreshResponseDto;
import com.indivaragroup.ageninlite.dto.auth.LogoutRequestDto;
import com.indivaragroup.ageninlite.entity.AuthJwtBlacklist;
import com.indivaragroup.ageninlite.entity.AuthRefreshToken;
import com.indivaragroup.ageninlite.entity.MstUser;
import com.indivaragroup.ageninlite.repository.auth.RefreshTokenRepository;
import com.indivaragroup.ageninlite.repository.auth.UserRepository;
import com.indivaragroup.ageninlite.repository.auth.JwtBlacklistRepository;
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

    private static final String VALID_PHONE = "+6281234567890";

    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private JwtBlacklistRepository jwtBlacklistRepository;
    @Mock
    private com.indivaragroup.ageninlite.service.audit.AuditService auditService;

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
        registerRequest.setPhoneNumber(VALID_PHONE);
        registerRequest.setPassword("password123");
        registerRequest.setEmail("budi@mail.com");

        loginRequest = new LoginRequestDto();
        loginRequest.setPhoneNumber(VALID_PHONE);
        loginRequest.setPassword("password123");

        refreshRequest = new RefreshRequestDto();
        refreshRequest.setRefreshToken("raw_refresh_token");

        inviter = new MstUser();
        inviter.setUserId(UUID.randomUUID());
        inviter.setReferralCode("AGN-INV1");
        inviter.setDeleted(false);

        existingUser = new MstUser();
        existingUser.setUserId(UUID.randomUUID());
        existingUser.setPhoneNumber(VALID_PHONE);
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
    void register_LocalPhone_NormalizesBeforeDuplicateCheckAndSave() {
        registerRequest.setPhoneNumber("081234567890");
        when(userRepository.existsByPhoneNumber(VALID_PHONE)).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any(MstUser.class))).thenAnswer(i -> i.getArguments()[0]);

        RegisterResponseDto response = authService.register(registerRequest);

        assertEquals(VALID_PHONE, response.getPhoneNumber());
        verify(userRepository).existsByPhoneNumber(VALID_PHONE);
        verify(userRepository).save(argThat(user -> VALID_PHONE.equals(user.getPhoneNumber())));
    }

    @Test
    void register_LocalPhoneMatchingExistingCanonicalPhone_ThrowsDuplicate() {
        registerRequest.setPhoneNumber("081234567890");
        when(userRepository.existsByPhoneNumber(VALID_PHONE)).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> authService.register(registerRequest));

        assertEquals(AuthErrorCode.AUTH_0001, ex.getErrorCode());
        verify(userRepository).existsByPhoneNumber(VALID_PHONE);
        verify(userRepository, never()).save(any(MstUser.class));
    }

    @Test
    void register_InvalidPhone_ThrowsAuth0005WithoutRepositoryCall() {
        registerRequest.setPhoneNumber("0812abc");

        AppException ex = assertThrows(AppException.class, () -> authService.register(registerRequest));

        assertEquals(AuthErrorCode.AUTH_0005, ex.getErrorCode());
        verifyNoInteractions(userRepository);
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

    @Test
    void register_Failed_ReferralCodeCollisionExhausted() {
        when(userRepository.existsByPhoneNumber(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        
        // Simulate collision every time
        when(userRepository.findByReferralCode(anyString())).thenReturn(Optional.of(inviter));

        AppException ex = assertThrows(AppException.class, () -> authService.register(registerRequest));
        assertEquals(AuthErrorCode.AUTH_9999, ex.getErrorCode());
        
        verify(userRepository, times(5)).findByReferralCode(anyString());
        verify(userRepository, never()).save(any(MstUser.class));
    }

    @Test
    void register_Success_WithReferralCodeCollisionResolved() {
        when(userRepository.existsByPhoneNumber(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        
        // Simulate collision twice, then success
        when(userRepository.findByReferralCode(anyString()))
                .thenReturn(Optional.of(inviter))
                .thenReturn(Optional.of(inviter))
                .thenReturn(Optional.empty());
                
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any(MstUser.class))).thenAnswer(i -> i.getArguments()[0]);

        RegisterResponseDto response = authService.register(registerRequest);

        assertNotNull(response);
        verify(userRepository, times(3)).findByReferralCode(anyString());
        verify(userRepository).save(any(MstUser.class));
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
        assertEquals(900, response.getExpiresIn());
        assertEquals("AGENT", response.getRole());
        verify(refreshTokenRepository).save(any(AuthRefreshToken.class));
    }

    @Test
    void login_NationalPhone_NormalizesBeforeLookup() {
        loginRequest.setPhoneNumber("81234567890");
        when(userRepository.findByPhoneNumber(VALID_PHONE)).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("password123", "hashedPassword")).thenReturn(true);
        when(jwtUtil.generateToken(any(), any(), any())).thenReturn("access_jwt");
        when(jwtUtil.generateRefreshToken(any(), any())).thenReturn("refresh_jwt");
        when(passwordEncoder.encode(anyString())).thenReturn("hashed_refresh");

        LoginResponseDto response = authService.login(loginRequest);

        assertEquals("access_jwt", response.getAccessToken());
        verify(userRepository).findByPhoneNumber(VALID_PHONE);
    }

    @Test
    void login_InvalidPhone_ThrowsAuth0005WithoutRepositoryCall() {
        loginRequest.setPhoneNumber("0812abc");

        AppException ex = assertThrows(AppException.class, () -> authService.login(loginRequest));

        assertEquals(AuthErrorCode.AUTH_0005, ex.getErrorCode());
        verifyNoInteractions(userRepository);
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
                .userId(existingUser.getUserId())
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
                .userId(existingUser.getUserId())
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
                .userId(existingUser.getUserId())
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
                .userId(existingUser.getUserId())
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
                .userId(existingUser.getUserId())
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
        assertEquals(AuthErrorCode.AUTH_0030, ex.getErrorCode());
    }

    @Test
    void refresh_Failed_InvalidSubjectUuid() {
        Claims claims = new DefaultClaims(Map.of(
                "tokenId", UUID.randomUUID().toString(),
                "sub", "not-a-uuid"
        ));

        when(jwtUtil.isTokenValid(anyString())).thenReturn(true);
        when(jwtUtil.extractAllClaims(anyString())).thenReturn(claims);

        AppException ex = assertThrows(AppException.class, () -> authService.refresh(refreshRequest));
        assertEquals(AuthErrorCode.AUTH_0030, ex.getErrorCode());
    }

    @Test
    void refresh_Failed_SubjectMismatch() {
        String tokenId = UUID.randomUUID().toString();
        Claims claims = new DefaultClaims(Map.of(
                "tokenId", tokenId,
                "sub", UUID.randomUUID().toString() // Different from stored token
        ));

        AuthRefreshToken storedToken = AuthRefreshToken.builder()
                .userId(existingUser.getUserId())
                .tokenId(tokenId)
                .tokenHash("storedHash")
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build();

        when(jwtUtil.isTokenValid(anyString())).thenReturn(true);
        when(jwtUtil.extractAllClaims(anyString())).thenReturn(claims);
        when(refreshTokenRepository.findByTokenId(tokenId)).thenReturn(Optional.of(storedToken));

        AppException ex = assertThrows(AppException.class, () -> authService.refresh(refreshRequest));
        assertEquals(AuthErrorCode.AUTH_0030, ex.getErrorCode());
    }

    @Test
    void refresh_Failed_TokenRevokedInDb() {
        String tokenId = UUID.randomUUID().toString();
        Claims claims = new DefaultClaims(Map.of(
                "tokenId", tokenId,
                "sub", existingUser.getUserId().toString()
        ));

        AuthRefreshToken storedToken = AuthRefreshToken.builder()
                .userId(existingUser.getUserId())
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

    // --- TEST LOGOUT ---

    @Test
    void logout_Success() {
        String accessToken = "valid_access_token";
        LogoutRequestDto request = new LogoutRequestDto();
        request.setRefreshToken("valid_refresh_token");

        Claims accessClaims = new DefaultClaims(Map.of(
                "type", "access",
                "jti", UUID.randomUUID().toString(),
                "sub", existingUser.getUserId().toString(),
                "exp", new java.util.Date(System.currentTimeMillis() + 900000)
        ));

        String tokenId = UUID.randomUUID().toString();
        Claims refreshClaims = new DefaultClaims(Map.of(
                "type", "refresh",
                "tokenId", tokenId,
                "sub", existingUser.getUserId().toString()
        ));

        AuthRefreshToken storedToken = AuthRefreshToken.builder()
                .userId(existingUser.getUserId())
                .tokenId(tokenId)
                .build();

        when(jwtUtil.extractAllClaims(accessToken)).thenReturn(accessClaims);
        when(jwtUtil.extractAllClaims(request.getRefreshToken())).thenReturn(refreshClaims);
        when(refreshTokenRepository.findByTokenId(tokenId)).thenReturn(Optional.of(storedToken));

        authService.logout(accessToken, request);

        verify(jwtBlacklistRepository).saveAndFlush(any(AuthJwtBlacklist.class));
        verify(refreshTokenRepository).delete(storedToken);
    }

    @Test
    void logout_Success_Idempotent_RefreshNotFound() {
        String accessToken = "valid_access_token";
        LogoutRequestDto request = new LogoutRequestDto();
        request.setRefreshToken("valid_refresh_token");

        Claims accessClaims = new DefaultClaims(Map.of(
                "type", "access",
                "jti", UUID.randomUUID().toString(),
                "sub", existingUser.getUserId().toString(),
                "exp", new java.util.Date(System.currentTimeMillis() + 900000)
        ));

        String tokenId = UUID.randomUUID().toString();
        Claims refreshClaims = new DefaultClaims(Map.of(
                "type", "refresh",
                "tokenId", tokenId,
                "sub", existingUser.getUserId().toString()
        ));

        when(jwtUtil.extractAllClaims(accessToken)).thenReturn(accessClaims);
        when(jwtUtil.extractAllClaims(request.getRefreshToken())).thenReturn(refreshClaims);
        when(refreshTokenRepository.findByTokenId(tokenId)).thenReturn(Optional.empty());

        authService.logout(accessToken, request);

        verify(jwtBlacklistRepository).saveAndFlush(any(AuthJwtBlacklist.class));
        verify(refreshTokenRepository, never()).delete(any());
    }

    @Test
    void logout_Failed_AccessInvalid() {
        String accessToken = "invalid_access_token";
        LogoutRequestDto request = new LogoutRequestDto();

        when(jwtUtil.extractAllClaims(accessToken)).thenThrow(new RuntimeException("Parsing error"));

        AppException ex = assertThrows(AppException.class, () -> authService.logout(accessToken, request));
        assertEquals(AuthErrorCode.AUTH_0020, ex.getErrorCode());
    }

    @Test
    void logout_Failed_AccessTypeInvalid() {
        String accessToken = "invalid_type_access_token";
        LogoutRequestDto request = new LogoutRequestDto();

        Claims accessClaims = new DefaultClaims(Map.of(
                "type", "refresh", // SALAH TIPE (harus 'access')
                "sub", existingUser.getUserId().toString()
        ));

        when(jwtUtil.extractAllClaims(accessToken)).thenReturn(accessClaims);

        AppException ex = assertThrows(AppException.class, () -> authService.logout(accessToken, request));
        assertEquals(AuthErrorCode.AUTH_0020, ex.getErrorCode());
    }

    @Test
    void logout_Failed_RefreshInvalid() {
        String accessToken = "valid_access_token";
        LogoutRequestDto request = new LogoutRequestDto();
        request.setRefreshToken("invalid_refresh_token");

        Claims accessClaims = new DefaultClaims(Map.of(
                "type", "access",
                "jti", UUID.randomUUID().toString(),
                "sub", existingUser.getUserId().toString(),
                "exp", new java.util.Date()
        ));

        when(jwtUtil.extractAllClaims(accessToken)).thenReturn(accessClaims);
        when(jwtUtil.extractAllClaims(request.getRefreshToken())).thenThrow(new RuntimeException("Parsing error"));

        AppException ex = assertThrows(AppException.class, () -> authService.logout(accessToken, request));
        assertEquals(AuthErrorCode.AUTH_0031, ex.getErrorCode());
    }

    @Test
    void logout_Failed_RefreshTypeInvalid() {
        String accessToken = "valid_access_token";
        LogoutRequestDto request = new LogoutRequestDto();
        request.setRefreshToken("invalid_type_refresh_token");

        Claims accessClaims = new DefaultClaims(Map.of(
                "type", "access",
                "jti", UUID.randomUUID().toString(),
                "sub", existingUser.getUserId().toString(),
                "exp", new java.util.Date()
        ));

        Claims refreshClaims = new DefaultClaims(Map.of(
                "type", "access", // SALAH TIPE (harus 'refresh')
                "tokenId", UUID.randomUUID().toString(),
                "sub", existingUser.getUserId().toString()
        ));

        when(jwtUtil.extractAllClaims(accessToken)).thenReturn(accessClaims);
        when(jwtUtil.extractAllClaims(request.getRefreshToken())).thenReturn(refreshClaims);

        AppException ex = assertThrows(AppException.class, () -> authService.logout(accessToken, request));
        assertEquals(AuthErrorCode.AUTH_0031, ex.getErrorCode());
    }

    @Test
    void logout_Failed_SubjectMismatch() {
        String accessToken = "valid_access_token";
        LogoutRequestDto request = new LogoutRequestDto();
        request.setRefreshToken("valid_refresh_token");

        Claims accessClaims = new DefaultClaims(Map.of(
                "type", "access",
                "jti", UUID.randomUUID().toString(),
                "sub", UUID.randomUUID().toString(), // User A
                "exp", new java.util.Date()
        ));

        Claims refreshClaims = new DefaultClaims(Map.of(
                "type", "refresh",
                "tokenId", UUID.randomUUID().toString(),
                "sub", UUID.randomUUID().toString() // User B
        ));

        when(jwtUtil.extractAllClaims(accessToken)).thenReturn(accessClaims);
        when(jwtUtil.extractAllClaims(request.getRefreshToken())).thenReturn(refreshClaims);

        AppException ex = assertThrows(AppException.class, () -> authService.logout(accessToken, request));
        assertEquals(AuthErrorCode.AUTH_0031, ex.getErrorCode());
    }

    @Test
    void logout_Failed_RefreshTokenIdMissing() {
        String accessToken = "valid_access_token";
        LogoutRequestDto request = new LogoutRequestDto();
        request.setRefreshToken("valid_refresh_token_no_jti");

        Claims accessClaims = new DefaultClaims(Map.of(
                "type", "access",
                "jti", UUID.randomUUID().toString(),
                "sub", existingUser.getUserId().toString(),
                "exp", new java.util.Date()
        ));

        Claims refreshClaims = new DefaultClaims(Map.of(
                "type", "refresh",
                // "tokenId" sengaja dihilangkan
                "sub", existingUser.getUserId().toString()
        ));

        when(jwtUtil.extractAllClaims(accessToken)).thenReturn(accessClaims);
        when(jwtUtil.extractAllClaims(request.getRefreshToken())).thenReturn(refreshClaims);

        AppException ex = assertThrows(AppException.class, () -> authService.logout(accessToken, request));
        assertEquals(AuthErrorCode.AUTH_0031, ex.getErrorCode());
    }

    @Test
    void logout_Success_ConcurrentDuplicateBlacklist() {
        String accessToken = "valid_access_token";
        LogoutRequestDto request = new LogoutRequestDto();
        request.setRefreshToken("valid_refresh_token");

        Claims accessClaims = new DefaultClaims(Map.of(
                "type", "access",
                "jti", UUID.randomUUID().toString(),
                "sub", existingUser.getUserId().toString(),
                "exp", new java.util.Date(System.currentTimeMillis() + 900000)
        ));

        String tokenId = UUID.randomUUID().toString();
        Claims refreshClaims = new DefaultClaims(Map.of(
                "type", "refresh",
                "tokenId", tokenId,
                "sub", existingUser.getUserId().toString()
        ));

        AuthRefreshToken storedToken = AuthRefreshToken.builder()
                .userId(existingUser.getUserId())
                .tokenId(tokenId)
                .build();

        when(jwtUtil.extractAllClaims(accessToken)).thenReturn(accessClaims);
        when(jwtUtil.extractAllClaims(request.getRefreshToken())).thenReturn(refreshClaims);
        when(refreshTokenRepository.findByTokenId(tokenId)).thenReturn(Optional.of(storedToken));
        
        // Simulasikan exception constraint unique token_jti saat saveAndFlush
        when(jwtBlacklistRepository.saveAndFlush(any())).thenThrow(new org.springframework.dao.DataIntegrityViolationException("Duplicate JTI"));

        // Harus tetap sukses tanpa melempar exception ke luar
        authService.logout(accessToken, request);

        verify(jwtBlacklistRepository).saveAndFlush(any(AuthJwtBlacklist.class));
        verify(refreshTokenRepository).delete(storedToken);
    }
}
