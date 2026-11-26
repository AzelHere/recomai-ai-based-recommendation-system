import {test,expect} from '@playwright/test';
import {accountFixture} from './account-fixture.js';
import fs from 'node:fs/promises';
import {createHash} from 'node:crypto';

test('publication gallery from an isolated demonstration account',async({page})=>{
 test.skip(process.env.PUBLICATION_CAPTURE!=='1','Explicit publication screenshots only');
 test.setTimeout(240000);await page.setViewportSize({width:1440,height:900});await page.emulateMedia({reducedMotion:'reduce'});
 const errors=[],manifest=[];page.on('pageerror',e=>errors.push(e.message));await fs.mkdir('docs/screenshots',{recursive:true});
 async function shot(name,ready){
  await expect(page.locator(ready).first()).toBeVisible({timeout:25000});await expect(page.locator('.loading-skeleton:visible')).toHaveCount(0);
  await page.evaluate(async()=>{await document.fonts.ready;scrollTo(0,0);document.getAnimations().filter(a=>a.effect?.getTiming().iterations!==Infinity).forEach(a=>a.finish());});
  expect(await page.evaluate(()=>document.documentElement.scrollWidth)).toBeLessThanOrEqual(1440);
  const bytes=await page.screenshot({path:`docs/screenshots/${name}.png`,animations:'disabled'});manifest.push({file:`${name}.png`,width:1440,height:900,sha256:createHash('sha256').update(bytes).digest('hex')});
 }
 async function route(id,ready){await page.goto('/#app/'+id);await expect(page.locator('#'+id)).toHaveClass(/active/);await expect(page.locator(ready).first()).toBeVisible();}
 async function json(p){const response=await p;expect(response.ok(),await response.text()).toBeTruthy();return response.json();}
 await page.goto('/');await expect(page).toHaveTitle(/Recom AI/);await shot('01-landing-page','#heroTitle');
 await page.locator('[data-auth="register"]').first().click();await shot('02-authentication','#registerForm');await page.keyboard.press('Escape');
 const account=await accountFixture(page,true),api=page.request;
 for(const filename of ['educational-resources.json','educational-articles.json']){
  const all=JSON.parse(await fs.readFile('data/processed/'+filename,'utf8'));
  for(let i=0;i<all.length;i+=250){const resources=all.slice(i,i+250),preview=await json(api.post('/api/admin/ingestion/preview',{data:{resources}}));await json(api.post('/api/admin/ingestion/commit',{data:{resources,expectedSha256:preview.sha256}}));}
 }
 expect((await json(api.get('/api/items/search?pageSize=1'))).totalItems).toBe(2365);
 expect((await json(api.get('/api/items/search?type=ARTICLE&pageSize=1'))).totalItems).toBe(505);
 const tech=account.niches.find(n=>n.name==='Technology'),java=tech.topics.find(t=>t.name==='Java');
 await json(api.post('/api/users/me/onboarding',{data:{purpose:'LEARN',categoryId:tech.categoryId,interestIds:[java.interestId]}}));
 await page.reload();await route('interestProfilePanel','#tasteIdentity');await page.getByRole('button',{name:'Update onboarding choices',exact:true}).click();await expect(page.locator('#onboardingPanel')).toHaveClass(/active/);
 // Walk real form stages; no DOM state or screenshot content is synthesized.
 for(let i=0;i<4;i++){if(await page.locator('[data-onboarding-step="0"]').isVisible())break;await page.getByRole('button',{name:'Back',exact:true}).click();}
 await shot('03-learning-goals','[data-onboarding-step="0"]');await page.locator('#onboardingPurpose').selectOption('LEARN');await page.locator('#onboardingContinue').click();await shot('04-interest-selection','[data-onboarding-step="1"]');
 const items=(await json(api.get('/api/items/search?type=ARTICLE&query=Java&pageSize=5'))).items;
 for(const item of items.slice(0,3))await json(api.post('/api/users/me/interactions',{data:{itemId:item.itemId,type:'SAVE'}}));
 await json(api.post('/api/users/me/interactions',{data:{itemId:items[0].itemId,type:'COMPLETE'}}));await json(api.post('/api/users/me/interactions',{data:{itemId:items[1].itemId,type:'READ_PROGRESS'}}));
 const shelf=await json(api.post('/api/users/me/collections',{data:{name:'Java reading shelf'}}));expect((await api.put(`/api/users/me/collections/${shelf.collectionId}/items/${items[0].itemId}`)).ok()).toBeTruthy();
 const plan=await json(api.post('/api/users/me/journeys',{data:{title:'Build a Java foundation',goal:'LEARN',categoryId:tech.categoryId,topic:'Java',steps:3,requestId:crypto.randomUUID()}}));
 await json(api.patch(`/api/users/me/journeys/${plan.journey.journeyId}/steps/${plan.steps[0].stepId}`,{data:{status:'COMPLETED'}}));await json(api.post('/api/users/me/recommendations?limit=10'));
 await route('recommendationsPanel','#goalFirstDashboard');await shot('05-personalized-recommendations','#goalFirstDashboard');
 await page.locator('#nextStepCard').getByRole('button',{name:'View details →',exact:true}).click();await expect(page.locator('.resource-detail-explanation').filter({hasText:'Why this was recommended'})).toContainText('Why this was recommended');await shot('06-recommendation-explanation','#resourceDetailsBody');await page.keyboard.press('Escape');
 await route('catalogPanel','#catalogItems .item-card');await shot('07-explore-catalog','#catalogItems .item-card');await page.locator('#typeFilter').selectOption('ARTICLE');await expect(page.locator('#catalogItems a.action-open').first()).toHaveText('Read Article ↗');await shot('08-articles-filter','#catalogItems .item-card');
 if(!await page.locator('#catalogFilterPanel').evaluate(n=>n.open))await page.locator('#catalogFilterPanel summary').click();await page.locator('#providerFilter').fill('Microsoft Learn');await page.locator('#catalogSearchQuery').fill('Java');await page.locator('#catalogSearchForm button[type=submit]').click();await expect(page.locator('#catalogItems .item-card').first()).toContainText('Java');await shot('09-search-and-filters','#catalogItems .item-card');
 await page.locator('#catalogItems .item-card').first().getByRole('button',{name:'View',exact:true}).click();await expect(page.locator('#resourceDetailsBody')).toContainText('Source attribution');await shot('18-resource-provenance','#resourceDetailsBody');await page.keyboard.press('Escape');
 for(const [name,id,ready]of [['10-saved-resources','savedPanel','#savedItems .item-card'],['11-collections','savedPanel','#collectionNavigation'],['12-learning-journey','journeysPanel','#journeyDetail'],['13-learning-progress','progressPanel','#journeyProgressSummary'],['14-activity-history','activityPanel','#activityList .activity-row'],['15-profile-settings','settingsPanel','#settingsAccount'],['16-admin-dashboard','adminPanel','#adminSnapshot'],['17-catalog-management','adminPanel','#adminItems']]){
  if(name==='17-catalog-management'){await page.goto('/#app/adminPanel');await page.locator('[data-admin-section=resources]').click();}else await route(id,ready);if(name==='11-collections')await page.locator('#collectionSelect').selectOption({label:'Java reading shelf'});await shot(name,ready);
 }
 expect(errors).toEqual([]);await fs.writeFile('docs/screenshots/manifest.json',JSON.stringify({capturedAt:new Date().toISOString(),application:'Recom AI',fixture:'Isolated generated demonstration account; no private database',errors,screenshots:manifest},null,2));
});
