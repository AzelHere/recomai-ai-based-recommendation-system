package com.guvi.recommendationsystem.service;
import com.guvi.recommendationsystem.model.*;
import com.guvi.recommendationsystem.exception.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminWriteService {
    private final JdbcTemplate jdbc;private final ItemService items;private final AdminSettingsService settings;
    public AdminWriteService(JdbcTemplate jdbc,ItemService items,AdminSettingsService settings){this.jdbc=jdbc;this.items=items;this.settings=settings;}
    private void requireRevision(Long expected){if(expected==null || expected<0)throw new InvalidUserInputException("Supply the loaded revision in X-Expected-Revision. Refresh before editing.");}
    @Transactional public CatalogItem item(long id,ItemRequest body,Long expected){
        requireRevision(expected);
        var rows=jdbc.queryForList("SELECT revision FROM items WHERE item_id=? FOR UPDATE",Long.class,id);
        if(rows.isEmpty())throw new ItemNotFoundException("Item was not found.");
        if(!rows.getFirst().equals(expected))throw new UpdateConflictException("Resource changed since you opened it. Your draft is preserved; reload the resource before saving.");
        items.update(id,body);
        jdbc.update("UPDATE items SET revision=revision+1 WHERE item_id=?",id);
        return items.get(id);
    }
    @Transactional public AdminSystemSettings settings(AdminSettingsUpdateRequest body,long actor,Long expected){
        requireRevision(expected);
        long current=jdbc.queryForObject("SELECT revision FROM recommendation_settings_revision WHERE singleton_id=1 FOR UPDATE",Long.class);
        if(current!=expected)throw new UpdateConflictException("Configuration changed since it was loaded. Your draft is preserved; reload saved configuration before retrying.");
        settings.update(body,actor);
        jdbc.update("UPDATE recommendation_settings_revision SET revision=revision+1 WHERE singleton_id=1");
        return settings.getSettings();
    }
}
