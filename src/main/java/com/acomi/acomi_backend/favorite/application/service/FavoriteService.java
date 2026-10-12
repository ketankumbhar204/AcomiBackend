package com.acomi.acomi_backend.favorite.application.service;

import com.acomi.acomi_backend.favorite.api.dto.response.FavoriteStateResponse;
import com.acomi.acomi_backend.favorite.infrastructure.persistence.entity.UserSpaceFavoriteEntity;
import com.acomi.acomi_backend.favorite.infrastructure.persistence.repository.UserSpaceFavoriteRepository;
import com.acomi.acomi_backend.space.api.dto.response.DiscoverSpaceCardResponse;
import com.acomi.acomi_backend.space.application.service.SpaceDiscoverService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final UserSpaceFavoriteRepository favoriteRepository;
    private final SpaceDiscoverService spaceDiscoverService;

    @Transactional
    public FavoriteStateResponse add(UUID userId, UUID spaceId) {
        spaceDiscoverService.requireDiscoverable(spaceId);
        if (favoriteRepository.findByUserIdAndSpaceId(userId, spaceId).isPresent()) {
            return favorited(spaceId);
        }
        try {
            favoriteRepository.save(UserSpaceFavoriteEntity.builder()
                    .userId(userId)
                    .spaceId(spaceId)
                    .build());
        } catch (DataIntegrityViolationException ignored) {
            // A concurrent add already stored this user/listing pair.
        }
        return favorited(spaceId);
    }

    @Transactional
    public FavoriteStateResponse remove(UUID userId, UUID spaceId) {
        favoriteRepository.deleteByUserIdAndSpaceId(userId, spaceId);
        return FavoriteStateResponse.builder().spaceId(spaceId).favorited(false).build();
    }

    @Transactional(readOnly = true)
    public List<UUID> listIds(UUID userId) {
        return favoriteRepository.findSpaceIdsByUserId(userId);
    }

    @Transactional(readOnly = true)
    public List<DiscoverSpaceCardResponse> list(UUID userId) {
        List<UUID> spaceIds = favoriteRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(UserSpaceFavoriteEntity::getSpaceId)
                .toList();
        return spaceDiscoverService.cardsFor(userId, spaceIds);
    }

    private static FavoriteStateResponse favorited(UUID spaceId) {
        return FavoriteStateResponse.builder().spaceId(spaceId).favorited(true).build();
    }
}
