import {createHash} from 'node:crypto';

// Editorial metadata proxy: fixed before rankings are computed, not independent human relevance labels.
const definitions=[
 ['TECHNOLOGY','Java','LEARN','BEGINNER',['java']],['TECHNOLOGY','SQL','LEARN','INTERMEDIATE',['sql','relational database']],['TECHNOLOGY','Machine Learning','EXPLORE','BEGINNER',['machine learning','artificial intelligence']],['TECHNOLOGY','Cybersecurity','CAREER','INTERMEDIATE',['cybersecurity','security','secure']],
 ['BUSINESS_FINANCE','Personal Finance','IMPROVE_MYSELF','BEGINNER',['personal finance','budget','saving','investing']],['BUSINESS_FINANCE','Entrepreneurship','CAREER','BEGINNER',['entrepreneur','startup','start a business']],['BUSINESS_FINANCE','Marketing','LEARN','INTERMEDIATE',['marketing','market research']],['BUSINESS_FINANCE','Business Strategy','EXPLORE','INTERMEDIATE',['business strategy','management','business ethics']],
 ['LEARNING_CAREER','Study Skills','LEARN','BEGINNER',['study','learning','academic success']],['LEARNING_CAREER','Career Development','CAREER','BEGINNER',['career','job search']],['LEARNING_CAREER','Interview Preparation','CAREER','INTERMEDIATE',['interview','resume']],['LEARNING_CAREER','Communication','IMPROVE_MYSELF','BEGINNER',['communication','presentation','public speaking']],
 ['HEALTH_FITNESS','Nutrition Basics','IMPROVE_MYSELF','BEGINNER',['nutrition','diet','food','healthy eating']],['HEALTH_FITNESS','Fitness','EXPLORE','INTERMEDIATE',['fitness','exercise','workout','physical activity']],['HEALTH_FITNESS','Sleep','IMPROVE_MYSELF','BEGINNER',['sleep','insomnia','circadian']],['HEALTH_FITNESS','Wellness','LEARN','BEGINNER',['wellness','mental health','stress','wellbeing']],
 ['CREATIVE_LIFESTYLE','Design','LEARN','BEGINNER',['design','visual art','drawing']],['CREATIVE_LIFESTYLE','Photography','CAREER','INTERMEDIATE',['photography','photo','camera']],['CREATIVE_LIFESTYLE','Writing','IMPROVE_MYSELF','INTERMEDIATE',['writing','story','fiction','poetry']],['CREATIVE_LIFESTYLE','Hobbies','EXPLORE','BEGINNER',['hobbies','hobby','craft','gardening','cooking']]
];
const normalize=s=>String(s||'').toLowerCase().normalize('NFKC');
const escape=s=>s.replace(/[.*+?^${}()|[\]\\]/g,'\\$&');
export function buildEvaluationInput(catalog,niches){
 const profiles=definitions.map(([categoryCode,topic,goal,level,terms],index)=>{
  const id='P'+String(index+1).padStart(2,'0'),niche=niches.find(n=>n.nicheCode===categoryCode||n.code===categoryCode);if(!niche)throw Error('Unknown evaluation category');const interest=niche.topics.find(t=>t.name===topic);if(!interest)throw Error('Unknown supported topic '+topic);
  const pattern=new RegExp('\\b(?:'+terms.map(escape).join('|')+')\\b','iu');
  function assess(item){const same=item.categoryId===niche.categoryId;let grade=!same?0:pattern.test(item.title)?3:item.tags.some(t=>normalize(t)===normalize(topic))?2:pattern.test(item.description||'')?1:0;const reason=grade===3?'Title matches declared topical intent':grade===2?'Explicit controlled topic matches intent':grade===1?'Description supplies weaker topical evidence':'Different primary category or no matching topical evidence';if(grade>0&&level==='BEGINNER'&&item.difficulty==='ADVANCED')grade=Math.max(1,grade-1);return{itemId:item.itemId,url:item.externalUrl,grade,reason,resourceType:item.type,provider:item.sourceName,difficulty:item.difficulty};}
  const assessed=catalog.map(assess),positives=assessed.filter(r=>r.grade>0).sort((a,b)=>b.grade-a.grade||a.itemId-b.itemId),pool=new Map();
  // Stratification affects the judged sample, never the production ranking or its feed allocation.
  for(const type of [...new Set(catalog.map(r=>r.type))]){const item=positives.find(r=>r.resourceType===type);if(item)pool.set(item.url,item);}
  for(const item of positives){if(pool.size>=16)break;pool.set(item.url,item);}
  const negatives=assessed.filter(r=>r.grade===0);for(const type of [...new Set(catalog.map(r=>r.type))]){const item=negatives.find(r=>r.resourceType===type);if(item)pool.set(item.url,item);}
  return{id,username:'tq_eval_'+id.toLowerCase(),categoryCode,categoryId:niche.categoryId,interestId:interest.interestId,topic,goal,level,terms,judgments:[...pool.values()]};
 });
 const distinct=new Set(profiles.flatMap(p=>p.judgments.map(j=>j.url)));if(profiles.length!==20||distinct.size<100)throw Error('Evaluation sample is insufficiently diverse');
 return{createdAt:new Date().toISOString(),catalogResources:catalog.length,catalogSha256:createHash('sha256').update(JSON.stringify(catalog)).digest('hex'),profiles,distinctJudgedResources:distinct.size,judgmentPolicy:'Assistant-authored deterministic editorial metadata proxies, frozen before model outputs. Not independent human assessment, user outcomes, or a user study. The lexical rubric can favor keyword-based methods. Knowledge level is recorded as context; unknown resource difficulty remains unknown and the production profile does not claim level adaptation.',sampling:'Purposive 20-profile sample across five categories; up to 16 topical entries plus resource-type negative controls per profile. Metrics are conditional on the common judged pool, not full-catalog recall.'};
}
