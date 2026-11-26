import { api } from './api-client.js';
const el = id => document.getElementById(id);
let step = 0;
export function preferenceMap(labels) {
 const ns='http://www.w3.org/2000/svg', svg=document.createElementNS(ns,'svg');
 svg.setAttribute('viewBox','0 0 400 260');svg.setAttribute('role','img');svg.setAttribute('aria-label','Selected interests: '+(labels.join(', ')||'none yet'));
 const positions=[[200,115],[85,65],[315,65],[85,205],[315,205]];
 labels.slice(0,5).forEach((label,i)=>{const [x,y]=positions[i];const line=document.createElementNS(ns,'line');for(const [key,value] of Object.entries({x1:200,y1:115,x2:x,y2:y}))line.setAttribute(key,value);svg.append(line);const circle=document.createElementNS(ns,'circle');circle.setAttribute('cx',x);circle.setAttribute('cy',y);circle.setAttribute('r',i?10:25);svg.append(circle);const text=document.createElementNS(ns,'text');text.setAttribute('x',x);text.setAttribute('y',y+36);text.setAttribute('text-anchor','middle');text.textContent=label.length>23?label.slice(0,21)+'…':label;svg.append(text);});return svg;
}
export function syncWizard() {
 const labels=['onboardingPurpose','onboardingNiche'].map(id=>el(id).selectedOptions[0]?.textContent||'Not selected');
 labels.push(...Array.from(el('onboardingTopics').querySelectorAll('input:checked'),input=>input.parentElement.textContent.trim()));
 el('onboardingMap').replaceChildren(preferenceMap(labels));el('onboardingSummary').textContent=labels.join(' · ');el('onboardingReview').textContent=labels.join(' · ');
 document.querySelectorAll('[data-choice-value]').forEach(button=>button.setAttribute('aria-pressed',String(el(button.dataset.choiceSelect).value===button.dataset.choiceValue)));
}
export function wizardStep(next,focus=true) {
 step=next;document.querySelectorAll('[data-onboarding-step]').forEach((section,i)=>{section.hidden=i!==step;if(i===step&&focus)section.querySelector('h3').focus();});
 document.querySelectorAll('.step-track li').forEach((item,i)=>{if(i===step)item.setAttribute('aria-current','step');else item.removeAttribute('aria-current');});
 el('onboardingBack').disabled=step===0;el('onboardingContinue').hidden=step===3;el('onboardingForm').querySelector('[type=submit]').hidden=step!==3;el('onboardingStatus').textContent='';syncWizard();
}
export function buildChoices() {
 for(const [selectId,containerId] of [['onboardingPurpose','goalCards'],['onboardingNiche','areaCards']]) {
  el(containerId).replaceChildren();Array.from(el(selectId).options).filter(option=>option.value).forEach((option,index)=>{const button=document.createElement('button');button.type='button';button.className='preference-choice';button.dataset.choiceValue=option.value;button.dataset.choiceSelect=selectId;const number=document.createElement('span');number.textContent=String(index+1).padStart(2,'0');const title=document.createElement('strong');title.textContent=option.textContent;button.append(number,title);button.addEventListener('click',()=>{el(selectId).value=option.value;el(selectId).dispatchEvent(new Event('change',{bubbles:true}));syncWizard();});el(containerId).append(button);});
 }syncWizard();
}
export function initializePersonalization() {
 el('onboardingBack').onclick=()=>wizardStep(Math.max(0,step-1));
 el('onboardingContinue').onclick=()=>{const required=step===0?el('onboardingPurpose'):step===1?el('onboardingNiche'):null;if(required&&!required.value){el('onboardingStatus').textContent=step===0?'Choose a goal to continue.':'Choose an area to continue.';required.focus();return;}wizardStep(Math.min(3,step+1));};
 el('clearOnboardingTopics').onclick=()=>{el('onboardingTopics').querySelectorAll('input').forEach(input=>{input.checked=false;input.disabled=false;});syncWizard();};
 el('onboardingForm').addEventListener('change',()=>{el('onboardingStatus').textContent='';syncWizard();});wizardStep(0,false);
}
let tasteSaved=new Set(), tasteVersion=0;
export async function loadTaste() {
 const version=++tasteVersion;el('tasteStatus').textContent='Loading saved interests…';el('tasteForm').querySelector('[type=submit]').disabled=true;
 try {
  const [status,profile,niches]=await Promise.all([api('/users/me/onboarding'),api('/users/me/recommendation-profile'),api('/discovery/niches')]);if(version!==tasteVersion)return;
  tasteSaved=new Set((profile.interests||[]).map(row=>String(row.interestId)));
  const identity=el('tasteIdentity');identity.replaceChildren();const copy=document.createElement('div'),eyebrow=document.createElement('p'),heading=document.createElement('h3'),summary=document.createElement('p');eyebrow.className='eyebrow';eyebrow.textContent='YOUR LEARNING IDENTITY';heading.textContent=status.completed?String(status.purpose).replaceAll('_',' ').toLowerCase()+' · '+(niches.find(row=>row.categoryId===status.categoryId)?.name||'Your area'):'Your next chapter starts here';summary.textContent=profile.interests?.length?profile.interests.map(row=>row.name).join(' · '):'No saved topics yet. Start personalization or choose interests below.';copy.append(eyebrow,heading,summary);identity.append(copy,preferenceMap((profile.interests||[]).slice(0,5).map(row=>row.name)));
  const container=el('tasteTopics');container.replaceChildren();const legend=document.createElement('legend');legend.textContent='Choose your interests';container.append(legend);
  niches.forEach(niche=>{const group=document.createElement('div');group.className='taste-topic-group';const title=document.createElement('h4');title.textContent=niche.name;group.append(title);niche.topics.forEach(topic=>{const label=document.createElement('label');label.className='topic-choice';const input=document.createElement('input');input.type='checkbox';input.value=topic.interestId;input.checked=tasteSaved.has(String(topic.interestId));label.append(input,document.createTextNode(topic.name));group.append(label);});container.append(group);});
  el('tasteStatus').textContent='Saved choices shown. Changes below are drafts until saved.';el('tasteForm').querySelector('[type=submit]').disabled=false;
 }catch(error){if(version===tasteVersion)el('tasteStatus').textContent='Could not load your saved interests. Use Refresh profile to try again. '+error.message;}
}
export function initializeTaste() {
 el('tasteForm').addEventListener('change',()=>{const labels=Array.from(el('tasteTopics').querySelectorAll('input:checked'),input=>input.parentElement.textContent.trim());el('tasteIdentity').querySelector('svg')?.replaceWith(preferenceMap(labels));el('tasteStatus').textContent='Draft changes · save to update your interests.';});
 el('tasteForm').onsubmit=async event=>{event.preventDefault();const button=event.currentTarget.querySelector('[type=submit]');if(button.disabled)return;button.disabled=true;const chosen=new Set(Array.from(el('tasteTopics').querySelectorAll('input:checked'),input=>input.value));el('tasteStatus').textContent='Saving your interests…';
 try{for(const id of chosen)if(!tasteSaved.has(id)){await api('/users/me/interests/'+id,{method:'PUT',body:JSON.stringify({weight:0.7})});tasteSaved.add(id);}for(const id of [...tasteSaved])if(!chosen.has(id)){await api('/users/me/interests/'+id,{method:'DELETE'});tasteSaved.delete(id);}await loadTaste();el('tasteStatus').textContent='Your interests are saved.';}catch(error){el('tasteStatus').textContent='Some changes may not have saved. Your draft is preserved; retry to finish. '+error.message;}finally{button.disabled=false;}};
}
