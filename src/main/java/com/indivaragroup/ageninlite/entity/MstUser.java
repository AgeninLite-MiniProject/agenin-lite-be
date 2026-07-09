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
    private UUID userId;

    @Column(nullable = false, unique = true, length = 15)
    private String phoneNumber;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 100)
    private String userName;

    @Column(unique = true, length = 8)
    private String referralCode;

    private UUID referredBy;

    @Column(nullable = false, length = 10)
    private String role;

    @Column(nullable = false, length = 10)
    private String userStatus;

    @Column(nullable = false)
    private boolean isDeleted = false;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
