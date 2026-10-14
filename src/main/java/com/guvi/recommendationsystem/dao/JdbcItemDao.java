package com.guvi.recommendationsystem.dao;

import com.guvi.recommendationsystem.model.CatalogItem;
import com.guvi.recommendationsystem.model.CatalogSort;
import com.guvi.recommendationsystem.model.ItemType;
import com.guvi.recommendationsystem.model.ItemDifficulty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import java.util.ArrayList;
import java.util.Collection;

@Repository
public class JdbcItemDao implements ItemDao {
    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<CatalogItem> itemMapper = (resultSet, rowNumber) -> new CatalogItem(
            resultSet.getLong("item_id"),
            resultSet.getLong("category_id"),
            resultSet.getString("title"),
            resultSet.getString("description"),
            ItemType.valueOf(resultSet.getString("item_type")),
            resultSet.getString("external_url"),
            resultSet.getString("source_name"),
            java.util.Optional.ofNullable(resultSet.getString("tag_list"))
                    .filter(tags -> !tags.isBlank()).map(tags -> java.util.Arrays.asList(tags.split(","))).orElse(List.of()),
            ItemDifficulty.valueOf(resultSet.getString("difficulty")),
            resultSet.getObject("estimated_minutes", Integer.class),
            resultSet.getBigDecimal("quality_score"),
            resultSet.getBigDecimal("price"),
            resultSet.getBoolean("is_active"),
            resultSet.getTimestamp("created_at").toLocalDateTime(),
            resultSet.getTimestamp("updated_at").toLocalDateTime(),
            resultSet.getBoolean("is_editor_pick"));

    public JdbcItemDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<CatalogItem> findById(long itemId) {
        return jdbcTemplate.query("""
                SELECT item_id, category_id, title, description, item_type,
                       external_url, source_name, difficulty, estimated_minutes, quality_score,
                       is_editor_pick, price, is_active, created_at, updated_at,
                       (SELECT GROUP_CONCAT(tag.name ORDER BY tag.name SEPARATOR ',')
                        FROM item_interests item_tag JOIN interests tag ON tag.interest_id = item_tag.interest_id
                        WHERE item_tag.item_id = item.item_id) AS tag_list
                FROM items item
                WHERE item.item_id = ?
                """, itemMapper, itemId).stream().findFirst();
    }

    @Override
    public List<CatalogItem> findAll() {
        return jdbcTemplate.query("""
                SELECT item_id, category_id, title, description, item_type,
                       external_url, source_name, difficulty, estimated_minutes, quality_score,
                       is_editor_pick, price, is_active, created_at, updated_at,
                       (SELECT GROUP_CONCAT(tag.name ORDER BY tag.name SEPARATOR ',')
                        FROM item_interests item_tag JOIN interests tag ON tag.interest_id = item_tag.interest_id
                        WHERE item_tag.item_id = item.item_id) AS tag_list
                FROM items item
                ORDER BY item.created_at DESC, item.item_id DESC
                """, itemMapper);
    }

    @Override
    public List<CatalogItem> findActive() {
        return jdbcTemplate.query("""
                SELECT item_id, category_id, title, description, item_type,
                       external_url, source_name, difficulty, estimated_minutes, quality_score,
                       is_editor_pick, price, is_active, created_at, updated_at,
                       (SELECT GROUP_CONCAT(tag.name ORDER BY tag.name SEPARATOR ',')
                        FROM item_interests item_tag JOIN interests tag ON tag.interest_id = item_tag.interest_id
                        WHERE item_tag.item_id = item.item_id) AS tag_list
                FROM items item
                WHERE item.is_active = TRUE
                ORDER BY item.created_at DESC, item.item_id DESC
                """, itemMapper);
    }

    @Override
    public List<CatalogItem> findDiscoveryForUser(long userId, int limit) {
        return jdbcTemplate.query("""
                SELECT item.item_id, item.category_id, item.title, item.description, item.item_type,
                       item.external_url, item.source_name, item.difficulty, item.estimated_minutes,
                       item.quality_score, item.is_editor_pick, item.price, item.is_active,
                       item.created_at, item.updated_at,
                       (SELECT GROUP_CONCAT(tag.name ORDER BY tag.name SEPARATOR ',')
                        FROM item_interests item_tag JOIN interests tag ON tag.interest_id = item_tag.interest_id
                        WHERE item_tag.item_id = item.item_id) AS tag_list
                FROM items item
                JOIN categories category ON category.category_id = item.category_id AND category.is_active = TRUE
                WHERE item.is_active = TRUE
                  AND NOT EXISTS (
                      SELECT 1 FROM interactions rejection
                      WHERE rejection.user_id = ? AND rejection.item_id = item.item_id
                        AND rejection.interaction_type IN ('DISLIKE', 'HIDE')
                  )
                  AND NOT EXISTS (
                      SELECT 1 FROM interactions exposure
                      WHERE exposure.user_id = ? AND exposure.item_id = item.item_id
                        AND exposure.interaction_type IN ('VIEW', 'CLICK', 'READ_PROGRESS')
                        AND exposure.occurred_at >= DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 14 DAY)
                  )
                ORDER BY item.is_editor_pick DESC, item.quality_score DESC, item.item_id
                LIMIT ?
                """, itemMapper, userId, userId, limit);
    }

    @Override
    public List<CatalogItem> findActiveByCategory(long categoryId) {
        return jdbcTemplate.query("""
                SELECT item_id, category_id, title, description, item_type,
                       external_url, source_name, difficulty, estimated_minutes, quality_score,
                       is_editor_pick, price, is_active, created_at, updated_at,
                       (SELECT GROUP_CONCAT(tag.name ORDER BY tag.name SEPARATOR ',')
                        FROM item_interests item_tag JOIN interests tag ON tag.interest_id = item_tag.interest_id
                        WHERE item_tag.item_id = item.item_id) AS tag_list
                FROM items item
                WHERE item.category_id = ? AND item.is_active = TRUE
                ORDER BY item.created_at DESC, item.item_id DESC
                """, itemMapper, categoryId);
    }

    @Override
    public List<CatalogItem> searchActive(
            String query, Long categoryId, ItemType type, CatalogSort sort, int limit, int offset) {
        FilterParts filters = filters(query, categoryId, type);
        String orderBy = switch (sort) {
            case NEWEST -> "item.created_at DESC, item.item_id DESC";
            case MOST_POPULAR -> "COUNT(interaction.interaction_id) DESC, item.created_at DESC, item.item_id DESC";
            case TITLE_ASC -> "LOWER(item.title) ASC, item.item_id ASC";
        };
        List<Object> arguments = new ArrayList<>(filters.arguments());
        arguments.add(limit);
        arguments.add(offset);
        return jdbcTemplate.query("""
                SELECT item.item_id, item.category_id, item.title, item.description, item.item_type,
                       item.external_url, item.source_name, item.difficulty, item.estimated_minutes,
                       item.quality_score, item.is_editor_pick, item.price, item.is_active, item.created_at, item.updated_at,
                       (SELECT GROUP_CONCAT(tag.name ORDER BY tag.name SEPARATOR ',')
                        FROM item_interests item_tag JOIN interests tag ON tag.interest_id = item_tag.interest_id
                        WHERE item_tag.item_id = item.item_id) AS tag_list
                FROM items item
                JOIN categories category
                  ON category.category_id = item.category_id AND category.is_active = TRUE
                LEFT JOIN interactions interaction ON interaction.item_id = item.item_id
                WHERE item.is_active = TRUE
                """ + filters.sql() + " GROUP BY item.item_id ORDER BY " + orderBy + " LIMIT ? OFFSET ?",
                itemMapper,
                arguments.toArray());
    }

    @Override
    public long countActive(String query, Long categoryId, ItemType type) {
        FilterParts filters = filters(query, categoryId, type);
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM items item
                JOIN categories category
                  ON category.category_id = item.category_id AND category.is_active = TRUE
                WHERE item.is_active = TRUE
                """ + filters.sql(), Long.class, filters.arguments().toArray());
        return count == null ? 0 : count;
    }

    private FilterParts filters(String query, Long categoryId, ItemType type) {
        StringBuilder sql = new StringBuilder();
        List<Object> arguments = new ArrayList<>();
        if (categoryId != null) {
            sql.append(" AND item.category_id = ?");
            arguments.add(categoryId);
        }
        if (type != null) {
            sql.append(" AND item.item_type = ?");
            arguments.add(type.name());
        }
        if (query != null && !query.isBlank()) {
            List<String> terms = SearchTerms.tokenize(query);
            if (terms.isEmpty()) terms = List.of(query.toLowerCase(java.util.Locale.ROOT));
            sql.append(" AND (");
            List<String> termPredicates = new ArrayList<>();
            for (String ignored : terms) {
                termPredicates.add("(LOWER(item.title) LIKE ? ESCAPE '!'"
                        + " OR LOWER(COALESCE(item.description, '')) LIKE ? ESCAPE '!'"
                        + " OR LOWER(COALESCE(item.source_name, '')) LIKE ? ESCAPE '!'"
                        + " OR LOWER(category.name) LIKE ? ESCAPE '!'"
                        + " OR EXISTS (SELECT 1 FROM item_interests item_tag"
                        + " JOIN interests tag ON tag.interest_id = item_tag.interest_id"
                        + " WHERE item_tag.item_id = item.item_id AND LOWER(tag.name) LIKE ? ESCAPE '!'))");
            }
            sql.append(String.join(" OR ", termPredicates)).append(")");
            for (String term : terms) {
                String pattern = SearchTerms.likePattern(term);
                arguments.addAll(List.of(pattern, pattern, pattern, pattern, pattern));
            }
        }
        return new FilterParts(sql.toString(), List.copyOf(arguments));
    }

    private record FilterParts(String sql, List<Object> arguments) {
    }

    @Override
    public CatalogItem insert(long categoryId, String title, String description, ItemType type,
                              String externalUrl, BigDecimal price, String sourceName, ItemDifficulty difficulty,
                              Integer estimatedMinutes, BigDecimal qualityScore, boolean editorPick) {
        String sql = """
                INSERT INTO items (category_id, title, description, item_type, external_url, price,
                                   source_name, difficulty, estimated_minutes, quality_score, is_editor_pick)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, categoryId);
            statement.setString(2, title);
            statement.setString(3, description);
            statement.setString(4, type.name());
            statement.setString(5, externalUrl);
            statement.setBigDecimal(6, price);
            statement.setString(7, sourceName);
            statement.setString(8, difficulty.name());
            if (estimatedMinutes == null) statement.setNull(9, java.sql.Types.SMALLINT);
            else statement.setInt(9, estimatedMinutes);
            statement.setBigDecimal(10, qualityScore);
            statement.setBoolean(11, editorPick);
            return statement;
        }, keyHolder);
        Number generatedId = keyHolder.getKey();
        if (generatedId == null) {
            throw new IllegalStateException("The database did not return the new item ID.");
        }
        return findById(generatedId.longValue())
                .orElseThrow(() -> new IllegalStateException("The new item could not be read back."));
    }

    @Override
    public boolean update(long itemId, long categoryId, String title, String description, ItemType type,
                          String externalUrl, BigDecimal price, String sourceName, ItemDifficulty difficulty,
                          Integer estimatedMinutes, BigDecimal qualityScore, boolean editorPick) {
        return jdbcTemplate.update("""
                UPDATE items
                SET category_id = ?, title = ?, description = ?, item_type = ?,
                    external_url = ?, price = ?, source_name = ?, difficulty = ?,
                    estimated_minutes = ?, quality_score = ?, is_editor_pick = ?
                WHERE item_id = ?
                """,
                categoryId,
                title,
                description,
                type.name(),
                externalUrl,
                price,
                sourceName,
                difficulty.name(),
                estimatedMinutes,
                qualityScore,
                editorPick,
                itemId) == 1;
    }

    @Override
    public boolean setActive(long itemId, boolean active) {
        return jdbcTemplate.update(
                "UPDATE items SET is_active = ? WHERE item_id = ?",
                active,
                itemId) == 1;
    }

    @Override
    public int setActive(Collection<Long> itemIds, boolean active) {
        if (itemIds.isEmpty()) return 0;
        String placeholders = String.join(",", java.util.Collections.nCopies(itemIds.size(), "?"));
        List<Object> arguments = new ArrayList<>();
        arguments.add(active);
        arguments.addAll(itemIds);
        return jdbcTemplate.update("UPDATE items SET is_active = ? WHERE item_id IN (" + placeholders + ")",
                arguments.toArray());
    }

    @Override
    public boolean existsByExternalUrl(String externalUrl) {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM items WHERE external_url = ?)", Boolean.class, externalUrl);
        return Boolean.TRUE.equals(exists);
    }
}
