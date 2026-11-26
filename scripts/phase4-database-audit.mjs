import {spawnSync} from 'node:child_process';
import fs from 'node:fs/promises';
const project=process.argv[2];if(!/^recomai-e2e-[\w-]+$/.test(project||''))throw Error('Disposable E2E project required');
function sql(input){const r=spawnSync('docker',['compose','-p',project,'exec','-T','mysql','sh','-c','MYSQL_PWD="$MYSQL_PASSWORD" mysql --batch --raw --skip-column-names -u "$MYSQL_USER" "$MYSQL_DATABASE"'],{input,encoding:'utf8',maxBuffer:10*1024*1024});if(r.status!==0)throw Error('Isolated query audit failed');return r.stdout.trim();}
const user=Number(sql('SELECT MIN(user_id) FROM users;')),item=Number(sql('SELECT MIN(item_id) FROM items;'));
const queries={
 adminPage:"SELECT item_id FROM items ORDER BY LOWER(title),item_id LIMIT 20",
 catalogPage:"SELECT item.item_id FROM items item JOIN categories category ON category.category_id=item.category_id AND category.is_active=TRUE WHERE item.is_active=TRUE ORDER BY item.created_at DESC,item.item_id DESC LIMIT 12",
 saved:`SELECT item_id FROM interactions WHERE user_id=${user} AND interaction_type='SAVE' GROUP BY item_id LIMIT 20`,
 activity:`SELECT interaction_id,item_id FROM interactions WHERE user_id=${user} ORDER BY occurred_at DESC,interaction_id DESC LIMIT 25`,
 provenance:`SELECT source_id,external_id,license FROM resource_provenance WHERE item_id=${item}`,
 journeys:`SELECT journey_id FROM learning_journeys WHERE user_id=${user} ORDER BY updated_at DESC LIMIT 20`
};
const plans={};for(const [name,query]of Object.entries(queries)){plans[name]={query,explain:JSON.parse(sql('EXPLAIN FORMAT=JSON '+query+';'))};}
const migrations=sql('SELECT version,description,success FROM flyway_schema_history ORDER BY installed_rank;');
await fs.writeFile('design-audit/recomai-2-phase-4/evidence/database-plans.json',JSON.stringify({capturedAt:new Date().toISOString(),isolated:true,items:Number(sql('SELECT COUNT(*) FROM items;')),plans,migrations,assessment:'Leading-wildcard/LOCATE searches and LOWER(title) ordering can scan/sort this bounded catalog. No index is claimed to fix substring search. Existing owner and provenance indexes are retained; no speculative migration.'},null,2));
console.log('Captured six actual MySQL query plans and migration status.');
