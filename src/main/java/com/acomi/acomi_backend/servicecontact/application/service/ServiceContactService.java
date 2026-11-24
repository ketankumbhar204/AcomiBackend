package com.acomi.acomi_backend.servicecontact.application.service;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.common.web.PagedResponse;
import com.acomi.acomi_backend.servicecontact.api.dto.request.SaveServiceContactRequest;
import com.acomi.acomi_backend.servicecontact.api.dto.response.DuplicateServiceContactResponse;
import com.acomi.acomi_backend.servicecontact.api.dto.response.ServiceContactCategoryResponse;
import com.acomi.acomi_backend.servicecontact.api.dto.response.ServiceContactDuplicateMatchResponse;
import com.acomi.acomi_backend.servicecontact.api.dto.response.ServiceContactResponse;
import com.acomi.acomi_backend.servicecontact.application.CallerScope;
import com.acomi.acomi_backend.servicecontact.application.DuplicateServiceContactException;
import com.acomi.acomi_backend.servicecontact.application.ServiceContactSpecifications;
import com.acomi.acomi_backend.servicecontact.domain.ServiceContactRules;
import com.acomi.acomi_backend.servicecontact.domain.model.ServiceContactCategory;
import com.acomi.acomi_backend.servicecontact.infrastructure.persistence.entity.ServiceContactEntity;
import com.acomi.acomi_backend.servicecontact.infrastructure.persistence.repository.ServiceContactRepository;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ServiceContactService {

    private static final Logger log = LoggerFactory.getLogger(ServiceContactService.class);
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;

    private final ServiceContactRepository contactRepository;
    private final ServiceContactAccessService accessService;

    @Transactional(readOnly = true)
    public List<ServiceContactCategoryResponse> categories(UUID callerId) {
        accessService.requireDirectoryAccess(callerId);
        return Arrays.stream(ServiceContactCategory.values())
                .map(category -> ServiceContactCategoryResponse.builder()
                        .code(category.name())
                        .label(category.label())
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public PagedResponse<ServiceContactResponse> list(
            UUID callerId,
            String q,
            String category,
            String sort,
            boolean includeArchived,
            int page,
            int size) {
        CallerScope scope = accessService.requireDirectoryAccess(callerId);
        ServiceContactCategory parsedCategory = category == null || category.isBlank()
                ? null
                : ServiceContactRules.requireCategory(category);
        String like = ServiceContactRules.likePattern(q);
        String digits = ServiceContactRules.digits(q);
        Page<ServiceContactEntity> result = contactRepository.findAll(
                ServiceContactSpecifications.directory(scope, includeArchived, parsedCategory, like, digits),
                PageRequest.of(Math.max(page, 0), clampSize(size), toSort(sort)));
        return PagedResponse.from(result.map(contact -> toResponse(scope, contact)));
    }

    @Transactional(readOnly = true)
    public ServiceContactResponse get(UUID callerId, UUID contactId) {
        CallerScope scope = accessService.requireDirectoryAccess(callerId);
        ServiceContactEntity contact = requireContact(contactId);
        accessService.requireRead(scope, contact);
        return toResponse(scope, contact);
    }

    @Transactional
    public ServiceContactResponse create(UUID callerId, SaveServiceContactRequest request) {
        CallerScope scope = accessService.requireDirectoryAccess(callerId);
        accessService.requireManageCapability(scope);
        NormalizedContact normalized = normalize(request);
        UUID accountOwnerId = resolveAccountOwner(scope);
        assertNoDuplicate(accountOwnerId, null, normalized, request.isAcknowledgeDuplicate());

        ServiceContactEntity contact = new ServiceContactEntity();
        contact.setOwnerUserId(accountOwnerId);
        contact.setCreatedByUserId(callerId);
        contact.setActive(true);
        apply(contact, normalized, callerId);
        ServiceContactEntity saved = contactRepository.save(contact);
        log.info("Created service contact {}", saved.getId());
        return toResponse(scope, saved);
    }

    @Transactional
    public ServiceContactResponse update(UUID callerId, UUID contactId, SaveServiceContactRequest request) {
        CallerScope scope = accessService.requireDirectoryAccess(callerId);
        ServiceContactEntity contact = requireContact(contactId);
        accessService.requireEdit(scope, contact);
        if (request.getVersion() == null || request.getVersion() != contact.getVersion()) {
            throw new BusinessException(
                    "SERVICE_CONTACT_CONFLICT",
                    "This contact was updated by someone else. Refresh and try again.",
                    HttpStatus.CONFLICT);
        }
        NormalizedContact normalized = normalize(request);
        assertNoDuplicate(contact.getOwnerUserId(), contact.getId(), normalized, request.isAcknowledgeDuplicate());
        apply(contact, normalized, callerId);
        ServiceContactEntity saved = contactRepository.save(contact);
        log.info("Updated service contact {}", saved.getId());
        return toResponse(scope, saved);
    }

    @Transactional
    public void archive(UUID callerId, UUID contactId) {
        CallerScope scope = accessService.requireDirectoryAccess(callerId);
        ServiceContactEntity contact = requireContact(contactId);
        accessService.requireArchive(scope, contact);
        contact.setActive(false);
        contact.setUpdatedByUserId(callerId);
        contactRepository.save(contact);
        log.info("Archived service contact {}", contact.getId());
    }

    @Transactional
    public ServiceContactResponse addSpace(UUID callerId, UUID contactId, UUID spaceId) {
        return unchangedContact(callerId, contactId);
    }

    @Transactional
    public ServiceContactResponse removeSpace(UUID callerId, UUID contactId, UUID spaceId) {
        return unchangedContact(callerId, contactId);
    }

    private ServiceContactResponse unchangedContact(UUID callerId, UUID contactId) {
        CallerScope scope = accessService.requireDirectoryAccess(callerId);
        ServiceContactEntity contact = requireContact(contactId);
        accessService.requireEdit(scope, contact);
        return toResponse(scope, contact);
    }

    private UUID resolveAccountOwner(CallerScope scope) {
        if (scope.ownsAccountDirectory()) {
            return scope.callerId();
        }
        if (scope.directoryOwnerIds().size() == 1) {
            return scope.directoryOwnerIds().iterator().next();
        }
        throw new BusinessException(
                "SERVICE_CONTACT_ACCOUNT_AMBIGUOUS",
                "Service contacts belong to one owner account",
                HttpStatus.BAD_REQUEST);
    }

    private void assertNoDuplicate(
            UUID accountOwnerId, UUID excludeId, NormalizedContact normalized, boolean acknowledge) {
        if (acknowledge || normalized.phones().isEmpty()) {
            return;
        }
        List<ServiceContactEntity> matches = contactRepository
                .findActiveByOwnerAndAnyPhone(accountOwnerId, normalized.phones())
                .stream()
                .filter(existing -> excludeId == null || !excludeId.equals(existing.getId()))
                .toList();
        if (matches.isEmpty()) {
            return;
        }
        String incomingName = ServiceContactRules.normalizeComparableName(normalized.businessName());
        List<ServiceContactDuplicateMatchResponse> summaries = matches.stream()
                .map(existing -> {
                    boolean nameMatch = incomingName.equals(
                            ServiceContactRules.normalizeComparableName(existing.getBusinessName()));
                    return ServiceContactDuplicateMatchResponse.builder()
                            .id(existing.getId())
                            .businessName(existing.getBusinessName())
                            .category(existing.getCategory().name())
                            .categoryLabel(displayCategory(existing))
                            .customCategoryName(existing.getCustomCategoryName())
                            .primaryPhone(ServiceContactRules.displayPhone(existing.getPrimaryPhone()))
                            .nameAndPhoneMatch(nameMatch)
                            .build();
                })
                .toList();
        boolean strong = summaries.stream().anyMatch(ServiceContactDuplicateMatchResponse::isNameAndPhoneMatch);
        throw new DuplicateServiceContactException(new DuplicateServiceContactResponse(strong, summaries));
    }

    private void apply(ServiceContactEntity contact, NormalizedContact normalized, UUID callerId) {
        contact.setBusinessName(normalized.businessName());
        contact.setCategory(normalized.category());
        contact.setCustomCategoryName(normalized.customCategoryName());
        contact.setContactPersonName(normalized.contactPersonName());
        contact.setPrimaryPhone(normalized.primaryPhone());
        contact.setAlternatePhone(normalized.alternatePhone());
        contact.setWhatsappPhone(normalized.whatsappPhone());
        contact.setEmail(normalized.email());
        contact.setDescription(normalized.description());
        contact.setAddress(normalized.address());
        contact.setNotes(normalized.notes());
        contact.setUpdatedByUserId(callerId);
    }

    private NormalizedContact normalize(SaveServiceContactRequest request) {
        String businessName = ServiceContactRules.requireBusinessName(request.getBusinessName());
        ServiceContactCategory category = ServiceContactRules.requireCategory(request.getCategory());
        String custom = ServiceContactRules.customCategoryName(category, request.getCustomCategoryName());
        String primary = ServiceContactRules.optionalPhone(request.getPrimaryPhone(), "Primary phone");
        String alternate = ServiceContactRules.optionalPhone(request.getAlternatePhone(), "Alternate phone");
        String whatsapp = ServiceContactRules.optionalPhone(request.getWhatsappPhone(), "WhatsApp number");
        String email = ServiceContactRules.optionalEmail(request.getEmail());
        ServiceContactRules.requireContactMethod(primary, alternate, whatsapp, email);
        return new NormalizedContact(
                businessName,
                category,
                custom,
                ServiceContactRules.optionalText(
                        request.getContactPersonName(),
                        ServiceContactRules.NAME_MAX,
                        "INVALID_NAME",
                        "Contact person"),
                primary,
                alternate,
                whatsapp,
                email,
                ServiceContactRules.optionalText(
                        request.getDescription(),
                        ServiceContactRules.DESCRIPTION_MAX,
                        "INVALID_DESCRIPTION",
                        "Description"),
                ServiceContactRules.optionalText(
                        request.getAddress(), ServiceContactRules.ADDRESS_MAX, "INVALID_ADDRESS", "Address"),
                ServiceContactRules.optionalText(
                        request.getNotes(), ServiceContactRules.NOTES_MAX, "INVALID_NOTES", "Notes"),
                ServiceContactRules.phoneSet(primary, alternate, whatsapp));
    }

    private ServiceContactEntity requireContact(UUID contactId) {
        return contactRepository
                .findById(contactId)
                .orElseThrow(() -> new BusinessException(
                        "SERVICE_CONTACT_NOT_FOUND", "Service contact not found", HttpStatus.NOT_FOUND));
    }

    private ServiceContactResponse toResponse(CallerScope scope, ServiceContactEntity contact) {
        return ServiceContactResponse.builder()
                .id(contact.getId())
                .businessName(contact.getBusinessName())
                .category(contact.getCategory().name())
                .categoryLabel(displayCategory(contact))
                .customCategoryName(contact.getCustomCategoryName())
                .contactPersonName(contact.getContactPersonName())
                .primaryPhone(ServiceContactRules.displayPhone(contact.getPrimaryPhone()))
                .alternatePhone(ServiceContactRules.displayPhone(contact.getAlternatePhone()))
                .whatsappPhone(ServiceContactRules.displayPhone(contact.getWhatsappPhone()))
                .email(contact.getEmail())
                .description(contact.getDescription())
                .address(contact.getAddress())
                .notes(contact.getNotes())
                .active(contact.isActive())
                .canEdit(accessService.canEdit(scope, contact))
                .canArchive(accessService.canArchive(scope, contact))
                .spaces(List.of())
                .version(contact.getVersion())
                .createdAt(contact.getCreatedAt())
                .updatedAt(contact.getUpdatedAt())
                .build();
    }

    private static String displayCategory(ServiceContactEntity contact) {
        if (contact.getCategory() == ServiceContactCategory.OTHER
                && contact.getCustomCategoryName() != null
                && !contact.getCustomCategoryName().isBlank()) {
            return contact.getCustomCategoryName();
        }
        return contact.getCategory().label();
    }

    private static int clampSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private static Sort toSort(String sort) {
        String key = sort == null ? "" : sort.trim().toLowerCase(Locale.ROOT);
        return switch (key) {
            case "name" -> Sort.by(Sort.Order.asc("businessName").ignoreCase());
            case "category" -> Sort.by(Sort.Order.asc("category"), Sort.Order.asc("businessName").ignoreCase());
            default -> Sort.by(Sort.Order.desc("updatedAt"));
        };
    }

    private record NormalizedContact(
            String businessName,
            ServiceContactCategory category,
            String customCategoryName,
            String contactPersonName,
            String primaryPhone,
            String alternatePhone,
            String whatsappPhone,
            String email,
            String description,
            String address,
            String notes,
            Set<String> phones) {}
}
