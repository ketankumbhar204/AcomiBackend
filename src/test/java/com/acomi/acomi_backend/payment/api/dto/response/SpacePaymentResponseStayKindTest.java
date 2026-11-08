package com.acomi.acomi_backend.payment.api.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.acomi.acomi_backend.member.infrastructure.persistence.entity.MemberEntity;
import com.acomi.acomi_backend.occupancy.domain.model.StayKind;
import com.acomi.acomi_backend.occupancy.infrastructure.persistence.entity.OccupancyEntity;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentStatus;
import com.acomi.acomi_backend.payment.infrastructure.persistence.entity.SpacePaymentEntity;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Stay type on payment responses must come from the linked occupancy only —
 * never from amount, category, or a MONTHLY default.
 */
class SpacePaymentResponseStayKindTest {

    @ParameterizedTest
    @EnumSource(StayKind.class)
    void from_copiesStayKindFromLinkedOccupancy(StayKind stayKind) {
        SpacePaymentEntity payment = basePayment();
        OccupancyEntity occupancy = OccupancyEntity.builder().stayKind(stayKind).build();
        occupancy.setId(UUID.randomUUID());
        payment.setOccupancy(occupancy);

        SpacePaymentResponse response = SpacePaymentResponse.from(payment, null, LocalDate.of(2026, 11, 6));

        assertThat(response.getOccupancyId()).isEqualTo(occupancy.getId());
        assertThat(response.getStayKind()).isEqualTo(stayKind);
    }

    @Test
    void from_nullStayKindWhenOccupancyHasLegacyNull() {
        SpacePaymentEntity payment = basePayment();
        OccupancyEntity occupancy = OccupancyEntity.builder().stayKind(null).build();
        occupancy.setId(UUID.randomUUID());
        payment.setOccupancy(occupancy);

        SpacePaymentResponse response = SpacePaymentResponse.from(payment, null, LocalDate.of(2026, 11, 6));

        assertThat(response.getOccupancyId()).isEqualTo(occupancy.getId());
        assertThat(response.getStayKind()).isNull();
    }

    @Test
    void from_nullStayKindWhenPaymentHasNoOccupancy() {
        SpacePaymentEntity payment = basePayment();
        payment.setOccupancy(null);

        SpacePaymentResponse response = SpacePaymentResponse.from(payment, null, LocalDate.of(2026, 11, 6));

        assertThat(response.getOccupancyId()).isNull();
        assertThat(response.getStayKind()).isNull();
    }

    @Test
    void withTargetLabel_preservesStayKind() {
        SpacePaymentEntity payment = basePayment();
        OccupancyEntity occupancy = OccupancyEntity.builder().stayKind(StayKind.OPEN_ENDED).build();
        occupancy.setId(UUID.randomUUID());
        payment.setOccupancy(occupancy);

        SpacePaymentResponse response = SpacePaymentResponse.from(payment, null, LocalDate.of(2026, 11, 6))
                .withTargetLabel("Block A · 101");

        assertThat(response.getTargetLabel()).isEqualTo("Block A · 101");
        assertThat(response.getStayKind()).isEqualTo(StayKind.OPEN_ENDED);
        assertThat(response.getOccupancyId()).isEqualTo(occupancy.getId());
    }

    private static SpacePaymentEntity basePayment() {
        SpaceEntity space = SpaceEntity.builder().build();
        space.setId(UUID.randomUUID());
        MemberEntity member = MemberEntity.builder()
                .fullName("Test Member")
                .mobileNumber("9999999999")
                .isActive(true)
                .build();
        member.setId(UUID.randomUUID());
        return SpacePaymentEntity.builder()
                .space(space)
                .member(member)
                .amount(new BigDecimal("10000"))
                .dueDate(LocalDate.of(2026, 11, 1))
                .paymentStatus(SpacePaymentStatus.PENDING)
                .build();
    }
}
