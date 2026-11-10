"""Tests for the normaliser and merge logic. Run: python3 -m unittest discover pipeline"""

import datetime as dt
import json
import tempfile
import unittest
from pathlib import Path

import fetch_mandi as fm


def record(**overrides):
    base = {
        "state": "Uttar Pradesh",
        "district": "Agra",
        "market": "Achnera",
        "commodity": "Wheat",
        "variety": "Dara",
        "grade": "FAQ",
        "arrival_date": "10/11/2026",
        "min_price": "2400",
        "max_price": "2500",
        "modal_price": "2450",
    }
    base.update(overrides)
    return base


NOW = dt.datetime(2026, 11, 10, 6, 0, tzinfo=dt.UTC)


class NormaliseTest(unittest.TestCase):
    def test_upstream_record(self):
        row = fm.normalise_record(record())
        self.assertEqual(row["date"], "2026-11-10")
        self.assertEqual((row["min"], row["max"], row["modal"]), (2400, 2500, 2450))
        self.assertEqual(row["state"], "Uttar Pradesh")

    def test_capitalised_and_x0020_field_names(self):
        row = fm.normalise_record(
            {
                "State": "Kerala",
                "District": "Ernakulam",
                "Market": "Aluva",
                "Commodity": "Banana",
                "Variety": "Nendra",
                "Grade": "FAQ",
                "Arrival_Date": "09/11/2026",
                "Min_x0020_Price": 3000,
                "Max_x0020_Price": 3600.0,
                "Modal_x0020_Price": "3250.50",
            }
        )
        self.assertEqual(row["modal"], 3250.5)
        self.assertEqual(row["max"], 3600)
        self.assertIsInstance(row["max"], int)

    def test_whitespace_is_collapsed(self):
        row = fm.normalise_record(
            record(market="  Agra  (F&V) ", state="Uttar  Pradesh")
        )
        self.assertEqual(row["market"], "Agra (F&V)")
        self.assertEqual(row["state"], "Uttar Pradesh")

    def test_unusable_records_are_dropped(self):
        self.assertIsNone(fm.normalise_record(record(modal_price="0")))
        self.assertIsNone(fm.normalise_record(record(modal_price="NR")))
        self.assertIsNone(fm.normalise_record(record(arrival_date="soon")))
        self.assertIsNone(fm.normalise_record(record(market="")))

    def test_missing_min_max_fall_back_to_modal(self):
        row = fm.normalise_record(record(min_price="", max_price=None))
        self.assertEqual((row["min"], row["max"]), (2450, 2450))

    def test_parse_price(self):
        self.assertEqual(fm.parse_price("1,250"), 1250)
        self.assertIsNone(fm.parse_price("nan"))
        self.assertIsNone(fm.parse_price(True))
        self.assertIsNone(fm.parse_price(-5))

    def test_slugify(self):
        self.assertEqual(fm.slugify("Andaman and Nicobar"), "andaman-and-nicobar")
        self.assertEqual(fm.slugify("NCT of Delhi"), "nct-of-delhi")


class PipelineTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.out = Path(self.tmp.name)

    def tearDown(self):
        self.tmp.cleanup()

    def load(self, relative):
        return json.loads((self.out / relative).read_text(encoding="utf-8"))

    def test_first_run_shapes(self):
        fm.run(self.out, [record(), record(market="Agra", modal_price="2500")], NOW)
        index = self.load("index.json")
        self.assertEqual(index["v"], 1)
        self.assertEqual(index["states"][0]["slug"], "uttar-pradesh")
        self.assertEqual(index["states"][0]["markets"], 2)
        self.assertEqual(index["states"][0]["latest"], "2026-11-10")
        rows = self.load("states/uttar-pradesh.json")["rows"]
        self.assertEqual(
            rows[0],
            [
                "Agra",
                "Achnera",
                "Wheat",
                "Dara",
                "FAQ",
                "2026-11-10",
                2400,
                2500,
                2450,
                None,
                None,
            ],
        )
        history = self.load("history/uttar-pradesh.json")
        self.assertEqual(history["days"], ["2026-11-10"])
        self.assertEqual(history["series"][0][5], [2450])

    def test_second_day_sets_previous_and_keeps_unreported_series(self):
        fm.run(
            self.out,
            [
                record(arrival_date="09/11/2026", modal_price="2400"),
                record(market="Agra", arrival_date="09/11/2026"),
            ],
            NOW,
        )
        fm.run(self.out, [record(modal_price="2450")], NOW)
        rows = self.load("states/uttar-pradesh.json")["rows"]
        by_market = {r[1]: r for r in rows}
        self.assertEqual(
            by_market["Achnera"][5:],
            ["2026-11-10", 2400, 2500, 2450, "2026-11-09", 2400],
        )
        # Agra did not report today but still shows yesterday's price.
        self.assertEqual(by_market["Agra"][5], "2026-11-09")
        history = self.load("history/uttar-pradesh.json")
        self.assertEqual(history["days"], ["2026-11-09", "2026-11-10"])
        series = {s[1]: s[5] for s in history["series"]}
        self.assertEqual(series["Achnera"], [2400, 2450])
        self.assertEqual(series["Agra"], [2450, None])

    def test_old_data_is_pruned(self):
        fm.run(self.out, [record(arrival_date="01/10/2026", market="Old")], NOW)
        fm.run(self.out, [record()], NOW)
        rows = self.load("states/uttar-pradesh.json")["rows"]
        self.assertEqual([r[1] for r in rows], ["Achnera"])
        history = self.load("history/uttar-pradesh.json")
        self.assertEqual(history["days"], ["2026-11-10"])

    def test_future_dates_are_ignored(self):
        fm.run(
            self.out, [record(), record(market="Typo", arrival_date="10/11/2027")], NOW
        )
        rows = self.load("states/uttar-pradesh.json")["rows"]
        self.assertEqual([r[1] for r in rows], ["Achnera"])

    def test_state_files_follow_the_index(self):
        fm.run(self.out, [record(arrival_date="01/10/2026", state="Goa")], NOW)
        self.assertTrue((self.out / "states/goa.json").exists())
        fm.run(self.out, [record()], NOW)
        self.assertFalse((self.out / "states/goa.json").exists())


if __name__ == "__main__":
    unittest.main()
