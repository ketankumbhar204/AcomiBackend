package com.acomi.acomi_backend.registration.infrastructure.persistence.repository;

import com.acomi.acomi_backend.registration.infrastructure.persistence.entity.RegistrationPhotoEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RegistrationPhotoRepository extends JpaRepository<RegistrationPhotoEntity, UUID> {

    @Modifying
    @Query("delete from RegistrationPhotoEntity p where p.propertyRegistrationId = :id")
    void deleteByPropertyRegistrationId(@Param("id") UUID id);

    @Modifying
    @Query("delete from RegistrationPhotoEntity p where p.messRegistrationId = :id")
    void deleteByMessRegistrationId(@Param("id") UUID id);

    int countByPropertyRegistrationId(UUID propertyRegistrationId);

    int countByMessRegistrationId(UUID messRegistrationId);
}
