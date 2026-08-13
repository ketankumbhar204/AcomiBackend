package com.acomi.acomi_backend.occupancy.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.member.domain.model.MemberGender;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.MemberEntity;
import com.acomi.acomi_backend.space.domain.model.GenderPolicy;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import org.junit.jupiter.api.Test;

class GenderPolicyValidatorTest {

    private final GenderPolicyValidator validator = new GenderPolicyValidator();

    @Test
    void validate_whenMaleOnlyAndFemaleMember_throws() {
        SpaceEntity space = SpaceEntity.builder().type(SpaceType.PG).genderPolicy(GenderPolicy.MALE).build();
        MemberEntity member = MemberEntity.builder().gender(MemberGender.FEMALE).build();

        assertThatThrownBy(() -> validator.validate(space, member))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("male-only");
    }

    @Test
    void validate_whenMixedPolicy_allowsAnyGender() {
        SpaceEntity space = SpaceEntity.builder().type(SpaceType.PG).genderPolicy(GenderPolicy.MIXED).build();
        MemberEntity member = MemberEntity.builder().gender(MemberGender.FEMALE).build();

        validator.validate(space, member);
    }

    @Test
    void validate_whenNoPolicy_skipsValidation() {
        SpaceEntity space = SpaceEntity.builder().type(SpaceType.PG).build();
        MemberEntity member = MemberEntity.builder().gender(MemberGender.FEMALE).build();

        validator.validate(space, member);
    }
}
