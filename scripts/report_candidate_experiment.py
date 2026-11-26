"""Render measured trials as Markdown. Times are milliseconds; memory is backend/MySQL."""
import gzip
import json
from candidate_experiment import OUT
from test_candidate_experiment import compare


def read(path):
    return json.loads((path/"metrics.json").read_text())


def row(j):
    q = next(row for row in j["database_statements"] if "user_category_ratings" in row["sql"])
    memory = "/".join(line.split()[1] for line in j["memory_after"].splitlines())
    return [j["avg_ms"], j["p50"], j["p95"], j["p99"],q["avg_ms"],
            round(q["rows_examined"]/q["calls"]),memory,j["error_rate"]]


def table():
    print("| Workload | Trial | Query | Mean | P50 | P95 | P99 | Candidate SQL | Rows/call | Container memory | Error rate |")
    print("|---|---|---|---:|---:|---:|---:|---:|---:|---|---:|")
    for size in ("small","medium","large","stress"):
        for path in sorted(OUT.glob("*-"+size)):
            if not (path/"metrics.json").exists(): continue
            trial,mode,_ = path.name.split("-")
            j = read(path)
            print("| "+" | ".join(map(str,[size,trial,mode,*row(j)]))+" |")
    for mode in ("deferred","grouped"):
        for path in sorted(OUT.glob(f"*-{mode}-*")):
            base=OUT/path.name.replace(f"-{mode}-","-original-")
            print(f"\n{path.name}: {compare(base,path)} exact candidate and recommendation comparisons.")
    for path in sorted(OUT.glob("*-cap*-*")):
        base=OUT/("1-original-"+path.name.split("-")[-1])
        picks=json.loads((path/"picks.json").read_text())
        for case in ("balanced","second-account","sparse-focused","dense-adventurous","other-after-feedback"):
            with gzip.open(base/(case+".tsv.gz"),"rt",encoding="utf8") as f:
                original_ids={int(line.split("\t")[0]) for line in f}
            gained=[r["itemId"] for r in picks[case] if r["itemId"] not in original_ids]
            print(f"\n{path.name}, {case}: {len(gained)} expanded top-ten picks outside the original shortlist ({gained}).")


if __name__=="__main__":
    table()

