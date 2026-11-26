import {test,expect} from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import {accountFixture} from './account-fixture.js';
import fs from 'node:fs/promises';

async function settled(page,selector){await expect(page.locator(selector).first()).toBeVisible({timeout:25000});await expect(page.locator('.loading-skeleton:visible')).toHaveCount(0);await page.evaluate(()=>document.fonts.ready);}

test('WCAG automated checks cover public forms and authenticated workspace surfaces',async({page})=>{
 test.setTimeout(180000);await page.emulateMedia({reducedMotion:'reduce'});const checks=[];
 async function audit(name,selector){await settled(page,selector);const result=await new AxeBuilder({page}).withTags(['wcag2a','wcag2aa','wcag21aa']).analyze();checks.push({name,violations:result.violations.map(v=>({id:v.id,impact:v.impact,help:v.help,helpUrl:v.helpUrl,nodes:v.nodes.map(n=>({target:n.target,failureSummary:n.failureSummary}))}))});}
 await page.goto('/');await audit('landing','#heroTitle');await page.locator('[data-auth=register]').first().click();await audit('registration','#registerForm');await page.keyboard.press('Escape');
 await accountFixture(page,true);await page.request.post('/api/users/me/recommendations?limit=10');await page.reload();
 for(const [name,panel,selector]of [['recommendations','recommendationsPanel','#goalFirstDashboard'],['catalog','catalogPanel','#catalogItems .item-card'],['saved','savedPanel','#libraryStatus'],['progress','progressPanel','[data-progress-completed]'],['profile','discoverPanel','#profileForm'],['settings','settingsPanel','#settingsAccount'],['admin','adminPanel','#adminSnapshot']]){await page.goto('/#app/'+panel);await audit(name,selector);}
 await fs.mkdir('design-audit/technical-quality',{recursive:true});await fs.writeFile('design-audit/technical-quality/accessibility-'+process.env.E2E_COMPOSE_PROJECT+'.json',JSON.stringify({tool:'@axe-core/playwright 4.14.1',scope:'Automated WCAG A/AA rule checks; not a complete conformance audit',checks},null,2));
 expect(checks.flatMap(c=>c.violations.map(v=>({screen:c.name,...v})))).toEqual([]);
});

test('a late catalog response cannot overwrite a newer search',async({page})=>{
 test.setTimeout(60000);await accountFixture(page);await page.goto('/#app/catalogPanel');await settled(page,'#catalogItems .item-card');
 let release,seen;const gate=new Promise(r=>release=r),requested=new Promise(r=>seen=r);let finished=false;
 await page.route('**/api/items/search?*',async route=>{
  if(new URL(route.request().url()).searchParams.get('query')!=='Java')return route.continue();
  const response=await route.fetch();seen();await gate;await route.fulfill({response});finished=true;
 });
 try{
  await page.locator('#catalogSearchQuery').fill('Java');await page.locator('#catalogSearchForm button[type=submit]').click();await requested;
  await page.locator('#catalogSearchQuery').fill('SQL');await page.locator('#catalogSearchForm button[type=submit]').click();
  await expect(page.locator('#catalogItems .item-card').first()).toContainText(/SQL/i);const titles=await page.locator('#catalogItems h3').allTextContents();expect(titles.length).toBeGreaterThan(0);
  release();await expect.poll(()=>finished).toBe(true);await expect(page.locator('#catalogItems h3')).toHaveText(titles);
 }finally{release();}
});

test('admin reload reads one paused record and preserves its authorization boundary',async({page,request,browser,baseURL})=>{
 test.setTimeout(90000);const admin=await accountFixture(page,true),api=page.request,title='Bounded reload '+crypto.randomUUID();
 const createdResponse=await api.post('/api/admin/items',{data:{categoryId:admin.niches[0].categoryId,title,description:'Isolated editor reload fixture.',type:'GUIDE',externalUrl:'https://example.com/technical-reload/'+crypto.randomUUID(),sourceName:'Isolated fixture',difficulty:'UNKNOWN',qualityScore:.5}});expect(createdResponse.status()).toBe(201);const created=await createdResponse.json();
 const pausedResponse=await api.patch('/api/admin/items/'+created.itemId+'/active',{headers:{'X-Expected-Revision':String(created.revision)},data:{active:false}});expect(pausedResponse.ok()).toBe(true);
 expect((await request.get('/api/admin/items/'+created.itemId)).status()).toBe(401);
 const memberContext=await browser.newContext({baseURL});try{const member=await memberContext.newPage();await accountFixture(member);expect((await member.request.get('/api/admin/items/'+created.itemId)).status()).toBe(403);}finally{await memberContext.close();}
 expect((await request.get('/api/items/'+created.itemId)).status()).toBe(404);
 const record=await api.get('/api/admin/items/'+created.itemId);expect(record.ok()).toBe(true);expect((await record.json()).active).toBe(false);
 await page.goto('/#app/adminPanel');await page.locator('[data-admin-section=resources]').click();await page.locator('#adminCatalogSearch').fill(title);await expect(page.locator('#adminItems tbody tr')).toHaveCount(1);await page.locator('#adminItems tbody tr').getByRole('button',{name:'Edit',exact:true}).click();
 await page.locator('#itemForm [name=title]').fill('Unsaved editor draft');const reads=[];page.on('request',r=>{if(r.method()==='GET')reads.push(new URL(r.url()).pathname);});page.once('dialog',d=>d.accept());await page.getByRole('button',{name:'Reload current resource',exact:true}).click();await expect(page.locator('#itemForm [name=title]')).toHaveValue(title);expect(reads).toContain('/api/admin/items/'+created.itemId);expect(reads).not.toContain('/api/admin/items');
 const overview=await(await api.get('/api/admin/ingestion')).json();expect(Array.isArray(overview.linkHealth)).toBe(true);expect(overview.linkHealth.reduce((sum,row)=>sum+Number(row.resources),0)).toBe(overview.statistics.reduce((sum,row)=>sum+Number(row.resources),0));
});
