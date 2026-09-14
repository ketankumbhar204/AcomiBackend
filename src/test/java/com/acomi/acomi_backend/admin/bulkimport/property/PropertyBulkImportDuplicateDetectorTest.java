package com.acomi.acomi_backend.admin.bulkimport.property;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.admin.bulkimport.property.dto.PropertyBulkImportDuplicateMatch;
import com.acomi.acomi_backend.mess.infrastructure.persistence.repository.MessRegistrationRepository;
import com.acomi.acomi_backend.property.infrastructure.persistence.entity.PropertyRegistrationEntity;
import com.acomi.acomi_backend.property.infrastructure.persistence.repository.PropertyRegistrationRepository;
import com.acomi.acomi_backend.space.infrastructure.persistence.repository.SpaceRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PropertyBulkImportDuplicateDetectorTest {

    @Mock
    private PropertyRegistrationRepository propertyRegistrationRepository;

    @Mock
    private MessRegistrationRepository messRegistrationRepository;

    @Mock
    private SpaceRepository spaceRepository;

    private PropertyBulkImportDuplicateDetector detector;

    @BeforeEach
    void setUp() {
        detector = new PropertyBulkImportDuplicateDetector(
                propertyRegistrationRepository, messRegistrationRepository, spaceRepository);
    }

    @Test
    void inFile_sameAddressAndName_isHighDuplicate() {
        var first = new PropertyBulkImportDuplicateDetector.Fingerprint(
                2,
                PropertyBulkImportRowProcessor.TargetKind.PROPERTY,
                "Sunrise PG",
                Set.of("9876543210"),
                "12 MG Road",
                "Pune",
                "411001",
                null,
                null);
        var second = new PropertyBulkImportDuplicateDetector.Fingerprint(
                3,
                PropertyBulkImportRowProcessor.TargetKind.PROPERTY,
                "Sunrise PG",
                Set.of("9123456780"),
                "12 MG Road",
                "Pune",
                "411001",
                null,
                null);

        Optional<PropertyBulkImportDuplicateMatch> match =
                detector.findMatch(second, List.of(first), List.of());

        assertThat(match).isPresent();
        assertThat(match.get().getConfidence()).isEqualTo("HIGH");
        assertThat(match.get().getSource()).isEqualTo("IN_FILE");
        assertThat(match.get().getMatchedRowNumber()).isEqualTo(2);
    }

    @Test
    void sameNameAndMobile_withoutAddress_isHighDuplicate() {
        var first = new PropertyBulkImportDuplicateDetector.Fingerprint(
                2,
                PropertyBulkImportRowProcessor.TargetKind.PROPERTY,
                "zolo",
                Set.of("7499914710"),
                null,
                null,
                null,
                null,
                null);
        var second = new PropertyBulkImportDuplicateDetector.Fingerprint(
                3,
                PropertyBulkImportRowProcessor.TargetKind.PROPERTY,
                "zolo",
                Set.of("7499914710"),
                null,
                null,
                "110001",
                null,
                null);

        Optional<PropertyBulkImportDuplicateMatch> match =
                detector.findMatch(second, List.of(first), List.of());

        assertThat(match).isPresent();
        assertThat(match.get().getConfidence()).isEqualTo("HIGH");
        assertThat(match.get().getReason()).contains("Same mobile and similar name");
    }

    @Test
    void sameMobileDifferentPlaces_isNotDuplicate() {
        var first = new PropertyBulkImportDuplicateDetector.Fingerprint(
                2,
                PropertyBulkImportRowProcessor.TargetKind.PROPERTY,
                "Alpha PG",
                Set.of("9876543210"),
                "12 MG Road",
                "Pune",
                "411001",
                null,
                null);
        var second = new PropertyBulkImportDuplicateDetector.Fingerprint(
                3,
                PropertyBulkImportRowProcessor.TargetKind.PROPERTY,
                "Beta Hostel",
                Set.of("9876543210"),
                "88 FC Road",
                "Pune",
                "411004",
                null,
                null);

        Optional<PropertyBulkImportDuplicateMatch> match =
                detector.findMatch(second, List.of(first), List.of());

        assertThat(match).isEmpty();
    }

    @Test
    void existingProperty_sameMobilePincodeName_isDuplicate() {
        when(propertyRegistrationRepository.findCandidatesByAnyMobile(any()))
                .thenReturn(List.of(PropertyRegistrationEntity.builder()
                        .reference("PR-2026-000001")
                        .propertyName("Sunrise PG")
                        .mobileNumber("9876543210")
                        .addressLine("12 MG Road")
                        .city("Pune")
                        .pincode("411001")
                        .build()));
        when(propertyRegistrationRepository.findCandidatesByPincodeIn(any())).thenReturn(List.of());
        when(messRegistrationRepository.findCandidatesByAnyMobile(any())).thenReturn(List.of());
        when(messRegistrationRepository.findCandidatesByPincodeIn(any())).thenReturn(List.of());
        when(spaceRepository.findActiveByContactNumberIn(any())).thenReturn(List.of());

        var row = new PropertyBulkImportDuplicateDetector.Fingerprint(
                2,
                PropertyBulkImportRowProcessor.TargetKind.PROPERTY,
                "Sunrise PG",
                Set.of("9876543210"),
                "Other street",
                "Pune",
                "411001",
                null,
                null);

        var existing = detector.loadExisting(List.of(row));
        Optional<PropertyBulkImportDuplicateMatch> match = detector.findMatch(row, List.of(), existing);

        assertThat(match).isPresent();
        assertThat(match.get().getMatchedReference()).isEqualTo("PR-2026-000001");
        assertThat(match.get().getSource()).isEqualTo("EXISTING_PROPERTY");
    }

    @Test
    void nearbyGeoAndSimilarName_isHighDuplicate() {
        var first = new PropertyBulkImportDuplicateDetector.Fingerprint(
                2,
                PropertyBulkImportRowProcessor.TargetKind.PROPERTY,
                "Campus Stay",
                Set.of("9000000001"),
                null,
                null,
                null,
                new BigDecimal("18.5204000"),
                new BigDecimal("73.8567000"));
        var second = new PropertyBulkImportDuplicateDetector.Fingerprint(
                3,
                PropertyBulkImportRowProcessor.TargetKind.PROPERTY,
                "Campus Stay PG",
                Set.of("9000000002"),
                null,
                null,
                null,
                new BigDecimal("18.5204100"),
                new BigDecimal("73.8567100"));

        Optional<PropertyBulkImportDuplicateMatch> match =
                detector.findMatch(second, List.of(first), List.of());

        assertThat(match).isPresent();
        assertThat(match.get().getConfidence()).isEqualTo("HIGH");
    }
}
