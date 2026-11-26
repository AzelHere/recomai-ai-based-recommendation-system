package com.guvi.recommendationsystem.service;

import com.guvi.recommendationsystem.dao.JdbcItemDao;
import com.guvi.recommendationsystem.model.CatalogItem;
import com.guvi.recommendationsystem.model.CatalogPage;
import com.guvi.recommendationsystem.exception.InvalidUserInputException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class AdminCatalogService {
    private final JdbcTemplate jdbc;
    private final JdbcItemDao items;
    public AdminCatalogService(JdbcTemplate jdbc,JdbcItemDao items){this.jdbc=jdbc;this.items=items;}
    public CatalogPage<CatalogItem> search(String query,Long categoryId,String status,String sort,int page,int pageSize){
        return search(query,categoryId,status,sort,page,pageSize,null);
    }
    public CatalogPage<CatalogItem> search(String query,Long categoryId,String status,String sort,int page,int pageSize,com.guvi.recommendationsystem.model.ItemType type){
        if(query==null || query.length()>120 || categoryId!=null&&categoryId<=0
                || !Set.of("","active","inactive").contains(status) || !Set.of("title","updated").contains(sort)
                || page<1 || page>10000 || pageSize<1 || pageSize>100)
            throw new InvalidUserInputException("Invalid admin catalog filter or page bounds.");
        String where=" WHERE 1=1";var args=new ArrayList<Object>();
        if(!query.isBlank()){
            where+=" AND (LOCATE(LOWER(?),LOWER(CONCAT_WS(' ',item.title,item.source_name)))>0 OR EXISTS (SELECT 1 FROM item_interests link JOIN interests tag ON tag.interest_id=link.interest_id WHERE link.item_id=item.item_id AND LOCATE(LOWER(?),LOWER(tag.name))>0))";
            args.add(query.strip());args.add(query.strip());
        }
        if(categoryId!=null){where+=" AND item.category_id=?";args.add(categoryId);}
        if(type!=null){where+=" AND item.item_type=?";args.add(type.name());}
        if(!status.isEmpty()){where+=" AND item.is_active=?";args.add(status.equals("active"));}
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM items item"+where,Long.class,args.toArray());
        long pages=(total+pageSize-1)/pageSize;page=(int)Math.min(page,Math.max(1,pages));
        String order=sort.equals("updated")?"item.updated_at DESC,item.item_id DESC":"LOWER(item.title),item.item_id";
        args.add(pageSize);args.add((page-1)*pageSize);
        var ids=jdbc.queryForList("SELECT item.item_id FROM items item"+where+" ORDER BY "+order+" LIMIT ? OFFSET ?",Long.class,args.toArray());
        Map<Long,CatalogItem> metadata=new HashMap<>();items.findByIds(ids).forEach(i->metadata.put(i.itemId(),i));
        return new CatalogPage<>(ids.stream().map(metadata::get).toList(),page,pageSize,total,pages);
    }
    public List<Map<String,Object>> composition(){
        return jdbc.queryForList("SELECT category_id AS categoryId,COUNT(*) AS total,SUM(is_active) AS active FROM items GROUP BY category_id ORDER BY category_id");
    }
}
