import unittest
from benchmark import config, guard, dataset, summarize


class BenchmarkTest(unittest.TestCase):
    def test_reproducible_relations_and_counts(self):
        args = (100,200,2,list(range(1,151)),[1,2],[1,2,3])
        tables = dataset(*args)
        self.assertEqual(tables,dataset(*args))
        self.assertEqual(len(tables["interactions(user_id,item_id,interaction_type)"]),800)
        self.assertEqual(len(next(v for k,v in tables.items() if k.startswith("items("))),50)

    def test_isolation(self):
        project = "recomai-bench-123456789abc"
        guard(project,project.replace("-","_"))
        for name,db in [(project,"ai_recommendation_system"),("development","development")]:
            with self.assertRaises(ValueError):
                guard(name,db)

    def test_configuration(self):
        for values in [(0,5,1),(10,0,1),(10,5,9),(2,5,3)]:
            with self.assertRaises(ValueError):
                config(*values)

    def test_failure_and_nearest_rank(self):
        result = summarize([(True,10),(True,20),(False,500)],2)
        self.assertEqual((result["avg_ms"],result["p50"],result["p99"]),(15,10,20))
        self.assertEqual(result["requests_per_sec"],1)
        self.assertEqual(result["error_rate"],1/3)
        self.assertIsNone(summarize([(False,1)],1)["p95"])


if __name__ == "__main__":
    unittest.main()
