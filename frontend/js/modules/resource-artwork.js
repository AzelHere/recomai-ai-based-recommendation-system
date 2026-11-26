const illustrations = {
  code: '<rect x="54" y="30" width="372" height="140" rx="14" fill="#1B1917" stroke="#72513E"/><circle cx="77" cy="51" r="5" fill="#a5b6c8"/><circle cx="96" cy="51" r="5" fill="#9D8575"/><path d="M82 82h105m-105 22h168m-168 22h118m-118 22h224" stroke="#D5B8A4" stroke-width="7" stroke-linecap="round" opacity=".72"/><path d="M204 82h45m-9 22h66m-52 22h40" stroke="#C98765" stroke-width="7" stroke-linecap="round"/>',
  ai: '<path d="M240 42v116M128 99h224M160 64l160 70M320 64L160 134" stroke="#8799AC" stroke-width="3" opacity=".75"/><g fill="#211C18" stroke="#C98765" stroke-width="4"><circle cx="240" cy="45" r="13"/><circle cx="128" cy="99" r="13"/><circle cx="352" cy="99" r="13"/><circle cx="160" cy="63" r="13"/><circle cx="320" cy="63" r="13"/><circle cx="160" cy="135" r="13"/><circle cx="320" cy="135" r="13"/><circle cx="240" cy="158" r="13"/></g><circle cx="240" cy="101" r="27" fill="#a5b6c8" fill-opacity=".22" stroke="#a5b6c8" stroke-width="4"/>',
  data: '<path d="M80 160V46m0 114h328" stroke="#8C7869" stroke-width="3"/><rect x="115" y="112" width="43" height="48" rx="7" fill="#A96D4E"/><rect x="184" y="83" width="43" height="77" rx="7" fill="#D18A61"/><rect x="253" y="61" width="43" height="99" rx="7" fill="#a5b6c8"/><rect x="322" y="92" width="43" height="68" rx="7" fill="#8C6048"/><path d="M116 94l73-32 68-10 75-27" stroke="#F4CFB5" stroke-width="5" stroke-linecap="round" stroke-linejoin="round"/>',
  database: '<ellipse cx="240" cy="55" rx="112" ry="28" fill="#382920" stroke="#9EAFBF" stroke-width="4"/><path d="M128 55v88c0 16 50 29 112 29s112-13 112-29V55" fill="#2C241F" stroke="#9EAFBF" stroke-width="4"/><path d="M128 98c0 16 50 29 112 29s112-13 112-29m-224 44c0 16 50 29 112 29s112-13 112-29" stroke="#9EAFBF" stroke-width="4"/><ellipse cx="240" cy="55" rx="52" ry="12" fill="#a5b6c8" fill-opacity=".55"/>',
  browser: '<rect x="64" y="35" width="352" height="132" rx="13" fill="#25211E" stroke="#9D7055" stroke-width="4"/><path d="M65 65h350" stroke="#9D7055" stroke-width="3"/><circle cx="85" cy="50" r="5" fill="#a5b6c8"/><circle cx="103" cy="50" r="5" fill="#9D8575"/><rect x="86" y="84" width="112" height="63" rx="7" fill="#493127"/><rect x="220" y="87" width="155" height="9" rx="4" fill="#E4C5AF" opacity=".8"/><rect x="220" y="107" width="124" height="7" rx="3" fill="#B19A89" opacity=".7"/><rect x="220" y="126" width="146" height="20" rx="8" fill="#a5b6c8"/>',
  security: '<path d="M240 35l94 35v45c0 37-42 66-94 84-52-18-94-47-94-84V70z" fill="#33241E" stroke="#9EAFBF" stroke-width="5"/><path d="M202 108l26 25 52-58" stroke="#C98765" stroke-width="10" stroke-linecap="round" stroke-linejoin="round"/><circle cx="240" cy="108" r="116" stroke="#8799AC" stroke-width="2" stroke-dasharray="7 10" opacity=".7"/>',
  cloud: '<path d="M150 141h185a39 39 0 0 0 0-78 66 66 0 0 0-125-17 49 49 0 0 0-60 47 25 25 0 0 0 0 48z" fill="#3A2B23" stroke="#9EAFBF" stroke-width="5"/><path d="M240 82v65m-27-26 27 27 27-27" stroke="#C98765" stroke-width="8" stroke-linecap="round" stroke-linejoin="round"/><path d="M121 166h238" stroke="#8C6048" stroke-width="4" stroke-linecap="round"/>',
  design: '<rect x="68" y="34" width="344" height="132" rx="14" fill="#24201D" stroke="#8C6048" stroke-width="4"/><rect x="88" y="54" width="142" height="90" rx="9" fill="#443026"/><path d="M105 123l31-37 22 19 28-34 28 36" stroke="#C98765" stroke-width="5" stroke-linecap="round" stroke-linejoin="round"/><rect x="250" y="55" width="140" height="14" rx="7" fill="#E9D0BD" opacity=".75"/><rect x="250" y="81" width="112" height="8" rx="4" fill="#B19A89"/><rect x="250" y="101" width="126" height="8" rx="4" fill="#B19A89"/><rect x="250" y="124" width="67" height="22" rx="8" fill="#a5b6c8"/>',
  book: '<path d="M94 48c53-18 101-10 146 17v111c-45-27-93-35-146-17z" fill="#3D2B21" stroke="#9EAFBF" stroke-width="4"/><path d="M386 48c-53-18-101-10-146 17v111c45-27 93-35 146-17z" fill="#292421" stroke="#9EAFBF" stroke-width="4"/><path d="M240 65v111M121 75c36-9 68-5 92 7m-92 15c36-9 68-5 92 7m-92 15c36-9 68-5 92 7m192-44c-36-9-68-5-92 7m92 15c-36-9-68-5-92 7m92 15c-36-9-68-5-92 7" stroke="#DBE2EA" stroke-width="4" stroke-linecap="round" opacity=".7"/>',
  photography: '<rect x="92" y="64" width="296" height="112" rx="18" fill="#252D38" stroke="#ADBBC9" stroke-width="5"/><path d="M145 64l20-26h74l20 26" fill="#3B2B22" stroke="#ADBBC9" stroke-width="5" stroke-linejoin="round"/><circle cx="244" cy="119" r="43" fill="#171513" stroke="#C98765" stroke-width="7"/><circle cx="244" cy="119" r="25" fill="#414D5C" stroke="#B8C9D9" stroke-width="4"/><circle cx="340" cy="91" r="8" fill="#a5b6c8"/><path d="M115 148h39m-39 12h57" stroke="#D1B7A5" stroke-width="4" stroke-linecap="round"/>',
  travel: '<path d="M82 56l96-20 122 19 98-19v129l-98 20-122-19-96 19z" fill="#293541" stroke="#8799AC" stroke-width="4" stroke-linejoin="round"/><path d="M178 36v130m122-111v130" stroke="#8799AC" stroke-width="4"/><path d="M116 128c43-62 69 27 113-18s68 31 116-28" fill="none" stroke="#C98765" stroke-width="6" stroke-linecap="round" stroke-dasharray="3 11"/><path d="M338 71c0-18 14-32 31-32s31 14 31 32c0 24-31 52-31 52s-31-28-31-52z" fill="#a5b6c8"/><circle cx="369" cy="71" r="9" fill="#F5F2EE"/>',
  science: '<circle cx="240" cy="102" r="18" fill="#a5b6c8"/><ellipse cx="240" cy="102" rx="132" ry="42" fill="none" stroke="#9EAFBF" stroke-width="5"/><ellipse cx="240" cy="102" rx="132" ry="42" fill="none" stroke="#DBE2EA" stroke-width="4" transform="rotate(60 240 102)"/><ellipse cx="240" cy="102" rx="132" ry="42" fill="none" stroke="#8799AC" stroke-width="4" transform="rotate(120 240 102)"/><circle cx="370" cy="102" r="9" fill="#F5F2EE"/><circle cx="176" cy="13" r="8" fill="#C98765"/><circle cx="172" cy="194" r="7" fill="#9EAFBF"/>',
  history: '<path d="M102 69h276v104H102z" fill="#293541" stroke="#8799AC" stroke-width="4"/><path d="M84 69l156-40 156 40z" fill="#414D5C" stroke="#9EAFBF" stroke-width="5" stroke-linejoin="round"/><path d="M128 83v73m55-73v73m55-73v73m55-73v73m55-73v73" stroke="#B8C9D9" stroke-width="10" stroke-linecap="round"/><path d="M88 175h304m-278 13h252" stroke="#9EAFBF" stroke-width="5" stroke-linecap="round"/>',
  finance: '<path d="M78 163h324M96 163V92h54v71m28 0V68h54v95m28 0V44h54v119m28 0V83h54v80" fill="#303C4A" stroke="#ADBBC9" stroke-width="4"/><path d="M98 72l81-29 66 23 82-31" fill="none" stroke="#C98765" stroke-width="7" stroke-linecap="round" stroke-linejoin="round"/><circle cx="98" cy="72" r="7" fill="#DEE5EB"/><circle cx="179" cy="43" r="7" fill="#DEE5EB"/><circle cx="245" cy="66" r="7" fill="#DEE5EB"/><circle cx="327" cy="35" r="7" fill="#a5b6c8"/>',
  career: '<path d="M112 62h256v116H112z" fill="#28211D" stroke="#B98563" stroke-width="4"/><path d="M205 62V46a15 15 0 0 1 15-15h40a15 15 0 0 1 15 15v16" fill="none" stroke="#D7AE94" stroke-width="5"/><path d="M112 98h256M222 98v18h36V98" fill="none" stroke="#D7AE94" stroke-width="4"/><path d="M147 141h59m18 0h109" stroke="#C98765" stroke-width="7" stroke-linecap="round"/><circle cx="147" cy="141" r="9" fill="#a5b6c8"/>',
  wellness: '<circle cx="240" cy="101" r="72" fill="#32251F" stroke="#ADBBC9" stroke-width="4"/><path d="M240 143s-49-29-49-61c0-25 31-31 49-10 18-21 49-15 49 10 0 32-49 61-49 61z" fill="#a5b6c8"/><path d="M240 25v18m0 116v18M164 101h18m116 0h18" stroke="#E2B39A" stroke-width="5" stroke-linecap="round"/><circle cx="240" cy="101" r="103" fill="none" stroke="#8C6048" stroke-width="3" stroke-dasharray="3 12"/>'
};

const subjects = [
  [/\b(java|python|javascript|typescript|programming|coding|software|algorithm|developer|spring|api)\b/i, "code", "Programming"],
  [/\b(ai|artificial intelligence|machine learning|neural|deep learning|llm|generative)\b/i, "ai", "Artificial intelligence"],
  [/\b(sql|database|mysql|postgres|data base|query)\b/i, "database", "Database"],
  [/\b(cyber|security|network|privacy|threat|encryption)\b/i, "security", "Cybersecurity"],
  [/\b(cloud|aws|azure|infrastructure|devops|docker|kubernetes)\b/i, "cloud", "Cloud computing"],
  [/\b(photograph|photography|camera|visual arts|image editing)\b/i, "photography", "Photography"],
  [/\b(travel|maps?|mapping|tourism|geography|destination|national park)\b/i, "travel", "Travel and geography"],
  [/\b(writing|poetry|literature|writer|novel|editor|journalism)\b/i, "book", "Writing and literature"],
  [/\b(history|heritage|museum|archive|culture)\b/i, "history", "History and culture"],
  [/\b(science|astronomy|physics|biology|chemistry|experiment|exploratorium)\b/i, "science", "Science"],
  [/\b(design|ux|ui|figma|wireframe|creative|illustration)\b/i, "design", "Design"],
  [/\b(data|analytics|statistics|visualization|science|machine)\b/i, "data", "Data analysis"],
  [/\b(web|html|css|frontend|browser|website)\b/i, "browser", "Web development"],
  [/\b(finance|business|econom|invest|account|market|budget|strategy)\b/i, "finance", "Business and finance"],
  [/\b(health|fitness|wellness|nutrition|exercise|movement|habit|mental health)\b/i, "wellness", "Health and fitness"],
  [/\b(career|professional|leadership|interview|resume|job|skill development|workplace)\b/i, "career", "Learning and career"]
];

const categorySubjects = [
  [/technology/i, "code", "Technology"],
  [/business|finance/i, "finance", "Business and finance"],
  [/learning|career/i, "career", "Learning and career"],
  [/health|fitness/i, "wellness", "Health and fitness"],
  [/creative|lifestyle/i, "design", "Creative practice"]
];

export function createResourceArtwork(item, categoryLabel = "Resource") {
  const catalogDetails = [categoryLabel, item?.type,
    ...(Array.isArray(item?.tags) ? item.tags : [])].filter(Boolean).join(" ");
  const subject = subjects.find(([pattern]) => pattern.test(item?.title || ""))
    || subjects.find(([pattern]) => pattern.test(item?.description || ""))
    || subjects.find(([pattern]) => pattern.test(catalogDetails))
    || categorySubjects.find(([pattern]) => pattern.test(categoryLabel));
  const key = subject ? subject[1] : ["VIDEO", "COURSE", "BOOK"].includes(item?.type) ? "book" : "design";
  const label = subject ? subject[2] : `${categoryLabel} learning`;
  const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
  svg.setAttribute("viewBox", "0 0 480 200");
  svg.setAttribute("role", "img");
  svg.setAttribute("aria-label", `${label} illustration for ${item?.title || "learning resource"}`);
  svg.setAttribute("focusable", "false");
  const seed = Number(item?.itemId) || Array.from(item?.title || categoryLabel).reduce((sum, character) => sum + character.charCodeAt(0), 0);
  svg.dataset.variation = String(seed % 3);
  svg.innerHTML = illustrations[key];
  return { element: svg, key };
}
