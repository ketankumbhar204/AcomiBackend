package com.acomi.acomi_backend.space.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.space.api.dto.RuleAssignmentDto;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceRuleEntity;
import com.acomi.acomi_backend.space.infrastructure.persistence.repository.SpaceRuleRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SpaceRuleServiceTest {

    @Mock
    private SpaceRuleRepository spaceRuleRepository;

    @InjectMocks
    private SpaceRuleService spaceRuleService;

    private SpaceEntity space;

    @BeforeEach
    void setUp() {
        space = SpaceEntity.builder().type(SpaceType.PG).build();
        space.setId(UUID.randomUUID());
    }

    @Test
    void normalizeAssignments_storesEditedTextPerSpace() {
        RuleAssignmentDto smoking = dto("NO_SMOKING", "No smoking anywhere in the building");
        RuleAssignmentDto custom = dto("CUSTOM", "Keep common areas quiet after 10 PM");

        List<RuleAssignmentDto> normalized =
                SpaceRuleService.normalizeAssignments(List.of(smoking, custom));

        assertThat(normalized).hasSize(2);
        assertThat(normalized.get(0).getLabel()).isEqualTo("No smoking anywhere in the building");
        assertThat(normalized.get(1).getCode()).isEqualTo("CUSTOM");
    }

    @Test
    void normalizeAssignments_usesDefaultWhenLabelBlank() {
        List<RuleAssignmentDto> normalized =
                SpaceRuleService.normalizeAssignments(List.of(dto("NO_PETS", "  ")));

        assertThat(normalized).hasSize(1);
        assertThat(normalized.get(0).getLabel()).isEqualTo("Pets not allowed on the premises");
    }

    @Test
    void normalizeAssignments_rejectsUnknownCode() {
        assertThatThrownBy(() -> SpaceRuleService.normalizeAssignments(List.of(dto("FLYING", null))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Unknown rule code");
    }

    @Test
    void replaceForSpace_persistsSpaceScopedCopies() {
        when(spaceRuleRepository.save(any(SpaceRuleEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        spaceRuleService.replaceForSpace(
                space, List.of(dto("CLEANLINESS", "Keep rooms tidy at Sai Residency")));

        verify(spaceRuleRepository).deleteBySpaceId(space.getId());
        ArgumentCaptor<SpaceRuleEntity> captor = ArgumentCaptor.forClass(SpaceRuleEntity.class);
        verify(spaceRuleRepository).save(captor.capture());
        assertThat(captor.getValue().getRuleText()).isEqualTo("Keep rooms tidy at Sai Residency");
        assertThat(captor.getValue().getSpace().getId()).isEqualTo(space.getId());
    }

    private static RuleAssignmentDto dto(String code, String label) {
        RuleAssignmentDto dto = new RuleAssignmentDto();
        dto.setCode(code);
        dto.setLabel(label);
        return dto;
    }
}
