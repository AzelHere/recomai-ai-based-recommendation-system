// Only disposable E2E projects. Dump stays in memory and is never written to an artifact.
import {spawnSync} from 'node:child_process';
import {createHash,randomBytes} from 'node:crypto';
import fs from 'node:fs/promises';
const project=process.argv[2];if(!/^recomai-e2e-[\w-]+$/.test(project||''))throw Error('Disposable E2E project required');
const restored='recomai_restore_'+randomBytes(8).toString('hex');
function run(command,input){const r=spawnSync('docker',['compose','-p',project,'exec','-T','mysql','sh','-c',command],{input,encoding:'utf8',maxBuffer:128*1024*1024});if(r.status!==0)throw Error('Isolated database command failed: '+r.stderr);return r.stdout;}
const mysql='MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --batch --raw --skip-column-names -u root';
const source=run(mysql+' -e "SELECT DATABASE()" "$MYSQL_DATABASE"').trim();if(!/^recomai_e2e_[\w]+$/.test(source))throw Error('Unexpected source database');
const tables=run(mysql+' "$MYSQL_DATABASE" -e "SHOW TABLES"').trim().split(/\r?\n/);if(tables.some(t=>!/^\w+$/.test(t)))throw Error('Unexpected table identifier');
function fingerprint(db){return Object.fromEntries(tables.map(table=>{
 const columns=run(`${mysql} ${db} -e 'SHOW COLUMNS FROM \`${table}\`'`).trim().split(/\r?\n/).map(line=>line.split('\t')[0]);
 if(columns.some(column=>!/^\w+$/.test(column)))throw Error('Unexpected column identifier');
 const fields=columns.map(column=>`IF(\`${column}\` IS NULL,"N",CONCAT("V",HEX(CAST(\`${column}\` AS BINARY))))`).join(',');
 const rows=run(`${mysql} ${db}`,`SELECT CONCAT_WS(":",${fields}) FROM \`${table}\`;`).trimEnd().split(/\r?\n/).filter(Boolean).sort();
 return[table,{rows:rows.length,sha256:createHash('sha256').update(rows.join('\n')).digest('hex')}];
}));}

// Freeze a consistent fixture snapshot first: another Playwright worker may still write the live fixture.
const snapshot=restored+'_snapshot';
const dumpCommand='MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump -u root --single-transaction --hex-blob --no-tablespaces --set-gtid-purged=OFF --routines --events --triggers --skip-comments --skip-dump-date ';
const initialDump=run(dumpCommand+'"$MYSQL_DATABASE"');
run(mysql,`CREATE DATABASE \`${snapshot}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;`);
run(`${mysql} ${snapshot}`,initialDump);
const before=fingerprint(snapshot);
const dump=run(dumpCommand+snapshot);
run(mysql,`CREATE DATABASE \`${restored}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;`);
run(`${mysql} ${restored}`,dump);
const after=fingerprint(restored),preserved=JSON.stringify(before)===JSON.stringify(after);
if(!preserved)throw Error('Backup/restore fingerprints differ');
await fs.writeFile('design-audit/recomai-2-phase-4/evidence/backup-restore.json',JSON.stringify({completedAt:new Date().toISOString(),isolated:true,dumpStored:false,snapshotDatabase:snapshot,restoredDatabase:restored,tableCount:tables.length,allTablesPreserved:preserved,tables:before,method:'Consistent live fixture dump restored into a new quiescent snapshot; snapshot backup restored into a separate new database; sorted null-aware binary column SHA256 comparison for every table. Concurrent browser writes cannot change the comparison source. Main database untouched. All isolated databases retained.'},null,2));
console.log(`Verified backup/restore: ${tables.length} tables preserved; no dump stored.`);
