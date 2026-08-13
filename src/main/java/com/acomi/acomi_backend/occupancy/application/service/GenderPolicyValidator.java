package com.acomi.acomi_backend.occupancy.application.service;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.member.domain.model.MemberGender;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.MemberEntity;
import com.acomi.acomi_backend.space.domain.model.GenderPolicy;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import org.springframework.stereotype.Service;

@Service
public class GenderPolicyValidator {

    public void validate(SpaceEntity space, MemberEntity member) {
        GenderPolicy policy = space.getGenderPolicy();
        if (policy == null || policy == GenderPolicy.MIXED) {
            return;
        }

        MemberGender gender = member.getGender();
        if (gender == null || gender == MemberGender.UNSPECIFIED) {
            return;
        }

        if (policy == GenderPolicy.MALE && gender != MemberGender.MALE) {
            throw new BusinessException("Member gender does not match space male-only policy");
        }
        if (policy == GenderPolicy.FEMALE && gender != MemberGender.FEMALE) {
            throw new BusinessException("Member gender does not match space female-only policy");
        }
    }
}
