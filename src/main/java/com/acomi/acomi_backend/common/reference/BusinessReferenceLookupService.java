package com.acomi.acomi_backend.common.reference;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.common.security.SecurityUtils;
import com.acomi.acomi_backend.config.security.UserPrincipal;
import com.acomi.acomi_backend.member.domain.model.MembershipStatus;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.SpaceMembershipRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BusinessReferenceLookupService {

    private static final String NOT_FOUND = "Reference not found";

    private final SpaceMembershipRepository membershipRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public BusinessReferenceLookupService(SpaceMembershipRepository membershipRepository) {
        this.membershipRepository = membershipRepository;
    }

    @Transactional(readOnly = true)
    public BusinessReferenceLookupResponse lookup(String raw) {
        UserPrincipal principal = SecurityUtils.getCurrentPrincipalOrNull();
        if (principal == null) {
            throw new BusinessException(NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        String code = raw == null ? "" : raw.trim();
        if (code.isEmpty()) {
            throw new BusinessException(NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        String upper = code.toUpperCase(Locale.ROOT);
        if (upper.startsWith("PAY-")) {
            return authorize(principal, "PAY", upper, paymentSql(), false);
        }
        if (upper.startsWith("PR-")) {
            return authorize(principal, "PR", upper, registrationSql("property_registrations"), true);
        }
        if (upper.startsWith("MR-")) {
            return authorize(principal, "MR", upper, registrationSql("mess_registrations"), true);
        }
        String canonical = BusinessReferenceCodes.normalize(upper);
        BusinessReferencePrefix prefix = BusinessReferenceCodes.prefixOf(canonical)
                .orElseThrow(() -> new BusinessException(NOT_FOUND, HttpStatus.NOT_FOUND));
        return authorize(principal, prefix.name(), canonical, sqlFor(prefix), prefix == BusinessReferencePrefix.ICP);
    }

    private BusinessReferenceLookupResponse authorize(
            UserPrincipal principal, String entityType, String code, String sql, boolean adminOnly) {
        List<?> rows = entityManager.createNativeQuery(sql).setParameter(1, code).getResultList();
        if (rows.isEmpty()) {
            throw new BusinessException(NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        Object[] row = rows.get(0) instanceof Object[] values ? values : new Object[] {rows.get(0), null, null};
        UUID entityId = toUuid(row[0]);
        UUID spaceId = row.length > 1 ? toUuid(row[1]) : null;
        UUID userId = row.length > 2 ? toUuid(row[2]) : null;
        boolean admin = principal.isAdmin();
        boolean allowed = admin
                || (!adminOnly
                        && (principal.getId().equals(userId)
                                || (spaceId != null && canAccessSpace(principal.getId(), spaceId))));
        if (!allowed || entityId == null) {
            throw new BusinessException(NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        return BusinessReferenceLookupResponse.builder()
                .referenceCode(code)
                .entityType(entityType)
                .entityId(entityId)
                .spaceId(spaceId)
                .build();
    }

    private boolean canAccessSpace(UUID userId, UUID spaceId) {
        if (membershipRepository.existsByUserIdAndSpaceIdAndStatus(userId, spaceId, MembershipStatus.ACTIVE)) {
            return true;
        }
        Number owned = (Number) entityManager
                .createNativeQuery("SELECT COUNT(*) FROM spaces WHERE id = ?1 AND owner_id = ?2")
                .setParameter(1, spaceId)
                .setParameter(2, userId)
                .getSingleResult();
        return owned != null && owned.longValue() > 0;
    }

    private static UUID toUuid(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof UUID uuid) {
            return uuid;
        }
        return UUID.fromString(value.toString());
    }

    private static String paymentSql() {
        return """
                SELECT id, space_id, NULL::uuid
                FROM space_payments
                WHERE payment_reference = ?1
                """;
    }

    private static String registrationSql(String table) {
        return "SELECT id, converted_space_id, NULL::uuid FROM " + table + " WHERE reference = ?1";
    }

    private static String sqlFor(BusinessReferencePrefix prefix) {
        return switch (prefix) {
            case FLR -> """
                    SELECT f.id, b.space_id, NULL::uuid
                    FROM floors f
                    JOIN buildings b ON b.id = f.building_id
                    WHERE f.reference_code = ?1
                    """;
            case UNT -> """
                    SELECT u.id, b.space_id, NULL::uuid
                    FROM units u
                    JOIN buildings b ON b.id = u.building_id
                    WHERE u.reference_code = ?1
                    """;
            case ROM -> """
                    SELECT r.id, COALESCE(fb.space_id, ub.space_id), NULL::uuid
                    FROM rooms r
                    LEFT JOIN floors f ON f.id = r.floor_id
                    LEFT JOIN buildings fb ON fb.id = f.building_id
                    LEFT JOIN units u ON u.id = r.unit_id
                    LEFT JOIN buildings ub ON ub.id = u.building_id
                    WHERE r.reference_code = ?1
                    """;
            case BED -> """
                    SELECT b.id, COALESCE(fb.space_id, ub.space_id), NULL::uuid
                    FROM beds b
                    JOIN rooms r ON r.id = b.room_id
                    LEFT JOIN floors f ON f.id = r.floor_id
                    LEFT JOIN buildings fb ON fb.id = f.building_id
                    LEFT JOIN units u ON u.id = r.unit_id
                    LEFT JOIN buildings ub ON ub.id = u.building_id
                    WHERE b.reference_code = ?1
                    """;
            case DOC -> """
                    SELECT d.id, m.space_id, m.user_id
                    FROM member_documents d
                    JOIN members m ON m.id = d.member_id
                    WHERE d.reference_code = ?1
                    """;
            case MLB -> """
                    SELECT l.id, m.space_id, m.user_id
                    FROM member_meal_balance_ledger l
                    JOIN member_meal_balances b ON b.id = l.balance_id
                    JOIN members m ON m.id = b.member_id
                    WHERE l.reference_code = ?1
                    """;
            case USR -> "SELECT id, NULL::uuid, id FROM users WHERE reference_code = ?1";
            case SPC -> "SELECT id, id, owner_id FROM spaces WHERE reference_code = ?1";
            case ICP -> "SELECT id, NULL::uuid, NULL::uuid FROM inquiry_credit_packages WHERE reference_code = ?1";
            default -> genericSql(prefix);
        };
    }

    private static String genericSql(BusinessReferencePrefix prefix) {
        String space = prefix.spaceColumn() == null ? "NULL::uuid" : prefix.spaceColumn();
        String user = prefix.userColumn() == null ? "NULL::uuid" : prefix.userColumn();
        return "SELECT id, " + space + ", " + user + " FROM " + prefix.table() + " WHERE reference_code = ?1";
    }
}
