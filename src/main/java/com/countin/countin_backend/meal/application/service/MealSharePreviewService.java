package com.countin.countin_backend.meal.application.service;

import com.countin.countin_backend.common.exception.BusinessException;
import com.countin.countin_backend.meal.api.dto.response.MealSharePreviewLineResponse;
import com.countin.countin_backend.meal.api.dto.response.MealSharePreviewResponse;
import com.countin.countin_backend.meal.api.dto.response.MealSharePreviewSlotResponse;
import com.countin.countin_backend.meal.domain.model.DailyMenuEntryType;
import com.countin.countin_backend.meal.domain.model.DailyMenuStatus;
import com.countin.countin_backend.meal.domain.model.MealType;
import com.countin.countin_backend.meal.application.support.ComboItemDetailFormatter;
import com.countin.countin_backend.meal.domain.policy.MealPollEligibilityPolicy;
import com.countin.countin_backend.meal.domain.policy.MealOccupancyPolicy;
import com.countin.countin_backend.meal.infrastructure.persistence.entity.DailyMenuEntity;
import com.countin.countin_backend.meal.infrastructure.persistence.entity.DailyMenuEntryEntity;
import com.countin.countin_backend.meal.infrastructure.persistence.entity.MealParticipationEntity;
import com.countin.countin_backend.meal.infrastructure.persistence.repository.DailyMenuEntryRepository;
import com.countin.countin_backend.meal.infrastructure.persistence.repository.DailyMenuRepository;
import com.countin.countin_backend.meal.infrastructure.persistence.repository.DailyMenuPackageItemRepository;
import com.countin.countin_backend.meal.infrastructure.persistence.repository.MealComboItemRepository;
import com.countin.countin_backend.meal.infrastructure.persistence.repository.MealParticipationRepository;
import com.countin.countin_backend.space.domain.model.SpaceType;
import com.countin.countin_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.countin.countin_backend.space.infrastructure.persistence.repository.SpaceRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MealSharePreviewService {

    private static final String NOT_PUBLISHED_LABEL = "(not published)";
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("EEEE, d MMM yyyy", Locale.ENGLISH);

    private final DailyMenuRepository dailyMenuRepository;
    private final DailyMenuEntryRepository dailyMenuEntryRepository;
    private final MealComboItemRepository mealComboItemRepository;
    private final DailyMenuPackageItemRepository dailyMenuPackageItemRepository;
    private final MealParticipationRepository participationRepository;
    private final SpaceRepository spaceRepository;
    private final MealAccessService mealAccessService;
    private final MealPollEligibilityPolicy pollEligibilityPolicy;
    private final MealOccupancyPolicy occupancyPolicy;

    @Transactional(readOnly = true)
    public MealSharePreviewResponse getSharePreview(
            UUID spaceId, UUID callerId, LocalDate date, MealType mealType) {
        mealAccessService.requireManageMeals(spaceId, callerId);
        LocalDate menuDate = date != null ? date : LocalDate.now();
        SpaceEntity space = spaceRepository
                .findById(spaceId)
                .orElseThrow(() -> new BusinessException("Space not found", HttpStatus.NOT_FOUND));

        List<MealType> targetTypes = mealType != null ? List.of(mealType) : List.of(MealType.values());
        List<MealParticipationEntity> participations = participationRepository.findAllNonStoppedBySpaceId(spaceId);
        Set<UUID> occupiedMemberIds = occupancyPolicy
                .occupiedMemberIdsForDate(space, menuDate)
                .orElse(null);
        List<MealSharePreviewSlotResponse> slots = new ArrayList<>();

        for (MealType type : targetTypes) {
            Optional<DailyMenuEntity> publishedMenu = dailyMenuRepository
                    .findBySpaceDateAndType(spaceId, menuDate, type)
                    .filter(menu -> !menu.isDeleted()
                            && (menu.getStatus() == DailyMenuStatus.PUBLISHED
                                    || menu.getStatus() == DailyMenuStatus.MODIFIED));

            if (publishedMenu.isPresent()) {
                DailyMenuEntity menu = publishedMenu.get();
                slots.add(MealSharePreviewSlotResponse.builder()
                        .mealType(type)
                        .dailyMenuId(menu.getId())
                        .lines(buildLines(menu))
                        .notes(menu.getNotes())
                        .eligibleCount(countEligible(participations, menuDate, type, occupiedMemberIds))
                        .build());
            } else if (mealType != null) {
                slots.add(MealSharePreviewSlotResponse.builder()
                        .mealType(type)
                        .lines(List.of(MealSharePreviewLineResponse.builder()
                                .label(NOT_PUBLISHED_LABEL)
                                .build()))
                        .eligibleCount(countEligible(participations, menuDate, type, occupiedMemberIds))
                        .build());
            }
        }

        if (slots.isEmpty()) {
            throw new BusinessException(
                    "No published menus to share for this date", HttpStatus.BAD_REQUEST);
        }

        DailyMenuEntity primaryPublished = slots.stream()
                .map(MealSharePreviewSlotResponse::getDailyMenuId)
                .filter(id -> id != null)
                .findFirst()
                .flatMap(id -> dailyMenuRepository.findById(id))
                .orElse(null);

        String messageText = buildMessageText(space.getName(), menuDate, mealType, slots, space.getType());

        return MealSharePreviewResponse.builder()
                .spaceName(space.getName())
                .menuDate(menuDate)
                .mealType(mealType != null ? mealType : (targetTypes.size() == 1 ? targetTypes.get(0) : null))
                .dailyMenuId(primaryPublished != null ? primaryPublished.getId() : null)
                .status(primaryPublished != null ? DailyMenuStatus.PUBLISHED : null)
                .eligibleCount(slots.isEmpty() ? 0 : slots.get(0).getEligibleCount())
                .messageText(messageText)
                .slots(slots)
                .build();
    }

    private List<MealSharePreviewLineResponse> buildLines(DailyMenuEntity menu) {
        List<DailyMenuEntryEntity> entries = dailyMenuEntryRepository.findByDailyMenuId(menu.getId());
        return entries.stream()
                .filter(DailyMenuEntryEntity::isAvailable)
                .map(this::toLine)
                .toList();
    }

    private MealSharePreviewLineResponse toLine(DailyMenuEntryEntity entry) {
        String detail = null;
        BigDecimal price = null;
        String currencyCode = entry.getCurrencyCode();
        if (entry.getEntryType() == DailyMenuEntryType.COMBO && entry.getCombo() != null) {
            detail = ComboItemDetailFormatter.join(
                    mealComboItemRepository.findByComboIdWithItems(entry.getCombo().getId()), " · ");
            price = entry.getCombo().getPrice();
            currencyCode = entry.getCombo().getCurrencyCode();
        } else if (entry.getEntryType() == DailyMenuEntryType.PACKAGE) {
            detail = dailyMenuPackageItemRepository.findByEntryIdWithItems(entry.getId()).stream()
                    .map(pi -> pi.getItem().getName())
                    .collect(Collectors.joining(" · "));
            price = entry.getPrice();
        }
        return MealSharePreviewLineResponse.builder()
                .entryType(entry.getEntryType())
                .label(entry.getLabel())
                .detail(detail != null && !detail.isBlank() ? detail : null)
                .price(price)
                .currencyCode(currencyCode)
                .build();
    }

    private int countEligible(
            List<MealParticipationEntity> participations,
            LocalDate date,
            MealType mealType,
            Set<UUID> occupiedMemberIds) {
        int count = 0;
        for (MealParticipationEntity participation : participations) {
            if (pollEligibilityPolicy.isPollEligible(participation, date, mealType, occupiedMemberIds)) {
                count++;
            }
        }
        return count;
    }

    private String buildMessageText(
            String spaceName,
            LocalDate menuDate,
            MealType mealType,
            List<MealSharePreviewSlotResponse> slots,
            SpaceType spaceType) {
        StringBuilder message = new StringBuilder();
        message.append("🍽 ").append(spaceName).append('\n');
        message.append(DATE_FORMAT.format(menuDate));
        if (mealType != null) {
            message.append(" · ").append(formatMealType(mealType));
        }
        message.append("\n\n");

        for (MealSharePreviewSlotResponse slot : slots) {
            if (mealType == null && slots.size() > 1) {
                message.append(formatMealType(slot.getMealType())).append('\n');
            }
            if (slot.getLines().size() == 1
                    && NOT_PUBLISHED_LABEL.equals(slot.getLines().get(0).getLabel())) {
                message.append(NOT_PUBLISHED_LABEL).append('\n');
            } else {
                appendNumberedSlotOptions(message, slot);
            }
            if (slot.getNotes() != null && !slot.getNotes().isBlank()) {
                message.append("Note: ").append(slot.getNotes()).append('\n');
            }
            message.append('\n');
        }

        if (mealType != null && !slots.isEmpty()) {
            message.append("Eligible participants: ").append(slots.get(0).getEligibleCount());
        } else if (slots.size() > 1) {
            message.append("Eligible participants: ");
            message.append(slots.stream()
                    .map(slot -> formatMealType(slot.getMealType()) + " " + slot.getEligibleCount())
                    .collect(Collectors.joining(" · ")));
        } else if (!slots.isEmpty()) {
            message.append("Eligible participants: ").append(slots.get(0).getEligibleCount());
        }

        if (spaceType == SpaceType.MESS) {
            message.append("\n\nSelect one option.");
            message.append("\n\nNeed multiple meal selections or quantities?");
            message.append("\nOpen the CountIn app to manage advanced meal selections.");
        }

        return message.toString().trim();
    }

    private String formatMealType(MealType mealType) {
        String name = mealType.name().toLowerCase(Locale.ENGLISH);
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private void appendNumberedSlotOptions(StringBuilder message, MealSharePreviewSlotResponse slot) {
        int optionNum = 1;
        for (MealSharePreviewLineResponse line : slot.getLines()) {
            message.append(optionNum).append(". ").append(formatOptionLabel(line)).append('\n');
            if (line.getDetail() != null && !line.getDetail().isBlank()) {
                message.append(formatDetailForShare(line.getDetail())).append('\n');
            }
            optionNum++;
        }
        message.append(optionNum)
                .append(". Not available for ")
                .append(formatMealType(slot.getMealType()))
                .append('\n');
    }

    private String formatOptionLabel(MealSharePreviewLineResponse line) {
        if (line.getPrice() == null) {
            return line.getLabel();
        }
        return line.getLabel() + " - " + formatPrice(line.getPrice(), line.getCurrencyCode());
    }

    private String formatPrice(BigDecimal price, String currencyCode) {
        String code = currencyCode != null ? currencyCode : "INR";
        String amount = price.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
        if ("INR".equalsIgnoreCase(code)) {
            return "₹" + amount;
        }
        return code + " " + amount;
    }

    private String formatDetailForShare(String detail) {
        return detail.replace(" · ", ", ");
    }
}
