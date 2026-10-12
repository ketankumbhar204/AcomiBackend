package com.acomi.acomi_backend.space.infrastructure.persistence.repository;

import com.acomi.acomi_backend.meal.domain.model.FoodType;
import com.acomi.acomi_backend.meal.domain.model.MealPlanCode;
import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.FoodItemEntity;
import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.MealComboEntity;
import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.MealPlanEntity;
import com.acomi.acomi_backend.mess.infrastructure.persistence.entity.MessRegistrationEntity;
import com.acomi.acomi_backend.property.infrastructure.persistence.entity.PropertyRegistrationEntity;
import com.acomi.acomi_backend.registration.application.AdminLeadDefaults;
import com.acomi.acomi_backend.space.application.support.DiscoverListingText;
import com.acomi.acomi_backend.space.application.support.FoodAvailability;
import com.acomi.acomi_backend.space.application.support.FurnishingLevel;
import com.acomi.acomi_backend.space.application.support.SharingOccupancy;
import com.acomi.acomi_backend.space.application.support.SpaceDiscoverQuery;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceAmenityEntity;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.List;

/** Extra discover predicates. Blank listing fields never match a selected value. */
final class DiscoverExtraFilters {

    private DiscoverExtraFilters() {}

    static void add(
            List<Predicate> predicates,
            Root<SpaceEntity> root,
            CriteriaQuery<?> query,
            CriteriaBuilder cb,
            SpaceDiscoverQuery filters) {
        if (query == null) {
            return;
        }
        if (!filters.genders().isEmpty()) {
            predicates.add(root.get("genderPolicy").in(filters.genders()));
        }
        if (!filters.sharings().isEmpty()) {
            predicates.add(anySharing(root, query, cb, filters.sharings()));
        }
        if (!filters.foods().isEmpty()) {
            predicates.add(anyFood(root, query, cb, filters.foods()));
        }
        if (!filters.furnishings().isEmpty()) {
            predicates.add(anyFurnishing(root, query, cb, filters.furnishings()));
        }
        if (filters.contactAvailable()) {
            predicates.add(usableContact(root, query, cb));
        }
        if (!filters.mealTypes().isEmpty()) {
            Predicate mealType = mealTypeOffered(root, query, cb, filters.mealTypes());
            if (mealType != null) {
                predicates.add(mealType);
            }
        }
        if (!filters.foodTypes().isEmpty()) {
            Predicate foodType = foodTypeOffered(root, query, cb, filters.foodTypes());
            if (foodType != null) {
                predicates.add(foodType);
            }
        }
    }

    /** Breakfast, lunch, or dinner from an active meal plan. Selected slots are OR. */
    private static Predicate mealTypeOffered(
            Root<SpaceEntity> root,
            CriteriaQuery<?> query,
            CriteriaBuilder cb,
            List<String> mealTypes) {
        List<Predicate> slots = new ArrayList<>();
        Subquery<Integer> plans = query.subquery(Integer.class);
        Root<MealPlanEntity> plan = plans.from(MealPlanEntity.class);
        plans.select(cb.literal(1));
        for (String slot : mealTypes) {
            if ("BREAKFAST".equals(slot)) {
                slots.add(cb.or(
                        cb.isTrue(plan.get("breakfastIncluded")),
                        plan.get("code").in(MealPlanCode.BREAKFAST, MealPlanCode.FULL)));
            } else if ("LUNCH".equals(slot)) {
                slots.add(cb.or(
                        cb.isTrue(plan.get("lunchIncluded")),
                        plan.get("code").in(MealPlanCode.LUNCH, MealPlanCode.FULL)));
            } else if ("DINNER".equals(slot)) {
                slots.add(cb.or(
                        cb.isTrue(plan.get("dinnerIncluded")),
                        plan.get("code").in(MealPlanCode.DINNER, MealPlanCode.FULL)));
            }
        }
        if (slots.isEmpty()) {
            return null;
        }
        plans.where(
                cb.equal(plan.get("space").get("id"), root.get("id")),
                cb.isTrue(plan.get("isActive")),
                cb.or(slots.toArray(Predicate[]::new)));
        return cb.exists(plans);
    }

    /** Veg, non-veg, or egg from an active combo or space food item. Selected types are OR. */
    private static Predicate foodTypeOffered(
            Root<SpaceEntity> root,
            CriteriaQuery<?> query,
            CriteriaBuilder cb,
            List<String> foodTypes) {
        List<FoodType> selected = new ArrayList<>();
        for (String code : foodTypes) {
            if ("VEG".equals(code) || "NON_VEG".equals(code) || "EGG".equals(code)) {
                selected.add(FoodType.valueOf(code));
            }
        }
        if (selected.isEmpty()) {
            return null;
        }
        Subquery<Integer> combos = query.subquery(Integer.class);
        Root<MealComboEntity> combo = combos.from(MealComboEntity.class);
        combos.select(cb.literal(1));
        combos.where(
                cb.equal(combo.get("space").get("id"), root.get("id")),
                cb.isTrue(combo.get("isActive")),
                combo.get("foodType").in(selected));

        Subquery<Integer> items = query.subquery(Integer.class);
        Root<FoodItemEntity> item = items.from(FoodItemEntity.class);
        items.select(cb.literal(1));
        items.where(
                cb.equal(item.get("space").get("id"), root.get("id")),
                cb.isTrue(item.get("isActive")),
                item.get("foodType").in(selected));
        return cb.or(cb.exists(combos), cb.exists(items));
    }

    static Predicate typePredicate(
            Root<SpaceEntity> root,
            CriteriaQuery<?> query,
            CriteriaBuilder cb,
            SpaceDiscoverQuery filters) {
        boolean typed = !filters.types().isEmpty();
        boolean serviced = filters.servicedApartment();
        if (!typed && !serviced) {
            return null;
        }
        Predicate listed = typed ? root.get("type").in(filters.types()) : null;
        if (!serviced) {
            return listed;
        }
        Predicate servicedMatch = cb.and(
                cb.equal(root.get("type"), SpaceType.RENTAL),
                textOnListing(root, query, cb, true, DiscoverListingText.servicedLikePatterns()));
        if (listed == null) {
            return servicedMatch;
        }
        return cb.or(listed, servicedMatch);
    }

    static Predicate amenityAlias(Root<SpaceEntity> root, CriteriaQuery<?> query, CriteriaBuilder cb, String code) {
        List<String> patterns = DiscoverListingText.amenityLikePatterns(code);
        if (patterns.isEmpty() || query == null) {
            return cb.disjunction();
        }
        return textOnListing(root, query, cb, false, patterns);
    }

    private static Predicate anySharing(
            Root<SpaceEntity> root,
            CriteriaQuery<?> query,
            CriteriaBuilder cb,
            List<SharingOccupancy> sharings) {
        List<Predicate> options = new ArrayList<>();
        for (SharingOccupancy sharing : sharings) {
            options.add(sharingOnNotes(root, query, cb, sharing));
        }
        return cb.or(options.toArray(Predicate[]::new));
    }

    private static Predicate sharingOnNotes(
            Root<SpaceEntity> root, CriteriaQuery<?> query, CriteriaBuilder cb, SharingOccupancy sharing) {
        Subquery<Integer> property = query.subquery(Integer.class);
        Root<PropertyRegistrationEntity> propertyRoot = property.from(PropertyRegistrationEntity.class);
        property.select(cb.literal(1));
        property.where(
                cb.equal(propertyRoot.get("convertedSpaceId"), root.get("id")),
                sharingMatch(cb, padded(cb, propertyRoot.get("sharingNotes")), sharing));

        Subquery<Integer> mess = query.subquery(Integer.class);
        Root<MessRegistrationEntity> messRoot = mess.from(MessRegistrationEntity.class);
        mess.select(cb.literal(1));
        mess.where(
                cb.equal(messRoot.get("convertedSpaceId"), root.get("id")),
                sharingMatch(cb, padded(cb, messRoot.get("sharingNotes")), sharing));
        return cb.or(cb.exists(property), cb.exists(mess));
    }

    private static Predicate sharingMatch(CriteriaBuilder cb, Expression<String> padded, SharingOccupancy sharing) {
        Predicate context = cb.or(
                cb.like(padded, "% shar%"),
                cb.like(padded, "% occup%"),
                cb.like(padded, "% bed%"),
                cb.like(padded, "% person%"),
                cb.like(padded, "% room%"));
        return switch (sharing) {
            case SINGLE -> cb.or(cb.like(padded, "% single %"), cb.and(context, cb.like(padded, "% 1 %")));
            case TWO -> cb.or(
                    cb.like(padded, "% double %"),
                    cb.like(padded, "% twin %"),
                    cb.and(context, cb.like(padded, "% 2 %")));
            case THREE -> cb.or(cb.like(padded, "% triple %"), cb.and(context, cb.like(padded, "% 3 %")));
            case FOUR -> cb.and(context, cb.like(padded, "% 4 %"));
            case FIVE -> cb.and(context, cb.like(padded, "% 5 %"));
            case SIX_PLUS -> cb.and(context, numberAtLeastSix(cb, padded));
        };
    }

    private static Predicate numberAtLeastSix(CriteriaBuilder cb, Expression<String> padded) {
        List<Predicate> numbers = new ArrayList<>();
        for (int number = 6; number <= 30; number++) {
            numbers.add(cb.like(padded, "% " + number + " %"));
        }
        return cb.or(numbers.toArray(Predicate[]::new));
    }

    private static Predicate anyFood(
            Root<SpaceEntity> root, CriteriaQuery<?> query, CriteriaBuilder cb, List<FoodAvailability> foods) {
        Predicate included = foodIncluded(root, query, cb);
        List<Predicate> options = new ArrayList<>();
        for (FoodAvailability food : foods) {
            options.add(switch (food) {
                case INCLUDED -> included;
                case AVAILABLE -> cb.and(cb.not(included), foodAvailableText(root, query, cb));
                case NONE -> cb.and(cb.not(included), cb.not(foodAvailableText(root, query, cb)), explicitNoFood(root, query, cb));
            });
        }
        return cb.or(options.toArray(Predicate[]::new));
    }

    private static Predicate foodIncluded(Root<SpaceEntity> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        Subquery<Integer> amenity = query.subquery(Integer.class);
        Root<SpaceAmenityEntity> amenityRoot = amenity.from(SpaceAmenityEntity.class);
        amenity.select(cb.literal(1));
        amenity.where(
                cb.equal(amenityRoot.get("space").get("id"), root.get("id")),
                cb.equal(cb.upper(amenityRoot.get("amenityCode")), "FOOD_INCLUDED"));

        Subquery<Integer> property = query.subquery(Integer.class);
        Root<PropertyRegistrationEntity> propertyRoot = property.from(PropertyRegistrationEntity.class);
        property.select(cb.literal(1));
        property.where(
                cb.equal(propertyRoot.get("convertedSpaceId"), root.get("id")),
                cb.isTrue(propertyRoot.get("foodIncludedListing")));

        Subquery<Integer> mess = query.subquery(Integer.class);
        Root<MessRegistrationEntity> messRoot = mess.from(MessRegistrationEntity.class);
        mess.select(cb.literal(1));
        mess.where(
                cb.equal(messRoot.get("convertedSpaceId"), root.get("id")),
                cb.isTrue(messRoot.get("foodIncludedListing")));

        return cb.or(cb.isTrue(root.get("foodIncludedInRent")), cb.exists(amenity), cb.exists(property), cb.exists(mess));
    }

    private static Predicate explicitNoFood(Root<SpaceEntity> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        Subquery<Integer> property = query.subquery(Integer.class);
        Root<PropertyRegistrationEntity> propertyRoot = property.from(PropertyRegistrationEntity.class);
        property.select(cb.literal(1));
        property.where(
                cb.equal(propertyRoot.get("convertedSpaceId"), root.get("id")),
                cb.isFalse(propertyRoot.get("foodIncludedListing")));

        Subquery<Integer> mess = query.subquery(Integer.class);
        Root<MessRegistrationEntity> messRoot = mess.from(MessRegistrationEntity.class);
        mess.select(cb.literal(1));
        mess.where(
                cb.equal(messRoot.get("convertedSpaceId"), root.get("id")),
                cb.isFalse(messRoot.get("foodIncludedListing")));
        return cb.or(cb.exists(property), cb.exists(mess));
    }

    private static Predicate foodAvailableText(Root<SpaceEntity> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        return textOnListing(root, query, cb, false, DiscoverListingText.foodAvailableLikePatterns());
    }

    private static Predicate anyFurnishing(
            Root<SpaceEntity> root, CriteriaQuery<?> query, CriteriaBuilder cb, List<FurnishingLevel> levels) {
        List<Predicate> options = new ArrayList<>();
        for (FurnishingLevel level : levels) {
            options.add(textOnListing(root, query, cb, false, DiscoverListingText.furnishingLikePatterns(level)));
        }
        return cb.or(options.toArray(Predicate[]::new));
    }

    private static Predicate usableContact(Root<SpaceEntity> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        Predicate spaceContact = usablePhone(cb, root.get("contactNumber"));

        Subquery<Integer> property = query.subquery(Integer.class);
        Root<PropertyRegistrationEntity> propertyRoot = property.from(PropertyRegistrationEntity.class);
        property.select(cb.literal(1));
        property.where(
                cb.equal(propertyRoot.get("convertedSpaceId"), root.get("id")),
                cb.or(
                        usablePhone(cb, propertyRoot.get("mobileNumber")),
                        usablePhone(cb, propertyRoot.get("alternateMobileNumber")),
                        usablePhone(cb, propertyRoot.get("additionalMobileNumber"))));

        Subquery<Integer> mess = query.subquery(Integer.class);
        Root<MessRegistrationEntity> messRoot = mess.from(MessRegistrationEntity.class);
        mess.select(cb.literal(1));
        mess.where(
                cb.equal(messRoot.get("convertedSpaceId"), root.get("id")),
                cb.or(
                        usablePhone(cb, messRoot.get("mobileNumber")),
                        usablePhone(cb, messRoot.get("alternateMobileNumber")),
                        usablePhone(cb, messRoot.get("additionalMobileNumber"))));
        return cb.or(spaceContact, cb.exists(property), cb.exists(mess));
    }

    private static Predicate usablePhone(CriteriaBuilder cb, Expression<String> field) {
        Expression<String> trimmed = cb.trim(field);
        return cb.and(
                cb.isNotNull(field),
                cb.greaterThanOrEqualTo(cb.length(trimmed), 8),
                cb.notEqual(trimmed, AdminLeadDefaults.PLACEHOLDER_MOBILE),
                cb.notEqual(trimmed, "-"));
    }

    /**
     * @param includeName serviced-apartment text can appear in the listing name. Furnishing and food notes do not.
     */
    private static Predicate textOnListing(
            Root<SpaceEntity> root,
            CriteriaQuery<?> query,
            CriteriaBuilder cb,
            boolean includeName,
            List<String> patterns) {
        if (patterns.isEmpty()) {
            return cb.disjunction();
        }
        List<Predicate> matches = new ArrayList<>();
        if (includeName) {
            matches.add(likes(cb, padded(cb, root.get("name")), patterns));
        }
        matches.add(registrationText(root, query, cb, PropertyRegistrationEntity.class, patterns));
        matches.add(registrationText(root, query, cb, MessRegistrationEntity.class, patterns));
        matches.add(customAmenityLabel(root, query, cb, patterns));
        return cb.or(matches.toArray(Predicate[]::new));
    }

    private static Predicate registrationText(
            Root<SpaceEntity> root,
            CriteriaQuery<?> query,
            CriteriaBuilder cb,
            Class<?> entity,
            List<String> patterns) {
        Subquery<Integer> rows = query.subquery(Integer.class);
        Root<?> registration = rows.from(entity);
        rows.select(cb.literal(1));
        Expression<String> combined = cb.concat(
                cb.concat(padded(cb, registration.get("description")), padded(cb, registration.get("sharingNotes"))),
                padded(cb, registration.get("unmappedAmenities")));
        rows.where(cb.equal(registration.get("convertedSpaceId"), root.get("id")), likes(cb, combined, patterns));
        return cb.exists(rows);
    }

    private static Predicate customAmenityLabel(
            Root<SpaceEntity> root, CriteriaQuery<?> query, CriteriaBuilder cb, List<String> patterns) {
        Subquery<Integer> rows = query.subquery(Integer.class);
        Root<SpaceAmenityEntity> amenity = rows.from(SpaceAmenityEntity.class);
        rows.select(cb.literal(1));
        rows.where(
                cb.equal(amenity.get("space").get("id"), root.get("id")),
                likes(cb, padded(cb, amenity.get("customLabel")), patterns));
        return cb.exists(rows);
    }

    private static Predicate likes(CriteriaBuilder cb, Expression<String> field, List<String> patterns) {
        List<Predicate> likes = new ArrayList<>();
        for (String pattern : patterns) {
            likes.add(cb.like(field, pattern));
        }
        return cb.or(likes.toArray(Predicate[]::new));
    }

    private static Expression<String> padded(CriteriaBuilder cb, Expression<String> field) {
        Expression<String> normalized = cb.function(
                "regexp_replace",
                String.class,
                cb.lower(cb.coalesce(field, cb.literal(""))),
                cb.literal("[^a-z0-9]+"),
                cb.literal(" "),
                cb.literal("g"));
        return cb.concat(cb.concat(cb.literal(" "), normalized), cb.literal(" "));
    }
}
