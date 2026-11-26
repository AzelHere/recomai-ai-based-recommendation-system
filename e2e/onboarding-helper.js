import { expect } from '@playwright/test';
export async function onboardingStage(page, target) {
 for (let count=0;count<5;count++) {
  const index=await page.locator('[data-onboarding-step]').evaluateAll(nodes=>nodes.findIndex(node=>!node.hidden));
  if(index===target)return;
  if(index>target)await page.getByRole('button',{name:'Back',exact:true}).click();
  else await page.locator('#onboardingContinue').click();
 }
 await expect(page.locator(`[data-onboarding-step="${target}"]`)).toBeVisible();
}
