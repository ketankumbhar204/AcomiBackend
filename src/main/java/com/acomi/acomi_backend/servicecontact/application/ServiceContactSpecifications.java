package com.acomi.acomi_backend.servicecontact.application;

import com.acomi.acomi_backend.servicecontact.domain.model.ServiceContactCategory;
import com.acomi.acomi_backend.servicecontact.infrastructure.persistence.entity.ServiceContactEntity;
import com.acomi.acomi_backend.servicecontact.infrastructure.persistence.entity.ServiceContactSpaceEntity;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

public final class ServiceContactSpecifications {

    private ServiceContactSpecifications() {}

    public static Specification<ServiceContactEntity> directory(
            CallerScope scope,
            boolean includeArchived,
            UUID spaceId,
            ServiceContactCategory category,
            String likePattern,
            String phoneDigits) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (includeArchived && scope.ownsAccountDirectory()) {
                predicates.add(cb.or(
                        cb.isTrue(root.get("active")),
                        cb.equal(root.get("ownerUserId"), scope.callerId())));
            } else {
                predicates.add(cb.isTrue(root.get("active")));
            }

            List<Predicate> visibility = new ArrayList<>();
            if (scope.ownsAccountDirectory()) {
                visibility.add(cb.equal(root.get("ownerUserId"), scope.callerId()));
            }
            if (!scope.visibleSpaceIds().isEmpty()) {
                visibility.add(linkedToAny(root, query, cb, scope.visibleSpaceIds()));
            }
            if (visibility.isEmpty()) {
                predicates.add(cb.disjunction());
            } else {
                predicates.add(cb.or(visibility.toArray(Predicate[]::new)));
            }

            if (spaceId != null) {
                predicates.add(linkedToAny(root, query, cb, Set.of(spaceId)));
            }
            if (category != null) {
                predicates.add(cb.equal(root.get("category"), category));
            }
            if (likePattern != null || (phoneDigits != null && phoneDigits.length() >= 4)) {
                List<Predicate> search = new ArrayList<>();
                if (likePattern != null) {
                    search.add(cb.like(cb.lower(root.get("businessName")), likePattern, '\\'));
                    search.add(cb.like(
                            cb.lower(cb.coalesce(root.get("contactPersonName"), "")), likePattern, '\\'));
                    search.add(cb.like(cb.lower(cb.coalesce(root.get("notes"), "")), likePattern, '\\'));
                    search.add(cb.like(
                            cb.lower(cb.coalesce(root.get("customCategoryName"), "")), likePattern, '\\'));
                }
                if (phoneDigits != null && phoneDigits.length() >= 4) {
                    String phoneLike = "%" + phoneDigits + "%";
                    search.add(cb.like(cb.coalesce(root.get("primaryPhone"), ""), phoneLike));
                    search.add(cb.like(cb.coalesce(root.get("alternatePhone"), ""), phoneLike));
                    search.add(cb.like(cb.coalesce(root.get("whatsappPhone"), ""), phoneLike));
                }
                predicates.add(cb.or(search.toArray(Predicate[]::new)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static Predicate linkedToAny(
            Root<ServiceContactEntity> root,
            jakarta.persistence.criteria.CriteriaQuery<?> query,
            jakarta.persistence.criteria.CriteriaBuilder cb,
            Set<UUID> spaceIds) {
        Subquery<Integer> subquery = query.subquery(Integer.class);
        Root<ServiceContactSpaceEntity> link = subquery.from(ServiceContactSpaceEntity.class);
        subquery.select(cb.literal(1));
        subquery.where(
                cb.equal(link.get("contact"), root),
                link.get("space").get("id").in(spaceIds));
        return cb.exists(subquery);
    }
}
