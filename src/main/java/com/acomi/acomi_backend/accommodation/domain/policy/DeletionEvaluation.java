package com.acomi.acomi_backend.accommodation.domain.policy;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class DeletionEvaluation {

    private final boolean deletable;
    private final String blockReason;

    public static DeletionEvaluation allowed() {
        return new DeletionEvaluation(true, null);
    }

    public static DeletionEvaluation blocked(String reason) {
        return new DeletionEvaluation(false, reason);
    }
}
