package com.guvi.recommendationsystem.dao;

import com.guvi.recommendationsystem.model.CatalogItem;
import com.guvi.recommendationsystem.model.CatalogSort;
import com.guvi.recommendationsystem.model.ItemType;
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
import java.util.Locale;

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
            resultSet.getBigDecimal("price"),
            resultSet.getBoolean("is_active"),
            resultSet.getTimestamp("created_at").toLocalDateTime(),
            resultSet.getTimestamp("updated_at").toLocalDateTime());

    public JdbcItemDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<CatalogItem> findById(long itemId) {
        return jdbcTemplate.query("""
                SELECT item_id, category_id, title, description, item_type,
                       external_url, price, is_active, created_at, updated_at
                FROM items
                WHERE item_id = ?
                """, itemMapper, itemId).stream().findFirst();
    }

    @Override
    public List<CatalogItem> findAll() {
        return jdbcTemplate.query("""
                SELECT item_id, category_id, title, description, item_type,
                       external_url, price, is_active, created_at, updated_at
                FROM items
                ORDER BY created_at DESC, item_id DESC
                """, itemMapper);
    }

    @Override
    public List<CatalogItem> findActive() {
        return jdbcTemplate.query("""
                SELECT item_id, category_id, title, description, item_type,
                       external_url, price, is_active, created_at, updated_at
                FROM items
                WHERE is_active = TRUE
                ORDER BY created_at DESC, item_id DESC
                """, itemMapper);
    }

    @Override
    public List<CatalogItem> findActiveByCategory(long categoryId) {
        return jdbcTemplate.query("""
                SELECT item_id, category_id, title, description, item_type,
                       external_url, price, is_active, created_at, updated_at
                FROM items
                WHERE category_id = ? AND is_active = TRUE
                ORDER BY created_at DESC, item_id DESC
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
                       item.external_url, item.price, item.is_active, item.created_at, item.updated_at
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
            sql.append(" AND (LOWER(item.title) LIKE ? ESCAPE '!'"
                    + " OR LOWER(COALESCE(item.description, '')) LIKE ? ESCAPE '!')");
            String pattern = "%" + escapeLike(query.toLowerCase(Locale.ROOT)) + "%";
            arguments.add(pattern);
            arguments.add(pattern);
        }
        return new FilterParts(sql.toString(), List.copyOf(arguments));
    }

    private String escapeLike(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    private record FilterParts(String sql, List<Object> arguments) {
    }

    @Override
    public CatalogItem insert(long categoryId, String title, String description, ItemType type,
                              String externalUrl, BigDecimal price) {
        String sql = """
                INSERT INTO items (category_id, title, description, item_type, external_url, price)
                VALUES (?, ?, ?, ?, ?, ?)
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
                          String externalUrl, BigDecimal price) {
        return jdbcTemplate.update("""
                UPDATE items
                SET category_id = ?, title = ?, description = ?, item_type = ?,
                    external_url = ?, price = ?
                WHERE item_id = ?
                """,
                categoryId,
                title,
                description,
                type.name(),
                externalUrl,
                price,
                itemId) == 1;
    }

    @Override
    public boolean setActive(long itemId, boolean active) {
        return jdbcTemplate.update(
                "UPDATE items SET is_active = ? WHERE item_id = ?",
                active,
                itemId) == 1;
    }
}
