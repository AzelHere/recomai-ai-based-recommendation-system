import { onboardingStage } from './onboarding-helper.js';
import {test,expect} from '@playwright/test';
import fs from 'node:fs/promises';

test('diagnose onboarding HTTP failures and verify For You and View behavior',async({page})=>{
 test.setTimeout(120000);
 const responses=[]; const consoleErrors=[]; let authenticated=false;
 page.on('response',async response=>{
  if(response.status()>=400){let body='';try{body=await response.text()}catch{} responses.push({phase:authenticated?'after-auth':'before-auth',url:response.url(),method:response.request().method(),status:response.status(),body});}
 });
 page.on('console',message=>{if(message.type()==='error')consoleErrors.push(message.text())});
 await page.goto('/'); await expect(page.locator('#landingPage')).toBeVisible();
 const id=Date.now(); await page.locator('[data-auth="register"]').first().click();
 await page.locator('#registerForm [name=displayName]').fill('Onboarding diagnostics');
 await page.locator('#registerForm [name=username]').fill(`diag_${id}`);
 await page.locator('#registerForm [name=email]').fill(`diag_${id}@example.test`);
 await page.locator('#registerForm [name=password]').fill('Diag-test-password-492!');
 await page.getByRole('button',{name:/Create your account/}).click(); await expect(page.locator('#appShell')).toBeVisible({timeout:25000}); authenticated=true;
 await expect(page.locator('#onboardingPanel')).toHaveClass(/active/);
 const niche=page.locator('#onboardingNiche'); await expect.poll(()=>niche.locator('option').count()).toBeGreaterThan(1); await onboardingStage(page,1); await niche.selectOption({index:1}); await onboardingStage(page,0); await page.locator('#onboardingPurpose').selectOption('LEARN');
 const topic=page.locator('#onboardingTopics input').first();if(await topic.count()){await onboardingStage(page,2); await topic.check();}
 const onboardResponse=page.waitForResponse(r=>new URL(r.url()).pathname==='/api/users/me/onboarding'&&r.request().method()==='POST');
 await onboardingStage(page,3); await page.locator('#onboardingForm button[type=submit]').click(); const onboard=await onboardResponse; expect(onboard.status()).toBe(200);
 await expect(page.locator('#recommendationsPanel')).toHaveClass(/active/); await expect(page.locator('#goalFirstDashboard')).toBeVisible({timeout:20000});
 const cards=page.locator('#nextStepCard'); await expect(cards.first()).toBeVisible({timeout:20000});
 const title=await cards.first().locator('h3').textContent();
 const view=page.locator('#catalogItems .item-card').first().getByRole('button',{name:'View',exact:true});
 await page.locator('.nav-link[data-panel="catalogPanel"]').click(); await expect(page.locator('#catalogItems .item-card').first()).toBeVisible({timeout:10000});
 const card=page.locator('#catalogItems .item-card').first(); const cardTitle=await card.locator('h3').textContent();
 const viewRequest=page.waitForResponse(r=>new URL(r.url()).pathname==='/api/users/me/interactions'&&r.request().method()==='POST'&&r.request().postDataJSON()?.type==='VIEW');
 await card.getByRole('button',{name:'View',exact:true}).click(); const viewed=await viewRequest; expect(viewed.status()).toBe(201);
 await expect(card.locator('h3')).toHaveText(cardTitle);
 await fs.writeFile('design-audit/diagnostic-results.json',JSON.stringify({onboarding:{status:onboard.status(),destination:'recommendationsPanel',recommendationSelectedTitle:title},view:{status:viewed.status(),requestBody:viewRequest?viewed.request().postDataJSON():null,visibleCardTitle:cardTitle,detailsInterface:'No separate detail modal/panel is opened by View; it records a VIEW interaction while card remains in the catalog.'},httpErrors:responses,consoleErrors},null,2));
});
