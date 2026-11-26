import {spawnSync} from 'node:child_process';
import {writeFileSync} from 'node:fs';
import {randomBytes,randomUUID} from 'node:crypto';
import fs from 'node:fs/promises';
import net from 'node:net';
import path from 'node:path';

const label=process.argv[2],image=process.env.QUALITY_IMAGE;
if(!['before','after','evaluation','evaluation-before'].includes(label)||!image)throw Error('Use before|after and an explicit QUALITY_IMAGE');
const token=randomBytes(6).toString('hex'),project='recomai-quality-'+token,database='recomai_quality_'+token;
const out='design-audit/technical-quality';await fs.mkdir(out,{recursive:true});await fs.mkdir('target',{recursive:true});
async function freePort(){const s=net.createServer();await new Promise((resolve,reject)=>{s.once('error',reject);s.listen(0,'127.0.0.1',resolve);});const port=s.address().port;await new Promise(r=>s.close(r));return port;}
const [appPort,mysqlPort,mailPort]=await Promise.all([freePort(),freePort(),freePort()]);
if(new Set([appPort,mysqlPort,mailPort]).size!==3)throw Error('Port reservation collision; retry without starting containers');
const base='http://127.0.0.1:'+appPort;
const env={...process.env,E2E_EXISTING_IMAGE:image,MYSQL_DATABASE:database,DB_USERNAME:'quality_fixture',DB_PASSWORD:randomBytes(24).toString('hex'),MYSQL_ROOT_PASSWORD:randomBytes(24).toString('hex'),APP_HOST_PORT:String(appPort),MYSQL_HOST_PORT:String(mysqlPort),MAILPIT_HOST_PORT:String(mailPort),COMPOSE_PROFILES:'local-mail',APP_PUBLIC_URL:base};
function docker(args,input){const r=spawnSync('docker',args,{env,input,encoding:'utf8',maxBuffer:32*1024*1024});if(r.status){writeFileSync('target/technical-operation-error.log',r.stdout+'\n'+r.stderr);throw Error('Isolated operation failed: '+args.slice(0,3).join(' '));}return r.stdout.trim();}
const compose=['compose','-f','docker-compose.yml','-f','docker-compose.audit-image.yml','-p',project];
function sql(query){return docker([...compose,'exec','-T','mysql','sh','-c','MYSQL_PWD="$MYSQL_PASSWORD" mysql --batch --raw --skip-column-names -u "$MYSQL_USER" "$MYSQL_DATABASE"'],query);}
function sqlRoot(query){return docker([...compose,'exec','-T','mysql','sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --batch --raw --skip-column-names -u root "$MYSQL_DATABASE"'],query);}
function queryCounters(){const [count,picoseconds]=sqlRoot("SELECT COALESCE(SUM(COUNT_STAR),0),COALESCE(SUM(SUM_TIMER_WAIT),0) FROM performance_schema.events_statements_summary_by_digest WHERE SCHEMA_NAME=DATABASE();").split('\t').map(Number);return{count,picoseconds};}
function client(){let cookie='';return async function(url,method='GET',body){const started=performance.now(),r=await fetch(base+url,{method,headers:{Origin:base,...(cookie?{Cookie:cookie}:{}),...(body?{'content-type':'application/json'}:{})},...(body?{body:JSON.stringify(body)}:{}),signal:AbortSignal.timeout(30000)});for(const c of r.headers.getSetCookie())if(c.startsWith('JSESSIONID='))cookie=c.split(';')[0];const text=await r.text();if(!r.ok)throw Error('Fixture API '+url.split('?')[0]+' returned '+r.status);return{body:text?JSON.parse(text):null,bytes:Buffer.byteLength(text),ms:performance.now()-started};};}
function summarize(values){const sorted=values.map(x=>x.ms).sort((a,b)=>a-b),q=p=>sorted[Math.max(0,Math.ceil(p*sorted.length)-1)]??null;return{requests:values.length,p50:q(.5),p95:q(.95),p99:q(.99),mean:values.length?values.reduce((s,x)=>s+x.ms,0)/values.length:null,meanBytes:values.length?values.reduce((s,x)=>s+x.bytes,0)/values.length:null};}
function stats(){return docker(['stats','--no-stream','--format','{{json .}}',project+'-backend-1',project+'-mysql-1']).split(/\r?\n/).map(JSON.parse);}
const results=[],failures=[],phases=[];const password=randomBytes(24).toString('hex');let startedAt;
try{
 const start=performance.now();docker([...compose,'up','--detach','--no-build','--wait']);const readyMs=performance.now()-start;startedAt=new Date().toISOString();
 if(sql('SELECT DATABASE();')!==database)throw Error('Unexpected benchmark database');
 const admin=client();await admin('/api/auth/register','POST',{username:'quality_admin',email:'quality_admin@example.test',password,displayName:'Isolated quality fixture'});
 sql("UPDATE users SET role='ADMIN',auth_version=auth_version+1 WHERE username='quality_admin' AND email='quality_admin@example.test';");await admin('/api/auth/logout','POST');await admin('/api/auth/login','POST',{email:'quality_admin@example.test',password});
 for(const file of ['educational-resources.json','educational-articles.json']){const rows=JSON.parse(await fs.readFile('data/processed/'+file,'utf8'));for(let offset=0;offset<rows.length;offset+=250){const resources=rows.slice(offset,offset+250),p=(await admin('/api/admin/ingestion/preview','POST',{resources})).body;await admin('/api/admin/ingestion/commit','POST',{resources,expectedSha256:p.sha256});}}
 const catalog=(await admin('/api/admin/items')).body;if(catalog.length!==2365)throw Error('Expected the current 2365-resource catalog');
 const niches=(await admin('/api/discovery/niches')).body;
 if(label.startsWith('evaluation')){
  const {buildEvaluationInput}=await import('./technical-evaluation-input.mjs');const input=buildEvaluationInput(catalog,niches);
  for(const profile of input.profiles){const api=client();await api('/api/auth/register','POST',{username:profile.username,email:profile.username+'@example.test',password,displayName:'Synthetic editorial profile'});await api('/api/users/me/onboarding','POST',{purpose:profile.goal,categoryId:profile.categoryId,interestIds:[profile.interestId]});}
  const inputPath=out+(label==='evaluation-before'?'/recommendation-input-before.json':'/recommendation-input.json');await fs.writeFile(inputPath,JSON.stringify(input,null,2));
  const packagePath='com/guvi/recommendationsystem/evaluation',compiled='backend/target/test-classes/'+packagePath,staging='target/technical-evaluation-classes';await fs.mkdir(staging+'/'+packagePath,{recursive:true});
  for(const name of await fs.readdir(compiled))if(/^CurrentCatalogEvaluation(?:\$.*)?\.class$/.test(name))await fs.copyFile(compiled+'/'+name,staging+'/'+packagePath+'/'+name);
  const container=project+'-backend-1';docker(['cp',staging+'/.',container+':/tmp/technical-evaluation-classes']);docker(['cp',inputPath,container+':/tmp/technical-evaluation-input.json']);
  const log=docker(['exec','-e','MYSQL_DATABASE='+database,container,'java','-Dloader.path=/tmp/technical-evaluation-classes','-Dloader.main=com.guvi.recommendationsystem.evaluation.CurrentCatalogEvaluation','-cp','/app/application.jar','org.springframework.boot.loader.launch.PropertiesLauncher','/tmp/technical-evaluation-input.json','/tmp/technical-evaluation-output']);
  await fs.writeFile('target/technical-evaluation-operator.log',log);const evaluationOutput=out+(label==='evaluation-before'?'/evaluation-before':'/evaluation');await fs.mkdir(evaluationOutput,{recursive:true});docker(['cp',container+':/tmp/technical-evaluation-output/.',evaluationOutput]);console.log(log.split('\n').find(line=>line.startsWith('CURRENT_CATALOG_EVALUATION')));
 }else{
 const actors=[];
 // Synthetic actors exercise workflows; these are not genuine users or a user study.
 for(let i=0;i<100;i++){
  const api=client(),niche=niches[i%niches.length],username='quality_actor_'+String(i).padStart(3,'0');
  await api('/api/auth/register','POST',{username,email:username+'@example.test',password,displayName:'Isolated load fixture'});
  await api('/api/users/me/onboarding','POST',{purpose:'LEARN',categoryId:niche.categoryId,interestIds:[niche.topics[0].interestId]});
  const item=catalog.find(item=>item.categoryId===niche.categoryId&&item.active);await api('/api/users/me/interactions','POST',{itemId:item.itemId,type:'SAVE'});
  actors.push({api,niche,item});
 }
 const operations=[['catalog',()=>'/api/items/search?pageSize=20'],['search',()=>'/api/items/search?query=Java&pageSize=20'],['filters',()=>'/api/items/search?type=ARTICLE&provider=Microsoft%20Learn&topic=Java&pageSize=20'],['recommendations',()=>'/api/users/me/recommendations?limit=10','POST'],['details',actor=>'/api/items/'+actor.item.itemId],['saved',()=>'/api/users/me/saved?pageSize=20'],['journeys',()=>'/api/users/me/journeys','POST',actor=>({title:'Measured learning path',goal:'LEARN',categoryId:actor.niche.categoryId,steps:3,requestId:randomUUID()})],['progress',()=>'/api/users/me/interactions/progress']];
 for(const [name,url,method,body]of operations)for(let i=0;i<5;i++)await actors[i].api(url(actors[i]),method||'GET',body?.(actors[i]));
 const info=JSON.parse(docker(['info','--format','{{json .}}']));const hardware={cpus:info.NCPU,memoryBytes:info.MemTotal,architecture:info.Architecture,os:info.OperatingSystem,dockerVersion:info.ServerVersion};const pool=sql("SHOW STATUS WHERE Variable_name IN ('Threads_connected','Threads_running','Max_used_connections');");
 const legacy=await admin('/api/admin/items'),firstId=catalog[0].itemId;
 const editor=label==='after'?await admin('/api/admin/items/'+firstId):legacy;
 await fs.writeFile(out+'/performance-'+label+'-setup.json',JSON.stringify({image,startedAt,stackReadyMs:readyMs,imageId:docker(['image','inspect','--format','{{.Id}}',image]),mysqlVersion:sql('SELECT VERSION();'),resources:catalog.length,actors:100,actorKind:'Generated isolated fixtures only',warmupsPerOperation:5,hardware,databasePool:pool,editorReload:{legacyBytes:legacy.bytes,currentBytes:editor.bytes},initialContainers:stats()},null,2));
 for(const concurrency of [10,25,50,100]){
  const selected=actors.slice(0,concurrency),samples=new Map(operations.map(([n])=>[n,[]]));const beforeQueries=queryCounters(),start=performance.now();let errors=0;
  await Promise.all(selected.map(async actor=>{for(const [name,url,method,body]of operations){try{const r=await actor.api(url(actor),method||'GET',body?.(actor));samples.get(name).push(r);}catch(error){errors++;failures.push({concurrency,operation:name,error:error.message});}}}));
  const elapsedMs=performance.now()-start,afterQueries=queryCounters(),containers=stats(),connections=Number(sql("SELECT COUNT(*) FROM information_schema.PROCESSLIST WHERE DB=DATABASE();"));
  for(const [name,values]of samples)results.push({concurrency,operation:name,...summarize(values),errors:failures.filter(f=>f.concurrency===concurrency&&f.operation===name).length});
  const globalMemoryPercent=docker(['stats','--no-stream','--format','{{.MemPerc}}']).split(/\r?\n/).reduce((sum,p)=>sum+parseFloat(p),0);
  phases.push({concurrency,elapsedMs,requestsPerSecond:(concurrency*operations.length-errors)/(elapsedMs/1000),containers,connections,globalMemoryPercent,schemaStatementCount:afterQueries.count-beforeQueries.count,schemaStatementDurationMs:(afterQueries.picoseconds-beforeQueries.picoseconds)/1e9});
  await fs.writeFile(out+'/performance-'+label+'.json',JSON.stringify({startedAt,image,results,failures,phases,latestPhase:{concurrency,elapsedMs,requestsPerSecond:(concurrency*operations.length-errors)/(elapsedMs/1000),containers,connections},sampling:'Nearest-rank quantiles over successful requests; one sequential 8-operation workflow per concurrent actor. End-of-phase resource samples are not peaks. Local fixture throughput is not production capacity.'},null,2));
  console.log(JSON.stringify({phase:label,concurrency,errors,elapsedMs}));

  if(errors/(concurrency*operations.length)>.05||containers.some(s=>parseFloat(s.MemPerc)>80)||globalMemoryPercent>85||connections>120||elapsedMs>60000)throw Error('Load-test safety stop triggered');
 }
 }
}finally{docker([...compose,'down','--remove-orphans']);console.log('Stopped only '+project+'; all database volumes retained.');}
