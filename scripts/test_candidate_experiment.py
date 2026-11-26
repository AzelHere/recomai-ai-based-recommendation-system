"""Structural safety checks and exact comparisons of recorded MySQL integration cases."""
import gzip
import json
from decimal import Decimal as D
import unittest
from candidate_experiment import OUT, original_sql, variant


class QueryExperimentTest(unittest.TestCase):
    def test_preserves_bindings_order_and_limit(self):
        query = original_sql()
        changed = variant(query,"deferred")
        self.assertEqual(query.count("?"),changed.count("?"))
        chunk = query.split("WHERE item.is_active = TRUE AND category.is_active = TRUE",1)[1]
        self.assertIn(chunk,changed)
        self.assertIn("f.user_id <> (SELECT owner_id FROM rating_owner)",changed)
        self.assertLess(changed.index("LIMIT 1000"),changed.index("AVG(f.rating)"))
        self.assertIn("DESC, s.item_id",changed)

    def test_unknown_mode_fails_closed(self):
        with self.assertRaises(ValueError):
            variant(original_sql(),"invalid")

    def test_grouped_aggregation_preserves_shortlist_and_bindings(self):
        query = original_sql()
        changed = variant(query,"grouped")
        self.assertEqual(query.count("?"),changed.count("?"))
        chunk = query.split("WHERE item.is_active = TRUE AND category.is_active = TRUE",1)[1]
        self.assertIn(chunk,changed)
        self.assertEqual(changed.count("AVG(feedback.rating) AS average_rating"),2)
        self.assertIn("JOIN feedback ON feedback.item_id = s.item_id",changed)
        self.assertIn("feedback.user_id <> rating_owner.owner_id",changed)
        self.assertLess(changed.index("LIMIT 1000"),changed.index("shortlisted_item_ratings AS"))
        self.assertIn("DESC, s.item_id",changed)
        self.assertNotIn("(SELECT AVG(f.rating)",changed)


def preliminary_scores(rows):
    """Reconstruct MySQL's preliminary ORDER BY expression from its exact decimal fields."""
    scores = []
    for row in rows:
        fields = row.split("\t")
        def value(index):
            return D(0) if fields[index]=="NULL" else D(fields[index])
        score = (12*value(3)+24*value(4)+10*value(5)+16*value(10)
                 -20*value(11)+2*value(12)+5*value(13))
        scores.append((int(fields[0]),score))
    return scores


def compare(original, after):
    def picks(path):
        return json.loads((path/"picks.json").read_text())
    a,b = picks(original),picks(after)
    if a != b:
        raise AssertionError(f"Different ordered recommendations in {after.name}")
    for case in a:
        with gzip.open(original/(case+".tsv.gz"),"rt",encoding="utf8") as f:
            left = f.read().splitlines()
        with gzip.open(after/(case+".tsv.gz"),"rt",encoding="utf8") as f:
            right = f.read().splitlines()
        if left != right:
            raise AssertionError(f"Ordered candidates or projected values differ: {after.name}/{case}")
        scores = preliminary_scores(left)
        if scores != sorted(scores,key=lambda pair:(-pair[1],pair[0])):
            raise AssertionError("Unexpected candidate score or tie-break order")
        if scores != preliminary_scores(right):
            raise AssertionError("Different preliminary scores")
        if case=="empty" and (left or a[case]):
            raise AssertionError("Inactive catalog returned candidates")
        if case=="sparse-focused" and any(row.split("\t")[8]!="NULL" for row in left):
            raise AssertionError("Category ratings leaked into an unrated account")
        if case=="dense-adventurous":
            rejected = {i for i in range(1,151) if i%4 in (0,1)}
            if rejected.intersection(int(row.split("\t")[0]) for row in left):
                raise AssertionError("Rejected resource in candidate pool")
    return len(a)


if __name__=="__main__":
    import sys
    if sys.argv[1:]==["compare"]:
        for mode in ("deferred","grouped"):
            for path in sorted(OUT.glob(f"*-{mode}-*")):
                base=OUT/path.name.replace(f"-{mode}-","-original-")
                print(path.name,compare(base,path),"exact matches")
    else:
        unittest.main()
