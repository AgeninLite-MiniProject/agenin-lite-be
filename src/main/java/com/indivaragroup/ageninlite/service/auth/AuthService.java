package com.indivaragroup.ageninlite.service.auth;

import com.indivaragroup.ageninlite.common.exception.AppException;
import com.indivaragroup.ageninlite.common.exception.code.AuthErrorCode;
import com.indivaragroup.ageninlite.dto.auth.*;
import com.indivaragroup.ageninlite.entity.AuthRefreshToken;
import com.indivaragroup.ageninlite.entity.MstUser;
import com.indivaragroup.ageninlite.repository.auth.RefreshTokenRepository;
import com.indivaragroup.ageninlite.repository.auth.UserRepository;
import com.indivaragroup.ageninlite.security.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Transactional
    public RegisterResponseDto register(RegisterRequestDto request) {
        log.info("Process register for phone: {}", request.getPhoneNumber());

        // 1. Cek Ketersediaan Phone & Email
        if (userRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new AppException(AuthErrorCode.AUTH_0001);
        }
        if (request.getEmail() != null && !request.getEmail().isEmpty()) {
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new AppException(AuthErrorCode.AUTH_0004);
            }
        }

        UUID referredById = null;

        // 2. Cek Referral Code (Jika Ada)
        if (request.getReferralCode() != null && !request.getReferralCode().isEmpty()) {
            MstUser inviter = userRepository.findByReferralCode(request.getReferralCode())
                    .orElseThrow(() -> new AppException(AuthErrorCode.AUTH_0006));

            if (inviter.isDeleted()) {
                throw new AppException(AuthErrorCode.AUTH_0008);
            }

            int downlineCount = userRepository.countByReferredBy(inviter.getUserId());
            if (downlineCount >= 10) {
                throw new AppException(AuthErrorCode.AUTH_0007);
            }

            referredById = inviter.getUserId();
        }

        // 3. Persiapan Data
        String newReferralCode = generateReferralCode();
        
        MstUser newUser = MstUser.builder()
                .userName(request.getName())
                .phoneNumber(request.getPhoneNumber())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .referralCode(newReferralCode)
                .referredBy(referredById)
                .role("AGENT")
                .userStatus("PASSIVE")
                .build();

        // 4. Simpan
        MstUser savedUser = userRepository.save(newUser);

        return RegisterResponseDto.builder()
                .userId(savedUser.getUserId())
                .name(savedUser.getUserName())
                .phoneNumber(savedUser.getPhoneNumber())
                .referralCode(savedUser.getReferralCode())
                .referredBy(savedUser.getReferredBy())
                .role(savedUser.getRole())
                .userStatus(savedUser.getUserStatus())
                .message("Registration successful")
                .build();
    }

    @Transactional
    public LoginResponseDto login(LoginRequestDto request) {
        log.info("Process login for phone: {}", request.getPhoneNumber());

        // 1. Cari User
        MstUser user = userRepository.findByPhoneNumber(request.getPhoneNumber())
                .orElseThrow(() -> new AppException(AuthErrorCode.AUTH_0010));

        // 2. Cek Password
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new AppException(AuthErrorCode.AUTH_0010);
        }

        // 3. Cek Blokir
        if (user.isDeleted()) {
            throw new AppException(AuthErrorCode.AUTH_0011);
        }

        // 4. Generate Token
        String accessToken = jwtUtil.generateToken(user.getUserId(), user.getPhoneNumber(), user.getRole());
        
        String tokenIdStr = UUID.randomUUID().toString();
        String refreshTokenString = jwtUtil.generateRefreshToken(user.getUserId(), tokenIdStr);

        String sha256Hash = org.apache.commons.codec.digest.DigestUtils.sha256Hex(refreshTokenString);
        String hashedToken = passwordEncoder.encode(sha256Hash);

        AuthRefreshToken refreshToken = AuthRefreshToken.builder()
                .userId(user.getUserId())
                .tokenId(tokenIdStr)
                .tokenHash(hashedToken)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();

        refreshTokenRepository.save(refreshToken);

        return LoginResponseDto.builder()
                .accessToken(accessToken)
                .refreshToken(refreshTokenString)
                .tokenType("Bearer")
                .build();
    }

    @Transactional
    public RefreshResponseDto refresh(RefreshRequestDto request) {
        log.info("Process refresh token");
        String rawToken = request.getRefreshToken();
        // 1. Validasi struktur & masa aktif JWT dari library
        if (!jwtUtil.isTokenValid(rawToken)) {
            throw new AppException(AuthErrorCode.AUTH_0030);
        }
        // 2. Ekstrak data dari dalam token (Claims)
        Claims claims;
        try {
            claims = jwtUtil.extractAllClaims(rawToken);
        } catch (Exception e) {
            throw new AppException(AuthErrorCode.AUTH_0030);
        }
        String tokenIdStr = claims.get("tokenId", String.class);
        if (tokenIdStr == null) {
            throw new AppException(AuthErrorCode.AUTH_0030);
        }
        UUID userId = UUID.fromString(claims.getSubject());
        // 3. Cari Data Refresh Token di Database
        AuthRefreshToken storedToken = refreshTokenRepository.findByTokenId(tokenIdStr)
                .orElseThrow(() -> new AppException(AuthErrorCode.AUTH_0030));
        // 4. Pastikan token belum di-revoke dan belum expired di database
        if (storedToken.getRevokedAt() != null || storedToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new AppException(AuthErrorCode.AUTH_0030);
        }
        // 5. Validasi Hashing (Bypass limit 72 byte BCrypt)
        String sha256Hash = org.apache.commons.codec.digest.DigestUtils.sha256Hex(rawToken);
        if (!passwordEncoder.matches(sha256Hash, storedToken.getTokenHash())) {
            throw new AppException(AuthErrorCode.AUTH_0030);
        }
        // 6. Cek status user (pastikan belum dihapus)
        MstUser user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(AuthErrorCode.AUTH_0030));

        if (user.isDeleted()) {
            throw new AppException(AuthErrorCode.AUTH_0011);
        }
        // 7. Token Rotation (Cabut / Revoke token lama)
        storedToken.setRevokedAt(LocalDateTime.now());
        refreshTokenRepository.save(storedToken);
        // 8. Buat token baru
        String newAccessToken = jwtUtil.generateToken(user.getUserId(), user.getPhoneNumber(), user.getRole());

        String newTokenId = UUID.randomUUID().toString();
        String newRefreshToken = jwtUtil.generateRefreshToken(user.getUserId(), newTokenId);
        
        String newSha256Hash = org.apache.commons.codec.digest.DigestUtils.sha256Hex(newRefreshToken);

        AuthRefreshToken newStoredToken = AuthRefreshToken.builder()
                .userId(user.getUserId())
                .tokenId(newTokenId)
                .tokenHash(passwordEncoder.encode(newSha256Hash))
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();
        refreshTokenRepository.save(newStoredToken);
        // 9. Kembalikan Response
        return RefreshResponseDto.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .expiresIn(900)
                .build();
    }

    private String generateReferralCode() {
        return "AGN-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
    }
}
