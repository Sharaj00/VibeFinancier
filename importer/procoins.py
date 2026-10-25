"""Lossless, conservative ProCoins 1.13.7 CSV staging importer (standard library only)."""
import argparse
import csv
import hashlib
import io
import json
from collections import Counter
from decimal import Decimal
from pathlib import Path

FIELDS = {
    "category": "id type icon value currency status name color color_false phone position show_in_report subtype value_limit".split(),
    "subcategory": "id name status category position".split(),
    "transaction": "id type category fromcategory subcategory value value_currency currency description status period iteration image_uri data time data2 child id_split".split(),
    "sms_parse": "id category subcategory name contains status type".split(),
    "sms_template": "id name contains amount_start amount_end status".split(),
}

def stage(path):
    raw = Path(path).read_bytes()
    digest = hashlib.sha256(raw).hexdigest()
    sections, section = {}, None
    errors = []
    for line, row in enumerate(csv.reader(io.StringIO(raw.decode("utf-8-sig"), newline="")), 1):
        if not row:
            continue
        if len(row) == 1:
            section = row[0]
            if section in sections:
                raise ValueError(f"Duplicate section at CSV line {line}")
            sections[section] = []
        elif section is None:
            raise ValueError(f"Missing section at CSV line {line}")
        else:
            sections[section].append(row)
    entities, issues = [], []
    cats = {r[0]: r for r in sections.get("category", []) if len(r) == 14}
    subs = {r[0]: r for r in sections.get("subcategory", []) if len(r) == 5}
    tids = {r[0] for r in sections.get("transaction", []) if len(r) == 18}
    account_ids = {k for k, r in cats.items() if r[1] in ("purse", "purse_done", "debt", "target", "target_done")}

    def ref(v):
        return "pc.category." + v

    def base(section, r):
        return {"source": {"format": "procoins-1.13.7", "section": section, "id": r[0], "raw": r}}

    for section, rows in sections.items():
        seen = set()
        for index, r in enumerate(rows):
            if r[0] in seen:
                errors.append({"section": section, "index": index, "error": "Duplicate source ID", "raw": r})
                continue
            seen.add(r[0])
            expected = FIELDS.get(section)
            if expected and len(r) != len(expected):
                errors.append({"section": section, "index": index, "error": "Invalid column count", "raw": r})
                continue
            p = base(section, r)
            try:
                if section == "category":
                    if r[0] in account_ids:
                        p.update(id=ref(r[0]), entity="account", name=r[6], currency=r[4], account_type="debt" if r[1] == "debt" else r[12] or r[1], archived=r[1].endswith("_done"), source_opening=r[3], source_limit=r[13])
                        entities.append(p)
                        opening = Decimal(r[3]) + Decimal(r[13])
                        if opening:
                            entities.append({"id": "pc.opening." + r[0], "entity": "transaction", "kind": "opening", "time": "", "note": "Source opening value plus value_limit; effective date is unavailable", "currency": r[4], "amount": str(opening), "postings": [{"account": ref(r[0]), "amount": str(opening), "currency": r[4]}], "source": p["source"]})
                    else:
                        p.update(id=ref(r[0]), entity="category", name=r[6], direction="income" if r[1] == "profit" else "expense", parent="", archived=False)
                        entities.append(p)
                elif section == "subcategory":
                    p.update(id="pc.subcategory." + r[0], entity="category", name=r[1], parent=ref(r[3]), direction="income" if cats.get(r[3], ["", ""])[1] == "profit" else "expense", archived=False)
                    if r[3] not in cats:
                        issues.append({"entity": p["id"], "field": "parent", "missing_source_id": r[3]})
                    entities.append(p)
                elif section == "transaction":
                    value, converted = Decimal(r[5]), Decimal(r[6])
                    if not value.is_finite() or not converted.is_finite():
                        raise ValueError("Non-finite source amount")
                    kind = {"purse": "expense", "profit": "income", "transfer": "transfer"}.get(r[1], "debt" if r[1].startswith("debt_") else "unclassified")
                    legs = []
                    if r[3] in account_ids:
                        legs.append({"account": ref(r[3]), "amount": str(-value), "currency": cats[r[3]][4]})
                    if r[2] in account_ids:
                        legs.append({"account": ref(r[2]), "amount": str(converted), "currency": cats[r[2]][4]})
                    missing = []
                    for field, v in (("category", r[2]), ("fromcategory", r[3])):
                        if v not in ("", "0") and v not in cats:
                            missing.append(field)
                            issues.append({"entity": "pc.transaction." + r[0], "field": field, "missing_source_id": v})
                    if r[4] not in ("", "0") and r[4] not in subs:
                        missing.append("subcategory")
                        issues.append({"entity": "pc.transaction." + r[0], "field": "subcategory", "missing_source_id": r[4]})
                    if r[16] not in ("", "0") and r[16] not in tids:
                        missing.append("parent_transaction")
                        issues.append({"entity": "pc.transaction." + r[0], "field": "child", "missing_source_id": r[16]})
                    account = r[3] if kind in ("expense", "transfer", "debt") else r[2]
                    category = ("pc.subcategory." + r[4]) if r[4] in subs else ref(r[2] if kind == "expense" else r[3])
                    p.update(id="pc.transaction." + r[0], entity="transaction", kind=kind, time=r[13] + "T" + r[14], amount=str(value if kind != "income" else converted), currency=r[7], account=ref(account), category=category, note=r[8], postings=legs, source_parent=r[16], due=r[15], unresolved=missing, deleted=r[9] == "0")
                    if kind in ("transfer", "debt"):
                        p.update(to=ref(r[2]), received_amount=str(converted), received_currency=cats[r[2]][4] if r[2] in cats else r[7], fee="0")
                    # No historical base currency is asserted. Identity conversion is only
                    # retained when both original amounts match and all present legs are RUB.
                    if r[7] == "RUB" and value == converted and all(l["currency"] == "RUB" for l in legs):
                        p.update(base_currency="RUB", rate="1", base_amount=str(value), rate_source="source_identity", rate_time=p["time"])
                    entities.append(p)
                else:
                    p.update(id="pc.raw." + section + "." + r[0], entity="source_record")
                    entities.append(p)
            except Exception as exc:
                errors.append({"section": section, "index": index, "error": str(exc), "raw": r})
    transactions = [p for p in entities if p["entity"] == "transaction" and p["kind"] != "opening"]
    balances = {ref(k): Decimal(0) for k in account_ids}
    for p in entities:
        if p["entity"] == "transaction" and not p.get("deleted"):
            for l in p.get("postings", []):
                balances[l["account"]] += Decimal(l["amount"])
    report = {"sha256": digest, "bytes": len(raw), "sections": {k: len(v) for k, v in sections.items()}, "transactions": len(transactions), "types": dict(Counter(r[1] for r in sections.get("transaction", []))), "first_date": min((p["time"][:10] for p in transactions), default=None), "last_date": max((p["time"][:10] for p in transactions), default=None), "currencies": sorted({p["currency"] for p in transactions}), "accounts": [{"id": ref(k), "name": cats[k][6], "currency": cats[k][4], "source_opening": cats[k][3], "source_limit": cats[k][13], "projected_balance": str(balances[ref(k)]), "reconciled": False} for k in sorted(account_ids)], "unresolved_records": sum(bool(p.get("unresolved")) for p in transactions), "issues": issues, "errors": errors, "limitations": ["Exporter includes only status=1 categories/subcategories; deleted and archived source entities may be absent.", "Exporter includes transactions with status!=0; deleted source transactions are absent.", "Source code uses DOUBLE and replaces note newlines with spaces. Precision or formatting already lost in ProCoins cannot be recovered.", "CSV does not include attachments, login identity, update timestamps or source timezone.", "Projected balances use known postings and source opening values. They are not confirmed current ProCoins balances."]}
    return {"format": "vibefinancier-import", "version": 1, "source_sha256": digest, "entities": entities, "raw_sections": sections, "report": report}

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("source")
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    result = stage(args.source)
    destination = Path(args.output)
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    destination.with_suffix(".audit.json").write_text(json.dumps(result["report"], ensure_ascii=False, indent=2), encoding="utf-8")
    r = result["report"]
    print(json.dumps({k: r[k] for k in ("sha256", "transactions", "first_date", "last_date", "currencies", "unresolved_records")}, ensure_ascii=True))
    print("Issues:", len(r["issues"]), "Errors:", len(r["errors"]))

if __name__ == "__main__":
    main()
