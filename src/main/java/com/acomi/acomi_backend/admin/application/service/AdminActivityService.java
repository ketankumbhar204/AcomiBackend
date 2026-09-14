package com.acomi.acomi_backend.admin.application.service;

import com.acomi.acomi_backend.admin.api.dto.response.AdminActivityItemResponse;
import com.acomi.acomi_backend.admin.domain.model.AdminActivityTargetType;
import com.acomi.acomi_backend.admin.domain.model.AdminActivityType;
import com.acomi.acomi_backend.common.web.PagedResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Reconstructs Admin Recent Activity from existing tables (no activity table).
 */
@Service
public class AdminActivityService {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public PagedResponse<AdminActivityItemResponse> list(
            LocalDateTime from,
            LocalDateTime to,
            AdminActivityType activityType,
            Pageable pageable) {
        int page = Math.max(pageable.getPageNumber(), 0);
        int size = Math.min(Math.max(pageable.getPageSize(), 1), 100);
        int offset = page * size;

        String filterType = activityType == null ? null : activityType.name();
        List<String> unionParts = buildUnionParts(filterType);
        if (unionParts.isEmpty()) {
            return emptyPage(page, size);
        }

        String unionSql = String.join(" UNION ALL ", unionParts);
        String countSql = "SELECT COUNT(*) FROM (" + unionSql + ") activity";
        String pageSql = "SELECT * FROM (" + unionSql + ") activity ORDER BY occurred_at DESC LIMIT :limit OFFSET :offset";

        Query countQuery = entityManager.createNativeQuery(countSql);
        bindCommonParams(countQuery, from, to);
        Number totalNumber = (Number) countQuery.getSingleResult();
        long total = totalNumber == null ? 0L : totalNumber.longValue();

        Query pageQuery = entityManager.createNativeQuery(pageSql);
        bindCommonParams(pageQuery, from, to);
        pageQuery.setParameter("limit", size);
        pageQuery.setParameter("offset", offset);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = pageQuery.getResultList();
        List<AdminActivityItemResponse> content = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            content.add(mapRow(row));
        }

        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) total / (double) size);
        return PagedResponse.<AdminActivityItemResponse>builder()
                .content(content)
                .page(page)
                .size(size)
                .totalElements(total)
                .totalPages(totalPages)
                .first(page == 0)
                .last(totalPages == 0 || page >= totalPages - 1)
                .build();
    }

    private static PagedResponse<AdminActivityItemResponse> emptyPage(int page, int size) {
        return PagedResponse.<AdminActivityItemResponse>builder()
                .content(List.of())
                .page(page)
                .size(size)
                .totalElements(0)
                .totalPages(0)
                .first(true)
                .last(true)
                .build();
    }

    private List<String> buildUnionParts(String filterType) {
        List<String> parts = new ArrayList<>();
        addIfMatch(parts, filterType, AdminActivityType.NEW_ENQUIRY, """
                SELECT
                    CAST(e.id AS varchar) AS target_id,
                    'NEW_ENQUIRY' AS activity_type,
                    'ENQUIRY' AS target_type,
                    CAST(NULL AS varchar) AS secondary_target_id,
                    CAST(NULL AS varchar) AS secondary_target_type,
                    e.requested_at AS occurred_at,
                    e.space_name_snapshot AS title_context,
                    CAST(NULL AS varchar) AS city_context,
                    CAST(NULL AS varchar) AS actor_context
                FROM space_enquiries e
                WHERE e.requested_at >= :fromAt AND e.requested_at < :toAt
                """);
        addIfMatch(parts, filterType, AdminActivityType.ENQUIRY_SHARED, """
                SELECT
                    CAST(e.id AS varchar),
                    'ENQUIRY_SHARED',
                    'ENQUIRY',
                    CAST(NULL AS varchar),
                    CAST(NULL AS varchar),
                    e.shared_at,
                    e.space_name_snapshot,
                    CAST(NULL AS varchar),
                    CAST(NULL AS varchar)
                FROM space_enquiries e
                WHERE e.shared_at IS NOT NULL
                  AND e.shared_at >= :fromAt AND e.shared_at < :toAt
                """);
        addIfMatch(parts, filterType, AdminActivityType.ENQUIRY_REJECTED, """
                SELECT
                    CAST(e.id AS varchar),
                    'ENQUIRY_REJECTED',
                    'ENQUIRY',
                    CAST(NULL AS varchar),
                    CAST(NULL AS varchar),
                    e.rejected_at,
                    e.space_name_snapshot,
                    CAST(NULL AS varchar),
                    CAST(NULL AS varchar)
                FROM space_enquiries e
                WHERE e.rejected_at IS NOT NULL
                  AND e.rejected_at >= :fromAt AND e.rejected_at < :toAt
                """);
        addIfMatch(parts, filterType, AdminActivityType.NEW_PROPERTY_REGISTRATION, """
                SELECT
                    CAST(r.id AS varchar),
                    'NEW_PROPERTY_REGISTRATION',
                    'PROPERTY_REGISTRATION',
                    CAST(NULL AS varchar),
                    CAST(NULL AS varchar),
                    r.created_at,
                    r.property_name,
                    r.city,
                    CAST(NULL AS varchar)
                FROM property_registrations r
                WHERE r.created_at >= :fromAt AND r.created_at < :toAt
                """);
        addIfMatch(parts, filterType, AdminActivityType.LEAD_CLAIMED_PROPERTY, """
                SELECT
                    CAST(r.id AS varchar),
                    'LEAD_CLAIMED_PROPERTY',
                    'PROPERTY_REGISTRATION',
                    CAST(NULL AS varchar),
                    CAST(NULL AS varchar),
                    r.claimed_at,
                    r.property_name,
                    r.city,
                    CAST(NULL AS varchar)
                FROM property_registrations r
                WHERE r.claimed_at IS NOT NULL
                  AND r.claimed_at >= :fromAt AND r.claimed_at < :toAt
                """);
        addIfMatch(parts, filterType, AdminActivityType.NEW_MESS_REGISTRATION, """
                SELECT
                    CAST(r.id AS varchar),
                    'NEW_MESS_REGISTRATION',
                    'MESS_REGISTRATION',
                    CAST(NULL AS varchar),
                    CAST(NULL AS varchar),
                    r.created_at,
                    r.mess_name,
                    r.city,
                    CAST(NULL AS varchar)
                FROM mess_registrations r
                WHERE r.created_at >= :fromAt AND r.created_at < :toAt
                """);
        addIfMatch(parts, filterType, AdminActivityType.LEAD_CLAIMED_MESS, """
                SELECT
                    CAST(r.id AS varchar),
                    'LEAD_CLAIMED_MESS',
                    'MESS_REGISTRATION',
                    CAST(NULL AS varchar),
                    CAST(NULL AS varchar),
                    r.claimed_at,
                    r.mess_name,
                    r.city,
                    CAST(NULL AS varchar)
                FROM mess_registrations r
                WHERE r.claimed_at IS NOT NULL
                  AND r.claimed_at >= :fromAt AND r.claimed_at < :toAt
                """);
        addIfMatch(parts, filterType, AdminActivityType.NEW_USER_REGISTRATION, """
                SELECT
                    CAST(u.id AS varchar),
                    'NEW_USER_REGISTRATION',
                    'USER',
                    CAST(NULL AS varchar),
                    CAST(NULL AS varchar),
                    COALESCE(u.mobile_verified_at, u.created_at),
                    u.full_name,
                    CAST(NULL AS varchar),
                    u.mobile_number
                FROM users u
                WHERE u.system_role = 'USER'
                  AND u.is_active = TRUE
                  AND u.mobile_verified_at IS NOT NULL
                  AND COALESCE(u.mobile_verified_at, u.created_at) >= :fromAt
                  AND COALESCE(u.mobile_verified_at, u.created_at) < :toAt
                """);
        addIfMatch(parts, filterType, AdminActivityType.NEW_PROPERTY_LISTED, """
                SELECT
                    CAST(COALESCE(pr.id, s.id) AS varchar),
                    'NEW_PROPERTY_LISTED',
                    CASE WHEN pr.id IS NOT NULL THEN 'PROPERTY_REGISTRATION' ELSE 'SPACE' END,
                    CAST(s.id AS varchar),
                    'SPACE',
                    s.created_at,
                    s.name,
                    CAST(NULL AS varchar),
                    CAST(NULL AS varchar)
                FROM spaces s
                LEFT JOIN property_registrations pr ON pr.converted_space_id = s.id
                WHERE s.is_active = TRUE
                  AND s.type IN ('PG', 'HOSTEL', 'CO_LIVING', 'RENTAL')
                  AND s.created_at >= :fromAt AND s.created_at < :toAt
                """);
        addIfMatch(parts, filterType, AdminActivityType.NEW_MESS_LISTED, """
                SELECT
                    CAST(COALESCE(mr.id, s.id) AS varchar),
                    'NEW_MESS_LISTED',
                    CASE WHEN mr.id IS NOT NULL THEN 'MESS_REGISTRATION' ELSE 'SPACE' END,
                    CAST(s.id AS varchar),
                    'SPACE',
                    s.created_at,
                    s.name,
                    CAST(NULL AS varchar),
                    CAST(NULL AS varchar)
                FROM spaces s
                LEFT JOIN mess_registrations mr ON mr.converted_space_id = s.id
                WHERE s.is_active = TRUE
                  AND s.type = 'MESS'
                  AND s.created_at >= :fromAt AND s.created_at < :toAt
                """);
        addIfMatch(parts, filterType, AdminActivityType.ADDRESS_SAVED, """
                SELECT
                    CAST(a.id AS varchar),
                    'ADDRESS_SAVED',
                    'SAVED_ADDRESS',
                    CAST(NULL AS varchar),
                    CAST(NULL AS varchar),
                    a.created_at,
                    a.address_line,
                    a.city,
                    CAST(NULL AS varchar)
                FROM saved_addresses a
                WHERE a.is_active = TRUE
                  AND a.created_at >= :fromAt AND a.created_at < :toAt
                """);
        return parts;
    }

    private static void addIfMatch(
            List<String> parts, String filterType, AdminActivityType type, String sql) {
        if (filterType == null || filterType.equals(type.name())) {
            parts.add("(" + sql + ")");
        }
    }

    private void bindCommonParams(Query query, LocalDateTime from, LocalDateTime to) {
        LocalDateTime fromAt = from != null ? from : LocalDateTime.of(2000, 1, 1, 0, 0);
        LocalDateTime toAt = to != null ? to : LocalDateTime.now().plusYears(1);
        query.setParameter("fromAt", Timestamp.valueOf(fromAt));
        query.setParameter("toAt", Timestamp.valueOf(toAt));
    }

    private AdminActivityItemResponse mapRow(Object[] row) {
        UUID targetId = parseUuid(asString(row[0]));
        AdminActivityType type = AdminActivityType.valueOf(asString(row[1]));
        AdminActivityTargetType targetType = AdminActivityTargetType.valueOf(asString(row[2]));
        UUID secondaryTargetId = parseUuid(asString(row[3]));
        AdminActivityTargetType secondaryTargetType =
                StringUtils.hasText(asString(row[4]))
                        ? AdminActivityTargetType.valueOf(asString(row[4]))
                        : null;
        LocalDateTime timestamp = toLocalDateTime(row[5]);
        String titleContext = asString(row[6]);
        String cityContext = asString(row[7]);
        String actorContext = asString(row[8]);

        String title = titleFor(type);
        String description = descriptionFor(type, titleContext, cityContext, actorContext);
        String id = type.name()
                + ":"
                + targetId
                + ":"
                + (timestamp == null ? "0" : timestamp.toEpochSecond(ZoneOffset.UTC));

        return AdminActivityItemResponse.builder()
                .id(id)
                .type(type)
                .title(title)
                .description(description)
                .timestamp(timestamp)
                .targetType(targetType)
                .targetId(targetId)
                .secondaryTargetType(secondaryTargetType)
                .secondaryTargetId(secondaryTargetId)
                .build();
    }

    static String titleFor(AdminActivityType type) {
        return switch (type) {
            case NEW_ENQUIRY -> "New Enquiry";
            case ENQUIRY_SHARED -> "Enquiry Shared";
            case ENQUIRY_REJECTED -> "Enquiry Rejected";
            case NEW_PROPERTY_REGISTRATION -> "New Property Lead";
            case NEW_MESS_REGISTRATION -> "New Mess Lead";
            case NEW_USER_REGISTRATION -> "New User Registration";
            case NEW_PROPERTY_LISTED -> "New Property Listed";
            case NEW_MESS_LISTED -> "New Mess Listed";
            case LEAD_CLAIMED_PROPERTY -> "Property Lead Claimed";
            case LEAD_CLAIMED_MESS -> "Mess Lead Claimed";
            case ADDRESS_SAVED -> "Address Saved";
        };
    }

    static String descriptionFor(
            AdminActivityType type, String titleContext, String cityContext, String actorContext) {
        String name = blankToDash(titleContext);
        String city = blankToNull(cityContext);
        return switch (type) {
            case NEW_ENQUIRY, ENQUIRY_SHARED, ENQUIRY_REJECTED -> "For " + name;
            case NEW_PROPERTY_REGISTRATION, NEW_MESS_REGISTRATION, NEW_PROPERTY_LISTED, NEW_MESS_LISTED,
                    LEAD_CLAIMED_PROPERTY, LEAD_CLAIMED_MESS -> city == null ? name : name + ", " + city;
            case NEW_USER_REGISTRATION -> {
                String display = "user".equalsIgnoreCase(name) || "—".equals(name)
                        ? (blankToNull(actorContext) != null ? actorContext : "New user")
                        : name;
                yield display;
            }
            case ADDRESS_SAVED -> city == null ? name : name + ", " + city;
        };
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static UUID parseUuid(String value) {
        if (!StringUtils.hasText(value) || "null".equalsIgnoreCase(value)) {
            return null;
        }
        return UUID.fromString(value);
    }

    private static LocalDateTime toLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        return LocalDateTime.parse(String.valueOf(value).replace(' ', 'T'));
    }

    private static String blankToDash(String value) {
        String trimmed = blankToNull(value);
        return trimmed == null ? "—" : trimmed;
    }

    private static String blankToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
