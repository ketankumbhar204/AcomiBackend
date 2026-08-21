package com.acomi.acomi_backend.user.infrastructure.persistence.repository;

import com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, UUID> {

    Optional<UserEntity> findByMobileNumber(String mobileNumber);

    Optional<UserEntity> findByMobileNumberAndIsActiveTrue(String mobileNumber);

    boolean existsByMobileNumber(String mobileNumber);

    boolean existsByMobileNumberAndIsActiveTrue(String mobileNumber);

    Optional<UserEntity> findByIdAndIsActiveTrue(UUID id);
}
