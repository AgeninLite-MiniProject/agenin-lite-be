package com.indivaragroup.ageninlite.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "auth_jwt_blacklist")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthJwtBlacklist {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "blacklist_id")
    private UUID blacklistId;

    @Column(name = "token_jti", nullable = false)
    private UUID tokenJti;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

}
