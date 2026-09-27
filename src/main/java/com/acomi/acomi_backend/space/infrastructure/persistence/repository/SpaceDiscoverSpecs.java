package com.acomi.acomi_backend.space.infrastructure.persistence.repository;

import com.acomi.acomi_backend.mess.infrastructure.persistence.entity.MessRegistrationEntity;
import com.acomi.acomi_backend.property.infrastructure.persistence.entity.PropertyRegistrationEntity;
import com.acomi.acomi_backend.space.application.support.LocationFilterTokens;
import com.acomi.acomi_backend.space.application.support.SpaceDiscoverQuery;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceAmenityEntity;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

/** Criteria for authenticated space discovery (active spaces). */
public final class SpaceDiscoverSpecs {

    private SpaceDiscoverSpecs() {}

    public static Specification<SpaceEntity> discover(String search, SpaceType type) {
        return discover(search, type, false, null);
    }

    /**
     * @param includeTestSpaces when true, also include active spaces converted from test leads
     *     even if not discoverable (local/dev only).
     */
    public static Specification<SpaceEntity> discover(
            String search, SpaceType type, boolean includeTestSpaces) {
        return discover(search, type, includeTestSpaces, null);
    }

    /**
     * @param location case-insensitive contains match against the listing address only
     *     ({@code spaces.address}, property {@code address_line}, or mess {@code address_line}).
     *     Parenthetical labels are split into location-level terms and explicit locality
     *     aliases (for example Hinjawadi/Hinjewadi). Does not match space name.
     */
    public static Specification<SpaceEntity> discover(
            String search, SpaceType type, boolean includeTestSpaces, String location) {
        return discover(SpaceDiscoverQuery.of(search, type, location), includeTestSpaces);
    }

    /**
     * Filters first. Callers must paginate the resulting specification with a stable sort.
     */
    public static Specification<SpaceEntity> discover(
            SpaceDiscoverQuery filters, boolean includeTestSpaces) {
        SpaceDiscoverQuery queryFilters = filters == null
                ? SpaceDiscoverQuery.of(null, null, null)
                : filters;
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isTrue(root.get("isActive")));

            Predicate discoverable = cb.isTrue(root.get("discoverable"));
            if (includeTestSpaces && query != null) {
                Subquery<Integer> propertyTest = query.subquery(Integer.class);
                Root<PropertyRegistrationEntity> propertyRoot =
                        propertyTest.from(PropertyRegistrationEntity.class);
                propertyTest.select(cb.literal(1));
                propertyTest.where(
                        cb.equal(propertyRoot.get("convertedSpaceId"), root.get("id")),
                        cb.isTrue(propertyRoot.get("testLead")));

                Subquery<Integer> messTest = query.subquery(Integer.class);
                Root<MessRegistrationEntity> messRoot = messTest.from(MessRegistrationEntity.class);
                messTest.select(cb.literal(1));
                messTest.where(
                        cb.equal(messRoot.get("convertedSpaceId"), root.get("id")),
                        cb.isTrue(messRoot.get("testLead")));

                predicates.add(cb.or(discoverable, cb.exists(propertyTest), cb.exists(messTest)));
            } else {
                predicates.add(discoverable);
            }

            if (!queryFilters.types().isEmpty()) {
                predicates.add(root.get("type").in(queryFilters.types()));
            }

            if (StringUtils.hasText(queryFilters.search())) {
                String pattern = containsPattern(queryFilters.search());
                Expression<String> nameLower = cb.lower(root.get("name"));
                Expression<String> addressLower =
                        cb.lower(cb.coalesce(root.get("address"), cb.literal("")));
                predicates.add(cb.or(cb.like(nameLower, pattern), cb.like(addressLower, pattern)));
            }

            if (StringUtils.hasText(queryFilters.location())) {
                predicates.add(addressContains(root, query, cb, queryFilters.location()));
            }

            if (query != null && (queryFilters.minRent() != null || queryFilters.maxRent() != null)) {
                predicates.add(rentInRange(root, query, cb, queryFilters.minRent(), queryFilters.maxRent()));
            }

            if (query != null) {
                for (String amenityCode : queryFilters.amenityCodes()) {
                    if (StringUtils.hasText(amenityCode)) {
                        predicates.add(hasAmenity(root, query, cb, amenityCode.trim()));
                    }
                }
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static Predicate addressContains(
            Root<SpaceEntity> root,
            jakarta.persistence.criteria.CriteriaQuery<?> query,
            jakarta.persistence.criteria.CriteriaBuilder cb,
            String location) {
        List<String> patterns = LocationFilterTokens.needles(location).stream()
                .map(SpaceDiscoverSpecs::containsPattern)
                .toList();
        if (patterns.isEmpty()) {
            patterns = List.of(containsPattern(location));
        }
        Expression<String> spaceAddressLower =
                cb.lower(cb.coalesce(root.get("address"), cb.literal("")));
        Predicate spaceAddressMatch = likeAny(cb, spaceAddressLower, patterns);
        if (query == null) {
            return spaceAddressMatch;
        }

        Subquery<Integer> propertyAddress = query.subquery(Integer.class);
        Root<PropertyRegistrationEntity> propertyRoot =
                propertyAddress.from(PropertyRegistrationEntity.class);
        propertyAddress.select(cb.literal(1));
        Expression<String> propertyAddressLower =
                cb.lower(cb.coalesce(propertyRoot.get("addressLine"), cb.literal("")));
        propertyAddress.where(
                cb.equal(propertyRoot.get("convertedSpaceId"), root.get("id")),
                likeAny(cb, propertyAddressLower, patterns));

        Subquery<Integer> messAddress = query.subquery(Integer.class);
        Root<MessRegistrationEntity> messRoot = messAddress.from(MessRegistrationEntity.class);
        messAddress.select(cb.literal(1));
        Expression<String> messAddressLower =
                cb.lower(cb.coalesce(messRoot.get("addressLine"), cb.literal("")));
        messAddress.where(
                cb.equal(messRoot.get("convertedSpaceId"), root.get("id")),
                likeAny(cb, messAddressLower, patterns));

        return cb.or(spaceAddressMatch, cb.exists(propertyAddress), cb.exists(messAddress));
    }

    private static Predicate rentInRange(
            Root<SpaceEntity> root,
            jakarta.persistence.criteria.CriteriaQuery<?> query,
            jakarta.persistence.criteria.CriteriaBuilder cb,
            BigDecimal minRent,
            BigDecimal maxRent) {
        Subquery<Integer> priced = query.subquery(Integer.class);
        Root<PropertyRegistrationEntity> propertyRoot = priced.from(PropertyRegistrationEntity.class);
        priced.select(cb.literal(1));
        List<Predicate> pricePredicates = new ArrayList<>();
        pricePredicates.add(cb.equal(propertyRoot.get("convertedSpaceId"), root.get("id")));
        Expression<BigDecimal> startingPrice = propertyRoot.get("startingPrice");
        if (minRent != null) {
            pricePredicates.add(cb.greaterThanOrEqualTo(startingPrice, minRent));
        }
        if (maxRent != null) {
            pricePredicates.add(cb.lessThanOrEqualTo(startingPrice, maxRent));
        }
        priced.where(pricePredicates.toArray(Predicate[]::new));
        return cb.exists(priced);
    }

    private static Predicate hasAmenity(
            Root<SpaceEntity> root,
            jakarta.persistence.criteria.CriteriaQuery<?> query,
            jakarta.persistence.criteria.CriteriaBuilder cb,
            String amenityCode) {
        Subquery<Integer> assigned = query.subquery(Integer.class);
        Root<SpaceAmenityEntity> amenityRoot = assigned.from(SpaceAmenityEntity.class);
        assigned.select(cb.literal(1));
        assigned.where(
                cb.equal(amenityRoot.get("space").get("id"), root.get("id")),
                cb.equal(cb.upper(amenityRoot.get("amenityCode")), amenityCode.toUpperCase(Locale.ROOT)));
        if ("FOOD_INCLUDED".equalsIgnoreCase(amenityCode)) {
            return cb.or(cb.isTrue(root.get("foodIncludedInRent")), cb.exists(assigned));
        }
        return cb.exists(assigned);
    }

    private static Predicate likeAny(
            jakarta.persistence.criteria.CriteriaBuilder cb,
            Expression<String> field,
            List<String> patterns) {
        List<Predicate> likes = new ArrayList<>();
        for (String pattern : patterns) {
            likes.add(cb.like(field, pattern));
        }
        return cb.or(likes.toArray(Predicate[]::new));
    }

    static String containsPattern(String value) {
        return "%" + value.trim().toLowerCase().replaceAll("\\s+", " ") + "%";
    }
}
