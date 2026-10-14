package com.guvi.recommendationsystem.dao;

import com.guvi.recommendationsystem.model.AdminSettingsAuditEntry;
import com.guvi.recommendationsystem.model.AdminSystemSettings;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Repository
public class JdbcAdminSettingsDao implements AdminSettingsDao {
    private final JdbcTemplate jdbc;
    public JdbcAdminSettingsDao(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public AdminSystemSettings getSettings() {
        Map<String, Object> values = jdbc.query("SELECT setting_key, setting_value, updated_at FROM recommendation_system_settings",
                rs -> {
                    java.util.Map<String, Object> result = new java.util.HashMap<>();
                    while (rs.next()) { result.put(rs.getString("setting_key"), rs.getBigDecimal("setting_value")); result.put("updated_at", rs.getTimestamp("updated_at")); }
                    return result;
                });
        java.sql.Timestamp updated = (java.sql.Timestamp) values.get("updated_at");
        return new AdminSystemSettings((BigDecimal) values.get("familiar"), (BigDecimal) values.get("related"),
                (BigDecimal) values.get("discovery"), updated == null ? null : updated.toLocalDateTime());
    }
    @Override public void save(String key, BigDecimal value, long actorUserId) {
        BigDecimal old = jdbc.queryForObject("SELECT setting_value FROM recommendation_system_settings WHERE setting_key = ?", BigDecimal.class, key);
        jdbc.update("UPDATE recommendation_system_settings SET setting_value = ?, updated_by = ? WHERE setting_key = ?", value, actorUserId, key);
        jdbc.update("INSERT INTO recommendation_settings_audit (setting_key, old_value, new_value, actor_user_id) VALUES (?, ?, ?, ?)",
                key, old, value, actorUserId);
    }
    @Override public List<AdminSettingsAuditEntry> findAudit(int limit) {
        return jdbc.query("""
                SELECT audit.audit_id, audit.setting_key, audit.old_value, audit.new_value,
                       account.username, audit.changed_at
                FROM recommendation_settings_audit audit
                LEFT JOIN users account ON account.user_id = audit.actor_user_id
                ORDER BY audit.changed_at DESC, audit.audit_id DESC LIMIT ?
                """, (rs, row) -> new AdminSettingsAuditEntry(rs.getLong("audit_id"), rs.getString("setting_key"),
                rs.getBigDecimal("old_value"), rs.getBigDecimal("new_value"), rs.getString("username"),
                rs.getTimestamp("changed_at").toLocalDateTime()), limit);
    }
}
