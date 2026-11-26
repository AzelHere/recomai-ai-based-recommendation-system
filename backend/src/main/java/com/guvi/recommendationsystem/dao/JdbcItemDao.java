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
import java.util.List;
import java.util.Optional;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

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
            resultSet.getBoolean("is_editor_pick"),resultSet.getLong("revision"));

    public JdbcItemDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public com.guvi.recommendationsystem.model.CatalogPage<CatalogItem> searchPage(String query, Long categoryId,
            ItemType type, CatalogSort sort, ItemDifficulty difficulty, Integer maximumTime, String provider, int page, int pageSize) {
        return searchPage(query,categoryId,type,sort,difficulty,maximumTime,provider,null,page,pageSize);
    }
    public com.guvi.recommendationsystem.model.CatalogPage<CatalogItem> searchPage(String query, Long categoryId,
            ItemType type, CatalogSort sort, ItemDifficulty difficulty, Integer maximumTime, String provider, String topic, int page, int pageSize) {
        FilterParts base=filters(query,categoryId,type);
        String where=base.sql();var args=new ArrayList<Object>(base.arguments());
        if(difficulty!=null){where+=" AND item.difficulty=?";args.add(difficulty.name());}
        if(maximumTime!=null){where+=" AND item.estimated_minutes<=?";args.add(maximumTime);}
        if(provider!=null && !provider.isBlank()){where+=" AND item.source_name=?";args.add(provider.strip());}
        if(topic!=null && !topic.isBlank()) {
            where+=" AND (EXISTS (SELECT 1 FROM item_interests it JOIN interests t ON t.interest_id=it.interest_id WHERE it.item_id=item.item_id AND LOWER(t.name)=LOWER(?)) OR EXISTS (SELECT 1 FROM resource_provenance p WHERE p.item_id=item.item_id AND JSON_CONTAINS(p.source_topics,JSON_QUOTE(?))))";
            args.add(topic.strip());args.add(topic.strip());
        }
        String from=" FROM items item JOIN categories category ON category.category_id=item.category_id AND category.is_active=TRUE WHERE item.is_active=TRUE "+where;
        long total=jdbcTemplate.queryForObject("SELECT COUNT(*)"+from,Long.class,args.toArray());
        String order=switch(sort){case TITLE_ASC -> "LOWER(item.title),item.item_id";case TIME_ASC -> "item.estimated_minutes IS NULL,item.estimated_minutes,item.item_id";case MOST_POPULAR -> "(SELECT COUNT(*) FROM interactions e WHERE e.item_id=item.item_id) DESC,item.created_at DESC,item.item_id DESC";default -> "item.created_at DESC,item.item_id DESC";};
        if(sort==CatalogSort.RELEVANCE && query!=null&&!query.isBlank()){
            order="CASE WHEN LOWER(item.title)=LOWER(?) THEN 3 WHEN LOCATE(LOWER(?),LOWER(item.title))>0 THEN 2 WHEN LOCATE(LOWER(?),LOWER(COALESCE(item.description,'')))>0 THEN 1 ELSE 0 END DESC,"+order;
            args.add(query.strip());args.add(query.strip());args.add(query.strip());
        }
        args.add(pageSize);args.add((page-1)*pageSize);
        List<Long> ids=jdbcTemplate.queryForList("SELECT item.item_id"+from+" ORDER BY "+order+" LIMIT ? OFFSET ?",Long.class,args.toArray());
        Map<Long,CatalogItem> metadata=new java.util.HashMap<>();findByIds(ids).forEach(item->metadata.put(item.itemId(),item));
        return new com.guvi.recommendationsystem.model.CatalogPage<>(ids.stream().map(metadata::get).toList(),page,pageSize,total,(total+pageSize-1)/pageSize);
    }

    /** One bounded metadata query, including unavailable resources for owner history. */
    public List<CatalogItem> findByIds(Collection<Long> ids) {
        if (ids.isEmpty()) return List.of();
        if (ids.size() > 100) throw new IllegalArgumentException("At most 100 resource IDs are allowed.");
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        return jdbcTemplate.query("""
                SELECT item.*, (item.is_active AND category.is_active) AS effective_active,
                       (SELECT GROUP_CONCAT(tag.name ORDER BY tag.name SEPARATOR ',')
                        FROM item_interests it JOIN interests tag ON tag.interest_id=it.interest_id
                        WHERE it.item_id=item.item_id) AS tag_list
                FROM items item JOIN categories category ON category.category_id=item.category_id
                WHERE item.item_id IN (%s) ORDER BY item.created_at DESC,item.item_id DESC
                """.formatted(placeholders), (rs,n) -> {
            CatalogItem item=itemMapper.mapRow(rs,n);
            return new CatalogItem(item.itemId(),item.categoryId(),item.title(),item.description(),item.type(),item.externalUrl(),item.sourceName(),item.tags(),item.difficulty(),item.estimatedMinutes(),item.qualityScore(),item.price(),rs.getBoolean("effective_active"),item.createdAt(),item.updatedAt(),item.editorPick(),item.revision());
        }, ids.toArray());
    }

    @Override
    public Optional<CatalogItem> findById(long itemId) {
        return jdbcTemplate.query("""
                SELECT revision, item_id, category_id, title, description, item_type,
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
                SELECT revision, item_id, category_id, title, description, item_type,
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
                SELECT revision, item_id, category_id, title, description, item_type,
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
                SELECT item.revision, item.item_id, item.category_id, item.title, item.description, item.item_type,
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
                SELECT revision, item_id, category_id, title, description, item_type,
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
            case TIME_ASC -> "item.estimated_minutes IS NULL, item.estimated_minutes, item.item_id";
            case NEWEST, RELEVANCE -> "item.created_at DESC, item.item_id DESC";
            case MOST_POPULAR -> "COUNT(interaction.interaction_id) DESC, item.created_at DESC, item.item_id DESC";
            case TITLE_ASC -> "LOWER(item.title) ASC, item.item_id ASC";
        };
        List<Object> arguments = new ArrayList<>(filters.arguments());
        arguments.add(limit);
        arguments.add(offset);
        return jdbcTemplate.query("""
                SELECT item.revision, item.item_id, item.category_id, item.title, item.description, item.item_type,
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
                        + " WHERE item_tag.item_id = item.item_id AND LOWER(tag.name) LIKE ? ESCAPE '!')"
                        + " OR EXISTS (SELECT 1 FROM resource_provenance p WHERE p.item_id=item.item_id AND LOWER(CAST(p.source_topics AS CHAR)) LIKE ? ESCAPE '!'))");
            }
            sql.append(String.join(" OR ", termPredicates)).append(")");
            for (String term : terms) {
                String pattern = SearchTerms.likePattern(term);
                arguments.addAll(List.of(pattern, pattern, pattern, pattern, pattern, pattern));
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
            return com.guvi.recommendationsystem.dao.JdbcStatements.generated(connection, sql, statement -> {
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
            });
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
                "UPDATE items SET is_active = ?, revision=revision+1 WHERE item_id = ?",
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
        return jdbcTemplate.update("UPDATE items SET is_active = ?, revision=revision+1 WHERE item_id IN (" + placeholders + ")",
                arguments.toArray());
    }

    @Override
    public boolean existsByExternalUrl(String externalUrl) {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM items WHERE external_url = ?)", Boolean.class, externalUrl);
        return Boolean.TRUE.equals(exists);
    }
}
