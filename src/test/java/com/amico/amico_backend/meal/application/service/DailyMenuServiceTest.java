package com.amico.amico_backend.meal.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amico.amico_backend.common.exception.BusinessException;
import com.amico.amico_backend.meal.api.dto.request.CopyDailyMenuRequest;
import com.amico.amico_backend.meal.api.dto.request.DailyMenuOptionRequest;
import com.amico.amico_backend.meal.api.dto.request.UpsertDailyMenuRequest;
import com.amico.amico_backend.meal.domain.model.DailyMenuEntryType;
import com.amico.amico_backend.meal.domain.model.DailyMenuStatus;
import com.amico.amico_backend.meal.domain.model.MealType;
import com.amico.amico_backend.meal.infrastructure.persistence.entity.DailyMenuEntity;
import com.amico.amico_backend.meal.infrastructure.persistence.entity.DailyMenuEntryEntity;
import com.amico.amico_backend.meal.infrastructure.persistence.entity.FoodItemEntity;
import com.amico.amico_backend.meal.infrastructure.persistence.entity.MealComboEntity;
import com.amico.amico_backend.meal.infrastructure.persistence.repository.DailyMenuEntryRepository;
import com.amico.amico_backend.meal.infrastructure.persistence.repository.DailyMenuPackageItemRepository;
import com.amico.amico_backend.meal.infrastructure.persistence.repository.DailyMenuRepository;
import com.amico.amico_backend.meal.infrastructure.persistence.repository.MealPollOptionRepository;
import com.amico.amico_backend.member.application.service.SpaceMembershipResolver;
import com.amico.amico_backend.member.domain.model.MembershipRole;
import com.amico.amico_backend.member.domain.model.MembershipStatus;
import com.amico.amico_backend.member.infrastructure.persistence.entity.SpaceMembershipEntity;
import com.amico.amico_backend.member.infrastructure.persistence.repository.MemberRepository;
import com.amico.amico_backend.member.infrastructure.persistence.repository.SpaceMembershipRepository;
import com.amico.amico_backend.space.domain.model.SpaceType;
import com.amico.amico_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.amico.amico_backend.space.infrastructure.persistence.repository.SpaceRepository;
import com.amico.amico_backend.user.infrastructure.persistence.entity.UserEntity;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class DailyMenuServiceTest {

    @Mock
    private DailyMenuRepository dailyMenuRepository;

    @Mock
    private DailyMenuEntryRepository dailyMenuEntryRepository;

    @Mock
    private DailyMenuPackageItemRepository dailyMenuPackageItemRepository;

    @Mock
    private MealPollOptionRepository mealPollOptionRepository;

    @Mock
    private MealComboService mealComboService;

    @Mock
    private FoodCatalogService foodCatalogService;

    @Mock
    private SpaceRepository spaceRepository;

    @Mock
    private SpaceMembershipRepository spaceMembershipRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private MenuPlanningHistoryService menuPlanningHistoryService;

    private DailyMenuService dailyMenuService;

    private UUID spaceId;
    private UUID callerId;
    private UUID itemId;
    private UUID comboId;
    private LocalDate menuDate;

    @BeforeEach
    void setUp() {
        dailyMenuService = new DailyMenuService(
                dailyMenuRepository,
                dailyMenuEntryRepository,
                dailyMenuPackageItemRepository,
                mealPollOptionRepository,
                mealComboService,
                foodCatalogService,
                spaceRepository,
                new MealAccessService(new SpaceMembershipResolver(spaceMembershipRepository), memberRepository),
                new ObjectMapper(),
                menuPlanningHistoryService);
        spaceId = UUID.randomUUID();
        callerId = UUID.randomUUID();
        itemId = UUID.randomUUID();
        comboId = UUID.randomUUID();
        menuDate = LocalDate.of(2026, 6, 18);
        lenient()
                .when(dailyMenuEntryRepository.save(any(DailyMenuEntryEntity.class)))
                .thenAnswer(invocation -> {
                    DailyMenuEntryEntity entry = invocation.getArgument(0);
                    if (entry.getId() == null) {
                        entry.setId(UUID.randomUUID());
                    }
                    return entry;
                });
    }

    @Test
    void getMenusByDate_managerSeesDraftAndPublished() {
        stubMembership(MembershipRole.OWNER);
        DailyMenuEntity draft = menu(MealType.LUNCH, DailyMenuStatus.DRAFT);
        DailyMenuEntity published = menu(MealType.DINNER, DailyMenuStatus.PUBLISHED);
        when(dailyMenuRepository.findBySpaceAndDate(spaceId, menuDate, false, DailyMenuStatus.PUBLISHED))
                .thenReturn(List.of(draft, published));
        when(dailyMenuEntryRepository.findByDailyMenuIdIn(any())).thenReturn(List.of());

        var menus = dailyMenuService.getMenusByDate(spaceId, callerId, menuDate);

        assertThat(menus).hasSize(2);
        assertThat(menus).extracting("status").contains(DailyMenuStatus.DRAFT, DailyMenuStatus.PUBLISHED);
    }

    @Test
    void getMenusByDate_tenantSeesPublishedOnly() {
        stubMembership(MembershipRole.TENANT);
        DailyMenuEntity published = menu(MealType.LUNCH, DailyMenuStatus.PUBLISHED);
        when(dailyMenuRepository.findBySpaceAndDate(spaceId, menuDate, true, DailyMenuStatus.PUBLISHED))
                .thenReturn(List.of(published));
        when(dailyMenuEntryRepository.findByDailyMenuIdIn(any())).thenReturn(List.of());

        var menus = dailyMenuService.getMenusByDate(spaceId, callerId, menuDate);

        assertThat(menus).hasSize(1);
        assertThat(menus.get(0).getStatus()).isEqualTo(DailyMenuStatus.PUBLISHED);
    }

    @Test
    void getTodayMenus_excludesDraftsForManager() {
        stubMembership(MembershipRole.OWNER);
        DailyMenuEntity published = menu(MealType.BREAKFAST, DailyMenuStatus.PUBLISHED);
        when(dailyMenuRepository.findBySpaceAndDate(eq(spaceId), any(LocalDate.class), eq(true), eq(DailyMenuStatus.PUBLISHED)))
                .thenReturn(List.of(published));
        when(dailyMenuEntryRepository.findByDailyMenuIdIn(any())).thenReturn(List.of());

        var menus = dailyMenuService.getTodayMenus(spaceId, callerId);

        assertThat(menus).hasSize(1);
        assertThat(menus.get(0).getStatus()).isEqualTo(DailyMenuStatus.PUBLISHED);
    }

    @Test
    void upsertMenu_persistsComboAndItemEntries() {
        stubOwnerMembership();
        when(spaceRepository.findById(spaceId)).thenReturn(Optional.of(space()));

        DailyMenuEntity savedMenu = menu(MealType.LUNCH, DailyMenuStatus.DRAFT);
        when(dailyMenuRepository.findBySpaceDateAndType(spaceId, menuDate, MealType.LUNCH))
                .thenReturn(Optional.empty(), Optional.of(savedMenu));
        when(dailyMenuRepository.save(any(DailyMenuEntity.class))).thenReturn(savedMenu);
        stubEmptySync(savedMenu.getId());

        MealComboEntity combo = MealComboEntity.builder().name("Thali").isActive(true).build();
        combo.setId(comboId);
        when(mealComboService.loadCombo(spaceId, comboId)).thenReturn(combo);

        FoodItemEntity item = FoodItemEntity.builder().name("Tea").isActive(true).build();
        item.setId(itemId);
        when(foodCatalogService.loadEnabledItemForSpace(spaceId, itemId)).thenReturn(item);

        DailyMenuOptionRequest comboOption = option(DailyMenuEntryType.COMBO, comboId, null, "Standard Lunch Thali", 1);
        DailyMenuOptionRequest itemOption = option(DailyMenuEntryType.ITEM, null, itemId, "Tea", 2);

        UpsertDailyMenuRequest request = new UpsertDailyMenuRequest();
        request.setOptions(List.of(comboOption, itemOption));
        request.setNotes("Extra salad today");

        dailyMenuService.upsertMenu(spaceId, callerId, menuDate, MealType.LUNCH, request);

        ArgumentCaptor<DailyMenuEntryEntity> captor = ArgumentCaptor.forClass(DailyMenuEntryEntity.class);
        verify(dailyMenuEntryRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(DailyMenuEntryEntity::getEntryType)
                .containsExactly(DailyMenuEntryType.COMBO, DailyMenuEntryType.ITEM);
    }

    @Test
    void upsertMenu_rejectsInvalidComboId() {
        stubOwnerMembership();
        when(spaceRepository.findById(spaceId)).thenReturn(Optional.of(space()));
        DailyMenuEntity savedMenu = menu(MealType.LUNCH, DailyMenuStatus.DRAFT);
        when(dailyMenuRepository.findBySpaceDateAndType(spaceId, menuDate, MealType.LUNCH))
                .thenReturn(Optional.empty());
        when(dailyMenuRepository.save(any(DailyMenuEntity.class))).thenReturn(savedMenu);
        stubEmptySync(savedMenu.getId());
        when(mealComboService.loadCombo(spaceId, comboId))
                .thenThrow(new BusinessException("Combo is not active", HttpStatus.BAD_REQUEST));

        UpsertDailyMenuRequest request = new UpsertDailyMenuRequest();
        request.setOptions(List.of(option(DailyMenuEntryType.COMBO, comboId, null, "Thali", 1)));

        assertThatThrownBy(() -> dailyMenuService.upsertMenu(spaceId, callerId, menuDate, MealType.LUNCH, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Combo is not active");
    }

    @Test
    void upsertMenu_allowsEmptyDraftOptions() {
        stubOwnerMembership();
        when(spaceRepository.findById(spaceId)).thenReturn(Optional.of(space()));
        DailyMenuEntity savedMenu = menu(MealType.DINNER, DailyMenuStatus.DRAFT);
        when(dailyMenuRepository.findBySpaceDateAndType(spaceId, menuDate, MealType.DINNER))
                .thenReturn(Optional.empty());
        when(dailyMenuRepository.save(any(DailyMenuEntity.class))).thenReturn(savedMenu);
        stubEmptySync(savedMenu.getId());

        UpsertDailyMenuRequest request = new UpsertDailyMenuRequest();
        request.setNotes("Planning later");

        var response = dailyMenuService.upsertMenu(spaceId, callerId, menuDate, MealType.DINNER, request);

        verify(dailyMenuEntryRepository, never()).delete(any());
        assertThat(response.getOptions()).isEmpty();
    }

    @Test
    void upsertMenu_marksPublishedAsModifiedAndCapturesSnapshot() {
        stubOwnerMembership();
        when(spaceRepository.findById(spaceId)).thenReturn(Optional.of(space()));
        DailyMenuEntity published = menu(MealType.LUNCH, DailyMenuStatus.PUBLISHED);
        when(dailyMenuRepository.findBySpaceDateAndType(spaceId, menuDate, MealType.LUNCH))
                .thenReturn(Optional.of(published));
        when(dailyMenuRepository.save(published)).thenReturn(published);
        when(dailyMenuEntryRepository.findByDailyMenuIdIn(any())).thenReturn(List.of());
        stubEmptySync(published.getId());

        UpsertDailyMenuRequest request = new UpsertDailyMenuRequest();
        request.setNotes("Updated note");

        var response = dailyMenuService.upsertMenu(spaceId, callerId, menuDate, MealType.LUNCH, request);

        assertThat(response.getStatus()).isEqualTo(DailyMenuStatus.MODIFIED);
        assertThat(published.getStatus()).isEqualTo(DailyMenuStatus.MODIFIED);
        assertThat(published.getPublishedSnapshot()).isNotBlank();
    }

    @Test
    void publishMenu_requiresAvailableOption() {
        stubOwnerMembership();
        DailyMenuEntity draft = menu(MealType.LUNCH, DailyMenuStatus.DRAFT);
        when(dailyMenuRepository.findBySpaceDateAndType(spaceId, menuDate, MealType.LUNCH))
                .thenReturn(Optional.of(draft));
        when(dailyMenuEntryRepository.findByDailyMenuId(draft.getId())).thenReturn(List.of());

        assertThatThrownBy(() -> dailyMenuService.publishMenu(spaceId, callerId, menuDate, MealType.LUNCH))
                .isInstanceOf(BusinessException.class)
                .hasMessage("At least one available option is required to publish");
    }

    @Test
    void copyMenu_createsDraftFromSource() {
        stubOwnerMembership();
        when(spaceRepository.findById(spaceId)).thenReturn(Optional.of(space()));

        LocalDate sourceDate = menuDate.minusDays(1);
        DailyMenuEntity source = menuOnDate(MealType.LUNCH, DailyMenuStatus.PUBLISHED, sourceDate);
        DailyMenuEntity target = menu(MealType.LUNCH, DailyMenuStatus.DRAFT);

        when(dailyMenuRepository.findBySpaceDateAndType(spaceId, sourceDate, MealType.LUNCH))
                .thenReturn(Optional.of(source));
        when(dailyMenuRepository.findBySpaceDateAndType(spaceId, menuDate, MealType.LUNCH))
                .thenReturn(Optional.empty());
        when(dailyMenuRepository.save(any(DailyMenuEntity.class))).thenReturn(target);
        DailyMenuEntryEntity sourceEntry = DailyMenuEntryEntity.builder()
                .dailyMenu(source)
                .entryType(DailyMenuEntryType.COMBO)
                .label("Standard Lunch Thali")
                .sortOrder(1)
                .isAvailable(true)
                .build();
        when(mealPollOptionRepository.findReferencedEntryIdsByDailyMenuId(target.getId()))
                .thenReturn(Collections.emptySet());
        when(dailyMenuEntryRepository.findByDailyMenuId(source.getId())).thenReturn(List.of(sourceEntry));
        when(dailyMenuEntryRepository.findByDailyMenuId(target.getId()))
                .thenReturn(List.of(), List.of(sourceEntry));

        var response = dailyMenuService.copyMenu(spaceId, callerId, menuDate, MealType.LUNCH, sourceDate, null);

        assertThat(response.getStatus()).isEqualTo(DailyMenuStatus.DRAFT);
        verify(dailyMenuEntryRepository).save(any(DailyMenuEntryEntity.class));
    }

    @Test
    void copyMenu_rejectsPublishedTargetWithoutForce() {
        stubOwnerMembership();
        LocalDate sourceDate = menuDate.minusDays(1);
        DailyMenuEntity source = menuOnDate(MealType.LUNCH, DailyMenuStatus.PUBLISHED, sourceDate);
        DailyMenuEntity publishedTarget = menu(MealType.LUNCH, DailyMenuStatus.PUBLISHED);

        when(dailyMenuRepository.findBySpaceDateAndType(spaceId, sourceDate, MealType.LUNCH))
                .thenReturn(Optional.of(source));
        when(dailyMenuRepository.findBySpaceDateAndType(spaceId, menuDate, MealType.LUNCH))
                .thenReturn(Optional.of(publishedTarget));

        assertThatThrownBy(() ->
                        dailyMenuService.copyMenu(spaceId, callerId, menuDate, MealType.LUNCH, sourceDate, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("force=true");
    }

    @Test
    void listMenus_rejectsRangeOver31Days() {
        stubOwnerMembership();
        LocalDate from = LocalDate.of(2026, 6, 1);
        LocalDate to = LocalDate.of(2026, 7, 5);

        assertThatThrownBy(() -> dailyMenuService.listMenus(spaceId, callerId, from, to))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Date range cannot exceed 31 days");
    }

    @Test
    void staffCannotUpsertMenu() {
        stubMembership(MembershipRole.STAFF);
        UpsertDailyMenuRequest request = new UpsertDailyMenuRequest();

        assertThatThrownBy(() -> dailyMenuService.upsertMenu(spaceId, callerId, menuDate, MealType.LUNCH, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Only OWNER or MANAGER can manage meals");
    }

    @Test
    void upsertMenu_persistsItemEntryAndRoundTripsOnGet() {
        stubOwnerMembership();
        SpaceEntity space = space();
        when(spaceRepository.findById(spaceId)).thenReturn(Optional.of(space));

        DailyMenuEntity savedMenu = menu(MealType.LUNCH, DailyMenuStatus.DRAFT);
        when(dailyMenuRepository.findBySpaceDateAndType(spaceId, menuDate, MealType.LUNCH))
                .thenReturn(Optional.empty(), Optional.of(savedMenu));
        when(dailyMenuRepository.save(any(DailyMenuEntity.class))).thenReturn(savedMenu);
        stubEmptySync(savedMenu.getId());

        FoodItemEntity item = FoodItemEntity.builder().name("Green Salad").isActive(true).build();
        item.setId(itemId);
        when(foodCatalogService.loadEnabledItemForSpace(spaceId, itemId)).thenReturn(item);

        DailyMenuOptionRequest option = option(DailyMenuEntryType.ITEM, null, itemId, "Extra Salad", 2);
        UpsertDailyMenuRequest request = new UpsertDailyMenuRequest();
        request.setOptions(List.of(option));
        request.setNotes("Extra salad today");

        dailyMenuService.upsertMenu(spaceId, callerId, menuDate, MealType.LUNCH, request);

        DailyMenuEntryEntity persistedEntry = DailyMenuEntryEntity.builder()
                .dailyMenu(savedMenu)
                .entryType(DailyMenuEntryType.ITEM)
                .item(item)
                .label("Extra Salad")
                .sortOrder(2)
                .isAvailable(true)
                .build();
        persistedEntry.setId(UUID.randomUUID());
        when(dailyMenuEntryRepository.findByDailyMenuIdIn(any())).thenReturn(List.of(persistedEntry));

        var response = dailyMenuService.getMenu(spaceId, callerId, menuDate, MealType.LUNCH);

        assertThat(response.getOptions()).hasSize(1);
        assertThat(response.getOptions().get(0).getEntryType()).isEqualTo(DailyMenuEntryType.ITEM);
        assertThat(response.getOptions().get(0).getItemId()).isEqualTo(itemId);
    }

    @Test
    void upsertMenu_marksPollLinkedOrphansUnavailableInsteadOfDeleting() {
        stubOwnerMembership();
        when(spaceRepository.findById(spaceId)).thenReturn(Optional.of(space()));

        DailyMenuEntity savedMenu = menu(MealType.BREAKFAST, DailyMenuStatus.PUBLISHED);
        UUID pollLinkedEntryId = UUID.randomUUID();
        DailyMenuEntryEntity pollLinked = DailyMenuEntryEntity.builder()
                .dailyMenu(savedMenu)
                .entryType(DailyMenuEntryType.COMBO)
                .label("Old combo")
                .sortOrder(1)
                .isAvailable(true)
                .build();
        pollLinked.setId(pollLinkedEntryId);

        when(dailyMenuRepository.findBySpaceDateAndType(spaceId, menuDate, MealType.BREAKFAST))
                .thenReturn(Optional.of(savedMenu));
        when(dailyMenuRepository.save(savedMenu)).thenReturn(savedMenu);
        when(dailyMenuEntryRepository.findByDailyMenuId(savedMenu.getId())).thenReturn(List.of(pollLinked));
        when(mealPollOptionRepository.findReferencedEntryIdsByDailyMenuId(savedMenu.getId()))
                .thenReturn(Set.of(pollLinkedEntryId));

        MealComboEntity combo = MealComboEntity.builder().name("New combo").isActive(true).build();
        combo.setId(comboId);
        when(mealComboService.loadCombo(spaceId, comboId)).thenReturn(combo);
        when(dailyMenuEntryRepository.save(any(DailyMenuEntryEntity.class)))
                .thenAnswer(invocation -> {
                    DailyMenuEntryEntity entry = invocation.getArgument(0);
                    if (entry.getId() == null) {
                        entry.setId(UUID.randomUUID());
                    }
                    return entry;
                });
        when(dailyMenuEntryRepository.findByDailyMenuId(savedMenu.getId()))
                .thenReturn(List.of(pollLinked), List.of(pollLinked));

        UpsertDailyMenuRequest request = new UpsertDailyMenuRequest();
        request.setOptions(List.of(option(DailyMenuEntryType.COMBO, comboId, null, "New combo", 1)));

        dailyMenuService.upsertMenu(spaceId, callerId, menuDate, MealType.BREAKFAST, request);

        verify(dailyMenuEntryRepository, never()).delete(any());
        ArgumentCaptor<DailyMenuEntryEntity> captor = ArgumentCaptor.forClass(DailyMenuEntryEntity.class);
        verify(dailyMenuEntryRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues())
                .anyMatch(entry -> pollLinkedEntryId.equals(entry.getId()) && !entry.isAvailable());
    }

    private void stubEmptySync(UUID menuId) {
        when(dailyMenuEntryRepository.findByDailyMenuId(menuId)).thenReturn(List.of());
        when(mealPollOptionRepository.findReferencedEntryIdsByDailyMenuId(menuId))
                .thenReturn(Collections.emptySet());
    }

    private DailyMenuOptionRequest option(
            DailyMenuEntryType entryType, UUID combo, UUID item, String label, int sortOrder) {
        DailyMenuOptionRequest option = new DailyMenuOptionRequest();
        option.setEntryType(entryType);
        option.setComboId(combo);
        option.setItemId(item);
        option.setLabel(label);
        option.setSortOrder(sortOrder);
        option.setAvailable(true);
        return option;
    }

    private DailyMenuEntity menu(MealType mealType, DailyMenuStatus status) {
        return menuOnDate(mealType, status, menuDate);
    }

    private DailyMenuEntity menuOnDate(MealType mealType, DailyMenuStatus status, LocalDate date) {
        DailyMenuEntity menu = DailyMenuEntity.builder()
                .space(space())
                .menuDate(date)
                .mealType(mealType)
                .status(status)
                .isDeleted(false)
                .build();
        menu.setId(UUID.randomUUID());
        return menu;
    }

    private void stubOwnerMembership() {
        stubMembership(MembershipRole.OWNER);
    }

    private void stubMembership(MembershipRole role) {
        UserEntity user = UserEntity.builder().fullName("User").mobileNumber("9000000000").build();
        user.setId(callerId);
        SpaceEntity space = space();
        SpaceMembershipEntity membership = SpaceMembershipEntity.builder()
                .user(user)
                .space(space)
                .role(role)
                .status(MembershipStatus.ACTIVE)
                .build();
        when(spaceMembershipRepository.findMembershipByUserAndSpace(callerId, spaceId))
                .thenReturn(Optional.of(membership));
    }

    private SpaceEntity space() {
        UserEntity owner = UserEntity.builder().fullName("Owner").mobileNumber("9000000001").build();
        owner.setId(UUID.randomUUID());
        SpaceEntity space = SpaceEntity.builder()
                .owner(owner)
                .name("Test Mess 1")
                .type(SpaceType.MESS)
                .isActive(true)
                .build();
        space.setId(spaceId);
        return space;
    }
}
