package com.acomi.acomi_backend.servicecontact.application;

import com.acomi.acomi_backend.servicecontact.domain.model.ServiceContactCategory;
import com.acomi.acomi_backend.servicecontact.infrastructure.persistence.entity.ServiceContactEntity;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

public final class ServiceContactSpecifications {

    private ServiceContactSpecifications() {}

    public static Specification<ServiceContactEntity> directory(
            CallerScope scope,
            boolean includeArchived,
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

            if (scope.directoryOwnerIds().isEmpty()) {
                predicates.add(cb.disjunction());
            } else {
                predicates.add(root.get("ownerUserId").in(scope.directoryOwnerIds()));
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
}
