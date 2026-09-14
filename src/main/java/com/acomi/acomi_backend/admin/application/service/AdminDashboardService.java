package com.acomi.acomi_backend.admin.application.service;

import com.acomi.acomi_backend.address.infrastructure.persistence.repository.SavedAddressRepository;
import com.acomi.acomi_backend.admin.api.dto.response.AdminActiveSpaceResponse;
import com.acomi.acomi_backend.admin.api.dto.response.AdminDashboardSummaryResponse;
import com.acomi.acomi_backend.admin.api.dto.response.AdminEnquiriesTrendPointResponse;
import com.acomi.acomi_backend.admin.api.dto.response.AdminEnquiriesTrendResponse;
import com.acomi.acomi_backend.admin.api.dto.response.AdminUserRegistrationBreakdownResponse;
import com.acomi.acomi_backend.admin.api.dto.response.AdminUserRegistrationBreakdownSliceResponse;
import com.acomi.acomi_backend.common.exception.ResourceNotFoundException;
import com.acomi.acomi_backend.enquiry.infrastructure.persistence.repository.SpaceEnquiryRepository;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.SpaceMembershipRepository;
import com.acomi.acomi_backend.mess.domain.model.MessRegistrationSource;
import com.acomi.acomi_backend.mess.domain.model.MessRegistrationStatus;
import com.acomi.acomi_backend.mess.infrastructure.persistence.entity.MessRegistrationEntity;
import com.acomi.acomi_backend.mess.infrastructure.persistence.repository.MessRegistrationRepository;
import com.acomi.acomi_backend.property.domain.model.PropertyRegistrationSource;
import com.acomi.acomi_backend.property.domain.model.PropertyRegistrationStatus;
import com.acomi.acomi_backend.property.infrastructure.persistence.entity.PropertyRegistrationEntity;
import com.acomi.acomi_backend.property.infrastructure.persistence.repository.PropertyRegistrationRepository;
import com.acomi.acomi_backend.registration.application.AdminLeadDefaults;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.space.infrastructure.persistence.repository.SpaceRepository;
import com.acomi.acomi_backend.user.domain.model.SystemRole;
import com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity;
import com.acomi.acomi_backend.user.infrastructure.persistence.repository.UserRepository;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private static final List<SpaceType> PROPERTY_SPACE_TYPES =
            List.of(SpaceType.PG, SpaceType.HOSTEL, SpaceType.CO_LIVING, SpaceType.RENTAL);

    private static final List<PropertyRegistrationStatus> CLOSED_PROPERTY_STATUSES =
            List.of(PropertyRegistrationStatus.CONVERTED, PropertyRegistrationStatus.REJECTED);

    private static final List<MessRegistrationStatus> CLOSED_MESS_STATUSES =
            List.of(MessRegistrationStatus.CONVERTED, MessRegistrationStatus.REJECTED);

    private final PropertyRegistrationRepository propertyRegistrationRepository;
    private final MessRegistrationRepository messRegistrationRepository;
    private final SpaceRepository spaceRepository;
    private final AdminRegisteredUsersService adminRegisteredUsersService;
    private final SpaceEnquiryRepository spaceEnquiryRepository;
    private final SpaceMembershipRepository spaceMembershipRepository;
    private final SavedAddressRepository savedAddressRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public AdminDashboardSummaryResponse getSummary(LocalDateTime from, LocalDateTime to) {
        long activePropertySpaces = PROPERTY_SPACE_TYPES.stream()
                .mapToLong(spaceRepository::countByTypeAndIsActiveTrue)
                .sum();
        long activeMessSpaces = spaceRepository.countByTypeAndIsActiveTrue(SpaceType.MESS);
        long openPropertyLeads = propertyRegistrationRepository.countByStatusNotIn(CLOSED_PROPERTY_STATUSES);
        long openMessLeads = messRegistrationRepository.countByStatusNotIn(CLOSED_MESS_STATUSES);

        AdminDashboardSummaryResponse.AdminDashboardSummaryResponseBuilder builder =
                AdminDashboardSummaryResponse.builder()
                        // Align with Properties/Mess list "Total" = open leads + active spaces.
                        .propertyRegistrationCount(openPropertyLeads + activePropertySpaces)
                        .messRegistrationCount(openMessLeads + activeMessSpaces)
                        .adminPropertyLeads(
                                propertyRegistrationRepository.countBySource(PropertyRegistrationSource.ADMIN))
                        .adminMessLeads(messRegistrationRepository.countBySource(MessRegistrationSource.ADMIN))
                        .websitePropertyLeads(propertyRegistrationRepository.countBySource(
                                PropertyRegistrationSource.PUBLIC_WEBSITE))
                        .websiteMessLeads(
                                messRegistrationRepository.countBySource(MessRegistrationSource.PUBLIC_WEBSITE))
                        .unclaimedAdminPropertyLeads(propertyRegistrationRepository.countByClaimedAtIsNullAndSource(
                                PropertyRegistrationSource.ADMIN))
                        .unclaimedAdminMessLeads(messRegistrationRepository.countByClaimedAtIsNullAndSource(
                                MessRegistrationSource.ADMIN))
                        .claimedPropertyLeads(propertyRegistrationRepository.countByClaimedAtIsNotNull())
                        .claimedMessLeads(messRegistrationRepository.countByClaimedAtIsNotNull())
                        .activePropertySpaces(activePropertySpaces)
                        .activeMessSpaces(activeMessSpaces)
                        .registeredUsersCount(adminRegisteredUsersService.countRegisteredUsers())
                        .totalEnquiriesCount(spaceEnquiryRepository.count())
                        .ownersCount(spaceMembershipRepository.countDistinctActiveOwners())
                        .savedAddressesCount(savedAddressRepository.countByIsActiveTrue());

        if (from != null && to != null && to.isAfter(from)) {
            applyPeriodDeltas(builder, from, to);
        }

        return builder.build();
    }

    @Transactional(readOnly = true)
    public AdminDashboardSummaryResponse getSummary() {
        return getSummary(null, null);
    }

    private void applyPeriodDeltas(
            AdminDashboardSummaryResponse.AdminDashboardSummaryResponseBuilder builder,
            LocalDateTime from,
            LocalDateTime to) {
        long periodDays = Math.max(1, ChronoUnit.DAYS.between(from.toLocalDate(), to.toLocalDate()));
        LocalDateTime prevTo = from;
        LocalDateTime prevFrom = from.minusDays(periodDays);

        long usersNow = userRepository.countVerifiedUsersRegisteredBetween(SystemRole.USER, from, to);
        long usersPrev = userRepository.countVerifiedUsersRegisteredBetween(SystemRole.USER, prevFrom, prevTo);
        long enquiriesNow = spaceEnquiryRepository.countByRequestedAtGreaterThanEqualAndRequestedAtLessThan(from, to);
        long enquiriesPrev =
                spaceEnquiryRepository.countByRequestedAtGreaterThanEqualAndRequestedAtLessThan(prevFrom, prevTo);
        long propsNow = spaceRepository.countByTypeInAndIsActiveTrueAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                PROPERTY_SPACE_TYPES, from, to);
        long propsPrev = spaceRepository.countByTypeInAndIsActiveTrueAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                PROPERTY_SPACE_TYPES, prevFrom, prevTo);
        long messNow = spaceRepository.countByTypeInAndIsActiveTrueAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                List.of(SpaceType.MESS), from, to);
        long messPrev = spaceRepository.countByTypeInAndIsActiveTrueAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                List.of(SpaceType.MESS), prevFrom, prevTo);
        long ownersNow = spaceMembershipRepository.countDistinctActiveOwnersJoinedBetween(from, to);
        long ownersPrev = spaceMembershipRepository.countDistinctActiveOwnersJoinedBetween(prevFrom, prevTo);
        long addressesNow =
                savedAddressRepository.countByIsActiveTrueAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(from, to);
        long addressesPrev =
                savedAddressRepository.countByIsActiveTrueAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        prevFrom, prevTo);

        builder.registeredUsersDeltaPercent(deltaPercent(usersNow, usersPrev))
                .totalEnquiriesDeltaPercent(deltaPercent(enquiriesNow, enquiriesPrev))
                .propertySpacesDeltaPercent(deltaPercent(propsNow, propsPrev))
                .messSpacesDeltaPercent(deltaPercent(messNow, messPrev))
                .ownersDeltaPercent(deltaPercent(ownersNow, ownersPrev))
                .savedAddressesDeltaPercent(deltaPercent(addressesNow, addressesPrev));
    }

    static Double deltaPercent(long current, long previous) {
        if (previous == 0L) {
            if (current == 0L) {
                return 0.0;
            }
            return 100.0;
        }
        return Math.round(((current - previous) * 1000.0) / previous) / 10.0;
    }

    @Transactional(readOnly = true)
    public AdminEnquiriesTrendResponse enquiriesTrend(LocalDate from, LocalDate to) {
        LocalDate safeFrom = from != null ? from : LocalDate.now().minusDays(6);
        LocalDate safeTo = to != null ? to : LocalDate.now();
        if (safeTo.isBefore(safeFrom)) {
            LocalDate swap = safeFrom;
            safeFrom = safeTo;
            safeTo = swap;
        }
        LocalDateTime fromAt = safeFrom.atStartOfDay();
        LocalDateTime toAt = safeTo.plusDays(1).atStartOfDay();

        Map<LocalDate, Long> byDay = new LinkedHashMap<>();
        for (LocalDate d = safeFrom; !d.isAfter(safeTo); d = d.plusDays(1)) {
            byDay.put(d, 0L);
        }
        for (Object[] row : spaceEnquiryRepository.countDailyRequestedBetween(fromAt, toAt)) {
            LocalDate day = toLocalDate(row[0]);
            long count = row[1] == null ? 0L : ((Number) row[1]).longValue();
            if (day != null && byDay.containsKey(day)) {
                byDay.put(day, count);
            }
        }

        List<AdminEnquiriesTrendPointResponse> points = new ArrayList<>();
        long total = 0L;
        for (Map.Entry<LocalDate, Long> entry : byDay.entrySet()) {
            total += entry.getValue();
            points.add(AdminEnquiriesTrendPointResponse.builder()
                    .date(entry.getKey())
                    .count(entry.getValue())
                    .build());
        }

        return AdminEnquiriesTrendResponse.builder()
                .from(safeFrom)
                .to(safeTo)
                .points(points)
                .total(total)
                .build();
    }

    @Transactional(readOnly = true)
    public AdminUserRegistrationBreakdownResponse userRegistrationBreakdown(
            LocalDateTime from, LocalDateTime to) {
        LocalDateTime fromAt = from != null ? from : LocalDateTime.now().minusDays(7);
        LocalDateTime toAt = to != null ? to : LocalDateTime.now().plusDays(1);
        List<UserEntity> users =
                userRepository.findVerifiedUsersRegisteredBetween(SystemRole.USER, fromAt, toAt);
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put(AdminRegisteredUsersService.ROLE_OWNER, 0L);
        counts.put(AdminRegisteredUsersService.ROLE_MEMBER, 0L);
        counts.put(AdminRegisteredUsersService.ROLE_OWNER_AND_MEMBER, 0L);
        counts.put(AdminRegisteredUsersService.ROLE_NOT_SELECTED, 0L);

        if (!users.isEmpty()) {
            List<UUID> userIds = users.stream().map(UserEntity::getId).toList();
            Map<UUID, String> roles = adminRegisteredUsersService.selectedRolesForUsers(userIds);
            for (UserEntity user : users) {
                String role = roles.getOrDefault(user.getId(), AdminRegisteredUsersService.ROLE_NOT_SELECTED);
                counts.merge(role, 1L, Long::sum);
            }
        }

        List<AdminUserRegistrationBreakdownSliceResponse> slices = new ArrayList<>();
        slices.add(slice(AdminRegisteredUsersService.ROLE_OWNER, "Owners", counts));
        slices.add(slice(AdminRegisteredUsersService.ROLE_MEMBER, "Members", counts));
        slices.add(slice(AdminRegisteredUsersService.ROLE_OWNER_AND_MEMBER, "Owner & Member", counts));
        slices.add(slice(AdminRegisteredUsersService.ROLE_NOT_SELECTED, "Not selected", counts));

        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        return AdminUserRegistrationBreakdownResponse.builder()
                .total(total)
                .slices(slices)
                .build();
    }

    private static AdminUserRegistrationBreakdownSliceResponse slice(
            String role, String label, Map<String, Long> counts) {
        return AdminUserRegistrationBreakdownSliceResponse.builder()
                .role(role)
                .label(label)
                .count(counts.getOrDefault(role, 0L))
                .build();
    }

    private static LocalDate toLocalDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        return LocalDate.parse(String.valueOf(value));
    }

    @Transactional(readOnly = true)
    public List<AdminActiveSpaceResponse> listActiveSpaces(SpaceType type) {
        List<SpaceEntity> spaces;
        if (type == null) {
            spaces = spaceRepository.findActiveByTypes(
                    List.of(SpaceType.PG, SpaceType.HOSTEL, SpaceType.CO_LIVING, SpaceType.RENTAL, SpaceType.MESS));
        } else if (type == SpaceType.MESS) {
            spaces = spaceRepository.findByTypeAndIsActiveTrue(SpaceType.MESS);
        } else {
            spaces = spaceRepository.findByTypeAndIsActiveTrue(type);
        }

        return spaces.stream().map(this::toActiveSpace).toList();
    }

    @Transactional
    public void deleteActiveSpace(UUID spaceId) {
        SpaceEntity space = spaceRepository
                .findById(spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Space", "id", spaceId));
        if (!space.isActive()) {
            return;
        }
        space.setActive(false);
        spaceRepository.save(space);
    }

    private AdminActiveSpaceResponse toActiveSpace(SpaceEntity space) {
        AdminActiveSpaceResponse.AdminActiveSpaceResponseBuilder builder = AdminActiveSpaceResponse.builder()
                .id(space.getId())
                .name(space.getName())
                .type(space.getType())
                .address(space.getAddress())
                .contactNumber(space.getContactNumber())
                .ownerId(space.getOwner().getId())
                .ownerName(space.getOwner().getFullName())
                .ownerMobile(space.getOwner().getMobileNumber())
                .createdAt(space.getCreatedAt())
                .testLead(false);

        if (space.getType() == SpaceType.MESS) {
            messRegistrationRepository.findByConvertedSpaceId(space.getId()).ifPresent(reg -> applyMessLead(builder, reg));
        } else {
            propertyRegistrationRepository
                    .findByConvertedSpaceId(space.getId())
                    .ifPresent(reg -> applyPropertyLead(builder, reg));
        }

        return builder.build();
    }

    private static void applyPropertyLead(
            AdminActiveSpaceResponse.AdminActiveSpaceResponseBuilder builder, PropertyRegistrationEntity reg) {
        builder.source(reg.getSource() == null ? null : reg.getSource().name())
                .testLead(reg.isTestLead())
                .registrationId(reg.getId());
        String listingMobile = firstUsableMobile(
                reg.getMobileNumber(), reg.getAlternateMobileNumber(), reg.getAdditionalMobileNumber());
        if (listingMobile != null) {
            builder.contactNumber(listingMobile);
        }
    }

    private static void applyMessLead(
            AdminActiveSpaceResponse.AdminActiveSpaceResponseBuilder builder, MessRegistrationEntity reg) {
        builder.source(reg.getSource() == null ? null : reg.getSource().name())
                .testLead(reg.isTestLead())
                .registrationId(reg.getId());
        String listingMobile = firstUsableMobile(
                reg.getMobileNumber(), reg.getAlternateMobileNumber(), reg.getAdditionalMobileNumber());
        if (listingMobile != null) {
            builder.contactNumber(listingMobile);
        }
    }

    private static String firstUsableMobile(String... mobiles) {
        if (mobiles == null) {
            return null;
        }
        for (String mobile : mobiles) {
            if (mobile == null || mobile.isBlank()) {
                continue;
            }
            String trimmed = mobile.trim();
            if (!AdminLeadDefaults.PLACEHOLDER_MOBILE.equals(trimmed)) {
                return trimmed;
            }
        }
        return null;
    }
}
