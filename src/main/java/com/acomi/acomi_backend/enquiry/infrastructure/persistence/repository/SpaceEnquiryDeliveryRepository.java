package com.acomi.acomi_backend.enquiry.infrastructure.persistence.repository;

import com.acomi.acomi_backend.enquiry.domain.model.EnquiryDeliveryChannel;
import com.acomi.acomi_backend.enquiry.infrastructure.persistence.entity.SpaceEnquiryDeliveryEntity;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpaceEnquiryDeliveryRepository extends JpaRepository<SpaceEnquiryDeliveryEntity, UUID> {

    Optional<SpaceEnquiryDeliveryEntity> findBySpaceIdAndRequesterUserIdAndDeliveryChannel(
            UUID spaceId, UUID requesterUserId, EnquiryDeliveryChannel deliveryChannel);

    boolean existsBySpaceIdAndRequesterUserIdAndDeliveryChannel(
            UUID spaceId, UUID requesterUserId, EnquiryDeliveryChannel deliveryChannel);

    Optional<SpaceEnquiryDeliveryEntity>
            findBySpaceIdAndRequesterUserIdAndDeliveryChannelAndRecipientEmail(
                    UUID spaceId,
                    UUID requesterUserId,
                    EnquiryDeliveryChannel deliveryChannel,
                    String recipientEmail);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            SELECT d FROM SpaceEnquiryDeliveryEntity d
            WHERE d.spaceId = :spaceId
              AND d.requesterUserId = :requesterUserId
              AND d.deliveryChannel = :channel
              AND d.recipientEmail IS NULL
            """)
    Optional<SpaceEnquiryDeliveryEntity> findAppDeliveryForUpdate(
            @Param("spaceId") UUID spaceId,
            @Param("requesterUserId") UUID requesterUserId,
            @Param("channel") EnquiryDeliveryChannel channel);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            SELECT d FROM SpaceEnquiryDeliveryEntity d
            WHERE d.spaceId = :spaceId
              AND d.requesterUserId = :requesterUserId
              AND d.deliveryChannel = :channel
              AND d.recipientEmail = :email
            """)
    Optional<SpaceEnquiryDeliveryEntity> findEmailDeliveryForUpdate(
            @Param("spaceId") UUID spaceId,
            @Param("requesterUserId") UUID requesterUserId,
            @Param("channel") EnquiryDeliveryChannel channel,
            @Param("email") String email);

    /**
     * Active APP delivery requires both a non-expired delivery row and an open
     * (PENDING/SHARED) enquiry. Admin/auto expiry of the enquiry must not keep blocking.
     */
    @Query(
            """
            SELECT d FROM SpaceEnquiryDeliveryEntity d, SpaceEnquiryEntity e
            WHERE d.enquiryId = e.id
              AND e.status IN (
                  com.acomi.acomi_backend.enquiry.domain.model.SpaceEnquiryStatus.PENDING,
                  com.acomi.acomi_backend.enquiry.domain.model.SpaceEnquiryStatus.SHARED)
              AND d.spaceId = :spaceId
              AND d.requesterUserId = :requesterUserId
              AND d.deliveryChannel = com.acomi.acomi_backend.enquiry.domain.model.EnquiryDeliveryChannel.APP
              AND d.expiresAt > :now
            """)
    Optional<SpaceEnquiryDeliveryEntity> findActiveAppDelivery(
            @Param("spaceId") UUID spaceId,
            @Param("requesterUserId") UUID requesterUserId,
            @Param("now") LocalDateTime now);

    @Query(
            """
            SELECT d FROM SpaceEnquiryDeliveryEntity d, SpaceEnquiryEntity e
            WHERE d.enquiryId = e.id
              AND e.status IN (
                  com.acomi.acomi_backend.enquiry.domain.model.SpaceEnquiryStatus.PENDING,
                  com.acomi.acomi_backend.enquiry.domain.model.SpaceEnquiryStatus.SHARED)
              AND d.spaceId = :spaceId
              AND d.requesterUserId = :requesterUserId
              AND d.deliveryChannel = com.acomi.acomi_backend.enquiry.domain.model.EnquiryDeliveryChannel.EMAIL
              AND d.recipientEmail = :email
              AND d.expiresAt > :now
            """)
    Optional<SpaceEnquiryDeliveryEntity> findActiveEmailDelivery(
            @Param("spaceId") UUID spaceId,
            @Param("requesterUserId") UUID requesterUserId,
            @Param("email") String email,
            @Param("now") LocalDateTime now);

    @Query(
            """
            SELECT d FROM SpaceEnquiryDeliveryEntity d
            WHERE d.enquiryId = :enquiryId
              AND d.expiresAt > :now
            """)
    java.util.List<SpaceEnquiryDeliveryEntity> findActiveByEnquiryId(
            @Param("enquiryId") UUID enquiryId, @Param("now") LocalDateTime now);

    @Query(
            """
            SELECT d FROM SpaceEnquiryDeliveryEntity d
            WHERE d.spaceId = :spaceId
              AND d.requesterUserId = :requesterUserId
              AND d.expiresAt > :now
            """)
    java.util.List<SpaceEnquiryDeliveryEntity> findActiveBySpaceAndRequester(
            @Param("spaceId") UUID spaceId,
            @Param("requesterUserId") UUID requesterUserId,
            @Param("now") LocalDateTime now);
}