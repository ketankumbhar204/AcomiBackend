package com.acomi.acomi_backend.favorite.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.common.exception.ResourceNotFoundException;
import com.acomi.acomi_backend.favorite.infrastructure.persistence.entity.UserSpaceFavoriteEntity;
import com.acomi.acomi_backend.favorite.infrastructure.persistence.repository.UserSpaceFavoriteRepository;
import com.acomi.acomi_backend.space.api.dto.response.DiscoverSpaceCardResponse;
import com.acomi.acomi_backend.space.application.service.SpaceDiscoverService;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class FavoriteServiceTest {

    @Mock
    private UserSpaceFavoriteRepository favoriteRepository;

    @Mock
    private SpaceDiscoverService spaceDiscoverService;

    @InjectMocks
    private FavoriteService favoriteService;

    @Test
    void addPersistsOneFavorite() {
        UUID userId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        when(favoriteRepository.findByUserIdAndSpaceId(userId, spaceId)).thenReturn(Optional.empty());

        var response = favoriteService.add(userId, spaceId);

        assertThat(response.isFavorited()).isTrue();
        assertThat(response.getSpaceId()).isEqualTo(spaceId);
        verify(spaceDiscoverService).requireDiscoverable(spaceId);
        verify(favoriteRepository).save(any(UserSpaceFavoriteEntity.class));
    }

    @Test
    void addDoesNotCreateADuplicateRow() {
        UUID userId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        when(favoriteRepository.findByUserIdAndSpaceId(userId, spaceId))
                .thenReturn(Optional.of(UserSpaceFavoriteEntity.builder()
                        .userId(userId)
                        .spaceId(spaceId)
                        .build()));

        var response = favoriteService.add(userId, spaceId);

        assertThat(response.isFavorited()).isTrue();
        verify(favoriteRepository, never()).save(any());
    }

    @Test
    void addTreatsAUniqueConstraintRaceAsAlreadySaved() {
        UUID userId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        when(favoriteRepository.findByUserIdAndSpaceId(userId, spaceId)).thenReturn(Optional.empty());
        when(favoriteRepository.save(any(UserSpaceFavoriteEntity.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        var response = favoriteService.add(userId, spaceId);

        assertThat(response.isFavorited()).isTrue();
    }

    @Test
    void addRejectsAListingThatIsNotDiscoverable() {
        UUID userId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new ResourceNotFoundException("Space", "id", spaceId))
                .when(spaceDiscoverService)
                .requireDiscoverable(spaceId);

        assertThatThrownBy(() -> favoriteService.add(userId, spaceId))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(favoriteRepository, never()).save(any());
    }

    @Test
    void removeIsIdempotent() {
        UUID userId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();

        var response = favoriteService.remove(userId, spaceId);

        assertThat(response.isFavorited()).isFalse();
        verify(favoriteRepository).deleteByUserIdAndSpaceId(userId, spaceId);
    }

    @Test
    void listKeepsSavedOrderAndReturnsCards() {
        UUID userId = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(favoriteRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(
                UserSpaceFavoriteEntity.builder().userId(userId).spaceId(first).build(),
                UserSpaceFavoriteEntity.builder().userId(userId).spaceId(second).build()));
        DiscoverSpaceCardResponse card = DiscoverSpaceCardResponse.builder()
                .spaceId(first)
                .name("Sunrise PG")
                .type(SpaceType.PG)
                .build();
        when(spaceDiscoverService.cardsFor(userId, List.of(first, second))).thenReturn(List.of(card));

        assertThat(favoriteService.list(userId)).containsExactly(card);
    }

    @Test
    void listIdsReturnsSavedSpaceIds() {
        UUID userId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        when(favoriteRepository.findSpaceIdsByUserId(userId)).thenReturn(List.of(spaceId));

        assertThat(favoriteService.listIds(userId)).containsExactly(spaceId);
    }
}
