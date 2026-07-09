package com.indivaragroup.ageninlite.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "mst_users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MstUser {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "phone_number", nullable = false, unique = true, length = 20)
    private String phoneNumber;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "user_name", nullable = false, length = 100)
    private String userName;

    @Column(name = "email", unique = true, length = 100)
    private String email;

    @Column(name = "referred_by")
    private UUID referredBy;

    @Column(name = "referral_code", unique = true, length = 10)
    private String referralCode;

    @Column(name = "role", nullable = false, length = 20)
    private String role;

    @Column(name = "user_status", nullable = false, length = 20)
    private String userStatus;

    @Builder.Default
    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
