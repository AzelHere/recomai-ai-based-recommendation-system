"""Isolated synthetic HTTP benchmark; run from the project root with Python 3.10+."""
import argparse
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
from http.cookiejar import CookieJar
import json
import math
import os
from pathlib import Path
import random
import re
import secrets
import statistics
import subprocess
import time
from urllib import request
import uuid

ROOT = Path(__file__).resolve().parents[1]
SIZES = {"small": (100, 200), "medium": (1000, 1000),
         "large": (5000, 5000), "stress": (10000, 10000)}
SEED = 8173


def config(iterations, warmup, workers):
    if not (1 <= workers <= 8 and workers <= iterations <= 1000 and 1 <= warmup <= 100):
        raise ValueError("Use 1–8 workers, workers–1000 iterations and 1–100 warmups")


def guard(project, database):
    if not re.fullmatch(r"recomai-bench-[0-9a-f]{12}", project) or database != project.replace("-", "_"):
        raise ValueError("Expected matching unique benchmark project and database")


def summarize(samples, elapsed):
    good = sorted(ms for ok, ms in samples if ok)
    def pct(p):
        return round(good[math.ceil(len(good)*p)-1], 2) if good else None
    return dict(avg_ms=round(statistics.mean(good), 2) if good else None,
                p50=pct(.5), p95=pct(.95), p99=pct(.99),
                requests_per_sec=round(len(good)/elapsed, 2),
                error_rate=(len(samples)-len(good))/len(samples))


def dataset(users, resources, clients, original, categories, interests):
    rng = random.Random(SEED)
    tables = {}
    def add(table, row):
        tables.setdefault(table, []).append(row)
    ids = list(original)
    for i in range(resources-len(original)):
        item = max(original)+1+i
        ids.append(item)
        cat = rng.choice(categories)
        add("items(item_id,category_id,title,description,item_type,quality_score)",
            (item, cat, f"Synthetic lesson {i}", f"Topic {cat} practice module {i%37}", "ARTICLE", rng.randint(40,99)/100))
        add("item_interests(item_id,interest_id)", (item,rng.choice(interests)))
    for user in range(1,users+1):
        if user > clients:
            add("users(user_id,username,email,password_hash,display_name)",
                (user,f"fixture{user}",f"u{user}@example.invalid","disabled","Synthetic user"))
        add("user_preferences(user_id,category_id)",(user,rng.choice(categories)))
        add("user_interests(user_id,interest_id)",(user,rng.choice(interests)))
        seen = rng.sample(ids,8)
        for item,kind in zip(seen,("LIKE","SAVE","CLICK","VIEW","COMPLETE","LIKE","DISLIKE","VIEW")):
            add("interactions(user_id,item_id,interaction_type)",(user,item,kind))
        add("feedback(user_id,item_id,rating)",(user,seen[0],rng.randint(3,5)))
        add("recommendation_batches(batch_id,user_id)",(user,user))
        add("recommendations(batch_id,user_id,item_id,score,reason)",(user,user,seen[1],10,"Synthetic history"))
    return tables


def run(scenario, iterations, warmup, workers, inspect_fixture=None):
    config(iterations,warmup,workers)
    users, resources = SIZES[scenario]
    project = "recomai-bench-" + uuid.uuid4().hex[:12]
    database = project.replace("-","_")
    guard(project,database)
    env = {**os.environ, "MYSQL_DATABASE":database,"DB_USERNAME":"benchmark",
           "DB_PASSWORD":secrets.token_hex(24),"MYSQL_ROOT_PASSWORD":secrets.token_hex(24),
           "APP_HOST_PORT":"0","MYSQL_HOST_PORT":"0","COMPOSE_PROFILES":"",
           "SESSION_COOKIE_SECURE":"false"}
    def docker(*args, stdin=None):
        return subprocess.run(["docker",*args],cwd=ROOT,env=env,input=stdin,text=True,
                              capture_output=True,check=True,timeout=600).stdout.strip()
    def compose(*args,stdin=None):
        return docker("compose","-f","docker-compose.yml","-p",project,*args,stdin=stdin)
    def sql(statement):
        return compose("exec","-T","mysql","sh","-c",
                       'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -NB -uroot "$MYSQL_DATABASE"',stdin=statement)
    def ids(table,key):
        return list(map(int,sql(f"SELECT {key} FROM {table} ORDER BY {key}").split()))
    def digests():
        rows = sql("SELECT DIGEST,COUNT_STAR,SUM_TIMER_WAIT,SUM_ROWS_EXAMINED,LEFT(DIGEST_TEXT,145) FROM performance_schema.events_statements_summary_by_digest WHERE SCHEMA_NAME=DATABASE() AND DIGEST IS NOT NULL")
        return {k:(int(n),int(t),int(r),q) for k,n,t,r,q in (row.split("\t",4) for row in rows.splitlines())}
    try:
        compose("up","-d","--build","--wait","backend")
        if sql("SELECT DATABASE(),(SELECT COUNT(*) FROM users)") != database+"\t0":
            raise ValueError("Expected empty isolated user table")
        port = int(compose("port","backend","8080").rsplit(":",1)[1])
        base = f"http://127.0.0.1:{port}/api/"
        def post(client,path,body=None):
            req = request.Request(base+path,method="POST",data=json.dumps(body).encode() if body else None,
                                  headers={"Content-Type":"application/json"})
            with client.open(req,timeout=90) as res:
                return json.load(res)
        clients = []
        for i in range(workers):
            client = request.build_opener(request.ProxyHandler({}),request.HTTPCookieProcessor(CookieJar()))
            post(client,"auth/register",dict(username=f"worker{i}",email=f"worker{i}@example.invalid",
                 password=secrets.token_hex(16),displayName="Synthetic worker"))
            clients.append(client)
        original = ids("items","item_id")
        if len(original)!=200: raise ValueError("Expected clean 200-item seed")
        tables = dataset(users,resources,workers,original,ids("categories","category_id"),ids("interests","interest_id"))
        statements = ["START TRANSACTION;"]
        for table in sorted(tables, key=lambda name: 0 if name.startswith('users(') else 1):
            rows = tables[table]
            for offset in range(0,len(rows),500):
                # All fields originate from the fixed generator or numeric fixture keys.
                values = ",".join("("+",".join(repr(v) for v in row)+")" for row in rows[offset:offset+500])
                statements.append(f"INSERT INTO {table} VALUES {values};")
        sql("\n".join(statements+["COMMIT;"]))
        def measure(i):
            start = time.perf_counter()
            try:
                picks = post(clients[i],"users/me/recommendations?limit=10")
                ok = len(picks)==10 and len({p["itemId"] for p in picks})==10
            except (OSError,ValueError,KeyError):
                ok = False
            return ok,(time.perf_counter()-start)*1000
        for i in range(warmup):
            if not measure(i%workers)[0]: raise RuntimeError("Warm-up request failed")
        before = digests()
        start = time.perf_counter()
        with ThreadPoolExecutor(workers) as pool:
            batches = list(pool.map(lambda i:[measure(i) for _ in range(i,iterations,workers)],range(workers)))
        elapsed = time.perf_counter()-start
        delta = []
        for key,(n,t,r,q) in digests().items():
            old = before.get(key,(0,0,0,""))
            if n>old[0] and "performance_schema" not in q:
                total_ms = (t-old[1])/1e9
                delta.append(dict(sql=q,calls=n-old[0],total_ms=round(total_ms,2),
                                  avg_ms=round(total_ms/(n-old[0]),3),rows_examined=r-old[2]))
        delta.sort(key=lambda row:-row["total_ms"])
        # Outside the timed interval: compare all eligible rows with the actual preliminary top 1000.
        dao = ROOT/"backend/src/main/java/com/guvi/recommendationsystem/dao/JdbcRecommendationDao.java"
        query = dao.read_text(encoding="utf-8").split('String query = """',1)[1].split('"""',1)[0]
        params = [1]*11 + [1,1.25,1,1.5] + [1]*4
        if query.count("?") != len(params) or "LIMIT 1000" not in query:
            raise RuntimeError("Update the diagnostic bindings for the current candidate SQL")
        for value in params:
            query = query.replace("?",str(value),1)
        uncapped = query.replace("LIMIT 1000","")
        probe = sql("SELECT COUNT(*), COALESCE(SUM(interest_match > 0 OR category_preference_weight > 0),0) FROM (" + uncapped + " LIMIT 18446744073709551615 OFFSET 1000) omitted")
        containers = compose("ps","-q").splitlines()
        memory = docker("stats","--no-stream","--format","{{.Name}} {{.MemUsage}}",*containers)
        inspection = inspect_fixture(sql, post, clients) if inspect_fixture else None
        return dict(inspection=inspection,scenario=scenario,source="SYNTHETIC",timestamp=datetime.now(timezone.utc).isoformat(),
                    project=project,users=users,resources=resources,seed=SEED,iterations=iterations,
                    warmup=warmup,concurrency=workers,**summarize(sum(batches,[]),elapsed),
                    cap_probe=probe,database_statements=delta[:12],memory_after=memory,
                    java=compose("exec","-T","backend","java","--version"),
                    db=sql("SELECT VERSION(), @@innodb_buffer_pool_size, @@max_connections"),
                    hardware=docker("info","--format","{{.Architecture}} {{.NCPU}} CPUs {{.MemTotal}} bytes"),
                    flyway=sql("SELECT COUNT(*),SUM(success) FROM flyway_schema_history"),
                    popularity_plan=sql("EXPLAIN SELECT item_id,COUNT(DISTINCT user_id) FROM interactions WHERE user_id<>1 AND occurred_at>=CURRENT_TIMESTAMP-INTERVAL 90 DAY AND interaction_type IN ('LIKE','SAVE','PURCHASE','SHARE','MORE_LIKE') GROUP BY item_id"))
    finally:
        compose("down")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("scenarios",choices=SIZES,nargs="+")
    parser.add_argument("--iterations",type=int,default=40)
    parser.add_argument("--warmup",type=int,default=5)
    parser.add_argument("--workers",type=int,default=2)
    args = parser.parse_args()
    for name in args.scenarios:
        result = run(name,args.iterations,args.warmup,args.workers)
        folder = ROOT/"backend"/"target"/"benchmarks"
        folder.mkdir(parents=True,exist_ok=True)
        path = folder/(result["project"]+".json")
        path.write_text(json.dumps(result,indent=2),encoding="utf-8")
        print(name,result["avg_ms"],result["error_rate"],path,flush=True)

