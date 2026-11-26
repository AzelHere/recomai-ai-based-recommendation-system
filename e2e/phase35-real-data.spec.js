import {test,expect,request as playwrightRequest} from '@playwright/test';
import {accountFixture} from './account-fixture.js';
import fs from 'node:fs/promises';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
const audit=process.env.PHASE4_MEASURE ? 'design-audit/recomai-2-phase-4/evidence/benchmark-'+process.env.PHASE4_MEASURE : 'design-audit/recomai-2-phase-3-5';
await fs.mkdir(audit,{recursive:true});
const sourceRecords=JSON.parse(await fs.readFile('data/processed/educational-resources.json','utf8'));
const exceptions=new WeakMap();
test.beforeEach(({page})=>{const errors=[];exceptions.set(page,errors);page.on('pageerror',e=>errors.push(e.message));page.on('requestfailed',request=>{const message=request.failure()?.errorText||'';if(!/ERR_ABORTED|NS_BINDING_ABORTED|cancel/i.test(message))errors.push('Network failure: '+new URL(request.url()).pathname+' '+message);});});
test.afterEach(({page})=>expect(exceptions.get(page)).toEqual([]));
function sql(input,root=false){const project=process.env.E2E_COMPOSE_PROJECT;if(!/^recomai-e2e-[\w-]+$/.test(project||''))throw Error('Disposable fixture project required');const result=spawnSync('docker',['compose','-p',project,'exec','-T','mysql','sh','-c',root?'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -N -u root "$MYSQL_DATABASE"':'MYSQL_PWD="$MYSQL_PASSWORD" mysql -N -u "$MYSQL_USER" "$MYSQL_DATABASE"'],{input,encoding:'utf8'});if(result.status!==0)throw Error(result.stderr);return result.stdout.trim();}
const literal=value=>"'"+value.replaceAll("'","''")+"'";
async function json(response){expect(response.ok(),await response.text()).toBeTruthy();return response.json();}
async function capture(page,name,dialog=false){await page.emulateMedia({reducedMotion:'reduce'});for(const [width,height]of [[1366,768],[1440,900],[1920,1080]]){await page.setViewportSize({width,height});await page.evaluate(()=>scrollTo(0,0));await expect(page.locator('.content-panel.active')).toHaveCSS('opacity','1');expect(await page.evaluate(()=>document.documentElement.scrollWidth)).toBeLessThanOrEqual(width);if(dialog)await expect(page.locator('#resourceDetailsDialog')).toBeVisible();if(name==='real-resource-attribution'){await page.locator('#resourceDetailsBody').getByRole('heading',{name:'Source attribution'}).scrollIntoViewIfNeeded();await expect(page.locator('#resourceDetailsBody').getByRole('heading',{name:'Source attribution'})).toBeInViewport();}const folder=path.resolve(audit,'screenshots',`${width}x${height}`);await fs.mkdir(folder,{recursive:true});await page.screenshot({path:path.join(folder,name+'.png'),animations:'disabled'});if(width===1366&&['real-journeys','real-admin-ingestion'].includes(name)){await fs.mkdir(path.join(audit,'review-states'),{recursive:true});await page.screenshot({path:path.join(audit,'review-states',name+'-full.png'),fullPage:true,animations:'disabled'});}}}
async function measure(api,categoryId){
 const endpoints={recommendations:()=>api.post('/api/users/me/recommendations?limit=10'),search:()=>api.get('/api/items/search?query=Java&pageSize=12'),catalog:()=>api.get('/api/items/search?pageSize=12'),saved:()=>api.get('/api/users/me/saved?pageSize=20'),activity:()=>api.get('/api/users/me/activity?pageSize=25'),adminCatalog:()=>api.get(process.env.PHASE4_MEASURE?.startsWith('baseline')?'/api/admin/items':'/api/admin/items/search?pageSize=20'),journey:()=>api.post('/api/users/me/journeys',{data:{title:'Isolated measured sequence',goal:'LEARN',categoryId,steps:3,requestId:crypto.randomUUID(),topic:'Java'}})};
 const result={};const questions=()=>Number(sql("SHOW GLOBAL STATUS LIKE 'Questions';").split(/\s+/).at(-1));
 const calibration=[];for(let i=0;i<5;i++){const before=questions();calibration.push(questions()-before);}const counterOverhead=Math.min(...calibration);
 for(const [name,run]of Object.entries(endpoints)){
  const samples=[],queryCounts=[],bytes=[];
  for(let i=0;i<18;i++){
   const before=i>=3&&i<6?questions():null,start=performance.now(),response=await run();const body=await response.body();expect(response.ok(),body.toString()).toBeTruthy();JSON.parse(body.toString());const elapsed=performance.now()-start,queries=before===null?null:questions()-before-counterOverhead;
   if(i>=3){samples.push(elapsed);if(queries!==null)queryCounts.push(queries);bytes.push(body.length);}
  }
  samples.sort((a,b)=>a-b);result[name]={samples:15,warmups:3,medianMs:samples[7],p95Ms:samples[14],counterProbeOverhead:counterOverhead,counterCalibrationDeltas:calibration,queryCounts,minimumQueries:Math.min(...queryCounts),maximumQueries:Math.max(...queryCounts),minimumBytes:Math.min(...bytes),maximumBytes:Math.max(...bytes)};
 }
 return result;
}

test('Phase 3.5 genuine ingestion is atomic, repeatable, authorized and preserves existing user relationships',async({page,browser,baseURL})=>{
 test.setTimeout(300000);const account=await accountFixture(page,true),api=page.request;
 const tech=account.niches.find(n=>n.nicheCode==='TECHNOLOGY'||n.name==='Technology');expect(tech).toBeTruthy();
 const java=tech.topics.find(t=>t.name==='Java');expect(java).toBeTruthy();await json(await api.post('/api/users/me/onboarding',{data:{purpose:'LEARN',categoryId:tech.categoryId,interestIds:[java.interestId]}}));
 const old=(await json(await api.get('/api/items/search?pageSize=12'))).items[0];
 await json(await api.post('/api/users/me/interactions',{data:{itemId:old.itemId,type:'SAVE'}}));await json(await api.post('/api/users/me/interactions',{data:{itemId:old.itemId,type:'COMPLETE'}}));
 const collection=await json(await api.post('/api/users/me/collections',{data:{name:'Preserved real-data import shelf'}}));expect((await api.put(`/api/users/me/collections/${collection.collectionId}/items/${old.itemId}`)).status()).toBe(204);
 const plan=await json(await api.post('/api/users/me/journeys',{data:{title:'Plan before genuine import',goal:'LEARN',categoryId:tech.categoryId,steps:2,requestId:crypto.randomUUID()}}));
 const originalPlan=await json(await api.get('/api/users/me/journeys/'+plan.journey.journeyId));
 const countBefore=Number(sql('SELECT COUNT(*) FROM items;'));
 const performanceEvidence={method:'Same isolated application and account, 3 warmups + 15 body-consumed HTTP samples, sequential endpoints; p95 is nearest-rank sample 15. Focused invocation selects only this spec. Query counts are MySQL global Questions deltas minus the minimum of five idle probe-pair deltas (including probe client setup). Counts include application transaction commands; periodic health checks may add one. Timing excludes the counter snapshots. Local single-host measurements, not production capacity.',baselineCatalog:countBefore};
 if(process.env.E2E_MEASURE==='true')performanceEvidence.before=await measure(api,tech.categoryId);
 const initial=sourceRecords.slice(0,2),preview=await json(await api.post('/api/admin/ingestion/preview',{data:{resources:initial}}));expect(preview.canImport).toBe(true);expect(preview.imported).toBe(2);
 // A controlled real MySQL error on the second genuine row proves rollback of the first row and batch header.
 const trigger='phase35_import_rollback';sql(`CREATE TRIGGER ${trigger} BEFORE INSERT ON items FOR EACH ROW SET NEW.title=IF(NEW.title=${literal(initial[1].title)},NULL,NEW.title);`,true);
 try{expect((await api.post('/api/admin/ingestion/commit',{data:{resources:initial,expectedSha256:preview.sha256}})).status()).toBe(409);expect(Number(sql('SELECT COUNT(*) FROM items;'))).toBe(countBefore);expect(Number(sql(`SELECT COUNT(*) FROM catalog_import_batches WHERE dataset_sha256=${literal(preview.sha256)};`))).toBe(0);}finally{sql(`DROP TRIGGER ${trigger};`,true);}
 const started=performance.now();const first=await json(await api.post('/api/admin/ingestion/commit',{data:{resources:initial,expectedSha256:preview.sha256}}));expect(first.imported).toBe(2);
 const repeated=await Promise.all([1,2].map(()=>api.post('/api/admin/ingestion/commit',{data:{resources:initial,expectedSha256:preview.sha256}}).then(json)));expect(repeated.every(r=>r.repeated&&r.imported===0)).toBe(true);
 let imported=first.imported,kept=0,review=0;
 for(let offset=0;offset<sourceRecords.length;offset+=250){const resources=sourceRecords.slice(offset,offset+250),p=await json(await api.post('/api/admin/ingestion/preview',{data:{resources}}));expect(p.rows.filter(r=>r.decision==='REJECT')).toEqual([]);const result=await json(await api.post('/api/admin/ingestion/commit',{data:{resources,expectedSha256:p.sha256}}));imported+=result.imported;kept+=result.skipped;review+=result.review;}
 const countAfter=Number(sql('SELECT COUNT(*) FROM items;'));expect(countAfter-countBefore).toBe(imported);
 const metadata=await json(await api.get('/api/admin/ingestion'));expect(metadata.statistics.reduce((n,r)=>n+Number(r.resources),0)).toBe(imported);
 expect(await json(await api.get('/api/users/me/journeys/'+plan.journey.journeyId))).toEqual(originalPlan);
 expect((await json(await api.get('/api/users/me/collections'))).find(c=>c.collectionId===collection.collectionId).itemIds).toEqual([old.itemId]);expect((await json(await api.get('/api/users/me/saved'))).items.some(i=>i.itemId===old.itemId)).toBe(true);
 const bad={...initial[0],license:'Unverified license'};expect((await json(await api.post('/api/admin/ingestion/preview',{data:{resources:[bad]}}))).canImport).toBe(false);expect((await api.post('/api/admin/ingestion/commit',{data:{resources:[bad],expectedSha256:preview.sha256}})).status()).toBe(400);
 expect((await json(await api.post('/api/admin/ingestion/preview',{data:{resources:[{...initial[0],canonicalUrl:'https://127.0.0.1/private'}]}}))).canImport).toBe(false);
 const existing=await json(await api.get('/api/items/search?query='+encodeURIComponent(initial[0].title)));const item=existing.items.find(i=>i.externalUrl===initial[0].canonicalUrl);expect(item).toBeTruthy();
 const manualTitle=item.title+' (isolated manual edit)';await json(await api.put('/api/admin/items/'+item.itemId,{headers:{'X-Expected-Revision':String(item.revision)},data:{...item,title:manualTitle,editorPick:false}}));
 const retryPreview=await json(await api.post('/api/admin/ingestion/preview',{data:{resources:initial}}));await json(await api.post('/api/admin/ingestion/commit',{data:{resources:initial,expectedSha256:retryPreview.sha256}}));expect((await json(await api.get('/api/items/'+item.itemId))).title).toBe(manualTitle);
 const outsider=await browser.newContext({baseURL}),ordinary=await outsider.newPage();await accountFixture(ordinary);expect((await ordinary.request.get('/api/admin/ingestion')).status()).toBe(403);expect((await ordinary.request.post('/api/admin/ingestion/commit',{data:{resources:initial,expectedSha256:preview.sha256}})).status()).toBe(403);await outsider.close();
 const anonymous=await playwrightRequest.newContext({baseURL});expect((await anonymous.get('/api/admin/ingestion')).status()).toBe(401);await anonymous.dispose();
 performanceEvidence.expandedCatalog=countAfter;performanceEvidence.importMs=performance.now()-started;
 if(process.env.E2E_MEASURE==='true'){performanceEvidence.after=await measure(api,tech.categoryId);await fs.writeFile(path.join(audit,'http-performance.json'),JSON.stringify(performanceEvidence,null,2));}
 await fs.writeFile(path.join(audit,'import-verification.json'),JSON.stringify({sourceRecords:sourceRecords.length,catalogBefore:countBefore,catalogAfter:countAfter,imported,preliminaryRowsReencountered:kept,review,rollbackVerified:true,concurrentRepeatedImportVerified:true,manualEditPreserved:true,ownerRelationshipsPreserved:true,statistics:metadata.statistics},null,2));
 const migration=spawnSync(process.execPath,['scripts/phase-3-5-migrations.mjs',process.env.E2E_COMPOSE_PROJECT],{encoding:'utf8'});expect(migration.status,migration.stdout+'\n'+migration.stderr).toBe(0);
 if(process.env.E2E_FULL_SUITE==='true'){
  const marker=path.join('test-results',process.env.E2E_COMPOSE_PROJECT,'ingestion-complete.json');
  await fs.mkdir(path.dirname(marker),{recursive:true});await fs.writeFile(marker,JSON.stringify({imported,review,rollbackVerified:true}));
 }
});

test('Phase 3.5 genuine catalog attribution and topic-focused journeys remain usable after reload',async({page})=>{
 test.setTimeout(180000);const account=await accountFixture(page),api=page.request;const tech=account.niches.find(n=>n.nicheCode==='TECHNOLOGY'||n.name==='Technology'),java=tech.topics.find(t=>t.name==='Java');await json(await api.post('/api/users/me/onboarding',{data:{purpose:'LEARN',categoryId:tech.categoryId,interestIds:[java.interestId]}}));
 const feed=await json(await api.post('/api/users/me/recommendations?topN=10'));expect(feed.length).toBeGreaterThan(0);
 await page.goto('/#app/recommendationsPanel');await expect(page.locator('#appShell')).toBeVisible();await capture(page,'real-for-you');
 await page.goto('/#app/catalogPanel');await expect(page.locator('#catalogItems .item-card').first()).toBeVisible();let releaseSearch;const heldSearch=new Promise(resolve=>{releaseSearch=resolve;});await page.route('**/api/items/search?*',async route=>{if(new URL(route.request().url()).searchParams.get('query')==='Introduction to Programming in Java')await heldSearch;await route.continue();});await page.locator('#catalogSearchQuery').fill('Introduction to Programming in Java');await page.locator('#catalogSearchForm button[type=submit]').click();try{await expect(page.locator('#catalogItems')).toBeHidden();await expect(page.locator('#catalogLoading')).toBeVisible();}finally{releaseSearch();}const card=page.locator('#catalogItems .item-card').filter({has:page.locator('h3',{hasText:'Introduction to Programming in Java'})}).first();await expect(card).toBeVisible();await capture(page,'real-explore');
 await card.getByRole('button',{name:'View',exact:true}).click();await expect(page.locator('#resourceDetailsBody')).toContainText('Source attribution');await expect(page.locator('#resourceDetailsBody')).toContainText('MIT OpenCourseWare');await expect(page.locator('#resourceDetailsBody a',{hasText:'CC-BY-NC-SA-4.0'})).toHaveAttribute('href','https://ocw.mit.edu/pages/privacy-and-terms-of-use/');await expect(page.locator('#resourceDetailsBody a',{hasText:'Open resource'})).toHaveAttribute('href',/https:\/\/ocw\.mit\.edu\/courses\//);await capture(page,'real-resource-details',true);await capture(page,'real-resource-attribution',true);
 await page.locator('#resourceDetailsBody').getByRole('button',{name:'Save resource'}).click();await page.locator('#resourceDetailsDialog [data-close-resource]').click();await page.goto('/#app/savedPanel');await expect(page.locator('#savedItems')).toContainText('Introduction to Programming in Java');await capture(page,'real-saved');
 await page.goto('/#app/journeysPanel');await page.locator('#journeyTitle').fill('A genuine Java learning sequence');await page.locator('#journeyCategory').selectOption(String(tech.categoryId));await page.locator('#journeySteps').fill('3');await page.locator('#journeyForm [name=topic]').fill('Java');const response=page.waitForResponse(r=>r.url().endsWith('/api/users/me/journeys')&&r.request().method()==='POST');await page.locator('#journeyForm button[type=submit]').click();const journey=await json(await response);expect(journey.steps.length).toBeGreaterThan(0);
 const provenance=await Promise.all(journey.steps.map(s=>api.get('/api/items/'+s.itemId+'/provenance').then(json)));expect(provenance.some(p=>p.source_id==='mit-ocw')).toBe(true);
 const first=journey.steps[0];await json(await api.patch(`/api/users/me/journeys/${journey.journey.journeyId}/steps/${first.stepId}`,{data:{status:'COMPLETED'}}));await page.reload();await expect(page.locator('#journeyDetail')).toContainText('user-marked complete');await capture(page,'real-journeys');
 const reloaded=await json(await api.get('/api/users/me/journeys/'+journey.journey.journeyId));expect(reloaded.completedSteps).toBe(1);expect(JSON.parse(reloaded.journey.preferenceSnapshot).requestedTopic).toBe('Java');
 await page.goto('/#app/progressPanel');await expect(page.locator('#journeyProgressSummary')).toContainText('A genuine Java learning sequence');await capture(page,'real-progress');
});

test('Phase 3.5 admin previews genuine metadata and repeats an import without duplicating resources',async({page})=>{
 test.setTimeout(120000);await accountFixture(page,true);await page.goto('/#app/adminPanel');await page.locator('[data-admin-section=resources]').click();await expect(page.locator('#adminItems')).not.toBeEmpty();await capture(page,'real-admin-catalog');
 await page.locator('[data-admin-section=import]').click();const sample=sourceRecords.filter(r=>r.title==='Introduction to Programming in Java');expect(sample).toHaveLength(1);await page.locator('#sourceMetadataFile').setInputFiles({name:'genuine-java-metadata.json',mimeType:'application/json',buffer:Buffer.from(JSON.stringify(sample))});await page.locator('#previewSourceImport').click();await expect(page.locator('#sourceImportStatus')).toContainText('Ready to import');await expect(page.locator('#sourceImportStatus')).toContainText('0 new');
 const before=Number(sql('SELECT COUNT(*) FROM items;'));await page.locator('#commitSourceImport').click();await expect(page.locator('#sourceImportStatus')).toContainText('Import finished: 0 added');await page.locator('#refreshSourceHistory').click();await expect(page.locator('#sourceRegistrySummary')).toContainText('MIT OpenCourseWare');await expect(page.locator('#sourceRegistrySummary')).toContainText('scheduled checks disabled');expect(Number(sql('SELECT COUNT(*) FROM items;'))).toBe(before);await capture(page,'real-admin-ingestion');
});
