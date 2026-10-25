import {fileURLToPath} from 'node:url';
import fs from 'node:fs/promises';
import crypto from 'node:crypto';
import {Workbook, SpreadsheetFile} from '@oai/artifact-tool';

const output=fileURLToPath(new URL('../sheets/',import.meta.url)).replace(/[\\\/]$/, '');
const journal='J_00000000000000000000000000000001';
function event(id,p,parents='') {const text=JSON.stringify(p),row=[id,p.id,parents,p.entity,text];return [...row,crypto.createHash('sha256').update(row.join('\n')).digest('hex')];}
for(const test of [false,true]) {
  const book=Workbook.create();
  const data={
    Guide:[['VibeFinancier',test?'Isolated test data only':'Private finance template'],['Setup','Choose this compatible table in the Android app. Setup is automatic; no Apps Script is required.'],['Money','Amounts are exact decimal TEXT. Do not convert journal values to numeric cells.'],['Editing','Use the Android app for accounts and operations. Never change or sort journal cells.'],['Filters','Config B3:B6 accept ISO dates and account/category IDs as text. Rates accept manual text quotes.'],['Test','The app checks exact storage automatically before uploading imported history.']],
    Config:[['schema','vibefinancier/1'],['base_currency','RUB'],['from_date','0000-01-01'],['to_date','9999-12-31'],['account_filter',''],['category_filter','']],
    Summary:[['Status','Action'],['Waiting for synchronization','The app calculates exact totals and writes a report snapshot here.']],
    Rates:[['currency','base','rate','source','updated_at','manual'],['RUB','RUB','1','identity','','false']],
  };
  if(test) {
    const account={id:'test.cash',entity:'account',name:'Test cash',currency:'RUB',account_type:'cash'};
    const tx={id:'test.income',entity:'transaction',kind:'income',time:'2026-01-01T00:00:00',amount:'10.25',base_amount:'10.25',base_currency:'RUB',postings:[{account:'test.cash',currency:'RUB',amount:'10.25'}]};
    data[journal]=[['event_id','entity_id','parents','kind','payload','checksum'],event('test.account',account),event('test.income.event',tx)];
    await fs.writeFile(output+'/synthetic-events.json',JSON.stringify(data[journal],null,2));
  }
  for(const [name,rows] of Object.entries(data)) {
    const sheet=book.worksheets.add(name);sheet.showGridLines=true;
    const width=rows[0].length;sheet.getRangeByIndexes(0,0,rows.length,width).values=rows;
    const range=sheet.getRangeByIndexes(0,0,rows.length,width);range.setNumberFormat('@');range.format.font={name:'Arial',size:10};range.format.verticalAlignment='center';range.format.rowHeight=25;
    const header=sheet.getRangeByIndexes(0,0,1,width);header.format.fill='#EEEEEE';header.format.font={bold:true,name:'Arial',size:10};
    sheet.getRangeByIndexes(0,0,rows.length,1).format.columnWidth=24;
    if(name==='Guide'){sheet.getRange('B1:B6').format.columnWidth=95;range.format.wrapText=true;range.format.rowHeight=36;}
    else if(name===journal){sheet.getRange('B1:D3').format.columnWidth=22;sheet.getRange('E1:E3').format.columnWidth=80;sheet.getRange('F1:F3').format.columnWidth=68;sheet.freezePanes.freezeRows(1);}
    else sheet.getRangeByIndexes(0,1,rows.length,width-1).format.columnWidth=30;
  }
  book.recalculate();
  console.log((await book.inspect({kind:'table',range:'Guide!A1:B6',include:'values,formulas',tableMaxRows:6,tableMaxCols:2,maxChars:1800})).ndjson);
  const name=test?'VibeFinancier-isolated-test':'VibeFinancier-template';
  await (await SpreadsheetFile.exportXlsx(book)).save(output+'/'+name+'.xlsx');
  const preview=await book.render({sheetName:'Guide',range:'A1:B6',scale:1.3,format:'png'});
  await fs.writeFile(output+'/'+name+'-preview.png',new Uint8Array(await preview.arrayBuffer()));
  console.log(name, (await book.inspect({kind:'sheet',include:'id,name',maxChars:1500})).ndjson);
}
