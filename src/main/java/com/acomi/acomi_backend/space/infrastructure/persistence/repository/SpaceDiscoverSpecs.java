package com.acomi.acomi_backend.space.infrastructure.persistence.repository;

import com.acomi.acomi_backend.mess.infrastructure.persistence.entity.MessRegistrationEntity;
import com.acomi.acomi_backend.property.infrastructure.persistence.entity.PropertyRegistrationEntity;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

/** Criteria for authenticated space discovery (active spaces). */
public final class SpaceDiscoverSpecs {

    private SpaceDiscoverSpecs() {}

    public static Specification<SpaceEntity> discover(String search, SpaceType type) {
        return discover(search, type, false);
    }

    /**
     * @param includeTestSpaces when true, also include active spaces converted from test leads
     *     even if not discoverable (local/dev only).
     */
    public static Specification<SpaceEntity> discover(
            String search, SpaceType type, boolean includeTestSpaces) {
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

            if (type != null) {
                predicates.add(cb.equal(root.get("type"), type));
            }

            if (StringUtils.hasText(search)) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                Expression<String> nameLower = cb.lower(root.get("name"));
                Expression<String> addressLower =
                        cb.lower(cb.coalesce(root.get("address"), cb.literal("")));
                predicates.add(cb.or(cb.like(nameLower, pattern), cb.like(addressLower, pattern)));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
