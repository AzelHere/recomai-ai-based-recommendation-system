import {test,expect} from '@playwright/test';
import {accountFixture} from './account-fixture.js';
import fs from 'node:fs/promises';
const audit='design-audit/article-integration';
const records=JSON.parse(await fs.readFile('data/processed/educational-articles.json','utf8'));
async function json(response){expect(response.ok(),await response.text()).toBe(true);return response.json();}
async function capture(page,name){if(!name.startsWith('before-'))await page.locator('#applyCatalogFilters').click();for(const [width,height]of [[1366,768],[1440,900],[1920,1080]]){await page.setViewportSize({width,height});await page.evaluate(()=>scrollTo(0,0));await expect(page.locator('#catalogPanel')).toBeVisible();expect(await page.evaluate(()=>document.documentElement.scrollWidth)).toBeLessThanOrEqual(width);await fs.mkdir(`${audit}/screenshots/${width}x${height}`,{recursive:true});await page.screenshot({path:`${audit}/screenshots/${width}x${height}/${name}.png`,animations:'disabled'});}}

test('Verified articles filter, import, ranking, library, journey and administration work together',async({page})=>{
 test.setTimeout(300000);
 if(process.env.E2E_FULL_SUITE==='true')await expect.poll(()=>fs.access(`test-results/${process.env.E2E_COMPOSE_PROJECT}/phase4-complete.json`).then(()=>true,()=>false),{timeout:120000}).toBe(true);
 const errors=[];page.on('pageerror',e=>errors.push(e.message));
 const account=await accountFixture(page,true),api=page.request,tech=account.niches.find(n=>n.nicheCode==='TECHNOLOGY'||n.name==='Technology'),java=tech.topics.find(t=>t.name==='Java');
 await json(await api.post('/api/users/me/onboarding',{data:{purpose:'LEARN',categoryId:tech.categoryId,interestIds:[java.interestId]}}));
 const before=await json(await api.get('/api/items/search?type=ARTICLE&pageSize=1'));
 expect((await json(await api.get('/api/items/search?type=ARTICLE&provider=Microsoft%20Learn'))).totalItems).toBe(0);
 await page.goto('/#app/catalogPanel');await page.locator('#typeFilter').selectOption('ARTICLE');await page.locator('#categoryFilter').selectOption(String(tech.categoryId));
 // Only the focused fresh-catalog run is a genuine empty baseline; other suite files create explicit synthetic articles.
 if(process.env.E2E_FULL_SUITE!=='true'){await expect(page.locator('#catalogEmpty')).toBeVisible();await capture(page,'before-technology-articles');}
 // Build the actual corpus cache before importing; post-import recommendations must include new IDs.
 await json(await api.post('/api/users/me/recommendations?limit=50'));
 let imported=0;const receipts=[];
 for(let i=0;i<records.length;i+=250){const resources=records.slice(i,i+250);const preview=await json(await api.post('/api/admin/ingestion/preview',{data:{resources}}));expect(preview.canImport).toBe(true);expect(preview.review).toBe(0);expect(preview.existing).toBe(0);const result=await json(await api.post('/api/admin/ingestion/commit',{data:{resources,expectedSha256:preview.sha256}}));expect(result.imported).toBe(resources.length);imported+=result.imported;receipts.push({preview,result});}
 const after=await json(await api.get('/api/items/search?type=ARTICLE&pageSize=100&sort=TITLE_ASC'));
 expect(imported).toBe(records.length);expect(after.totalItems).toBe(before.totalItems+records.length);expect(after.items.every(i=>i.type==='ARTICLE')).toBe(true);
 const repeat=await json(await api.post('/api/admin/ingestion/preview',{data:{resources:records.slice(0,250)}}));expect(repeat.imported).toBe(0);expect(repeat.existing).toBe(250);
 expect((await json(await api.post('/api/admin/ingestion/commit',{data:{resources:records.slice(0,250),expectedSha256:repeat.sha256}}))).imported).toBe(0);
 const second=await json(await api.get('/api/items/search?type=ARTICLE&pageSize=100&page=2&sort=TITLE_ASC'));expect(second.items).toHaveLength(100);expect(second.items.some(i=>after.items.some(j=>i.itemId===j.itemId))).toBe(false);
 const queries={};for(const query of ['Java','data structures','SQL','artificial intelligence','finance','career']){const p=await json(await api.get('/api/items/search?type=ARTICLE&pageSize=100&query='+encodeURIComponent(query)));expect(p.totalItems,query).toBeGreaterThan(0);expect(p.items.every(i=>i.type==='ARTICLE')).toBe(true);queries[query]={total:p.totalItems,titles:p.items.slice(0,4).map(i=>i.title)};}
 const combo=await json(await api.get('/api/items/search?type=ARTICLE&topic=Java&query=Java&difficulty=UNKNOWN&provider=Microsoft%20Learn&categoryId='+tech.categoryId+'&sort=TITLE_ASC&pageSize=2'));
 expect(combo.totalItems).toBeGreaterThan(2);expect(combo.items).toHaveLength(2);expect(combo.items.every(i=>i.type==='ARTICLE'&&i.sourceName==='Microsoft Learn'&&i.categoryId===tech.categoryId&&i.difficulty==='UNKNOWN'&&i.tags.includes('Java')&&i.estimatedMinutes===null)).toBe(true);
 expect((await json(await api.get('/api/items/search?type=ARTICLE&provider=Microsoft%20Learn&maximumTime=15'))).totalItems).toBe(0);
 expect((await json(await api.get('/api/items/search?type=ARTICLE&topic=not-a-real-topic&query=Java'))).totalItems).toBe(0);
 for(const type of ['ARTICLE','COURSE','VIDEO','DOCUMENTATION','GUIDE','RESOURCE','TOOL','PROJECT','CONTENT','PRODUCT','OTHER']){const p=await json(await api.get('/api/items/search?type='+type+'&pageSize=100'));expect(p.items.every(i=>i.type===type)).toBe(true);}
 expect((await api.get('/api/items/search?type=BOOK')).status()).toBe(400);
 const article=combo.items[0],provenance=await json(await api.get('/api/items/'+article.itemId+'/provenance'));expect(provenance.source_id).toBe('microsoft-learn');expect(provenance.license).toBe('CC-BY-4.0');
 await json(await api.post('/api/users/me/interactions',{data:{itemId:article.itemId,type:'CLICK'}}));
 expect((await json(await api.get('/api/users/me/activity?type=COMPLETE&pageSize=100'))).items).toHaveLength(0);
 const feed=await json(await api.post('/api/users/me/recommendations?limit=50'));expect(feed.some(i=>records.some(r=>r.title===i.itemTitle))).toBe(true);expect(feed.every(i=>i.reason&&i.scoreBreakdown)).toBe(true);
 await json(await api.post('/api/users/me/interactions',{data:{itemId:article.itemId,type:'SAVE'}}));
 const saved=await json(await api.get('/api/users/me/saved?pageSize=100'));expect(saved.items.some(i=>(i.resource?.itemId||i.itemId)===article.itemId)).toBe(true);
 const collection=await json(await api.post('/api/users/me/collections',{data:{name:'Verified article shelf'}}));expect((await api.put(`/api/users/me/collections/${collection.collectionId}/items/${article.itemId}`)).status()).toBe(204);
 const shelf=await json(await api.get(`/api/users/me/saved?collectionId=${collection.collectionId}&pageSize=100`));expect(JSON.stringify(shelf)).toContain(article.title);
 await json(await api.post('/api/users/me/interactions',{data:{itemId:article.itemId,type:'READ_PROGRESS'}}));await json(await api.post('/api/users/me/interactions',{data:{itemId:article.itemId,type:'COMPLETE'}}));
 const activity=await json(await api.get('/api/users/me/activity?pageSize=100'));expect(activity.items.some(i=>i.itemId===article.itemId&&i.type==='COMPLETE')).toBe(true);
 const journey=await json(await api.post('/api/users/me/journeys',{data:{title:'Verified Java readings',goal:'LEARN',categoryId:tech.categoryId,topic:'Java',steps:10,requestId:crypto.randomUUID()}}));
 expect(journey.steps.some(s=>records.some(r=>r.title===s.title||r.title===s.itemTitle))).toBe(true);
 await json(await api.post('/api/users/me/interactions',{data:{itemId:article.itemId,type:'HIDE'}}));
 expect((await json(await api.post('/api/users/me/recommendations?limit=50'))).some(i=>i.itemId===article.itemId)).toBe(false);
 const admin=await json(await api.get('/api/admin/items/search?type=ARTICLE&query=Java&pageSize=20'));expect(admin.totalItems).toBeGreaterThan(0);expect(admin.items.every(i=>i.type==='ARTICLE')).toBe(true);
 await page.reload();await page.locator('#typeFilter').selectOption('ARTICLE');await page.locator('#categoryFilter').selectOption(String(tech.categoryId));await expect(page.locator('#catalogItems .item-card').first()).toBeVisible();await expect(page.locator('#catalogItems a.action-open').first()).toHaveText('Read Article ↗');await capture(page,'after-technology-articles');
 await page.locator('#catalogFilterPanel summary').click();await page.locator('#providerFilter').fill('Microsoft Learn');await page.locator('#catalogSearchQuery').fill('Java');await page.locator('#catalogSearchForm button[type=submit]').click();await expect(page.locator('#catalogItems .item-card').first()).toContainText('Java');await capture(page,'java-articles');
 const card=page.locator('#catalogItems .item-card').first();await card.getByRole('button',{name:'View',exact:true}).click();await expect(page.locator('#resourceDetailsBody')).toContainText('Source attribution');await expect(page.locator('#resourceDetailsBody').getByRole('link',{name:'Read Article ↗'})).toHaveAttribute('href',/^https:\/\/learn.microsoft.com\//);await page.locator('#resourceDetailsDialog [data-close-resource]').click();
 const visibleTitle=await card.locator('h3').innerText();const saveResponse=page.waitForResponse(r=>new URL(r.url()).pathname==='/api/users/me/interactions'&&r.request().method()==='POST');await card.getByRole('button',{name:'Save',exact:true}).click();expect((await saveResponse).ok()).toBe(true);await page.goto('/#app/savedPanel');await expect(page.locator('#savedItems')).toContainText(visibleTitle);
 expect(errors).toEqual([]);await fs.writeFile(`${audit}/browser-${process.env.PLAYWRIGHT_WORKERS||'run'}.json`,JSON.stringify({isolatedProject:process.env.E2E_COMPOSE_PROJECT,imported,baselineArticleCount:before.totalItems,articleCount:after.totalItems,queries,combinedFilters:true,disjointPagination:true,duplicateIdempotency:true,postImportCacheRefresh:true,saved:true,collection:true,activity:true,journey:true,admin:true,receipts},null,2));
});
