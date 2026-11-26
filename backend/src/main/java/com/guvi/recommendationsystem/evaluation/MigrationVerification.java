package com.guvi.recommendationsystem.evaluation;

import org.flywaydb.core.Flyway;
import java.sql.*;
import java.util.*;

/** Operator CLI for a newly provisioned, isolated upgrade fixture; never resets or cleans. */
public final class MigrationVerification {
    public static void main(String[] args) throws Exception {
        if(args.length!=1 || !args[0].matches("recomai_upgrade_[a-f0-9]{16}"))throw new IllegalArgumentException("A dedicated recomai_upgrade_<16 hex> database is required.");
        String url="jdbc:mysql://mysql:3306/"+args[0]+"?serverTimezone=UTC",user=System.getenv("DB_USERNAME"),password=System.getenv("DB_PASSWORD");
        try(var connection=DriverManager.getConnection(url,user,password);var statement=connection.createStatement()) {
            try(var result=statement.executeQuery("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()")){result.next();if(result.getInt(1)!=0)throw new IllegalArgumentException("Upgrade fixture must be a new empty database; existing databases are never reset.");}
        }
        var previous=Flyway.configure().dataSource(url,user,password).locations("classpath:db/migration").target("13").cleanDisabled(true).load();
        previous.migrate();
        Map<String,Long> before;
        try(var connection=DriverManager.getConnection(url,user,password);var statement=connection.createStatement()) {
            statement.executeUpdate("INSERT INTO users(username,email,password_hash,display_name) VALUES('upgrade_fixture','upgrade@example.test','disabled','Isolated upgrade marker')");
            statement.executeUpdate("INSERT INTO interactions(user_id,item_id,interaction_type) SELECT user_id,(SELECT MIN(item_id) FROM items),'SAVE' FROM users WHERE username='upgrade_fixture'");
            statement.executeUpdate("INSERT INTO user_collections(user_id,name) SELECT user_id,'Preserved shelf' FROM users WHERE username='upgrade_fixture'");
            statement.executeUpdate("INSERT INTO user_collection_items(collection_id,item_id) SELECT collection_id,(SELECT MIN(item_id) FROM items) FROM user_collections WHERE name='Preserved shelf'");
            before=counts(statement);
        }
        var current=Flyway.configure().dataSource(url,user,password).locations("classpath:db/migration").cleanDisabled(true).load();
        var migration=current.migrate();current.validate();
        try(var connection=DriverManager.getConnection(url,user,password);var statement=connection.createStatement()) {
            Map<String,Long> after=counts(statement);if(!before.equals(after))throw new IllegalStateException("Upgrade changed existing fixture rows");
            try(var result=statement.executeQuery("SELECT COUNT(*) FROM items WHERE revision<>0")){result.next();if(result.getInt(1)!=0)throw new IllegalStateException("Invalid initial resource revision");}
            System.out.println("Upgrade V13 -> "+current.info().current().getVersion()+"; new migrations="+migration.migrationsExecuted+"; Flyway validation passed");
            System.out.println("Unchanged isolated fixture row counts: "+after);
            System.out.println("Existing SAVE event, collection membership and marker account preserved; no clean, reset or drop performed.");
        }
    }
    private static Map<String,Long> counts(Statement statement) throws SQLException {
        var result=new LinkedHashMap<String,Long>();
        for(String table:List.of("users","items","interactions","user_collections","user_collection_items"))try(var rows=statement.executeQuery("SELECT COUNT(*) FROM "+table)){rows.next();result.put(table,rows.getLong(1));}
        return result;
    }
}
