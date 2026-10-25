"""Write a readable audit from an existing staging file without changing it."""
import json
import sys
from pathlib import Path

source=Path(sys.argv[1])
report=json.loads(source.read_text(encoding='utf-8'))['report']
lines=['# ProCoins extraction audit','',f"SHA-256: {report['sha256']}",'',
       f"Transactions: {report['transactions']}",
       f"Date range: {report['first_date']} through {report['last_date']}",
       'Currencies: '+', '.join(report['currencies']),
       f"Unresolved transactions: {report['unresolved_records']}",
       f"Parsing errors: {len(report['errors'])}",'',
       '## Known exported account projections','',
       '| Source ID | Name | Currency | Opening | Projected balance | Reconciled |',
       '| --- | --- | --- | --- | --- | --- |']
for a in report['accounts']:
    lines.append(f"| {a['id']} | {a['name']} | {a['currency']} | {a['source_opening']} | {a['projected_balance']} | No |")
lines+=['','## Export limits','']+['- '+item for item in report['limitations']]
source.with_suffix('.audit.md').write_text('\n'.join(lines)+'\n',encoding='utf-8')
print('Readable audit written.')
