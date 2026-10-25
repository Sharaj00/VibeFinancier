import csv
import io
import tempfile
import unittest
from decimal import Decimal
from pathlib import Path
from procoins import stage

class ProCoinsTests(unittest.TestCase):
    def source(self, transactions):
        stream = io.StringIO(newline="")
        writer = csv.writer(stream)
        writer.writerow(["category"])
        writer.writerow(["cash","purse","b27","0.001","RUB","1","Cash","-13751","0","0","0","1","cash","0"])
        writer.writerow(["food","expense","e19","0","RUB","1","Food","-13751","0","0","0","1","","0"])
        writer.writerow(["subcategory"])
        writer.writerow(["transaction"])
        for row in transactions:
            writer.writerow(row)
        return stream.getvalue().encode("utf-8")

    def transaction(self, identity="tx", category="food"):
        return [identity,"purse",category,"cash","","0.1","0.1","RUB",'Quoted "note", with comma',"1","0","","","2026-01-01","12:00:00","","0","0"]

    def convert(self, data):
        with tempfile.TemporaryDirectory() as directory:
            p = Path(directory) / "source.csv"
            p.write_bytes(data)
            result = stage(p)
            self.assertEqual(data,p.read_bytes())
            return result

    def test_exact_opening_quotes_and_date(self):
        result = self.convert(self.source([self.transaction()]))
        self.assertEqual(1,result["report"]["transactions"])
        self.assertEqual("-0.099",result["report"]["accounts"][0]["projected_balance"])
        tx = next(p for p in result["entities"] if p["id"] == "pc.transaction.tx")
        self.assertEqual('Quoted "note", with comma',tx["note"])
        self.assertEqual("2026-01-01T12:00:00",tx["time"])

    def test_missing_reference_is_reported_not_invented(self):
        result = self.convert(self.source([self.transaction(category="missing")]))
        self.assertEqual(1,result["report"]["unresolved_records"])
        self.assertFalse(any(p["id"] == "pc.category.missing" for p in result["entities"]))
        self.assertEqual("missing",result["report"]["issues"][0]["missing_source_id"])

    def test_duplicate_and_bad_width_are_quarantined(self):
        result = self.convert(self.source([self.transaction(),self.transaction(),["bad","purse"]]))
        self.assertEqual(1,result["report"]["transactions"])
        self.assertEqual(2,len(result["report"]["errors"]))
        self.assertEqual(3,len(result["raw_sections"]["transaction"]))

if __name__ == "__main__":
    unittest.main()
