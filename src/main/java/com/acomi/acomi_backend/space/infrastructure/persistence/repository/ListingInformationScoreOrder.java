package com.acomi.acomi_backend.space.infrastructure.persistence.repository;

import com.acomi.acomi_backend.mess.infrastructure.persistence.entity.MessRegistrationEntity;
import com.acomi.acomi_backend.property.infrastructure.persistence.entity.PropertyRegistrationEntity;
import com.acomi.acomi_backend.registration.application.AdminLeadDefaults;
import com.acomi.acomi_backend.space.application.support.ListingInformationCompleteness;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceAmenityEntity;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

/**
 * SQL ORDER BY key for {@link ListingInformationCompleteness}.
 * Computed per matching row before LIMIT. Does not select contact values.
 */
final class ListingInformationScoreOrder {

    private ListingInformationScoreOrder() {}

    static Expression<? extends Number> score(
            Root<SpaceEntity> space, CriteriaQuery<?> query, CriteriaBuilder cb) {
        Expression<Integer> mobile = points(cb, hasMobile(space, query, cb), ListingInformationCompleteness.MOBILE_CONTACT_SCORE);
        Expression<Integer> address = points(cb, hasAddress(space, query, cb), ListingInformationCompleteness.ADDRESS_SCORE);
        Expression<Integer> map = points(cb, hasMap(space, query, cb), ListingInformationCompleteness.MAP_URL_SCORE);
        Expression<Integer> amenities =
                points(cb, hasAmenity(space, query, cb), ListingInformationCompleteness.AMENITIES_SCORE);
        return cb.sum(cb.sum(mobile, address), cb.sum(map, amenities));
    }

    private static Expression<Integer> points(CriteriaBuilder cb, Predicate when, int weight) {
        return cb.<Integer>selectCase().when(when, weight).otherwise(0);
    }

    private static Predicate hasMobile(Root<SpaceEntity> space, CriteriaQuery<?> query, CriteriaBuilder cb) {
        return cb.or(
                usableMobile(cb, space.get("contactNumber")),
                registrationMatches(
                        query,
                        cb,
                        space,
                        PropertyRegistrationEntity.class,
                        (reg) -> cb.or(
                                usableMobile(cb, reg.get("mobileNumber")),
                                usableMobile(cb, reg.get("alternateMobileNumber")),
                                usableMobile(cb, reg.get("additionalMobileNumber")))),
                registrationMatches(
                        query,
                        cb,
                        space,
                        MessRegistrationEntity.class,
                        (reg) -> cb.or(
                                usableMobile(cb, reg.get("mobileNumber")),
                                usableMobile(cb, reg.get("alternateMobileNumber")),
                                usableMobile(cb, reg.get("additionalMobileNumber")))));
    }

    private static Predicate hasAddress(Root<SpaceEntity> space, CriteriaQuery<?> query, CriteriaBuilder cb) {
        return cb.or(
                usableAddress(cb, space.get("address")),
                registrationMatches(
                        query, cb, space, PropertyRegistrationEntity.class, reg -> usableAddress(cb, reg.get("addressLine"))),
                registrationMatches(
                        query, cb, space, MessRegistrationEntity.class, reg -> usableAddress(cb, reg.get("addressLine"))));
    }

    private static Predicate hasMap(Root<SpaceEntity> space, CriteriaQuery<?> query, CriteriaBuilder cb) {
        return cb.or(
                registrationMatches(
                        query, cb, space, PropertyRegistrationEntity.class, reg -> validMapUrl(cb, reg.get("mapUrl"))),
                registrationMatches(
                        query, cb, space, MessRegistrationEntity.class, reg -> validMapUrl(cb, reg.get("mapUrl"))));
    }

    private static Predicate hasAmenity(Root<SpaceEntity> space, CriteriaQuery<?> query, CriteriaBuilder cb) {
        Subquery<Integer> assigned = query.subquery(Integer.class);
        Root<SpaceAmenityEntity> amenity = assigned.from(SpaceAmenityEntity.class);
        Expression<String> code = cb.trim(cb.coalesce(amenity.get("amenityCode"), cb.literal("")));
        Expression<String> lower = cb.lower(code);
        assigned.select(cb.literal(1));
        assigned.where(
                cb.equal(amenity.get("space").get("id"), space.get("id")),
                cb.greaterThan(cb.length(code), 0),
                cb.not(lower.in("-", "–", "—", "na", "n/a")),
                cb.notEqual(cb.upper(code), "FOOD_INCLUDED"));
        return cb.exists(assigned);
    }

    private static Predicate registrationMatches(
            CriteriaQuery<?> query,
            CriteriaBuilder cb,
            Root<SpaceEntity> space,
            Class<?> registrationType,
            java.util.function.Function<Root<?>, Predicate> match) {
        Subquery<Integer> rows = query.subquery(Integer.class);
        Root<?> registration = rows.from(registrationType);
        rows.select(cb.literal(1));
        rows.where(cb.equal(registration.get("convertedSpaceId"), space.get("id")), match.apply(registration));
        return cb.exists(rows);
    }

    /**
     * Mirrors {@link com.acomi.acomi_backend.common.util.MobileNumberNormalizer}:
     * digits only, drop a leading 0 from an 11-digit value, take the last 10 digits
     * when the value is longer than 10 and starts with 91, then require {@code [6-9][0-9]{9}}.
     * Emails and the admin placeholder mobile do not count. The number is not selected.
     */
    private static Predicate usableMobile(CriteriaBuilder cb, Expression<String> raw) {
        Expression<String> trimmed = cb.trim(cb.coalesce(raw, cb.literal("")));
        Expression<String> digits = cb.function(
                "regexp_replace", String.class, trimmed, cb.literal("[^0-9]"), cb.literal(""), cb.literal("g"));
        Expression<Integer> digitLength = cb.length(digits);
        Expression<String> national = cb.<String>selectCase()
                .when(
                        cb.and(cb.equal(digitLength, 11), cb.equal(cb.substring(digits, 1, 1), "0")),
                        cb.substring(digits, 2, 10))
                .when(
                        cb.and(cb.greaterThan(digitLength, 10), cb.equal(cb.substring(digits, 1, 2), "91")),
                        cb.function(
                                "substring",
                                String.class,
                                digits,
                                cb.diff(digitLength, 9),
                                cb.literal(10)))
                .otherwise(digits);
        return cb.and(
                cb.greaterThan(cb.length(trimmed), 0),
                cb.equal(cb.locate(trimmed, "@"), 0),
                cb.not(cb.lower(trimmed).in("-", "–", "—", "na", "n/a")),
                cb.equal(cb.length(national), 10),
                cb.substring(national, 1, 1).in("6", "7", "8", "9"),
                cb.notEqual(national, AdminLeadDefaults.PLACEHOLDER_MOBILE));
    }

    private static Predicate usableAddress(CriteriaBuilder cb, Expression<String> raw) {
        Expression<String> trimmed = cb.trim(cb.coalesce(raw, cb.literal("")));
        return cb.and(
                cb.greaterThan(cb.length(trimmed), 0),
                cb.not(cb.lower(trimmed).in("-", "–", "—", "na", "n/a")));
    }

    /** http(s) URL only. Latitude and longitude are not read. */
    private static Predicate validMapUrl(CriteriaBuilder cb, Expression<String> raw) {
        Expression<String> lower = cb.lower(cb.trim(cb.coalesce(raw, cb.literal(""))));
        return cb.or(cb.like(lower, "https://%"), cb.like(lower, "http://%"));
    }
}
