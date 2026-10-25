package com.acomi.acomi_backend.meal.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.inOrder;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.meal.api.dto.request.SubmitMealPollOptionQuantityRequest;
import com.acomi.acomi_backend.meal.api.dto.request.SubmitMealPollResponsesRequest;
import com.acomi.acomi_backend.meal.api.dto.request.SubmitMealPollSelectionRequest;
import com.acomi.acomi_backend.meal.api.dto.response.MealPollDayResponse;
import com.acomi.acomi_backend.meal.api.dto.response.MealPollResponse;
import com.acomi.acomi_backend.meal.application.support.MealBillingResolver;
import com.acomi.acomi_backend.meal.domain.model.MealParticipationStatus;
import com.acomi.acomi_backend.meal.domain.model.MealPollOptionType;
import com.acomi.acomi_backend.meal.domain.model.MealPollPaymentChoice;
import com.acomi.acomi_backend.meal.domain.model.MealPollStatus;
import com.acomi.acomi_backend.meal.domain.model.MealType;
import com.acomi.acomi_backend.meal.domain.policy.MealPollEligibilityPolicy;
import com.acomi.acomi_backend.meal.domain.policy.MemberSubscriptionPolicy;
import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.DailyMenuEntity;
import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.MealParticipationEntity;
import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.MealPollEntity;
import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.MealPollOptionEntity;
import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.MealPollResponseEntity;
import com.acomi.acomi_backend.meal.infrastructure.persistence.repository.DailyMenuEntryRepository;
import com.acomi.acomi_backend.meal.infrastructure.persistence.repository.DailyMenuPackageItemRepository;
import com.acomi.acomi_backend.meal.infrastructure.persistence.repository.DailyMenuRepository;
import com.acomi.acomi_backend.meal.infrastructure.persistence.repository.MealComboItemRepository;
import com.acomi.acomi_backend.meal.infrastructure.persistence.repository.MealDeliveryLocationRepository;
import com.acomi.acomi_backend.meal.infrastructure.persistence.repository.MealParticipationRepository;
import com.acomi.acomi_backend.meal.infrastructure.persistence.repository.MealPollDayPaymentRepository;
import com.acomi.acomi_backend.meal.infrastructure.persistence.repository.MealPollMemberDeliveryRepository;
import com.acomi.acomi_backend.meal.infrastructure.persistence.repository.MealPollOptionRepository;
import com.acomi.acomi_backend.meal.infrastructure.persistence.repository.MealPollRepository;
import com.acomi.acomi_backend.meal.infrastructure.persistence.repository.MealPollResponseRepository;
import com.acomi.acomi_backend.meal.infrastructure.persistence.repository.SpaceFoodItemSettingsRepository;
import com.acomi.acomi_backend.member.domain.model.MembershipRole;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.MemberEntity;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.SpaceMembershipEntity;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.MemberRepository;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.SpaceMembershipRepository;
import com.acomi.acomi_backend.notification.application.service.NotificationService;
import com.acomi.acomi_backend.payment.application.service.MealDaySpacePaymentBridge;
import com.acomi.acomi_backend.payment.application.service.PaymentReferenceService;
import com.acomi.acomi_backend.space.domain.model.MealBillingType;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.space.infrastructure.persistence.repository.SpaceRepository;
import com.acomi.acomi_backend.storage.application.service.StoredFileService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MealPollServiceSubmitResponsesTest {

    @Mock private MealPollRepository pollRepository;
    @Mock private MealPollOptionRepository optionRepository;
    @Mock private MealPollResponseRepository responseRepository;
    @Mock private MealPollMemberDeliveryRepository memberDeliveryRepository;
    @Mock private MealPollDayPaymentRepository dayPaymentRepository;
    @Mock private MealDeliveryLocationRepository deliveryLocationRepository;
    @Mock private MealDeliveryLocationService deliveryLocationService;
    @Mock private MealParticipationLastDeliveryService lastDeliveryService;
    @Mock private DailyMenuRepository dailyMenuRepository;
    @Mock private DailyMenuEntryRepository dailyMenuEntryRepository;
    @Mock private MealComboItemRepository mealComboItemRepository;
    @Mock private DailyMenuPackageItemRepository dailyMenuPackageItemRepository;
    @Mock private MealParticipationRepository participationRepository;
    @Mock private SpaceRepository spaceRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private MealAccessService mealAccessService;
    @Mock private MemberMealBalanceService memberMealBalanceService;
    @Mock private MealBillingResolver mealBillingResolver;
    @Mock private MealPollPaymentEventService paymentEventService;
    @Mock private MealPollEligibilityPolicy pollEligibilityPolicy;
    @Mock private MemberSubscriptionPolicy subscriptionPolicy;
    @Mock private NotificationService notificationService;
    @Mock private SpaceMembershipRepository membershipRepository;
    @Mock private MealDaySpacePaymentBridge mealDaySpacePaymentBridge;
    @Mock private PaymentReferenceService paymentReferenceService;
    @Mock private StoredFileService storedFileService;
    @Mock private SpaceFoodItemSettingsRepository spaceFoodItemSettingsRepository;

    @InjectMocks private MealPollService mealPollService;

    private final UUID spaceId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();
    private final UUID memberId = UUID.randomUUID();
    private final LocalDate pollDate = LocalDate.now().plusDays(1);

    private SpaceEntity space;
    private MemberEntity member;
    private final Map<MealType, MealPollEntity> loadedPolls = new EnumMap<>(MealType.class);
    private final Map<MealType, MealPollEntity> managedPolls = new EnumMap<>(MealType.class);
    private final Map<MealType, MealPollOptionEntity> options = new EnumMap<>(MealType.class);
    private final List<MealPollResponseEntity> stored = new ArrayList<>();

    @BeforeEach
    void setUp() {
        space = SpaceEntity.builder().type(SpaceType.PG).timezone("Asia/Kolkata").build();
        space.setId(spaceId);
        member = MemberEntity.builder().fullName("Rahul Kumar").build();
        member.setId(memberId);

        for (MealType mealType : List.of(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER)) {
            MealPollEntity loaded = openPoll(mealType);
            MealPollEntity managed = openPoll(mealType);
            managed.setId(loaded.getId());
            loadedPolls.put(mealType, loaded);
            managedPolls.put(mealType, managed);
            options.put(mealType, option(loaded, "Dal Rice Combo"));
        }
    }

    @Test
    void tenantSavesBreakfastLunchAndDinner() {
        stubParticipant(MembershipRole.TENANT, SpaceType.PG);

        MealPollDayResponse day = mealPollService.submitResponses(
                spaceId, callerId, pollDate, request(single(MealType.BREAKFAST), single(MealType.LUNCH), single(MealType.DINNER)));

        assertThat(stored).hasSize(3);
        assertThat(stored).allSatisfy(response -> {
            MealType mealType = response.getPoll().getMealType();
            assertThat(response.getPoll()).isSameAs(managedPolls.get(mealType));
            assertThat(response.getMember()).isSameAs(member);
            assertThat(response.getSelectedOption()).isSameAs(options.get(mealType));
            assertThat(response.getQuantity()).isEqualTo(1);
        });
        assertThat(selectedIds(day)).containsExactlyInAnyOrder(
                options.get(MealType.BREAKFAST).getId(),
                options.get(MealType.LUNCH).getId(),
                options.get(MealType.DINNER).getId());
        assertThat(day.getMyChooseMealsBlockReason()).isNull();

        InOrder order = inOrder(responseRepository, pollRepository, optionRepository);
        order.verify(responseRepository).deleteByPollIdAndMemberId(loadedPolls.get(MealType.BREAKFAST).getId(), memberId);
        order.verify(pollRepository).findById(loadedPolls.get(MealType.BREAKFAST).getId());
        order.verify(optionRepository).findById(options.get(MealType.BREAKFAST).getId());
        order.verify(responseRepository).save(any(MealPollResponseEntity.class));
    }

    @Test
    void customerSavesMessQuantities() {
        space.setType(SpaceType.MESS);
        stubParticipant(MembershipRole.CUSTOMER, SpaceType.MESS);
        lenient().when(deliveryLocationRepository.findBySpaceIdAndActiveTrueOrderBySortOrderAscNameAsc(spaceId))
                .thenReturn(List.of());

        SubmitMealPollSelectionRequest breakfast = quantities(
                MealType.BREAKFAST,
                quantity(options.get(MealType.BREAKFAST).getId(), 2),
                quantity(UUID.randomUUID(), 0));
        options.get(MealType.BREAKFAST).setId(options.get(MealType.BREAKFAST).getId());
        MealPollOptionEntity extra = option(loadedPolls.get(MealType.BREAKFAST), "Boiled Egg");
        breakfast.getOptions().get(1).setOptionId(extra.getId());
        lenient().when(optionRepository.findById(extra.getId())).thenReturn(Optional.of(extra));

        SubmitMealPollResponsesRequest request = request(breakfast, quantities(MealType.LUNCH, quantity(options.get(MealType.LUNCH).getId(), 1)));
        request.setPaymentChoice(MealPollPaymentChoice.PAY_LATER);

        MealPollDayResponse day = mealPollService.submitResponses(spaceId, callerId, pollDate, request);

        assertThat(stored).hasSize(2);
        assertThat(stored).extracting(MealPollResponseEntity::getQuantity).containsExactlyInAnyOrder(2, 1);
        assertThat(day.getPolls()).isNotEmpty();
        verify(dayPaymentRepository).save(any());
    }

    @Test
    void partialSelectionSavesOnlyChosenMeals() {
        stubParticipant(MembershipRole.TENANT, SpaceType.PG);

        mealPollService.submitResponses(spaceId, callerId, pollDate, request(single(MealType.DINNER)));

        assertThat(stored).hasSize(1);
        assertThat(stored.get(0).getPoll().getMealType()).isEqualTo(MealType.DINNER);
        verify(responseRepository, never()).deleteByPollIdAndMemberId(loadedPolls.get(MealType.BREAKFAST).getId(), memberId);
        verify(responseRepository, never()).deleteByPollIdAndMemberId(loadedPolls.get(MealType.LUNCH).getId(), memberId);
    }

    @Test
    void invalidOptionIdIsRejected() {
        stubParticipant(MembershipRole.TENANT, SpaceType.PG);
        SubmitMealPollSelectionRequest selection = single(MealType.LUNCH);
        selection.setSelectedOptionId(UUID.randomUUID());

        assertThatThrownBy(() -> mealPollService.submitResponses(spaceId, callerId, pollDate, request(selection)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Invalid menu option");
        assertThat(stored).isEmpty();
    }

    @Test
    void optionFromAnotherPollIsRejected() {
        stubParticipant(MembershipRole.TENANT, SpaceType.PG);
        MealPollEntity otherPoll = openPoll(MealType.LUNCH);
        MealPollOptionEntity foreign = option(otherPoll, "Other space combo");
        SubmitMealPollSelectionRequest selection = single(MealType.LUNCH);
        selection.setSelectedOptionId(foreign.getId());
        lenient().when(optionRepository.findById(foreign.getId())).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> mealPollService.submitResponses(spaceId, callerId, pollDate, request(selection)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Option does not belong to this poll");
        assertThat(stored).isEmpty();
    }

    @Test
    void ownerCannotSubmitForAParticipant() {
        SpaceMembershipEntity membership = membership(MembershipRole.OWNER);
        lenient().when(mealAccessService.requireViewMeals(spaceId, callerId)).thenReturn(membership);
        lenient().when(mealAccessService.isParticipantScopeOnly(membership)).thenReturn(false);

        assertThatThrownBy(() -> mealPollService.submitResponses(
                        spaceId, callerId, pollDate, request(single(MealType.BREAKFAST))))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Only meal participants can submit poll responses");
        verify(responseRepository, never()).save(any());
    }

    @Test
    void callerWithoutOwnMemberProfileCannotSave() {
        SpaceMembershipEntity membership = membership(MembershipRole.TENANT);
        lenient().when(mealAccessService.requireViewMeals(spaceId, callerId)).thenReturn(membership);
        lenient().when(mealAccessService.isParticipantScopeOnly(membership)).thenReturn(true);
        lenient().when(mealAccessService.resolveOwnMemberId(spaceId, callerId))
                .thenThrow(new BusinessException("No member profile linked to your account", org.springframework.http.HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> mealPollService.submitResponses(
                        spaceId, callerId, pollDate, request(single(MealType.BREAKFAST))))
                .isInstanceOf(BusinessException.class)
                .hasMessage("No member profile linked to your account");
        verify(responseRepository, never()).save(any());
    }

    @Test
    void closedPollRejectsTheSave() {
        stubParticipant(MembershipRole.TENANT, SpaceType.PG);
        loadedPolls.get(MealType.BREAKFAST).setStatus(MealPollStatus.CLOSED);

        assertThatThrownBy(() -> mealPollService.submitResponses(
                        spaceId, callerId, pollDate, request(single(MealType.BREAKFAST))))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Poll is closed for Breakfast");
        assertThat(stored).isEmpty();
    }

    @Test
    void openPollPastCloseTimeIsRejectedWithoutSavingChoices() {
        stubParticipant(MembershipRole.TENANT, SpaceType.PG);
        loadedPolls.get(MealType.LUNCH).setPollCloseAt(LocalDateTime.now().minusMinutes(5));
        lenient().when(pollRepository.save(any(MealPollEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> mealPollService.submitResponses(
                        spaceId, callerId, pollDate, request(single(MealType.LUNCH))))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Poll is closed for Lunch");
        verify(responseRepository, never()).save(any());
    }

    @Test
    void missingOptionAndNegativeQuantityAreRejected() {
        stubParticipant(MembershipRole.TENANT, SpaceType.PG);
        SubmitMealPollSelectionRequest missing = new SubmitMealPollSelectionRequest();
        missing.setMealType(MealType.BREAKFAST);

        assertThatThrownBy(() -> mealPollService.submitResponses(spaceId, callerId, pollDate, request(missing)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Select a meal option");

        space.setType(SpaceType.MESS);
        stubParticipant(MembershipRole.CUSTOMER, SpaceType.MESS);
        SubmitMealPollResponsesRequest messRequest = request(
                quantities(MealType.DINNER, quantity(options.get(MealType.DINNER).getId(), -1)));
        messRequest.setPaymentChoice(MealPollPaymentChoice.PAY_LATER);

        assertThatThrownBy(() -> mealPollService.submitResponses(spaceId, callerId, pollDate, messRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Quantity must be zero or greater");
        assertThat(stored).isEmpty();
    }

    @Test
    void repositoryFailureDoesNotReportSuccess() {
        stubParticipant(MembershipRole.TENANT, SpaceType.PG);
        lenient().when(responseRepository.save(any(MealPollResponseEntity.class)))
                .thenThrow(new RuntimeException("database unavailable"));

        assertThatThrownBy(() -> mealPollService.submitResponses(
                        spaceId, callerId, pollDate, request(single(MealType.BREAKFAST))))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("database unavailable");
    }

    @Test
    void repeatedSubmissionUpdatesTheSameSelection() {
        stubParticipant(MembershipRole.TENANT, SpaceType.PG);
        SubmitMealPollResponsesRequest request = request(single(MealType.BREAKFAST));

        mealPollService.submitResponses(spaceId, callerId, pollDate, request);
        mealPollService.submitResponses(spaceId, callerId, pollDate, request);

        assertThat(stored).hasSize(1);
        assertThat(stored.get(0).getSelectedOption().getId()).isEqualTo(options.get(MealType.BREAKFAST).getId());
        verify(responseRepository, org.mockito.Mockito.times(1))
                .deleteByPollIdAndMemberId(loadedPolls.get(MealType.BREAKFAST).getId(), memberId);
    }

    @Test
    void savedChoicesAreReturnedForTheNextRead() {
        stubParticipant(MembershipRole.TENANT, SpaceType.PG);

        MealPollDayResponse day = mealPollService.submitResponses(
                spaceId, callerId, pollDate, request(single(MealType.LUNCH)));

        MealPollResponse lunch = day.getPolls().stream()
                .filter(poll -> poll.getMealType() == MealType.LUNCH)
                .findFirst()
                .orElseThrow();
        assertThat(lunch.getMySelectedOptionId()).isEqualTo(options.get(MealType.LUNCH).getId());
    }

    @Test
    void openPollsTellAPgTenantWithoutParticipationWhyTheyCannotChoose() {
        stubParticipant(MembershipRole.TENANT, SpaceType.PG);
        lenient().when(participationRepository.findAllNonStoppedBySpaceId(spaceId)).thenReturn(List.of());
        lenient().when(participationRepository.findBySpaceIdAndMemberIdAndStatus(any(), any(), any()))
                .thenReturn(Optional.empty());
        lenient().when(memberRepository.findByIdAndSpaceIdAndActiveTrue(memberId, spaceId))
                .thenReturn(Optional.of(member));

        MealPollDayResponse day = mealPollService.getPollsForDate(spaceId, callerId, pollDate);

        assertThat(day.getPolls()).isNotEmpty();
        assertThat(day.getMyChooseMealsBlockReason())
                .isEqualTo("Move in to a bed before choosing meals. The owner has to allocate your stay and turn on meals.");

        assertThatThrownBy(() -> mealPollService.submitResponses(
                        spaceId, callerId, pollDate, request(single(MealType.DINNER))))
                .isInstanceOf(BusinessException.class)
                .hasMessage(day.getMyChooseMealsBlockReason());
        assertThat(stored).isEmpty();
    }

    private void stubParticipant(MembershipRole role, SpaceType spaceType) {
        space.setType(spaceType);
        SpaceMembershipEntity membership = membership(role);
        lenient().when(mealAccessService.requireViewMeals(spaceId, callerId)).thenReturn(membership);
        lenient().when(mealAccessService.isParticipantScopeOnly(membership)).thenReturn(true);
        lenient().when(mealAccessService.resolveOwnMemberId(spaceId, callerId)).thenReturn(memberId);
        lenient().when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        lenient().when(spaceRepository.findById(spaceId)).thenReturn(Optional.of(space));
        lenient().when(mealBillingResolver.resolve(space, member)).thenReturn(MealBillingType.PAY_PER_MEAL);
        lenient().when(pollEligibilityPolicy.isPollEligible(any(), eq(pollDate), any())).thenReturn(true);

        MealParticipationEntity participation = MealParticipationEntity.builder()
                .space(space)
                .member(member)
                .build();
        lenient().when(participationRepository.findAllNonStoppedBySpaceId(spaceId)).thenReturn(List.of(participation));
        lenient().when(participationRepository.findBySpaceIdAndMemberIdAndStatus(
                        spaceId, memberId, MealParticipationStatus.ACTIVE))
                .thenReturn(Optional.of(participation));
        lenient().when(lastDeliveryService.resolvePreferredDeliveryLocationIds(any(), any()))
                .thenReturn(Map.of());

        lenient().when(pollRepository.findBySpaceIdAndPollDateAndMealType(eq(spaceId), eq(pollDate), any()))
                .thenAnswer(invocation -> Optional.of(loadedPolls.get(invocation.getArgument(2))));
        lenient().when(pollRepository.findById(any())).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            return managedPolls.values().stream().filter(poll -> poll.getId().equals(id)).findFirst();
        });
        lenient().when(pollRepository.findBySpaceIdAndPollDateOrderByMealTypeAsc(spaceId, pollDate))
                .thenReturn(List.copyOf(loadedPolls.values()));

        lenient().when(optionRepository.findById(any())).thenReturn(Optional.empty());
        for (MealPollOptionEntity option : options.values()) {
            lenient().when(optionRepository.findById(option.getId())).thenReturn(Optional.of(option));
            lenient().when(optionRepository.findByPollIdWithEntriesOrderBySortOrderAsc(option.getPoll().getId()))
                    .thenReturn(List.of(option));
        }
        lenient().when(spaceFoodItemSettingsRepository.findAllBySpaceId(spaceId)).thenReturn(List.of());
        lenient().when(responseRepository.findAllByPollIdAndMemberId(any(), any())).thenAnswer(invocation -> {
            UUID pollId = invocation.getArgument(0);
            UUID responseMemberId = invocation.getArgument(1);
            return stored.stream()
                    .filter(response -> response.getPoll().getId().equals(pollId)
                            && response.getMember().getId().equals(responseMemberId))
                    .toList();
        });
        lenient().when(responseRepository.findForMemberInDateRange(any(), any(), any(), any()))
                .thenAnswer(invocation -> List.copyOf(stored));
        lenient().when(responseRepository.countDistinctRespondingMembers(any())).thenReturn(stored.isEmpty() ? 0 : 1);
        lenient().doAnswer(invocation -> {
            UUID pollId = invocation.getArgument(0);
            UUID responseMemberId = invocation.getArgument(1);
            stored.removeIf(response -> response.getPoll().getId().equals(pollId)
                    && response.getMember().getId().equals(responseMemberId));
            return null;
        }).when(responseRepository).deleteByPollIdAndMemberId(any(), any());
        lenient().when(responseRepository.save(any(MealPollResponseEntity.class))).thenAnswer(invocation -> {
            MealPollResponseEntity entity = invocation.getArgument(0);
            if (!stored.contains(entity)) {
                stored.add(entity);
            }
            return entity;
        });
        lenient().when(dayPaymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private MealPollEntity openPoll(MealType mealType) {
        DailyMenuEntity menu = DailyMenuEntity.builder().build();
        menu.setId(UUID.randomUUID());
        MealPollEntity poll = MealPollEntity.builder()
                .space(space)
                .dailyMenu(menu)
                .mealType(mealType)
                .pollDate(pollDate)
                .status(MealPollStatus.OPEN)
                .pollCloseAt(LocalDateTime.now().plusDays(1))
                .build();
        poll.setId(UUID.randomUUID());
        return poll;
    }

    private MealPollOptionEntity option(MealPollEntity poll, String label) {
        MealPollOptionEntity option = MealPollOptionEntity.builder()
                .poll(poll)
                .optionType(MealPollOptionType.MENU_ENTRY)
                .sortOrder(1)
                .label(label)
                .build();
        option.setId(UUID.randomUUID());
        return option;
    }

    private SpaceMembershipEntity membership(MembershipRole role) {
        SpaceMembershipEntity membership = SpaceMembershipEntity.builder().role(role).space(space).build();
        membership.setId(UUID.randomUUID());
        return membership;
    }

    private SubmitMealPollSelectionRequest single(MealType mealType) {
        SubmitMealPollSelectionRequest selection = new SubmitMealPollSelectionRequest();
        selection.setMealType(mealType);
        selection.setSelectedOptionId(options.get(mealType).getId());
        return selection;
    }

    private SubmitMealPollSelectionRequest quantities(
            MealType mealType, SubmitMealPollOptionQuantityRequest... rows) {
        SubmitMealPollSelectionRequest selection = new SubmitMealPollSelectionRequest();
        selection.setMealType(mealType);
        selection.setOptions(new ArrayList<>(List.of(rows)));
        return selection;
    }

    private SubmitMealPollOptionQuantityRequest quantity(UUID optionId, int amount) {
        SubmitMealPollOptionQuantityRequest row = new SubmitMealPollOptionQuantityRequest();
        row.setOptionId(optionId);
        row.setQuantity(amount);
        return row;
    }

    private SubmitMealPollResponsesRequest request(SubmitMealPollSelectionRequest... selections) {
        SubmitMealPollResponsesRequest request = new SubmitMealPollResponsesRequest();
        request.setSelections(List.of(selections));
        return request;
    }

    private List<UUID> selectedIds(MealPollDayResponse day) {
        return day.getPolls().stream()
                .map(MealPollResponse::getMySelectedOptionId)
                .filter(id -> id != null)
                .toList();
    }
}
