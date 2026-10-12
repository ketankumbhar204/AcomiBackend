package com.acomi.acomi_backend.space.infrastructure.persistence.repository;

import com.acomi.acomi_backend.space.application.support.ListingInformationCompleteness;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

/**
 * Applies the discover specification, scores every match with
 * {@link ListingInformationCompleteness}, then pages.
 * Ranking stays before the page slice. The score is computed in memory from one
 * batch of registration and amenity rows, instead of an ORDER BY of correlated
 * mobile/address/map/amenity subqueries.
 */
@Component
@RequiredArgsConstructor
public class SpaceDiscoverRankedPageQuery {

    private static final int ID_BATCH = 500;

    private final EntityManager entityManager;

    public Page<SpaceEntity> find(Specification<SpaceEntity> specification, Pageable pageable) {
        List<SpaceEntity> matches = loadMatches(specification);
        long total = matches.size();
        if (matches.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        List<UUID> ids = matches.stream().map(SpaceEntity::getId).toList();
        ScoreInputs inputs = loadScoreInputs(ids);
        Map<UUID, Integer> scores = new HashMap<>();
        for (SpaceEntity space : matches) {
            scores.put(space.getId(), score(space, inputs));
        }
        matches.sort(Comparator.comparingInt((SpaceEntity space) -> scores.getOrDefault(space.getId(), 0))
                .reversed()
                .thenComparing(SpaceEntity::getCreatedAt, Comparator.nullsFirst(Comparator.reverseOrder()))
                .thenComparing(SpaceEntity::getId, Comparator.reverseOrder()));

        int from = (int) Math.min(pageable.getOffset(), total);
        int to = (int) Math.min(from + pageable.getPageSize(), total);
        List<SpaceEntity> content = from >= to ? List.of() : List.copyOf(matches.subList(from, to));
        return new PageImpl<>(content, pageable, total);
    }

    private List<SpaceEntity> loadMatches(Specification<SpaceEntity> specification) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<SpaceEntity> criteria = cb.createQuery(SpaceEntity.class);
        Root<SpaceEntity> root = criteria.from(SpaceEntity.class);
        Predicate predicate = specification == null ? null : specification.toPredicate(root, criteria, cb);
        if (predicate != null) {
            criteria.where(predicate);
        }
        criteria.select(root);
        return new ArrayList<>(entityManager.createQuery(criteria).getResultList());
    }

    private ScoreInputs loadScoreInputs(List<UUID> ids) {
        ScoreInputs inputs = new ScoreInputs();
        for (List<UUID> batch : batches(ids)) {
            List<Object[]> properties = entityManager
                    .createQuery(
                            "select p.convertedSpaceId, p.mobileNumber, p.alternateMobileNumber, "
                                    + "p.additionalMobileNumber, p.addressLine, p.mapUrl "
                                    + "from PropertyRegistrationEntity p where p.convertedSpaceId in :ids",
                            Object[].class)
                    .setParameter("ids", batch)
                    .getResultList();
            for (Object[] row : properties) {
                UUID spaceId = (UUID) row[0];
                if (spaceId != null) {
                    inputs.properties.computeIfAbsent(spaceId, ignored -> new ArrayList<>()).add(row);
                }
            }

            List<Object[]> messes = entityManager
                    .createQuery(
                            "select m.convertedSpaceId, m.mobileNumber, m.alternateMobileNumber, "
                                    + "m.additionalMobileNumber, m.addressLine, m.mapUrl "
                                    + "from MessRegistrationEntity m where m.convertedSpaceId in :ids",
                            Object[].class)
                    .setParameter("ids", batch)
                    .getResultList();
            for (Object[] row : messes) {
                UUID spaceId = (UUID) row[0];
                if (spaceId != null) {
                    inputs.messes.computeIfAbsent(spaceId, ignored -> new ArrayList<>()).add(row);
                }
            }

            TypedQuery<Object[]> amenities = entityManager.createQuery(
                    "select a.space.id, a.amenityCode from SpaceAmenityEntity a where a.space.id in :ids",
                    Object[].class);
            amenities.setParameter("ids", batch);
            for (Object[] row : amenities.getResultList()) {
                UUID spaceId = (UUID) row[0];
                if (spaceId != null) {
                    inputs.amenities.computeIfAbsent(spaceId, ignored -> new ArrayList<>()).add((String) row[1]);
                }
            }
        }
        return inputs;
    }

    private static int score(SpaceEntity space, ScoreInputs inputs) {
        List<String> mobiles = new ArrayList<>();
        List<String> addresses = new ArrayList<>();
        mobiles.add(space.getContactNumber());
        addresses.add(space.getAddress());
        boolean map = false;
        for (Object[] row : inputs.properties.getOrDefault(space.getId(), List.of())) {
            mobiles.add((String) row[1]);
            mobiles.add((String) row[2]);
            mobiles.add((String) row[3]);
            addresses.add((String) row[4]);
            map = map || storedHttpMap((String) row[5]);
        }
        for (Object[] row : inputs.messes.getOrDefault(space.getId(), List.of())) {
            mobiles.add((String) row[1]);
            mobiles.add((String) row[2]);
            mobiles.add((String) row[3]);
            addresses.add((String) row[4]);
            map = map || storedHttpMap((String) row[5]);
        }
        return ListingInformationCompleteness.score(
                ListingInformationCompleteness.hasUsableMobile(mobiles.toArray(String[]::new)),
                ListingInformationCompleteness.hasUsableAddress(addresses.toArray(String[]::new)),
                map,
                ListingInformationCompleteness.hasUsableAmenity(
                        inputs.amenities.getOrDefault(space.getId(), List.of())));
    }

    /** Same rule as the previous SQL rank: trimmed http(s) prefix, including imperfect URLs. */
    private static boolean storedHttpMap(String raw) {
        if (raw == null) {
            return false;
        }
        String lower = raw.trim().toLowerCase(Locale.ROOT);
        return lower.startsWith("https://") || lower.startsWith("http://");
    }

    private static List<List<UUID>> batches(List<UUID> ids) {
        List<List<UUID>> batches = new ArrayList<>();
        for (int start = 0; start < ids.size(); start += ID_BATCH) {
            batches.add(ids.subList(start, Math.min(start + ID_BATCH, ids.size())));
        }
        return batches;
    }

    private static final class ScoreInputs {
        private final Map<UUID, List<Object[]>> properties = new HashMap<>();
        private final Map<UUID, List<Object[]>> messes = new HashMap<>();
        private final Map<UUID, List<String>> amenities = new HashMap<>();
    }
}
