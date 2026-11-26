import {test,expect} from '@playwright/test';
import {accountFixture} from './account-fixture.js';
import fs from 'node:fs/promises';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {inspectContrast} from './phase4-contrast.js';

test('Phase 4 populated desktop evidence',async({page})=>{
 test.skip(!['baseline','final'].includes(process.env.PHASE4_CAPTURE),'Explicit isolated visual audit only');
 test.setTimeout(300000);
 const stage=process.env.PHASE4_CAPTURE,root='design-audit/recomai-2-phase-4',manifest=[],errors=[],contrast=[];
 page.on('pageerror',e=>errors.push(e.message));
 await page.emulateMedia({reducedMotion:'reduce'});
 async function capture(name,route,ready){
  await page.goto(route);await expect(page.locator(ready).first()).toBeVisible({timeout:25000});
  if(name==='admin')await expect(page.locator('#adminSnapshot')).toContainText('1,865');
  if(name==='profile')await expect(page.locator('#accountDisplayName')).toHaveText('Admin audit fixture');
  if(name==='settings')await expect(page.locator('#settingsAccount')).toContainText('Admin audit fixture');
  if(name==='progress')await expect(page.locator('#journeyProgressSummary')).toContainText('Build a Java foundation');
  if(name==='journeys')await expect(page.locator('#journeyDetail')).toContainText('Build a Java foundation');
  if(name==='my-taste')await expect(page.locator('#tasteTopics')).toContainText('Java');
  await page.evaluate(()=>document.fonts.ready);
  await expect(page.locator('.loading-skeleton:visible')).toHaveCount(0);
  for(const [width,height]of [[1366,768],[1440,900],[1536,864],[1920,1080],[2560,1440]]){
   await page.setViewportSize({width,height});await page.evaluate(()=>scrollTo(0,0));
   await expect.poll(()=>page.evaluate(()=>document.documentElement.scrollWidth)).toBeLessThanOrEqual(width);
   if(name==='explore'){
    const clipped=await page.locator('#catalogItems .item-actions .small-action').evaluateAll(buttons=>buttons.filter(button=>{
     const bounds=button.getBoundingClientRect();if(!bounds.width||!bounds.height)return false;
     const range=document.createRange();range.selectNodeContents(button);const text=range.getBoundingClientRect();
     return text.left<bounds.left-1||text.right>bounds.right+1||text.top<bounds.top-1||text.bottom>bounds.bottom+1;
    }).map(button=>button.textContent));
    expect(clipped).toEqual([]);
   }
   const file=`screenshots/${stage}/${name}-${width}x${height}.png`;
   await fs.mkdir(path.dirname(path.join(root,file)),{recursive:true});
   const bytes=await page.screenshot({path:path.join(root,file),animations:'disabled'});
   manifest.push({stage,page:name,route,width,height,file,sha256:createHash('sha256').update(bytes).digest('hex')});
   if(width===1440)contrast.push({page:name,...await page.evaluate(inspectContrast)});
  }
 }
 await capture('landing','/','#heroTitle');
 const account=await accountFixture(page,true),api=page.request;
 async function json(response){expect(response.ok(),await response.text()).toBeTruthy();return response.json();}
 const records=JSON.parse(await fs.readFile('data/processed/educational-resources.json','utf8'));
 for(let i=0;i<records.length;i+=250){const resources=records.slice(i,i+250),p=await json(await api.post('/api/admin/ingestion/preview',{data:{resources}}));await json(await api.post('/api/admin/ingestion/commit',{data:{resources,expectedSha256:p.sha256}}));}
 const tech=account.niches.find(n=>n.name==='Technology'),java=tech.topics.find(t=>t.name==='Java');
 await json(await api.post('/api/users/me/onboarding',{data:{purpose:'LEARN',categoryId:tech.categoryId,interestIds:[java.interestId]}}));
 const items=(await json(await api.get('/api/items/search?query=Java&pageSize=5'))).items;
 for(const item of items.slice(0,3))await json(await api.post('/api/users/me/interactions',{data:{itemId:item.itemId,type:'SAVE'}}));
 await json(await api.post('/api/users/me/interactions',{data:{itemId:items[0].itemId,type:'COMPLETE'}}));
 const plan=await json(await api.post('/api/users/me/journeys',{data:{title:'Build a Java foundation',goal:'LEARN',categoryId:tech.categoryId,steps:3,topic:'Java',requestId:crypto.randomUUID()}}));
 await json(await api.patch(`/api/users/me/journeys/${plan.journey.journeyId}/steps/${plan.steps[0].stepId}`,{data:{status:'COMPLETED'}}));
 await json(await api.post('/api/users/me/recommendations?limit=10'));
 // Registration used the API after the public landing loaded: initialize the browser session state.
 await page.reload();
 for(const [name,id,ready]of [
  ['for-you','recommendationsPanel','#goalFirstDashboard'],['explore','catalogPanel','#catalogItems .item-card'],
  ['onboarding','onboardingPanel','#onboardingForm'],['my-taste','interestProfilePanel','#tasteTopics'],
  ['saved','savedPanel','#savedItems .item-card'],['progress','progressPanel','#journeyProgressSummary'],
  ['activity','activityPanel','#activityPanel'],['journeys','journeysPanel','#journeyDetail'],
  ['profile','discoverPanel','#profileForm'],['settings','settingsPanel','#settingsAccount'],['admin','adminPanel','#adminSnapshot']
 ])await capture(name,'/#app/'+id,ready);
 expect(errors).toEqual([]);expect(manifest).toHaveLength(60);
 if(stage==='final')expect(contrast.flatMap(row=>row.failures)).toEqual([]);
 await fs.writeFile(path.join(root,`${stage}-screenshots.json`),JSON.stringify({capturedAt:new Date().toISOString(),fixture:'Generated isolated audit account; 1865 resources, saved resources, completion and journey',errors,contrast,screenshots:manifest},null,2));
});
