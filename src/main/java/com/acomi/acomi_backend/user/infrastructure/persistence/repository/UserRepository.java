package com.acomi.acomi_backend.user.infrastructure.persistence.repository;

import com.acomi.acomi_backend.user.domain.model.SystemRole;
import com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, UUID> {

    Optional<UserEntity> findByMobileNumber(String mobileNumber);

    Optional<UserEntity> findByMobileNumberAndIsActiveTrue(String mobileNumber);

    boolean existsByMobileNumber(String mobileNumber);

    boolean existsByMobileNumberAndIsActiveTrue(String mobileNumber);

    Optional<UserEntity> findByIdAndIsActiveTrue(UUID id);

    /**
     * Phone-verified app accounts. Platform admins are excluded via {@code systemRole}.
     * Role selection, onboarding, and property/mess ownership are not part of this query.
     */
    long countByMobileVerifiedAtIsNotNullAndIsActiveTrueAndSystemRole(SystemRole systemRole);

    Page<UserEntity> findByMobileVerifiedAtIsNotNullAndIsActiveTrueAndSystemRole(
            SystemRole systemRole, Pageable pageable);
}
