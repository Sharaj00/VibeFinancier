const fs=require('fs');
const vm=require('vm');
const crypto=require('crypto');
const assert=require('assert/strict');
const file=process.argv[2];
if(!file)throw new Error('Usage: node scripts/verify-import.cjs <staging.json>');
const bundle=JSON.parse(fs.readFileSync(file,'utf8'));
const context=vm.createContext({Utilities:{DigestAlgorithm:{SHA_256:'sha256'},Charset:{UTF_8:'utf8'},computeDigest:(_a,text)=>[...crypto.createHash('sha256').update(text).digest()]}});
vm.runInContext(fs.readFileSync('sheets/Code.gs','utf8')+'\nglobalThis.D=VFDecimal;',context);
const rows=bundle.entities.map(p=>{const text=JSON.stringify(p);const id='import.'+crypto.createHash('sha256').update(bundle.source_sha256+'\n'+p.id).digest('hex');const fields=[id,p.id,'',p.entity,text];return [...fields,crypto.createHash('sha256').update(fields.join('\n')).digest('hex')];});
const report=context.VF_REPORT(rows,[],'RUB','0000-01-01','9999-12-31');
for(const account of bundle.report.accounts){const row=report.find(r=>r[0]==='balance'&&r[1]===account.id);assert.ok(row,'Missing account output');assert.equal(row[4],new context.D(account.projected_balance).toString());}
const replay=context.VF_REPORT([...rows,...rows],[],'RUB','0000-01-01','9999-12-31');
assert.deepEqual(JSON.parse(JSON.stringify(report)),JSON.parse(JSON.stringify(replay)));
console.log(JSON.stringify({source_sha256:bundle.source_sha256,transactions:bundle.report.transactions,retained_entities:bundle.entities.length,account_projections_matched:bundle.report.accounts.length,duplicate_replay:'passed',unresolved_records:bundle.report.unresolved_records,cloud_upload:false}));
