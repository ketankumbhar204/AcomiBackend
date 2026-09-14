package com.acomi.acomi_backend.space.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.space.domain.model.SpaceType;
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
        when(cb.equal(typePath, SpaceType.PG)).thenReturn(mock(Predicate.class));
        when(cb.and(any(Predicate[].class))).thenReturn(mock(Predicate.class));

        SpaceDiscoverSpecs.discover(null, SpaceType.PG).toPredicate(root, query, cb);

        verify(cb).equal(typePath, SpaceType.PG);
        ArgumentCaptor<Predicate[]> predicatesCaptor = ArgumentCaptor.forClass(Predicate[].class);
        verify(cb).and(predicatesCaptor.capture());
        assertThat(predicatesCaptor.getValue()).hasSize(3);
    }
}
