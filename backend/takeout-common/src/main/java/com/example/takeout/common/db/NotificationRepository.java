package com.example.takeout.common.db;

import com.example.takeout.common.auth.UserRole;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 通知消息仓储，负责把订单、接单、轨迹和状态变更写成可读消息，并提供列表查询。
 */
@Repository
public class NotificationRepository {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm");
    private final JdbcTemplate jdbcTemplate;

    public NotificationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void createNotification(UserRole targetRole,
                                   Long targetId,
                                   String type,
                                   String title,
                                   String content,
                                   String entityType,
                                   Long entityId,
                                   String eventKey) {
        if (targetRole == null || targetId == null || targetId <= 0) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                insert into t_notification(
                    target_role, target_id, type, title, content, entity_type, entity_id,
                    event_key, read_flag, created_at, updated_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, false, ?, ?)
                on duplicate key update updated_at = values(updated_at)
                """,
                targetRole.name(),
                targetId,
                type,
                title,
                content,
                entityType,
                entityId,
                eventKey,
                Timestamp.valueOf(now),
                Timestamp.valueOf(now));
    }

    public Map<String, Object> listNotifications(UserRole role, Long targetId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 50));
        List<Map<String, Object>> items;
        if (targetId == null) {
            items = jdbcTemplate.query("""
                    select id, target_role, target_id, type, title, content, entity_type, entity_id,
                           read_flag, read_at, created_at
                    from t_notification
                    where target_role = ? and target_id is null
                    order by read_flag asc, created_at desc, id desc
                    limit ?
                    """,
                    this::mapNotificationRow,
                    role.name(), safeLimit);
        } else {
            items = jdbcTemplate.query("""
                    select id, target_role, target_id, type, title, content, entity_type, entity_id,
                           read_flag, read_at, created_at
                    from t_notification
                    where target_role = ? and (target_id = ? or target_id is null)
                    order by read_flag asc, created_at desc, id desc
                    limit ?
                    """,
                    this::mapNotificationRow,
                    role.name(), targetId, safeLimit);
        }
        return linkedMap(
                "items", items,
                "unreadCount", unreadCount(role, targetId));
    }

    public Map<String, Object> markNotificationRead(UserRole role, Long targetId, Long notificationId) {
        if (notificationId == null) {
            return linkedMap("notificationId", null, "read", false);
        }
        if (targetId == null) {
            jdbcTemplate.update("""
                    update t_notification
                    set read_flag = true, read_at = ?, updated_at = ?
                    where id = ? and target_role = ? and target_id is null
                    """,
                    Timestamp.valueOf(LocalDateTime.now()),
                    Timestamp.valueOf(LocalDateTime.now()),
                    notificationId,
                    role.name());
        } else {
            jdbcTemplate.update("""
                    update t_notification
                    set read_flag = true, read_at = ?, updated_at = ?
                    where id = ? and target_role = ? and (target_id = ? or target_id is null)
                    """,
                    Timestamp.valueOf(LocalDateTime.now()),
                    Timestamp.valueOf(LocalDateTime.now()),
                    notificationId,
                    role.name(),
                    targetId);
        }
        return linkedMap("notificationId", notificationId, "read", true);
    }

    public Map<String, Object> markAllNotificationsRead(UserRole role, Long targetId) {
        int updated;
        LocalDateTime now = LocalDateTime.now();
        if (targetId == null) {
            updated = jdbcTemplate.update("""
                    update t_notification
                    set read_flag = true, read_at = ?, updated_at = ?
                    where target_role = ? and target_id is null and read_flag = false
                    """,
                    Timestamp.valueOf(now),
                    Timestamp.valueOf(now),
                    role.name());
        } else {
            updated = jdbcTemplate.update("""
                    update t_notification
                    set read_flag = true, read_at = ?, updated_at = ?
                    where target_role = ? and (target_id = ? or target_id is null) and read_flag = false
                    """,
                    Timestamp.valueOf(now),
                    Timestamp.valueOf(now),
                    role.name(),
                    targetId);
        }
        return linkedMap("updated", updated, "read", true);
    }

    public int unreadCount(UserRole role, Long targetId) {
        Integer count;
        if (targetId == null) {
            count = jdbcTemplate.queryForObject("""
                    select count(*)
                    from t_notification
                    where target_role = ? and target_id is null and read_flag = false
                    """, Integer.class, role.name());
        } else {
            count = jdbcTemplate.queryForObject("""
                    select count(*)
                    from t_notification
                    where target_role = ? and (target_id = ? or target_id is null) and read_flag = false
                    """, Integer.class, role.name(), targetId);
        }
        return count == null ? 0 : count;
    }

    private Map<String, Object> mapNotificationRow(ResultSet rs, int rowNum) throws SQLException {
        String type = stringValue(rs.getString("type"), "");
        boolean read = rs.getBoolean("read_flag");
        Object entityId = rs.getObject("entity_id");
        return linkedMap(
                "notificationId", rs.getLong("id"),
                "type", type,
                "title", stringValue(rs.getString("title"), ""),
                "content", stringValue(rs.getString("content"), ""),
                "tone", notificationTone(type, read),
                "read", read,
                "time", notificationTime(rs.getTimestamp("created_at")),
                "entityType", stringValue(rs.getString("entity_type"), ""),
                "entityId", entityId == null ? null : rs.getLong("entity_id")
        );
    }

    private String notificationTone(String type, boolean read) {
        if (!read && ("ORDER_CANCELLED".equals(type) || type.contains("CANCEL"))) {
            return "danger";
        }
        return switch (type) {
            case "ORDER_CREATED", "TRACK_ARRIVED_STORE" -> "warning";
            case "ORDER_PAID", "ORDER_COMPLETED", "TRACK_DELIVERED" -> "success";
            case "ORDER_CANCELLED" -> "danger";
            default -> "info";
        };
    }

    private String notificationTime(Timestamp timestamp) {
        if (timestamp == null) {
            return "";
        }
        return timestamp.toLocalDateTime().format(TIME_FORMATTER);
    }

    private Map<String, Object> linkedMap(Object... keyValues) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < keyValues.length; index += 2) {
            result.put(String.valueOf(keyValues[index]), keyValues[index + 1]);
        }
        return result;
    }

    private String stringValue(Object value, String defaultValue) {
        return value == null ? defaultValue : String.valueOf(value);
    }
}
