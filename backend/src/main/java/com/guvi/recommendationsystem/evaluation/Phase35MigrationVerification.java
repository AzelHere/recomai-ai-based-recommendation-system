package com.guvi.recommendationsystem.evaluation;

import org.flywaydb.core.Flyway;
import java.sql.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import com.guvi.recommendationsystem.catalog.RealDataAcquisition;

/** Isolated V15 upgrade with row-content, relationship and configuration preservation, never clean/drop. */
public final class Phase35MigrationVerification {
    public static void main(String[] args)throws Exception {
        if((args.length!=1&&args.length!=2)||!args[0].matches("recomai_upgrade_[a-f0-9]{16}"))throw new IllegalArgumentException("A newly provisioned upgrade fixture is required.");
        String url="jdbc:mysql://mysql:3306/"+args[0]+"?serverTimezone=UTC",user=System.getenv("DB_USERNAME"),password=System.getenv("DB_PASSWORD");
        try(var c=DriverManager.getConnection(url,user,password);var s=c.createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()")){r.next();if(r.getInt(1)!=0)throw new IllegalArgumentException("Refusing nonempty database; no reset is permitted.");}
        String baseline=args.length==2?args[1]:"15";if(!Set.of("15","16").contains(baseline))throw new IllegalArgumentException("Reviewed baseline required.");
        Set<Long> mutableItems=new HashSet<>();
        Flyway.configure().dataSource(url,user,password).target(baseline).cleanDisabled(true).load().migrate();
        Map<String,String> before;
        Map<Long,Map<String,String>> typeCorrections=new HashMap<>();
        try(var c=DriverManager.getConnection(url,user,password);var s=c.createStatement()){
            s.executeUpdate("INSERT INTO users(username,email,password_hash,display_name) VALUES('phase35_upgrade','phase35_upgrade@example.test','disabled','Upgrade fixture')");
            s.execute("SET @u=LAST_INSERT_ID()");s.execute("SET @i=(SELECT MIN(item_id) FROM items)");s.execute("SET @c=(SELECT category_id FROM items WHERE item_id=@i)");
            s.executeUpdate("INSERT INTO user_preferences(user_id,category_id,preference_weight) VALUES(@u,@c,0.8)");
            s.executeUpdate("INSERT INTO user_interests(user_id,interest_id) SELECT @u,MIN(interest_id) FROM interests");
            s.executeUpdate("INSERT INTO interactions(user_id,item_id,interaction_type) VALUES(@u,@i,'SAVE'),(@u,@i,'LIKE'),(@u,@i,'COMPLETE')");
            s.executeUpdate("INSERT INTO feedback(user_id,item_id,rating) VALUES(@u,@i,4)");
            s.executeUpdate("INSERT INTO user_collections(user_id,name) VALUES(@u,'Preserved Phase 3 shelf')");s.execute("SET @s=LAST_INSERT_ID()");
            s.executeUpdate("INSERT INTO user_collection_items(collection_id,item_id) VALUES(@s,@i)");
            s.executeUpdate("INSERT INTO learning_journeys(user_id,title,goal,category_id,preference_snapshot,request_id,request_hash) VALUES(@u,'Preserved Phase 3 plan','LEARN',@c,JSON_OBJECT('fixture',true),'upgrade',REPEAT('a',64))");s.execute("SET @j=LAST_INSERT_ID()");
            s.executeUpdate("INSERT INTO learning_journey_steps(journey_id,item_id,position,title_snapshot,rationale) SELECT @j,@i,1,title,'Original recorded rationale' FROM items WHERE item_id=@i");
            Set<Long> expectedTypeCorrections=new HashSet<>();
            try(var r=s.executeQuery("SELECT item_id FROM items WHERE title IN ('The Lean Startup: Build-Measure-Learn','Deep Work and Focus') AND item_type='ARTICLE' AND revision=0")){while(r.next())expectedTypeCorrections.add(r.getLong(1));}
            if(expectedTypeCorrections.size()!=2)throw new IllegalStateException("Expected both original landing-page seeds before fixture edits");
            if(baseline.equals("16")){
                long first,second;try(var r=s.executeQuery("SELECT item_id FROM items ORDER BY item_id LIMIT 2")){r.next();first=r.getLong(1);r.next();second=r.getLong(1);}
                mutableItems.add(first);mutableItems.add(second);
                s.executeUpdate("UPDATE items SET title='Google Maps Trip Planning',external_url='https://support.google.com/maps/answer/6230175',revision=0 WHERE item_id="+first);
                s.executeUpdate("UPDATE items SET title='Royal Photographic Society Learning',external_url='https://rps.org/learning/',revision=0 WHERE item_id="+second);
                s.executeUpdate("INSERT INTO items(category_id,title,item_type,external_url) SELECT MIN(category_id),'Royal Photographic Society Learning','GUIDE','https://rps.org/manually-reviewed-link/' FROM categories");
            }
            try(var r=s.executeQuery("SELECT item_id,item_type,revision,updated_at FROM items WHERE title IN ('The Lean Startup: Build-Measure-Learn','Deep Work and Focus') AND item_type='ARTICLE' AND revision=0")){while(r.next())typeCorrections.put(r.getLong("item_id"),Map.of("item_type",r.getString("item_type"),"revision",r.getString("revision"),"updated_at",r.getString("updated_at")));}
            expectedTypeCorrections.removeAll(mutableItems);
            if(!typeCorrections.keySet().equals(expectedTypeCorrections))throw new IllegalStateException("Reviewed landing-page fixtures differ from the exact unedited seeds");
            before=snapshot(s,mutableItems,typeCorrections);
        }
        var flyway=Flyway.configure().dataSource(url,user,password).cleanDisabled(true).load();var migrated=flyway.migrate();flyway.validate();
        try(var c=DriverManager.getConnection(url,user,password);var s=c.createStatement()){
            for(long id:typeCorrections.keySet())try(var r=s.executeQuery("SELECT item_type,revision FROM items WHERE item_id="+id)){if(!r.next()||!r.getString(1).equals("RESOURCE")||r.getInt(2)!=1)throw new IllegalStateException("Reviewed type correction failed: "+id);}
            var after=snapshot(s,mutableItems,typeCorrections);
            if(baseline.equals("16")){
                try(var r=s.executeQuery("SELECT COUNT(*) FROM items WHERE (title='Google Maps Trip Planning' AND external_url='https://support.google.com/maps/answer/144339' AND revision=1) OR (title='Royal Photographic Society Learning' AND external_url='https://rps.org/qualifications/' AND revision=1)")){r.next();if(r.getInt(1)!=2)throw new IllegalStateException("Expected two guarded URL corrections");}
                try(var r=s.executeQuery("SELECT COUNT(*) FROM items WHERE external_url='https://rps.org/manually-reviewed-link/'")){r.next();if(r.getInt(1)!=1)throw new IllegalStateException("Manual URL changed");}
                System.out.println("Two reviewed links corrected; manually edited URL preserved; resource IDs, other metadata and owner relationships preserved.");
            }if(!before.equals(after))throw new IllegalStateException("Existing content or relationships changed during upgrade");
            System.out.println("Upgrade V"+baseline+" -> "+flyway.info().current().getVersion()+"; new migrations="+migrated.migrationsExecuted+"; Flyway validation passed.");
            after.forEach((table,fingerprint)->System.out.println(table+": unchanged count/content fingerprint "+fingerprint));
            System.out.println("Users, preferences, saved/feedback/activity/completion events, collection membership, journey snapshots and admin configuration preserved. No clean, reset, database drop or volume deletion.");
        }
    }
    private static Map<String,String> snapshot(Statement s,Set<Long> mutableItems,Map<Long,Map<String,String>> typeCorrections)throws Exception {
        var result=new LinkedHashMap<String,String>();
        for(String table:List.of("users","items","user_preferences","user_interests","interactions","feedback","user_collections","user_collection_items","learning_journeys","learning_journey_steps","recommendation_settings_revision","recommendation_system_settings")){
            var rows=new ArrayList<String>();try(var r=s.executeQuery("SELECT * FROM "+table)){int columns=r.getMetaData().getColumnCount();while(r.next()){var row=new StringBuilder();for(int i=1;i<=columns;i++){if(table.equals("items")&&mutableItems.contains(r.getLong("item_id"))&&Set.of("external_url","revision","updated_at").contains(r.getMetaData().getColumnName(i)))continue;String value=r.getString(i);if(table.equals("items")&&typeCorrections.containsKey(r.getLong("item_id")))value=typeCorrections.get(r.getLong("item_id")).getOrDefault(r.getMetaData().getColumnName(i),value);row.append(value==null?-1:value.length()).append(':').append(value).append('|');}rows.add(row.toString());}}
            Collections.sort(rows);result.put(table,rows.size()+" / "+RealDataAcquisition.sha(String.join("\n",rows).getBytes(StandardCharsets.UTF_8)));
        }
        return result;
    }
}
