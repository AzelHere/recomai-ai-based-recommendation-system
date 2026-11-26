// Focused computed-color check. Gradients/artwork and offscreen elements require visual review.
export function inspectContrast(){
 const color=value=>{const parts=value.match(/[\d.]+/g)?.map(Number);return parts?.length>=3?[...parts.slice(0,3),parts[3]??1]:null;};
 const blend=(fg,bg)=>fg.slice(0,3).map((v,i)=>v*fg[3]+bg[i]*(1-fg[3]));
 const luminance=c=>c.map(v=>v/255).map(v=>v<=.04045?v/12.92:((v+.055)/1.055)**2.4).reduce((n,v,i)=>n+v*[.2126,.7152,.0722][i],0);
 let checked=0,skipped=0;const failures=[];
 for(const node of document.querySelectorAll('p,span,label,a,button,h1,h2,h3,h4,small,summary,dt,dd,legend,th,td')){
  const rect=node.getBoundingClientRect(),style=getComputedStyle(node),text=[...node.childNodes].filter(n=>n.nodeType===Node.TEXT_NODE).map(n=>n.textContent).join('').trim();
  if(!text||!rect.width||!rect.height||rect.top>=innerHeight||rect.bottom<0||style.visibility!=='visible')continue;
  const chain=[];let unknown=false;
  for(let current=node;current;current=current.parentElement){const s=getComputedStyle(current);if(Number(s.opacity)<.99||s.backgroundImage!=='none')unknown=true;chain.unshift(color(s.backgroundColor));}
  if(unknown){skipped++;continue;}
  let background=[255,255,255];for(const layer of chain)if(layer)background=blend(layer,background);
  const foreground=color(style.color);if(!foreground){skipped++;continue;}
  const a=luminance(blend(foreground,background)),b=luminance(background),ratio=(Math.max(a,b)+.05)/(Math.min(a,b)+.05);
  const size=parseFloat(style.fontSize),large=size>=24||size>=18.66&&Number(style.fontWeight)>=700,target=large?3:4.5;checked++;
  if(ratio+.01<target)failures.push({tag:node.tagName,id:node.id,className:node.className,text:text.slice(0,70),ratio:Number(ratio.toFixed(2)),target,foreground:style.color,background});
 }
 return {checked,skipped,failures,scope:'Visible direct text nodes over computed flat backgrounds at 1440x900. Artwork, gradients, opacity, icons and offscreen content require manual review. This is not a WCAG certification.'};
}
