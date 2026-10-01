package com.acomi.acomi_backend.space.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.space.application.support.SpaceDiscoverQuery;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.math.BigDecimal;
import java.util.List;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.jpa.domain.Specification;

class SpaceDiscoverSpecsTest {

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void discover_requiresActiveAndDiscoverable() {
        Root<SpaceEntity> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Path isActivePath = mock(Path.class);
        Path discoverablePath = mock(Path.class);
        Predicate activePred = mock(Predicate.class);
        Predicate discoverablePred = mock(Predicate.class);
        Predicate andPred = mock(Predicate.class);

        when(root.get("isActive")).thenReturn(isActivePath);
        when(root.get("discoverable")).thenReturn(discoverablePath);
        when(cb.isTrue(any(Expression.class))).thenReturn(activePred, discoverablePred);
        when(cb.and(any(Predicate[].class))).thenReturn(andPred);

        Specification<SpaceEntity> spec = SpaceDiscoverSpecs.discover(null, null);
        Predicate result = spec.toPredicate(root, query, cb);

        assertThat(result).isSameAs(andPred);
        ArgumentCaptor<Predicate[]> predicatesCaptor = ArgumentCaptor.forClass(Predicate[].class);
        verify(cb).and(predicatesCaptor.capture());
        assertThat(predicatesCaptor.getValue()).hasSize(2);
        verify(cb, org.mockito.Mockito.times(2)).isTrue(any(Expression.class));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void discover_withTypeAddsTypePredicate() {
        Root<SpaceEntity> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Path isActivePath = mock(Path.class);
        Path discoverablePath = mock(Path.class);
        Path typePath = mock(Path.class);

        when(root.get("isActive")).thenReturn(isActivePath);
        when(root.get("discoverable")).thenReturn(discoverablePath);
        when(root.get("type")).thenReturn(typePath);
        when(cb.isTrue(any(Expression.class))).thenReturn(mock(Predicate.class));
        when(typePath.in(List.of(SpaceType.PG))).thenReturn(mock(Predicate.class));
        when(cb.and(any(Predicate[].class))).thenReturn(mock(Predicate.class));

        SpaceDiscoverSpecs.discover(null, SpaceType.PG).toPredicate(root, query, cb);

        verify(typePath).in(List.of(SpaceType.PG));
        ArgumentCaptor<Predicate[]> predicatesCaptor = ArgumentCaptor.forClass(Predicate[].class);
        verify(cb).and(predicatesCaptor.capture());
        assertThat(predicatesCaptor.getValue()).hasSize(3);
    }

    @Test
    void containsPattern_isCaseInsensitiveAndTrimsWhitespace() {
        assertThat(SpaceDiscoverSpecs.containsPattern("  Aundh  ")).isEqualTo("%aundh%");
        assertThat(SpaceDiscoverSpecs.containsPattern("AUNDH")).isEqualTo("%aundh%");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void discover_withLocationMatchesAddressNotName() {
        Root<SpaceEntity> root = mock(Root.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Path addressPath = mock(Path.class);
        Expression coalesced = mock(Expression.class);
        Expression lowered = mock(Expression.class);
        Predicate likePred = mock(Predicate.class);

        when(root.get("isActive")).thenReturn(mock(Path.class));
        when(root.get("discoverable")).thenReturn(mock(Path.class));
        when(root.get("address")).thenReturn(addressPath);
        when(cb.isTrue(any(Expression.class))).thenReturn(mock(Predicate.class));
        when(cb.literal("")).thenReturn(mock(Expression.class));
        when(cb.coalesce(any(), any())).thenReturn(coalesced);
        when(cb.lower(any(Expression.class))).thenReturn(lowered);
        when(cb.like(any(Expression.class), org.mockito.ArgumentMatchers.eq("%aundh%")))
                .thenReturn(likePred);
        when(cb.and(any(Predicate[].class))).thenReturn(mock(Predicate.class));

        SpaceDiscoverSpecs.discover(null, null, false, "Aundh").toPredicate(root, null, cb);

        verify(cb).like(lowered, "%aundh%");
        verify(root, org.mockito.Mockito.never()).get("name");
        ArgumentCaptor<Predicate[]> predicatesCaptor = ArgumentCaptor.forClass(Predicate[].class);
        verify(cb).and(predicatesCaptor.capture());
        assertThat(predicatesCaptor.getValue()).hasSize(3);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void discover_withParentheticalLocationLikesExtractedTermsNotPune() {
        Root<SpaceEntity> root = mock(Root.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Expression coalesced = mock(Expression.class);
        Expression lowered = mock(Expression.class);

        when(root.get("isActive")).thenReturn(mock(Path.class));
        when(root.get("discoverable")).thenReturn(mock(Path.class));
        when(root.get("address")).thenReturn(mock(Path.class));
        when(cb.isTrue(any(Expression.class))).thenReturn(mock(Predicate.class));
        when(cb.literal("")).thenReturn(mock(Expression.class));
        when(cb.coalesce(any(), any())).thenReturn(coalesced);
        when(cb.lower(any(Expression.class))).thenReturn(lowered);
        when(cb.like(any(Expression.class), any(String.class))).thenReturn(mock(Predicate.class));
        when(cb.or(any(Predicate[].class))).thenReturn(mock(Predicate.class));
        when(cb.and(any(Predicate[].class))).thenReturn(mock(Predicate.class));

        SpaceDiscoverSpecs.discover(null, null, false, "Infotech Park (Hinjawadi)")
                .toPredicate(root, null, cb);

        verify(cb).like(lowered, "%infotech park%");
        verify(cb).like(lowered, "%hinjawadi%");
        verify(cb).like(lowered, "%hinjewadi%");
        verify(cb, org.mockito.Mockito.never()).like(lowered, "%pune%");
        verify(root, org.mockito.Mockito.never()).get("name");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void discover_withTypesUsesInPredicateNotName() {
        Root<SpaceEntity> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Path typePath = mock(Path.class);
        when(root.get("isActive")).thenReturn(mock(Path.class));
        when(root.get("discoverable")).thenReturn(mock(Path.class));
        when(root.get("type")).thenReturn(typePath);
        when(cb.isTrue(any(Expression.class))).thenReturn(mock(Predicate.class));
        when(typePath.in(List.of(SpaceType.PG))).thenReturn(mock(Predicate.class));
        when(cb.and(any(Predicate[].class))).thenReturn(mock(Predicate.class));

        SpaceDiscoverSpecs.discover(
                        new SpaceDiscoverQuery(null, null, List.of(SpaceType.PG), null, null, List.of()),
                        false)
                .toPredicate(root, query, cb);

        verify(typePath).in(List.of(SpaceType.PG));
        verify(root, org.mockito.Mockito.never()).get("name");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void discover_withRentAddsPropertyPriceExists() {
        Root<SpaceEntity> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        jakarta.persistence.criteria.Subquery subquery = mock(jakarta.persistence.criteria.Subquery.class);
        when(root.get("isActive")).thenReturn(mock(Path.class));
        when(root.get("discoverable")).thenReturn(mock(Path.class));
        when(cb.isTrue(any(Expression.class))).thenReturn(mock(Predicate.class));
        when(query.subquery(Integer.class)).thenReturn(subquery);
        when(subquery.from(any(Class.class))).thenReturn(mock(Root.class));
        when(subquery.select(any())).thenReturn(subquery);
        when(cb.literal(1)).thenReturn(mock(Expression.class));
        when(cb.equal(any(), any())).thenReturn(mock(Predicate.class));
        when(cb.greaterThanOrEqualTo(any(), any(BigDecimal.class))).thenReturn(mock(Predicate.class));
        when(cb.lessThanOrEqualTo(any(), any(BigDecimal.class))).thenReturn(mock(Predicate.class));
        when(cb.exists(subquery)).thenReturn(mock(Predicate.class));
        when(cb.and(any(Predicate[].class))).thenReturn(mock(Predicate.class));

        SpaceDiscoverSpecs.discover(
                        new SpaceDiscoverQuery(
                                null, null, List.of(), new BigDecimal("2000"), new BigDecimal("15000"), List.of()),
                        false)
                .toPredicate(root, query, cb);

        verify(cb).exists(subquery);
        verify(cb).greaterThanOrEqualTo(any(), org.mockito.ArgumentMatchers.eq(new BigDecimal("2000")));
        verify(cb).lessThanOrEqualTo(any(), org.mockito.ArgumentMatchers.eq(new BigDecimal("15000")));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void discover_withAmenityAddsExists() {
        Root<SpaceEntity> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        jakarta.persistence.criteria.Subquery subquery = mock(jakarta.persistence.criteria.Subquery.class);
        Root amenityRoot = mock(Root.class);
        Path spacePath = mock(Path.class);
        when(root.get("isActive")).thenReturn(mock(Path.class));
        when(root.get("discoverable")).thenReturn(mock(Path.class));
        when(cb.isTrue(any(Expression.class))).thenReturn(mock(Predicate.class));
        when(query.subquery(Integer.class)).thenReturn(subquery);
        when(subquery.from(any(Class.class))).thenReturn(amenityRoot);
        when(amenityRoot.get("space")).thenReturn(spacePath);
        when(spacePath.get("id")).thenReturn(mock(Path.class));
        when(amenityRoot.get("amenityCode")).thenReturn(mock(Path.class));
        when(subquery.select(any())).thenReturn(subquery);
        when(cb.literal(1)).thenReturn(mock(Expression.class));
        when(cb.equal(any(), any())).thenReturn(mock(Predicate.class));
        when(cb.upper(any())).thenReturn(mock(Expression.class));
        when(cb.exists(subquery)).thenReturn(mock(Predicate.class));
        when(cb.and(any(Predicate[].class))).thenReturn(mock(Predicate.class));

        SpaceDiscoverSpecs.discover(
                        new SpaceDiscoverQuery(null, null, List.of(), null, null, List.of("WIFI")),
                        false)
                .toPredicate(root, query, cb);

        verify(cb).exists(subquery);
        verify(root, org.mockito.Mockito.never()).get("name");
    }

    @ParameterizedTest
    @EnumSource(value = SpaceType.class, names = {"PG", "HOSTEL", "RENTAL", "CO_LIVING", "MESS"})
    @SuppressWarnings({"unchecked", "rawtypes"})
    void typeFilterIsInTheWhereClauseBeforeAnyOrdering(SpaceType type) {
        Root<SpaceEntity> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Path typePath = mock(Path.class);
        when(root.get("isActive")).thenReturn(mock(Path.class));
        when(root.get("discoverable")).thenReturn(mock(Path.class));
        when(root.get("type")).thenReturn(typePath);
        when(cb.isTrue(any(Expression.class))).thenReturn(mock(Predicate.class));
        when(typePath.in(List.of(type))).thenReturn(mock(Predicate.class));
        when(cb.and(any(Predicate[].class))).thenReturn(mock(Predicate.class));

        SpaceDiscoverSpecs.discover(
                        new SpaceDiscoverQuery(null, null, List.of(type), null, null, List.of()), false)
                .toPredicate(root, query, cb);

        verify(typePath).in(List.of(type));
        verify(query, org.mockito.Mockito.never()).orderBy(any(jakarta.persistence.criteria.Order[].class));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void searchLocationRentAndAmenitiesAreWherePredicates() {
        Root<SpaceEntity> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Path typePath = mock(Path.class);
        jakarta.persistence.criteria.Subquery subquery = mock(jakarta.persistence.criteria.Subquery.class);
        Root joined = mock(Root.class);
        Path spacePath = mock(Path.class);
        Expression coalesced = mock(Expression.class);
        Expression lowered = mock(Expression.class);

        when(root.get("isActive")).thenReturn(mock(Path.class));
        when(root.get("discoverable")).thenReturn(mock(Path.class));
        when(root.get("type")).thenReturn(typePath);
        when(root.get("name")).thenReturn(mock(Path.class));
        when(root.get("address")).thenReturn(mock(Path.class));
        when(cb.isTrue(any(Expression.class))).thenReturn(mock(Predicate.class));
        when(typePath.in(List.of(SpaceType.PG))).thenReturn(mock(Predicate.class));
        when(cb.literal("")).thenReturn(mock(Expression.class));
        when(cb.coalesce(any(), any())).thenReturn(coalesced);
        when(cb.lower(any(Expression.class))).thenReturn(lowered);
        when(cb.like(any(Expression.class), any(String.class))).thenReturn(mock(Predicate.class));
        when(cb.or(any(Predicate[].class))).thenReturn(mock(Predicate.class));
        when(query.subquery(Integer.class)).thenReturn(subquery);
        when(subquery.from(any(Class.class))).thenReturn(joined);
        when(subquery.select(any())).thenReturn(subquery);
        when(joined.get("convertedSpaceId")).thenReturn(mock(Path.class));
        when(joined.get("startingPrice")).thenReturn(mock(Path.class));
        when(joined.get("space")).thenReturn(spacePath);
        when(spacePath.get("id")).thenReturn(mock(Path.class));
        when(joined.get("amenityCode")).thenReturn(mock(Path.class));
        when(cb.literal(1)).thenReturn(mock(Expression.class));
        when(cb.equal(any(), any())).thenReturn(mock(Predicate.class));
        when(cb.upper(any())).thenReturn(mock(Expression.class));
        when(cb.greaterThanOrEqualTo(any(), any(BigDecimal.class))).thenReturn(mock(Predicate.class));
        when(cb.lessThanOrEqualTo(any(), any(BigDecimal.class))).thenReturn(mock(Predicate.class));
        when(cb.exists(any())).thenReturn(mock(Predicate.class));
        when(cb.and(any(Predicate[].class))).thenReturn(mock(Predicate.class));

        SpaceDiscoverSpecs.discover(
                        new SpaceDiscoverQuery(
                                "sunrise",
                                "Hinjewadi",
                                List.of(SpaceType.PG),
                                new BigDecimal("8000"),
                                new BigDecimal("15000"),
                                List.of("WIFI")),
                        false)
                .toPredicate(root, query, cb);

        verify(typePath).in(List.of(SpaceType.PG));
        verify(cb, org.mockito.Mockito.atLeastOnce()).like(any(Expression.class), org.mockito.ArgumentMatchers.contains("hinjewadi"));
        verify(cb, org.mockito.Mockito.atLeastOnce()).like(any(Expression.class), org.mockito.ArgumentMatchers.contains("sunrise"));
        verify(cb, org.mockito.Mockito.atLeastOnce()).exists(any());
        verify(query, org.mockito.Mockito.never()).orderBy(any(jakarta.persistence.criteria.Order[].class));
    }
}
