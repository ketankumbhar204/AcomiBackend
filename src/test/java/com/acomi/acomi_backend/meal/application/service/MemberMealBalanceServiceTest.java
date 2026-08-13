package com.acomi.acomi_backend.meal.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.meal.api.dto.request.RecordMealBalancePurchaseRequest;
import com.acomi.acomi_backend.meal.domain.model.MealBalanceLedgerEntryType;
import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.MemberMealBalanceEntity;
import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.MemberMealBalanceLedgerEntryEntity;
import com.acomi.acomi_backend.meal.infrastructure.persistence.repository.MemberMealBalanceLedgerRepository;
import com.acomi.acomi_backend.meal.infrastructure.persistence.repository.MemberMealBalanceRepository;
import com.acomi.acomi_backend.meal.infrastructure.persistence.repository.MealPollResponseRepository;
import com.acomi.acomi_backend.meal.application.support.MealBillingResolver;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.MemberEntity;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.SpaceMembershipEntity;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.MemberRepository;
import com.acomi.acomi_backend.space.domain.model.MealBillingType;
import com.acomi.acomi_backend.space.domain.model.PrepaidBalanceUnit;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.space.infrastructure.persistence.repository.SpaceRepository;
import com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MemberMealBalanceServiceTest {

    @Mock
    private MemberMealBalanceRepository balanceRepository;

    @Mock
    private MemberMealBalanceLedgerRepository ledgerRepository;

    @Mock
    private MealPollResponseRepository responseRepository;

    @Mock
    private SpaceRepository spaceRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private MealAccessService mealAccessService;

    @Mock
    private MealBillingResolver mealBillingResolver;

    @InjectMocks
    private MemberMealBalanceService service;

    private final UUID spaceId = UUID.randomUUID();
    private final UUID memberId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();

    @Test
    void recordPurchase_addsToBalanceByDefault() {
        SpaceEntity space = prepaidSpace();
        MemberEntity member = member();
        MemberMealBalanceEntity balance = balance(space, member, "30");

        stubPurchase(space, member, balance, "45", "4500");

        RecordMealBalancePurchaseRequest request = new RecordMealBalancePurchaseRequest();
        request.setAmount(new BigDecimal("15"));
        request.setPaidAmount(new BigDecimal("1500"));

        var response = service.recordPurchase(spaceId, memberId, callerId, request);

        assertThat(balance.getBalance()).isEqualByComparingTo("45");
        assertThat(response.getBalance()).isEqualByComparingTo("45");
        assertThat(response.getMealsRemaining()).isEqualByComparingTo("45");
        assertThat(response.getCurrentAmountPaid()).isEqualByComparingTo("4500");
        assertThat(response.getMealsIncluded()).isEqualByComparingTo("45");
        verify(balanceRepository).save(balance);
    }

    @Test
    void recordPurchase_canReplaceWhenExplicitlyRequested() {
        SpaceEntity space = prepaidSpace();
        MemberEntity member = member();
        MemberMealBalanceEntity balance = balance(space, member, "30");

        stubPurchase(space, member, balance, "15", "1500");

        RecordMealBalancePurchaseRequest request = new RecordMealBalancePurchaseRequest();
        request.setAmount(new BigDecimal("15"));
        request.setPaidAmount(new BigDecimal("1500"));
        request.setReplaceBalance(true);

        var response = service.recordPurchase(spaceId, memberId, callerId, request);

        assertThat(balance.getBalance()).isEqualByComparingTo("15");
        assertThat(response.getBalance()).isEqualByComparingTo("15");
        verify(balanceRepository).save(balance);

        ArgumentCaptor<MemberMealBalanceLedgerEntryEntity> captor =
                ArgumentCaptor.forClass(MemberMealBalanceLedgerEntryEntity.class);
        verify(ledgerRepository).save(captor.capture());
        assertThat(captor.getValue().getEntryType()).isEqualTo(MealBalanceLedgerEntryType.PURCHASE);
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo("15");
        assertThat(captor.getValue().getPaidAmount()).isEqualByComparingTo("1500");
    }

    private void stubPurchase(
            SpaceEntity space,
            MemberEntity member,
            MemberMealBalanceEntity balance,
            String totalMeals,
            String totalPaid) {
        when(spaceRepository.findById(spaceId)).thenReturn(Optional.of(space));
        when(memberRepository.findByIdAndSpaceIdAndActiveTrue(memberId, spaceId)).thenReturn(Optional.of(member));
        when(mealAccessService.requireManageMeals(spaceId, callerId)).thenReturn(new SpaceMembershipEntity());
        when(mealBillingResolver.isPrepaid(space, member)).thenReturn(true);
        when(balanceRepository.findBySpaceIdAndMemberId(spaceId, memberId)).thenReturn(Optional.of(balance));
        when(balanceRepository.save(balance)).thenReturn(balance);
        when(ledgerRepository.save(any(MemberMealBalanceLedgerEntryEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(ledgerRepository.sumAmountByMemberAndTypeInRange(
                        eq(spaceId), eq(memberId), eq(MealBalanceLedgerEntryType.PURCHASE), any(), any()))
                .thenReturn(new BigDecimal("15"));
        when(ledgerRepository.sumAmountByMemberAndTypeInRange(
                        eq(spaceId), eq(memberId), eq(MealBalanceLedgerEntryType.DEBIT), any(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(ledgerRepository.sumPaidAmountByMemberAndTypeInRange(
                        eq(spaceId), eq(memberId), eq(MealBalanceLedgerEntryType.PURCHASE), any(), any()))
                .thenReturn(new BigDecimal("1500"));
        when(ledgerRepository.findFirstByBalanceIdAndEntryTypeOrderByCreatedAtDesc(
                        any(), eq(MealBalanceLedgerEntryType.PURCHASE)))
                .thenReturn(Optional.empty());
        when(ledgerRepository.sumAmountByBalanceIdAndEntryTypeSince(
                        any(), eq(MealBalanceLedgerEntryType.DEBIT), any()))
                .thenReturn(BigDecimal.ZERO);
        when(ledgerRepository.sumAmountByBalanceIdAndEntryType(
                        any(), eq(MealBalanceLedgerEntryType.DEBIT)))
                .thenReturn(BigDecimal.ZERO);
        when(ledgerRepository.sumAmountByBalanceIdAndEntryTypeSince(
                        any(), eq(MealBalanceLedgerEntryType.PURCHASE), any()))
                .thenReturn(new BigDecimal(totalMeals));
        when(ledgerRepository.sumPaidAmountByBalanceIdAndEntryTypeSince(
                        any(), eq(MealBalanceLedgerEntryType.PURCHASE), any()))
                .thenReturn(new BigDecimal(totalPaid));
        when(ledgerRepository.findFirstByBalanceIdAndEntryTypeOrderByCreatedAtDesc(
                        any(), eq(MealBalanceLedgerEntryType.ENDED)))
                .thenReturn(Optional.empty());
        when(ledgerRepository.findFirstByBalanceIdAndEntryTypeAndCreatedAtAfterOrderByCreatedAtAsc(
                        any(), eq(MealBalanceLedgerEntryType.PURCHASE), any()))
                .thenReturn(Optional.empty());
    }

    private MemberMealBalanceEntity balance(SpaceEntity space, MemberEntity member, String amount) {
        MemberMealBalanceEntity balance = MemberMealBalanceEntity.builder()
                .space(space)
                .member(member)
                .balance(new BigDecimal(amount))
                .unit(PrepaidBalanceUnit.MEALS)
                .currencyCode("INR")
                .build();
        balance.setId(UUID.randomUUID());
        return balance;
    }

    private SpaceEntity prepaidSpace() {
        SpaceEntity space = SpaceEntity.builder()
                .owner(UserEntity.builder().build())
                .name("Sunrise Mess")
                .type(SpaceType.MESS)
                .mealBillingType(MealBillingType.PREPAID_BALANCE)
                .prepaidBalanceUnit(PrepaidBalanceUnit.MEALS)
                .prepaidFallbackToPayPerMeal(true)
                .build();
        space.setId(spaceId);
        return space;
    }

    private MemberEntity member() {
        MemberEntity member = MemberEntity.builder().fullName("Rahul").build();
        member.setId(memberId);
        return member;
    }
}
