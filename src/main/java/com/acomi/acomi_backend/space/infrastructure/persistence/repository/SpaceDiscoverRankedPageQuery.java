package com.acomi.acomi_backend.space.infrastructure.persistence.repository;

import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

/**
 * Applies the discover specification, then orders by information completeness
 * (desc), createdAt (desc), id (desc), then applies offset/limit.
 * Ranking is never applied to a page that was already sliced.
 */
@Component
@RequiredArgsConstructor
public class SpaceDiscoverRankedPageQuery {

    private final EntityManager entityManager;

    public Page<SpaceEntity> find(Specification<SpaceEntity> specification, Pageable pageable) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<SpaceEntity> criteria = cb.createQuery(SpaceEntity.class);
        Root<SpaceEntity> root = criteria.from(SpaceEntity.class);
        Predicate predicate = specification == null ? null : specification.toPredicate(root, criteria, cb);
        if (predicate != null) {
            criteria.where(predicate);
        }
        criteria.select(root);
        criteria.orderBy(
                cb.desc(ListingInformationScoreOrder.score(root, criteria, cb)),
                cb.desc(root.get("createdAt")),
                cb.desc(root.get("id")));

        TypedQuery<SpaceEntity> rows = entityManager.createQuery(criteria);
        rows.setFirstResult((int) pageable.getOffset());
        rows.setMaxResults(pageable.getPageSize());
        List<SpaceEntity> content = rows.getResultList();

        CriteriaQuery<Long> countCriteria = cb.createQuery(Long.class);
        Root<SpaceEntity> countRoot = countCriteria.from(SpaceEntity.class);
        Predicate countPredicate =
                specification == null ? null : specification.toPredicate(countRoot, countCriteria, cb);
        if (countPredicate != null) {
            countCriteria.where(countPredicate);
        }
        countCriteria.select(cb.count(countRoot));
        long total = entityManager.createQuery(countCriteria).getSingleResult();
        return new PageImpl<>(content, pageable, total);
    }
}
