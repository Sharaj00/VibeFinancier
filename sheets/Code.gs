/** VibeFinancier schema 1. All monetary results are decimal strings. */
class VFDecimal {
  constructor(value) {
    const text = String(value);
    if (!/^-?\d{1,80}(\.\d{1,40})?$/.test(text)) throw new Error('Invalid exact decimal');
    const parts = text.split('.');
    this.scale = (parts[1] || '').length;
    this.units = BigInt(parts[0] + (parts[1] || ''));
  }
  static make(units, scale) { const d = Object.create(VFDecimal.prototype); d.units = units; d.scale = scale; return d; }
  add(other) {
    const d = other instanceof VFDecimal ? other : new VFDecimal(other);
    const s = Math.max(this.scale, d.scale);
    return VFDecimal.make(this.units * BigInt('10') ** BigInt(s - this.scale) + d.units * BigInt('10') ** BigInt(s - d.scale), s);
  }
  neg() { return VFDecimal.make(-this.units, this.scale); }
  mul(other) { const d = other instanceof VFDecimal ? other : new VFDecimal(other); return VFDecimal.make(this.units * d.units, this.scale + d.scale); }
  round(scale) {
    if (this.scale <= scale) return this;
    const divisor = BigInt('10') ** BigInt(this.scale - scale), sign = this.units < BigInt('0') ? BigInt('-1') : BigInt('1');
    const absolute = this.units * sign, quotient = absolute / divisor, remainder = absolute % divisor;
    const extra = remainder * BigInt('2') > divisor || (remainder * BigInt('2') === divisor && quotient % BigInt('2') !== BigInt('0')) ? BigInt('1') : BigInt('0');
    return VFDecimal.make((quotient + extra) * sign, scale);
  }
  toString() {
    const sign = this.units < BigInt('0') ? '-' : '', abs = this.units < BigInt('0') ? -this.units : this.units;
    let digits = String(abs).padStart(this.scale + 1, '0');
    if (this.scale) digits = (digits.slice(0, -this.scale) + '.' + digits.slice(-this.scale)).replace(/0+$/, '').replace(/\.$/, '');
    return (abs === BigInt('0') ? '' : sign) + digits;
  }
}

function vfHash_(text) {
  return Utilities.computeDigest(Utilities.DigestAlgorithm.SHA_256, text, Utilities.Charset.UTF_8)
    .map(b => (b & 255).toString(16).padStart(2, '0')).join('');
}
function vfState_(rows) {
  const events = new Map(), referenced = new Set();
  for (const row of rows) {
    if (!row[0]) continue;
    const [id, entity, parents, kind, payload, checksum] = row.map(String);
    if (!checksum || vfHash_([id, entity, parents, kind, payload].join('\n')) !== checksum) throw new Error('Journal checksum mismatch: ' + id);
    if (events.has(id) && events.get(id).checksum !== checksum) throw new Error('Event ID collision');
    const p = JSON.parse(payload);
    if (p.id !== entity || p.entity !== kind) throw new Error('Entity identity mismatch');
    events.set(id, {id, entity, parents: parents ? parents.split(',') : [], p, checksum});
  }
  const visiting = new Set(), visited = new Set();
  function visit(event) {
    if (visiting.has(event.id)) throw new Error('Revision cycle');
    if (visited.has(event.id)) return;
    visiting.add(event.id);
    for (const parent of event.parents) {
      const previous = events.get(parent);
      if (!previous || previous.entity !== event.entity) throw new Error('Missing or invalid revision parent');
      referenced.add(parent); visit(previous);
    }
    visiting.delete(event.id); visited.add(event.id);
  }
  for (const e of events.values()) visit(e);
  const heads = new Map();
  for (const e of events.values()) if (!referenced.has(e.id)) {
    if (!heads.has(e.entity)) heads.set(e.entity, []);
    heads.get(e.entity).push(e);
  }
  const entities = [], conflicts = [];
  for (const [id, h] of heads) {
    if (h.length > 1) conflicts.push(id);
    else if (!h[0].p.deleted) entities.push(h[0].p);
  }
  return {entities, conflicts};
}

/**
 * Exact balances and historical aggregates for immutable VibeFinancier journals.
 * @customfunction
 */
function VF_REPORT(journals, manualRates, base, fromDate, toDate, accountFilter, categoryFilter) {
  const state = vfState_(journals), accounts = new Map(), categories = new Map(), balances = new Map(), rates = new Map();
  base = String(base || 'RUB');
  const date = v => v instanceof Date ? Utilities.formatDate(v, 'Etc/UTC', 'yyyy-MM-dd') : String(v);
  const from = date(fromDate || '0000-01-01'), to = date(toDate || '9999-12-31');
  accountFilter=String(accountFilter||'');categoryFilter=String(categoryFilter||'');
  for (const p of state.entities) {
    if (p.entity === 'account') { accounts.set(p.id, p); balances.set(p.id, new VFDecimal('0')); }
    if (p.entity === 'category') categories.set(p.id, p);
    if (p.entity === 'rate' && p.base === base) rates.set(p.currency, p);
  }
  for (const r of manualRates || []) if (r[0] && r[1] === base && String(r[5]).toLowerCase() === 'true')
    rates.set(String(r[0]), {rate: String(r[2]), source: String(r[3]), asof: String(r[4])});
  rates.set(base, {rate: '1', source: 'identity', asof: ''});
  let income = new VFDecimal('0'), expense = new VFDecimal('0'), assets = new VFDecimal('0');
  const groups = new Map(), months = new Map(), warnings = [], missingQuotes = [];
  const bump = (map, key, amount) => map.set(key, (map.get(key) || new VFDecimal('0')).add(amount));
  for (const p of state.entities) if (p.entity === 'transaction') {
    for (const leg of p.postings || []) {
      if (!accounts.has(leg.account)) { warnings.push('Missing account: ' + leg.account); continue; }
      if (leg.currency !== accounts.get(leg.account).currency) throw new Error('Posting currency mismatch');
      bump(balances, leg.account, leg.amount);
    }
    if (p.unresolved && p.unresolved.length) warnings.push('Unresolved source references: ' + p.id);
    if (!p.time || p.time.slice(0, 10) < from || p.time.slice(0, 10) > to) continue;
    if(accountFilter && !(p.postings||[]).some(leg=>leg.account===accountFilter))continue;
    if(categoryFilter) {
      let category=p.category||'',found=false;const visited=new Set();
      while(category) {if(visited.has(category))throw new Error('Category parent cycle');visited.add(category);if(category===categoryFilter){found=true;break;}category=categories.has(category)?categories.get(category).parent||'':'';}
      if(!found)continue;
    }
    if (p.kind !== 'income' && p.kind !== 'expense' && !p.fee_base) continue;
    if (p.base_currency !== base || !p.base_amount) { warnings.push('Historical rate unavailable: ' + p.id); continue; }
    const a = new VFDecimal(p.base_amount), k = p.kind;
    if (k === 'income') income = income.add(a);
    if (k === 'expense') expense = expense.add(a);
    if (p.fee_base && (!accountFilter||p.account===accountFilter)) expense = expense.add(p.fee_base);
    if (k === 'income' || k === 'expense') {
      const seen = new Set(); let cat = p.category || '';
      do {
        if (seen.has(cat)) throw new Error('Category parent cycle');
        seen.add(cat); bump(groups, k + '\t' + cat, a);
        cat = categories.has(cat) ? categories.get(cat).parent || '' : '';
      } while (cat);
      bump(months, p.time.slice(0, 7), k === 'expense' ? a.neg() : a);
    }
    if (p.fee_base && (!accountFilter||p.account===accountFilter)) bump(months, p.time.slice(0, 7), new VFDecimal(p.fee_base).neg());
  }
  const result = [['section', 'id', 'label', 'currency', 'exact_value', 'detail']];
  for (const [id, a] of accounts) {
    const amount = balances.get(id);
    result.push(['balance', id, a.name, a.currency, amount.toString(), a.account_type === 'debt' ? '+ owed to you / - you owe' : '']);
    if (a.account_type !== 'debt') {
      const quote = rates.get(a.currency);
      if (quote) assets = assets.add(amount.mul(quote.rate)); else missingQuotes.push(a.currency);
    }
  }
  result.push(['assets', '', 'Current asset valuation', base, assets.toString(), missingQuotes.length ? 'PARTIAL: missing quotes for ' + missingQuotes.join(', ') : 'Current quotes; debt accounts excluded']);
  result.push(['income', '', 'Income', base, income.toString(), from + ' / ' + to]);
  result.push(['expense', '', 'Expenses including fees', base, expense.toString(), from + ' / ' + to]);
  result.push(['difference', '', 'Income minus expenses', base, income.add(expense.neg()).toString(), 'Transfers, debt principal and openings excluded']);
  for (const [key, amount] of groups) {
    const [kind, id] = key.split('\t'); result.push(['category_' + kind, id, categories.has(id) ? categories.get(id).name : 'Missing / uncategorized', base, amount.toString(), 'Includes descendants; do not sum parent and child rows']);
  }
  for (const [month, amount] of [...months].sort()) result.push(['month_net', month, month, base, amount.toString(), 'Historical saved rates']);
  for (const id of state.conflicts) result.push(['conflict', id, 'Resolve in app', '', '', 'Excluded until resolved']);
  if (warnings.length) result.push(['warning', '', 'Review import / exchange rates', '', String(warnings.length), warnings.slice(0, 10).join('; ')]);
  return result;
}

function onOpen() { SpreadsheetApp.getUi().createMenu('VibeFinancier').addItem('Set up / refresh', 'setup').addToUi(); }
function setup() {
  const book = SpreadsheetApp.getActive();
  book.setSpreadsheetLocale('en_US');book.setSpreadsheetTimeZone('Asia/Omsk');
  let config = book.getSheetByName('Config');
  if (!config) {
    config = book.insertSheet('Config'); config.getRange('A1:B4').setNumberFormat('@').setValues([['schema','vibefinancier/1'],['base_currency','RUB'],['from_date','0000-01-01'],['to_date','9999-12-31']]);
  }
  if (config.getRange('B1').getDisplayValue() !== 'vibefinancier/1') throw new Error('Incompatible workbook');
  if(!config.getRange('A5').getValue())config.getRange('A5:A6').setValues([['account_filter'],['category_filter']]);
  let rates = book.getSheetByName('Rates'); if (!rates) rates = book.insertSheet('Rates');
  if (!rates.getRange('A1').getValue()) rates.getRange('A1:F1').setValues([['currency','base','rate','source','updated_at','manual']]);
  let summary = book.getSheetByName('Summary'); if (!summary) summary = book.insertSheet('Summary');
  const journals = book.getSheets().filter(s => /^J_[a-f0-9]{32}$/.test(s.getName()));
  for (const s of book.getSheets()) {
    s.setFrozenRows(1); s.getRange(1,1,1,Math.max(s.getLastColumn(),6)).setBackground('#146c60').setFontColor('white').setFontWeight('bold');
    if (/^J_/.test(s.getName())) { if(s.getMaxRows()<20000)s.insertRowsAfter(s.getMaxRows(),20000-s.getMaxRows());s.getRange('A:F').setNumberFormat('@'); if (!s.getProtections(SpreadsheetApp.ProtectionType.SHEET).length) s.protect().setWarningOnly(true); }
  }
  summary.clearContents();
  if (journals.length) summary.getRange('A1').setFormula('=VF_REPORT({' + journals.map(s => "'" + s.getName() + "'!A2:F20000").join(';') + '},Rates!A2:F100,Config!B2,Config!B3,Config!B4,Config!B5,Config!B6)');
  summary.setColumnWidths(1,6,180);summary.getRange('A:F').setWrap(true);
}
