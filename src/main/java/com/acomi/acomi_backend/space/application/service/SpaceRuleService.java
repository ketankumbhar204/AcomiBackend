package com.acomi.acomi_backend.space.application.service;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.space.api.dto.RuleAssignmentDto;
import com.acomi.acomi_backend.space.domain.model.RuleCode;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceRuleEntity;
import com.acomi.acomi_backend.space.infrastructure.persistence.repository.SpaceRuleRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SpaceRuleService {

    public static final int MAX_RULES = 30;
    public static final int MAX_RULE_TEXT_LENGTH = 500;

    private final SpaceRuleRepository spaceRuleRepository;

    public SpaceRuleService(SpaceRuleRepository spaceRuleRepository) {
        this.spaceRuleRepository = spaceRuleRepository;
    }

    /** House rules are supported for every operational space type. */
    public static boolean supportsRules(SpaceType spaceType) {
        return spaceType != null;
    }

    @Transactional(readOnly = true)
    public List<RuleAssignmentDto> getForSpace(UUID spaceId) {
        return spaceRuleRepository.findAllBySpaceIdOrderByDisplayOrderAscCreatedAtAsc(spaceId).stream()
                .map(SpaceRuleService::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<UUID, List<RuleAssignmentDto>> getForSpaces(Collection<UUID> spaceIds) {
        if (spaceIds == null || spaceIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<RuleAssignmentDto>> bySpaceId = new LinkedHashMap<>();
        for (UUID spaceId : spaceIds) {
            bySpaceId.put(spaceId, new ArrayList<>());
        }
        for (SpaceRuleEntity entity :
                spaceRuleRepository.findAllBySpaceIdInOrderByDisplayOrderAscCreatedAtAsc(spaceIds)) {
            bySpaceId.computeIfAbsent(entity.getSpace().getId(), ignored -> new ArrayList<>()).add(toDto(entity));
        }
        return bySpaceId;
    }

    @Transactional
    public void replaceForSpace(SpaceEntity space, List<RuleAssignmentDto> rules) {
        if (!supportsRules(space.getType())) {
            return;
        }
        List<RuleAssignmentDto> normalized = normalizeAssignments(rules);
        spaceRuleRepository.deleteBySpaceId(space.getId());
        if (normalized.isEmpty()) {
            return;
        }
        int order = 0;
        for (RuleAssignmentDto rule : normalized) {
            spaceRuleRepository.save(SpaceRuleEntity.builder()
                    .space(space)
                    .ruleCode(rule.getCode())
                    .ruleText(rule.getLabel())
                    .displayOrder(order++)
                    .build());
        }
    }

    public static List<RuleAssignmentDto> normalizeAssignments(List<RuleAssignmentDto> rules) {
        if (rules == null || rules.isEmpty()) {
            return List.of();
        }
        if (rules.size() > MAX_RULES) {
            throw new BusinessException(
                    "A space can have at most " + MAX_RULES + " rules", HttpStatus.BAD_REQUEST);
        }

        List<RuleAssignmentDto> normalized = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (RuleAssignmentDto raw : rules) {
            if (raw == null || raw.getCode() == null || raw.getCode().isBlank()) {
                throw new BusinessException("Rule code is required", HttpStatus.BAD_REQUEST);
            }
            RuleCode code = RuleCode.fromValue(raw.getCode())
                    .orElseThrow(() -> new BusinessException(
                            "Unknown rule code: " + raw.getCode(), HttpStatus.BAD_REQUEST));
            String text = resolveText(code, raw.getLabel());
            String key = code.isCustom()
                    ? code.name() + "::" + text.toLowerCase(Locale.ROOT)
                    : code.name();
            if (!seen.add(key)) {
                continue;
            }
            RuleAssignmentDto dto = new RuleAssignmentDto();
            dto.setCode(code.name());
            dto.setLabel(text);
            normalized.add(dto);
        }
        return normalized;
    }

    public static String resolveText(RuleCode code, String providedLabel) {
        String text = providedLabel != null ? providedLabel.trim() : "";
        if (text.isEmpty()) {
            if (code.isCustom() || code.getDefaultLabel() == null) {
                throw new BusinessException("Rule text is required", HttpStatus.BAD_REQUEST);
            }
            text = code.getDefaultLabel();
        }
        if (text.length() > MAX_RULE_TEXT_LENGTH) {
            throw new BusinessException(
                    "Rule text must be at most " + MAX_RULE_TEXT_LENGTH + " characters",
                    HttpStatus.BAD_REQUEST);
        }
        return text;
    }

    private static RuleAssignmentDto toDto(SpaceRuleEntity entity) {
        RuleAssignmentDto dto = new RuleAssignmentDto();
        dto.setCode(entity.getRuleCode());
        dto.setLabel(entity.getRuleText());
        return dto;
    }
}
