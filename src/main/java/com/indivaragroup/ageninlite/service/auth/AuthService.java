package com.indivaragroup.ageninlite.service.auth;

import com.indivaragroup.ageninlite.common.exception.AppException;
import com.indivaragroup.ageninlite.common.exception.code.AuthErrorCode;
import com.indivaragroup.ageninlite.dto.auth.LoginRequestDto;
import com.indivaragroup.ageninlite.dto.auth.LoginResponseDto;
import com.indivaragroup.ageninlite.dto.auth.RegisterRequestDto;
import com.indivaragroup.ageninlite.dto.auth.RegisterResponseDto;
import com.indivaragroup.ageninlite.entity.AuthRefreshToken;
import com.indivaragroup.ageninlite.entity.MstUser;
import com.indivaragroup.ageninlite.repository.auth.RefreshTokenRepository;
import com.indivaragroup.ageninlite.repository.auth.UserRepository;
import com.indivaragroup.ageninlite.security.JwtUtil;
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
        log.info("Process register for phone: {}", request.getPhone());

        // 1. Cek Ketersediaan Phone & Email
        if (userRepository.existsByPhoneNumber(request.getPhone())) {
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
                .phoneNumber(request.getPhone())
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
                .id(savedUser.getUserId())
                .name(savedUser.getUserName())
                .phone(savedUser.getPhoneNumber())
                .email(savedUser.getEmail())
                .referralCode(savedUser.getReferralCode())
                .status(savedUser.getUserStatus())
                .message("Registration successful")
                .build();
    }

    @Transactional
    public LoginResponseDto login(LoginRequestDto request) {
        log.info("Process login for phone: {}", request.getPhone());

        // 1. Cari User
        MstUser user = userRepository.findByPhoneNumber(request.getPhone())
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
        
        // FIX: BCrypt has a hard limit of 72 bytes. A JWT string is >200 bytes.
        // To satisfy the DB requirement without crashing, we BCrypt hash the unique tokenIdStr instead.
        String hashedToken = passwordEncoder.encode(tokenIdStr);

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

    private String generateReferralCode() {
        // Simple generation: AGN-XXXX
        return "AGN-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
    }
}
