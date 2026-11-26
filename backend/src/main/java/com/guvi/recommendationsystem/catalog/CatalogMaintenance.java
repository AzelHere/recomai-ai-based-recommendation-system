package com.guvi.recommendationsystem.catalog;

import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.time.Instant;

/** Bounded maintenance checks availability only: never deactivates, deletes, or overwrites provider/manual metadata. */
@Service
@EnableScheduling
public class CatalogMaintenance {
    private final JdbcTemplate jdbc;
    private final SourceHttp http=new SourceHttp();
    private final AtomicBoolean running=new AtomicBoolean();
    private volatile Map<String,Object> last=Map.of("state","NOT_RUN");
    @Value("${catalog.maintenance.enabled:false}") private boolean enabled;
    public CatalogMaintenance(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public Map<String,Object> status(){return Map.of("running",running.get(),"scheduledEnabled",enabled,"last",last);}
    public Map<String,Object> trigger(){
        if(!running.compareAndSet(false,true))return Map.of("state","ALREADY_RUNNING");
        Thread.startVirtualThread(()->{try{check();}finally{running.set(false);}});
        return Map.of("state","STARTED","limit",10);
    }
    @Scheduled(fixedDelayString="${catalog.maintenance.delay-ms:86400000}",initialDelayString="${catalog.maintenance.initial-delay-ms:86400000}")
    public void scheduled(){if(enabled)trigger();}
    private void check(){
        int checked=0;String lock="recomai_catalog_url_maintenance";
        // MySQL advisory lock is connection-scoped: hold one physical connection for the entire job.
        try(var connection=Objects.requireNonNull(jdbc.getDataSource()).getConnection()){
            try(var s=connection.prepareStatement("SELECT GET_LOCK(?,0)")){s.setString(1,lock);try(var r=s.executeQuery()){r.next();if(r.getInt(1)!=1){last=Map.of("state","ANOTHER_INSTANCE_RUNNING");return;}}}
            try {
                long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.MINUTES.toNanos(3);
                var rows=jdbc.queryForList("SELECT item_id,canonical_url,source_id FROM resource_provenance WHERE url_checked_at IS NULL OR url_checked_at<DATE_SUB(CURRENT_TIMESTAMP,INTERVAL 7 DAY) ORDER BY url_checked_at,item_id LIMIT 10");
                for(var row:rows){if(System.nanoTime()>deadline)break;String url=(String)row.get("canonical_url"),state="TEMPORARILY_UNAVAILABLE",destination=url;
                    try{var result=http.request(url,true,0);state=urlStatus(result.status(),!url.equals(result.destination().toString()));destination=result.destination().toString();}catch(IllegalArgumentException ex){state="UNSAFE_DESTINATION";}catch(Exception ex){/* Network failures do not mark a genuine resource broken. */}
                    jdbc.update("UPDATE resource_provenance SET url_status=?,url_checked_at=CURRENT_TIMESTAMP,final_url=? WHERE item_id=?",state,destination,row.get("item_id"));
                    jdbc.update("UPDATE catalog_sources SET last_check_at=CURRENT_TIMESTAMP WHERE source_id=?",row.get("source_id"));checked++;
                }
                last=Map.of("state","COMPLETED","checked",checked,"finishedAt",Instant.now().toString());
            }finally{try(var s=connection.prepareStatement("SELECT RELEASE_LOCK(?)")){s.setString(1,lock);s.execute();}}
        }catch(Exception ex){last=Map.of("state","FAILED","checked",checked,"reason",ex.getClass().getSimpleName(),"finishedAt",Instant.now().toString());}
    }
    public static String urlStatus(int status,boolean redirected){return status>=200&&status<300?(redirected?"REDIRECTED":"ACCESSIBLE"):status==404||status==410?"NOT_FOUND":status==401||status==403||status==429||status==405?"ACCESS_RESTRICTED":"TEMPORARILY_UNAVAILABLE";}
}
