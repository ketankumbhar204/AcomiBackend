package com.amico.amico_backend.occupancy.application.service;

import com.amico.amico_backend.common.exception.BusinessException;
import com.amico.amico_backend.member.domain.model.MemberGender;
import com.amico.amico_backend.member.infrastructure.persistence.entity.MemberEntity;
import com.amico.amico_backend.space.domain.model.GenderPolicy;
import com.amico.amico_backend.space.infrastructure.persistence.entity.SpaceEntity;
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
