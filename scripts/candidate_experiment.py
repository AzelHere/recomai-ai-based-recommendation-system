"""Compare complete JDBC rows and API drafts on fresh, deterministic benchmark fixtures."""
import argparse
import gzip
import json
from pathlib import Path
import shutil
import tempfile
import benchmark

ROOT = benchmark.ROOT
DAO = Path("backend/src/main/java/com/guvi/recommendationsystem/dao/JdbcRecommendationDao.java")
OUT = ROOT/"logs/candidate-experiment"


def original_sql():
    return (ROOT/DAO).read_text(encoding="utf8").split('String query = """',1)[1].split('"""',1)[0].strip()


def variant(query, mode):
    if mode in {"cap2000","cap10000"}:
        return query.replace("LIMIT 1000","LIMIT "+mode[3:])
    if mode not in {"deferred", "grouped"}:
        raise ValueError("Unknown experiment")
    # Keep the parameter in its JDBC position. A scalar AVG returns NULL on no match.
    start = query.index("item_ratings AS (")
    stop = query.index("item_interest_matches AS (",start)
    query = query[:start]+"rating_owner AS (SELECT ? AS owner_id),\n"+query[stop:]
    start = query.index("SELECT item.item_id, item.title, category.name AS category_name")
    query = query[:start]+", shortlist AS (\n"+query[start:]
    query = query.replace("item_ratings.average_rating AS item_average_rating,","")
    query = query.replace("LEFT JOIN item_ratings ON item_ratings.item_id = item.item_id","")
    order = query.split("ORDER BY (12 *",1)[1].split("LIMIT 1000",1)[0]
    outer_order = "ORDER BY (12 *"+order.replace("item.","s.")
    if mode == "grouped":
        return query+"""), shortlisted_item_ratings AS (
            SELECT feedback.item_id, AVG(feedback.rating) AS average_rating
            FROM shortlist s
            JOIN feedback ON feedback.item_id = s.item_id
            CROSS JOIN rating_owner
            WHERE feedback.user_id <> rating_owner.owner_id
            GROUP BY feedback.item_id
        )
        SELECT s.item_id, s.title, s.category_name, s.category_preference_weight,
               s.interest_match, s.related_interest_match, s.matched_topics, s.purpose_match,
               s.user_average_rating, item_ratings.average_rating AS item_average_rating,
               s.positive_interaction_signal, s.negative_interaction_signal, s.quality_score, s.is_editor_pick
        FROM shortlist s
        LEFT JOIN shortlisted_item_ratings item_ratings ON item_ratings.item_id = s.item_id
        """+outer_order
    return query+""")
        SELECT s.item_id, s.title, s.category_name, s.category_preference_weight,
               s.interest_match, s.related_interest_match, s.matched_topics, s.purpose_match,
               s.user_average_rating,
               (SELECT AVG(f.rating) FROM feedback f WHERE f.item_id=s.item_id
                AND f.user_id <> (SELECT owner_id FROM rating_owner)) AS item_average_rating,
               s.positive_interaction_signal, s.negative_interaction_signal, s.quality_score, s.is_editor_pick
        FROM shortlist s
        """+outer_order


def inspect(sql, post, clients, query, directory):
    directory.mkdir(parents=True,exist_ok=True)
    def bind(user):
        values = [user]*11 + [1,1.25,1,1.5] + [user]*4
        parts = query.split("?")
        if len(parts)!=len(values)+1:
            raise ValueError("Review JDBC parameter bindings")
        return parts[0]+''.join(str(v)+part for v,part in zip(values,parts[1:]))
    (directory/"plan.txt").write_text(sql("EXPLAIN ANALYZE "+bind(1)).replace("\\n","\n"),encoding="utf8")
    outcomes = {}
    def capture(name, user):
        # Both projections have identical column order, including nullable rating averages.
        rows = sql(bind(user))
        with gzip.open(directory/(name+".tsv.gz"),"wt",encoding="utf8") as f:
            f.write(rows)
        picks = post(clients[user-1],"users/me/recommendations?limit=10")
        outcomes[name] = [{k:v for k,v in p.items() if k not in
                           {"generatedAt","recommendationId","batchId"}} for p in picks]
    capture("balanced",1)
    capture("second-account",2)
    # The callback is only reachable on benchmark.run's guarded synthetic database.
    sql("DELETE FROM feedback WHERE user_id=1; DELETE FROM interactions WHERE user_id=1; UPDATE users SET exploration=0 WHERE user_id=1")
    capture("sparse-focused",1)
    sql("""INSERT INTO feedback(user_id,item_id,rating) SELECT 1,item_id,2 FROM items ORDER BY item_id LIMIT 100;
           INSERT INTO feedback(user_id,item_id,rating) VALUES(2,1,5) ON DUPLICATE KEY UPDATE rating=5;
           INSERT INTO interactions(user_id,item_id,interaction_type)
             SELECT 1,item_id,CASE MOD(item_id,4) WHEN 0 THEN 'HIDE' WHEN 1 THEN 'DISLIKE' ELSE 'LIKE' END
             FROM items ORDER BY item_id LIMIT 150;
           UPDATE users SET exploration=100 WHERE user_id=1""")
    capture("dense-adventurous",1)
    capture("other-after-feedback",2)
    # Deliberately break the query only after all timed work in this disposable fixture.
    from urllib.error import HTTPError
    sql("RENAME TABLE feedback TO experiment_unavailable_feedback")
    try:
        try:
            post(clients[0],"users/me/recommendations?limit=10")
        except HTTPError as error:
            if error.code!=500:
                raise
        else:
            raise AssertionError("Expected SQL failure to propagate")
    finally:
        sql("RENAME TABLE experiment_unavailable_feedback TO feedback")
    sql("UPDATE items SET is_active=FALSE")
    capture("empty",1)
    (directory/"picks.json").write_text(json.dumps(outcomes,sort_keys=True,indent=2),encoding="utf8")
    return str(directory)


def execute(mode, scenarios, trial):
    if trial < 1 or len(set(scenarios)) != len(scenarios):
        raise ValueError("Use a positive trial number and distinct workloads")
    for size in scenarios:
        if (OUT/f"{trial}-{mode}-{size}").exists():
            raise FileExistsError("Use a fresh trial number to preserve captured artifacts")
    original = original_sql()
    query = original if mode=="original" else variant(original,mode)
    with tempfile.TemporaryDirectory(prefix="recomai-query-") as tmp:
        copied = Path(tmp)
        for folder in ("backend","frontend"):
            shutil.copytree(ROOT/folder,copied/folder,ignore=shutil.ignore_patterns("target"))
        for file in ("Dockerfile","docker-compose.yml",".dockerignore"):
            shutil.copy2(ROOT/file,copied/file)
        if mode!="original":
            path = copied/DAO
            source = path.read_text(encoding="utf8")
            marker = "        List<RecommendationCandidate> candidates = jdbcTemplate.query(query, candidateMapper,"
            assert source.count(marker)==1
            source = source.replace(marker,'        if (System.getenv("RECOMAI_SQL_EXPERIMENT") != null) query = """\n'+query+'\n""";\n'+marker)
            path.write_text(source,encoding="utf8")
            with (copied/"Dockerfile").open("a") as f:
                f.write("\nENV RECOMAI_SQL_EXPERIMENT=true\n")
        benchmark.ROOT = copied
        for size in scenarios:
            directory = OUT/f"{trial}-{mode}-{size}"
            result = benchmark.run(size,40,5,2,lambda *args: inspect(*args,query,directory))
            (directory/"metrics.json").write_text(json.dumps(result,indent=2),encoding="utf8")
            print(directory.name,result["avg_ms"],flush=True)


if __name__=="__main__":
    parser=argparse.ArgumentParser()
    parser.add_argument("mode",choices=["original","deferred","grouped","cap2000","cap10000"])
    parser.add_argument("trial",type=int)
    parser.add_argument("scenarios",choices=benchmark.SIZES,nargs="+")
    args=parser.parse_args()
    execute(args.mode,args.scenarios,args.trial)

