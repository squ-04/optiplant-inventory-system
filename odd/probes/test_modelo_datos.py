"""Document consistency probes, not PostgreSQL integration tests or renderers."""

import re
import unittest
from decimal import Decimal as D, ROUND_HALF_UP, localcontext
from pathlib import Path


DOC = Path(__file__).resolve().parents[2] / "docs/06-modelo-base-datos/modelo-datos.md"


class DataModelDocumentTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.text = DOC.read_text(encoding="utf-8")
        cls.blocks = re.findall(r"```(mermaid|plantuml)\n(.*?)```", cls.text, re.S)

    @staticmethod
    def edges(block):
        return set(re.findall(
            r"^\s*(\w+)\s+([o|}{]+\.\.[o|}{]+)\s+(\w+)\s*:\s*(\w+)\s*$",
            block, re.M,
        ))

    def table(self, name):
        section = self.text.split("#### " + name + " —", 1)[1]
        return section.split("\n###", 1)[0]

    def test_inventory_and_diagram_pairs(self):
        dictionary = self.text.split("## 6. Diccionario", 1)[1].split("## 7. Integridad", 1)[0]
        tables = set(re.findall(r"^#### (\w+) —", dictionary, re.M))
        self.assertEqual(len(tables), 32)
        self.assertEqual(len(self.blocks), 12)
        integral = self.edges(self.blocks[0][1])
        self.assertEqual(len(integral), 72)
        for i in range(0, len(self.blocks), 2):
            with self.subTest(view=i // 2 + 1):
                (ml, mermaid), (pl, plantuml) = self.blocks[i:i + 2]
                self.assertEqual((ml, pl), ("mermaid", "plantuml"))
                self.assertTrue(mermaid.startswith("erDiagram\n"))
                self.assertTrue(plantuml.startswith("@startuml\n"))
                self.assertTrue(plantuml.strip().endswith("@enduml"))
                edges = self.edges(mermaid)
                self.assertEqual(edges, self.edges(plantuml))
                self.assertLessEqual(edges, integral)
                declared = set(re.findall(r"^\s*(\w+)\s*\{", mermaid, re.M))
                entities = declared | {x for e in edges for x in (e[0], e[2])}
                other = set(re.findall(r"^entity (\w+)", plantuml, re.M))
                self.assertEqual(entities, other)
                self.assertLessEqual(entities, tables)
                if i == 0:
                    self.assertEqual(entities, tables)

    def test_signed_derived_values_are_explicit(self):
        for table, fields in {
            "inventory_balance": ("inventory_value", "average_base_cost"),
            "inventory_movement": ("historical_base_cost",),
            "sales_line": ("historical_base_cost",),
            "logistics_dispatch_line": ("frozen_base_cost", "base_value"),
            "inventory_transit": ("pending_base_value",),
        }.items():
            for field in fields:
                with self.subTest(table=table, field=field):
                    row = re.search(r"^\| " + field + r" \|.*$", self.table(table), re.M)
                    self.assertIsNotNone(row)
                    self.assertIn("Firmado", row[0])
                    self.assertNotIn("≥0", row[0])

    def test_initial_password_obligation_is_persisted(self):
        account = self.table("identity_user_account")
        self.assertRegex(account, r"must_change_password \| BOOLEAN \| NN / TRUE")
        self.assertIn("persiste entre sesiones", account)
        self.assertIn("atómicamente", account)

    def test_exact_net_avoids_double_rounding(self):
        with localcontext() as ctx:
            ctx.prec = 100
            exact = D(".001") * D("5.000001") * (1 - D(".000020") / 100)
            stored = exact.quantize(D("1e-17"), rounding=ROUND_HALF_UP)
            self.assertEqual(exact, stored)
            self.assertEqual(stored.quantize(D(".01"), rounding=ROUND_HALF_UP), D(".00"))
            for table in ("purchases_line", "sales_line"):
                self.assertRegex(self.table(table), r"net_amount \| NUMERIC\(51,17\)")

    def test_ordinary_exit_signed_value_counterexample(self):
        with localcontext() as ctx:
            ctx.prec = 100
            value = D(1000000) + D(1000000) * D("1.000001")
            average = (value / D(2000000)).quantize(D(".000001"), rounding=ROUND_HALF_UP)
            outgoing = D("1999999.999")
            remainder = value - outgoing * average
            self.assertEqual(D(2000000) - outgoing, D(".001"))
            self.assertEqual(remainder, D("-.998999999"))
            last_base = -D(".001") * average
            difference = -remainder - last_base
            self.assertEqual(remainder + last_base + difference, 0)

    def test_historical_correction_and_approved_examples(self):
        with localcontext() as ctx:
            ctx.prec = 100
            self.assertEqual(D(10) - 9 + 10 - 2, D(9))
            self.assertEqual(D(10) * 100 - 9 * 100 + 10 - 2 * 100, D(-90))
            self.assertEqual(D(10) - 2 * D("3.333333"), D("3.333334"))
            self.assertEqual((D(100) / 6).quantize(D(".000001"), rounding=ROUND_HALF_UP), D("16.666667"))
            self.assertEqual(D(120) - 6 * 15 - 20 - 10, 0)
            self.assertEqual(D(10) * 9 - D(7) * 9, 27)


if __name__ == "__main__":
    unittest.main()
