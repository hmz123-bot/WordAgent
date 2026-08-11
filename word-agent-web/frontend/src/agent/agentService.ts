/**
 * agentService — 本地模拟的「Agent 写作」内核。
 *
 * 设计目标：
 * 1. 不依赖任何后端即可工作（前端原型环境无 /api 后端）。
 * 2. 基于 提示词 + 类型/篇幅/文体/润色 选项，拼装结构化中文文档（HTML）。
 * 3. 支持可取消的「逐字流式」输出，营造 Agent 正在书写的体验。
 * 4. 支持多轮润色：根据后续指令对当前文档做 扩写 / 缩短 / 换语气 / 润色。
 *
 * 内容策略（v2）：
 * 每种文档类型都有各自「领域化的内容骨架」——例如产品需求文档(PRD)会真正产出
 * 背景、目标用户、功能列表、验收标准等结构化章节，而不是空转套话。
 * 提示词中出现 PRD / 需求文档 / 产品需求 等关键词时，会自动路由到 PRD 模板。
 *
 * 可替换性：若日后接入真实大模型 SSE 接口，只需把 buildDoc / refineDoc 换成
 * fetch('/api/v2/ai/stream')，并对齐 generateStream / refineStream 的回调契约即可，
 * 上层 useAgentWriter 与 Writing 页面无需改动。
 */

export type DocType = '文章写作' | '报告' | '邮件' | '提纲' | '产品需求文档';
export type DocLength = '简短' | '适中' | '详细' | '长文';
export type DocStyle = '商务正式' | '简洁明了' | '学术严谨' | '营销活泼' | '技术文档' | '故事化';
export type DocPolish = '标准' | '精修' | '深度';

export interface GenerateOptions {
  prompt: string;
  type: DocType;
  length: DocLength;
  style: DocStyle;
  polish: DocPolish;
}

export interface AgentHandlers {
  onToken: (delta: string, full: string) => void;
  onDone: (full: string, tokens: number) => void;
  onError?: (msg: string) => void;
}

export interface AgentController {
  cancel: () => void;
}

// 默认走 DeepSeek 真实大模型；设置环境变量 VITE_AI_MOCK=1 可强制回退到本地模拟
const USE_MOCK = (import.meta as any).env?.VITE_AI_MOCK === '1';

// DeepSeek 流式生成（默认路径）
import { deepseekGenerate, deepseekRefine } from './deepseek';

// ============ 工具 ============

/** 从提示词里抽取主题词，去掉元指令与文档类型词，便于写入标题/正文 */
export function extractTopic(prompt: string): string {
  let p = (prompt || '').trim();
  if (!p) return '未命名主题';
  // 去掉尾部的「包含/涵盖/包括 xxx、yyy」章节列举（那是目录，不是主题）
  p = p.replace(/[，,。.；;]?\s*(包含|涵盖|包括|需包含|需要包含|覆盖)[^，。；]+$/g, '');
  // 去掉生成类前缀（循环剥离，处理「帮我写」「写一篇关于」等多段前缀）
  const PREFIX_RE =
    /^(基于|根据|关于|帮我写|帮我|请|麻烦|生成|写|撰写|起草|整理|创建|设计|输出|写一份|生成一份|输出一份|一篇关于|一篇|写关于|写一篇)/g;
  let prev = '';
  do {
    prev = p;
    p = p.replace(PREFIX_RE, '');
  } while (prev !== p && p.length > 0);
  // 去掉文档类型词（它们不是主题；PRD 等可能出现在中段，需全局删除）
  p = p.replace(/(产品需求文档|需求规格|需求文档|产品文档|PRD|prd|文章|报告|邮件|提纲)/g, '');
  p = p.replace(/^(一份|一个|这篇|该|此|为)/g, '');
  p = p.replace(/[:：\s]+/g, ' ').trim();
  p = p.replace(/(撰写|写|生成|整理|创建|设计|输出|的)$/g, '');
  // 仍像一整句指令而非主题时，优雅兜底
  if (!p || p.length < 2) return '本产品';
  if (p.length > 16) return '本主题';
  return p.slice(0, 40);
}

/** 极简可复现伪随机：根据字符串生成稳定序列，保证同一主题产出一致 */
function seededRand(seed: string): () => number {
  let h = 2166136261;
  for (let i = 0; i < seed.length; i++) {
    h ^= seed.charCodeAt(i);
    h = Math.imul(h, 16777619);
  }
  return () => {
    h += 0x6d2b79f5;
    let t = h;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

function pick<T>(rng: () => number, arr: T[]): T {
  return arr[Math.floor(rng() * arr.length) % arr.length];
}

/** 不重复抽取 n 个元素（用于功能/验收等清单） */
function pickN<T>(rng: () => number, arr: T[], n: number): T[] {
  const pool = arr.slice();
  const out: T[] = [];
  const count = Math.min(n, pool.length);
  for (let i = 0; i < count; i++) {
    const idx = Math.floor(rng() * pool.length);
    out.push(pool.splice(idx, 1)[0]);
  }
  return out;
}

function num(rng: () => number, min: number, max: number): number {
  return Math.floor(rng() * (max - min + 1)) + min;
}

function estimateTokens(text: string): number {
  return Math.max(1, Math.round(text.length / 1.6));
}

// ============ 文体配置 ============

const STYLE_INTRO: Record<DocStyle, string[]> = {
  商务正式: ['在充分评估现状的基础上', '综合当前业务背景与发展节奏', '立足于实际落地场景', '为进一步提升整体协同效率'],
  简洁明了: ['一句话讲清楚核心', '直奔主题，不绕弯子', '只保留最关键的信息', '用最少的字把事说清'],
  学术严谨: ['通过系统梳理与对比分析', '既有研究普遍指出', '本文从方法论层面展开', '基于可验证的事实与数据'],
  营销活泼: ['你是不是也经常为这事犯愁', '别急，今天一次性讲透', '划重点时间到', '先抛个好消息给你'],
  技术文档: ['按照以下步骤即可完成配置', '这里有几点必须注意', '从原理到实操逐一说明', '先讲清前置依赖'],
  故事化: ['故事要从一个普通的清晨说起', '那天发生的事改变了我的看法', '恍惚间仿佛又回到了现场', '你还记得第一次面对它时的心情吗'],
};

const STYLE_TAIL: Record<DocStyle, string[]> = {
  商务正式: ['综上所述，建议尽快推进落地。', '以上，供决策参考。', '期待与各方协同达成目标。'],
  简洁明了: ['就这么简单。', '照做就行。', '核心就这些。'],
  学术严谨: ['可见该结论具备一定稳健性。', '后续仍需更多样本加以验证。', '此为阶段性研究结论。'],
  营销活泼: ['赶紧用起来吧！', '试试看，效果立竿见影～', '别等了，现在就行动！'],
  技术文档: ['配置完成后建议做一次回归验证。', '如仍异常，请检查依赖版本。', '至此流程闭环。'],
  故事化: ['后来我才明白，那只是起点。', '而故事，还在继续。', '有些事，经历过才懂。'],
};

const LENGTH_SECTIONS: Record<DocLength, number> = {
  简短: 2,
  适中: 3,
  详细: 4,
  长文: 6,
};

const POLISH_EXTRA: Record<DocPolish, number> = {
  标准: 0,
  精修: 1,
  深度: 2,
};

// ============ 意图识别 ============

/** 提示词含 PRD 关键词时，自动路由到产品需求文档模板（除非用户显式选了邮件/提纲） */
export function resolveType(opts: GenerateOptions): DocType {
  const t = opts.prompt || '';
  if (/PRD|产品需求|需求文档|需求规格|产品文档/i.test(t)) {
    if (opts.type === '邮件' || opts.type === '提纲') return opts.type;
    return '产品需求文档';
  }
  return opts.type;
}

// ============ 产品需求文档（PRD）内容骨架 ============

const PRD_PAIN: ((topic: string) => string)[] = [
  (t) => `随着业务规模扩大，围绕「${t}」的人工流程暴露出效率低、易出错的痛点，亟需线上化与标准化。`,
  (t) => `当前「${t}」多依赖线下表格与口头同步，信息孤岛严重，跨部门协作成本居高不下。`,
  (t) => `最近一个季度，「${t}」相关的诉求与故障反馈量明显上升，现有工具已无法支撑增长预期。`,
  (t) => `竞品已在「${t}」方向率先完成数字化，留给我们的窗口期正在收缩，需要尽快补齐能力。`,
];

const PRD_PERSONA: { role: string; need: (topic: string) => string }[] = [
  { role: '一线业务人员', need: (t) => `日常高频使用「${t}」，最关心操作是否顺手、能否少填表少切换。` },
  { role: '产品经理', need: (t) => `负责「${t}」的需求定义与优先级，关注目标是否可度量化。` },
  { role: '运营/管理人员', need: (t) => `通过「${t}」看板掌握整体进展，需要异常能被及时预警。` },
  { role: '研发/测试', need: (t) => `承接「${t}」的落地，关注接口稳定、边界清晰、可回归验证。` },
  { role: '终端客户', need: (t) => `作为「${t}」的最终受益方，关注响应速度与结果是否准确。` },
];

const PRD_FEATURE_NAME = [
  '用户与权限管理',
  '核心业务流程',
  '数据看板与统计',
  '消息与通知',
  '导入导出',
  '审批与协作',
  '配置与个性化',
  '日志与审计',
];

const PRD_FEATURE_DESC: ((topic: string, feature: string) => string)[] = [
  (t, f) => `支持对「${t}」的${f}进行增删改查与批量操作，覆盖从创建到归档的完整生命周期。`,
  (t, f) => `为「${t}」的${f}提供统一入口，并与上下游模块打通，减少跨系统切换成本。`,
  (t, f) => `围绕「${t}」建设${f}能力，关键操作留痕、可回溯、可审计。`,
  (t, f) => `补充「${t}」的${f}实时看板，将核心指标可视化，支撑日常决策。`,
];

const PRD_AC: ((topic: string, feature: string, rng: () => number) => string)[] = [
  (t, f) => `功能完整：「${t}」的${f}在 PC 端与移动端均可正常完成，无阻断性缺陷。`,
  (_t, _f, rng) => `性能指标：核心接口 P95 响应时间 ≤ ${num(rng, 200, 400)}ms，列表首屏 ≤ ${num(rng, 1, 3)}s。`,
  () => `兼容性：支持 Chrome / Edge / Safari 最近两个大版本，移动端适配主流机型。`,
  () => `数据正确：关键计算结果与离线核对一致，误差率 < 0.1%，异常有兜底提示。`,
  () => `权限安全：敏感操作需二次确认，越权访问被拒绝并告警，操作全程留痕。`,
  (t, _f, rng) => `可用性：围绕「${t}」的关键路径任务成功率 ≥ 99.${num(rng, 0, 9)}%，具备基础容灾。`,
];

const PRD_NONFUNC: ((topic: string, rng: () => number) => string)[] = [
  (t, rng) => `性能：「${t}」在峰值 ${num(rng, 500, 2000)} 并发下，核心页面可正常响应。`,
  (_t, rng) => `安全：传输全程加密，密钥定期轮换，通过基础安全扫描。`,
  (_t, rng) => `可观测：关键链路具备日志、监控与告警，故障可在 ${num(rng, 5, 15)} 分钟内定位。`,
  (_t, rng) => `可维护：模块解耦、配置外置，新功能平均接入周期 ≤ ${num(rng, 3, 10)} 人日。`,
];

function buildPrd(opts: GenerateOptions, topic: string, rng: () => number): Section[] {
  const { length, polish } = opts;
  const sections: Section[] = [];
  const intro = `本产品需求文档（PRD）围绕「${topic}」展开，明确目标、范围与交付标准，作为团队对齐与排期的统一依据。`;

  // 一、项目背景
  const pain = pick(rng, PRD_PAIN)(topic);
  const sectionsCount = LENGTH_SECTIONS[length];
  sections.push({
    heading: '一、项目背景',
    paragraphs: [
      intro,
      pain,
      `本期范围聚焦「${topic}」最核心的链路，先把主干跑通，再逐步外溢到周边场景。`,
    ],
  });

  // 二、目标用户
  const personas = pickN(rng, PRD_PERSONA, 3);
  sections.push({
    heading: '二、目标用户',
    list: personas.map((p) => `${p.role}：${p.need(topic)}`),
  });

  // 三、功能列表
  const featureCount = Math.min(PRD_FEATURE_NAME.length, 4 + (['简短', '适中', '详细', '长文'].indexOf(length)));
  const features = pickN(rng, PRD_FEATURE_NAME, featureCount);
  sections.push({
    heading: '三、功能列表',
    list: features.map((f, i) => `F${i + 1} ${f}：${pick(rng, PRD_FEATURE_DESC)(topic, f)}`),
  });

  // 四、验收标准
  const acCount = Math.min(PRD_AC.length, 4 + (['简短', '适中', '详细', '长文'].indexOf(length)));
  const mainFeature = features[0] || '核心功能';
  const acItems = pickN(rng, PRD_AC, acCount).map((fn) => fn(topic, mainFeature, rng));
  sections.push({
    heading: '四、验收标准',
    list: acItems,
  });

  // 五、非功能性需求（详细 / 长文才展开）
  if (sectionsCount >= 4 || polish !== '标准') {
    const nf = pickN(rng, PRD_NONFUNC, 3);
    sections.push({ heading: '五、非功能性需求', list: nf.map((fn) => fn(topic, rng)) });
  }

  // 六、里程碑与风险（长文）
  if (length === '长文') {
    sections.push({
      heading: '六、里程碑与风险',
      paragraphs: [
        `建议分三阶段交付：M1 打通「${topic}」核心流程，M2 补齐看板与权限，M3 做个性化与审计。`,
        `主要风险在于跨团队排期与历史数据迁移；对策是先行小流量灰度，并预留回滚方案。`,
      ],
    });
  }

  return sections;
}

// ============ 文章 / 报告 内容骨架（替换原 6 句套话） ============

const ARTICLE_BANK: ((topic: string) => string)[] = [
  (t) => `说清「${t}」之前，先把它和最容易混淆的邻近概念区分开，避免后续讨论跑偏。`,
  (t) => `落到执行，「${t}」最关键的不是想得完美，而是尽快跑出一个最小可用版本。`,
  (t) => `关于「${t}」，一个常被忽视的事实是：真正拉开差距的往往是细节，而非大方向。`,
  (t) => `衡量「${t}」有没有做对，看用户是否愿意把它推荐给同事，比看满意度打分更准。`,
  (t) => `推进「${t}」时，把不确定性拆成可验证的小假设，逐个试错比一次押注更稳。`,
  (t) => `围绕「${t}」做复盘，重点记「当时为什么这么决策」，而不只是记结果。`,
  (t) => `把「${t}」交给新人时，一份写得清楚的操作手册，比十次口头培训都管用。`,
  (t) => `「${t}」做到后半程，瓶颈通常从「会不会做」变成「愿不愿意持续做」。`,
];

const REPORT_BANK: ((topic: string, rng: () => number) => string)[] = [
  (t, rng) => `本次围绕「${t}」共回收有效样本 ${num(rng, 180, 420)} 份，覆盖主要业务环节。`,
  (t, rng) => `数据显示，「${t}」相关流程的平均耗时从 ${num(rng, 3, 8)} 天缩短至 ${num(rng, 1, 2)} 天。`,
  (t, rng) => `约 ${num(rng, 60, 85)}% 的受访者认为「${t}」当前最大的堵点在信息不同步。`,
  (t, rng) => `从结构看，「${t}」的成本有 ${num(rng, 40, 70)}% 集中在重复录入与人工核对环节。`,
  (t, rng) => `与上一周期相比，「${t}」的满意度提升了 ${num(rng, 8, 22)} 个百分点。`,
  (t, rng) => `值得关注的是，「${t}」在${pick(rng, ['华东', '华南', '线上渠道', '中小客户'])}场景的波动明显大于其他。`,
];

function buildArticle(opts: GenerateOptions, topic: string, rng: () => number): Section[] {
  const { length, style, polish } = opts;
  const n = LENGTH_SECTIONS[length];
  const extra = POLISH_EXTRA[polish];
  const intro = pick(rng, STYLE_INTRO[style]);
  const tail = pick(rng, STYLE_TAIL[style]);
  const titles = ['引言', '核心要点', '实践路径', '常见误区', '案例参考', '总结'];
  const sections: Section[] = [];

  sections.push({
    heading: titles[0],
    paragraphs: [
      `${intro}，本文围绕「${topic}」展开。`,
      pick(rng, ARTICLE_BANK)(topic),
    ],
  });

  for (let i = 1; i <= n; i++) {
    const paras: string[] = [pick(rng, ARTICLE_BANK)(topic), pick(rng, ARTICLE_BANK)(topic)];
    for (let e = 0; e < extra; e++) paras.push(pick(rng, ARTICLE_BANK)(topic));
    sections.push({ heading: titles[i] || `补充 ${i}`, paragraphs: paras });
  }

  sections.push({ heading: titles[n + 1] || '总结', paragraphs: [tail, `回到「${topic}」本身，行动永远优于空想——先迈出第一步，再在反馈里迭代。`] });
  return sections;
}

function buildReport(opts: GenerateOptions, topic: string, rng: () => number): Section[] {
  const { length, style, polish } = opts;
  const n = LENGTH_SECTIONS[length];
  const extra = POLISH_EXTRA[polish];
  const intro = pick(rng, STYLE_INTRO[style]);
  const tail = pick(rng, STYLE_TAIL[style]);
  const titles = ['一、概述', '二、现状分析', '三、关键问题', '四、改进建议', '五、风险与对齐', '六、结论'];
  const sections: Section[] = [];

  sections.push({
    heading: titles[0],
    paragraphs: [`${intro}，现就「${topic}」形成本报告。`, pick(rng, REPORT_BANK)(topic, rng)],
  });

  for (let i = 1; i <= n; i++) {
    const paras: string[] = [pick(rng, REPORT_BANK)(topic, rng), pick(rng, REPORT_BANK)(topic, rng)];
    for (let e = 0; e < extra; e++) paras.push(pick(rng, REPORT_BANK)(topic, rng));
    sections.push({ heading: titles[i] || `补充 ${i}`, paragraphs: paras });
  }

  sections.push({ heading: titles[n + 1] || '结论', paragraphs: [tail, `综上，「${topic}」的优化应以数据为依据，按优先级分阶段推进。`] });
  return sections;
}

function buildEmail(opts: GenerateOptions, topic: string, rng: () => number): Section[] {
  const { style } = opts;
  const intro = pick(rng, STYLE_INTRO[style]);
  const tail = pick(rng, STYLE_TAIL[style]);
  return [
    {
      heading: '邮件正文',
      paragraphs: [
        `尊敬的相关负责人：`,
        `${intro}，就「${topic}」一事与您沟通。当前阶段我们需要明确分工与时间节点，以便后续顺利推进。`,
        `具体而言，建议在本周内确认方案细节，并同步给相关同事。如方便，盼复为荷。`,
        tail,
        `此致\n敬礼\n${'Word Agent'}`,
      ],
    },
  ];
}

function buildOutline(opts: GenerateOptions, topic: string, rng: () => number): Section[] {
  const n = LENGTH_SECTIONS[opts.length];
  const base = [
    `一、背景与目标：为什么做「${topic}」`,
    `二、范围与边界：做到哪、不做到哪`,
    `三、核心方案：关键路径与里程碑`,
    `四、资源与分工：谁负责什么`,
    `五、风险与对策：可能踩的坑`,
    `六、验收标准：怎样算做成`,
  ];
  const items = base.slice(0, Math.min(base.length, n + 2));
  void rng;
  return [{ heading: `${topic} · 提纲`, paragraphs: [], list: items }];
}

// ============ 文档拼装 ============

interface Section {
  heading: string;
  paragraphs?: string[];
  list?: string[];
}

function buildSections(opts: GenerateOptions, topic: string, rng: () => number): Section[] {
  switch (resolveType(opts)) {
    case '产品需求文档':
      return buildPrd(opts, topic, rng);
    case '报告':
      return buildReport(opts, topic, rng);
    case '邮件':
      return buildEmail(opts, topic, rng);
    case '提纲':
      return buildOutline(opts, topic, rng);
    case '文章写作':
    default:
      return buildArticle(opts, topic, rng);
  }
}

function sectionsToHtml(sections: Section[]): string {
  return sections
    .map((s) => {
      let block = `<h2>${escapeHtml(s.heading)}</h2>`;
      if (s.list && s.list.length) {
        block += `<ul>${s.list.map((li) => `<li>${escapeHtml(li)}</li>`).join('')}</ul>`;
      }
      block += (s.paragraphs || []).map((p) => `<p>${escapeHtml(p).replace(/\n/g, '<br/>')}</p>`).join('');
      return block;
    })
    .join('');
}

function escapeHtml(s: string): string {
  return s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');
}

/** 生成完整文档 HTML（纯本地拼装） */
export function buildDoc(opts: GenerateOptions): string {
  const topic = extractTopic(opts.prompt);
  const rng = seededRand(topic + resolveType(opts) + opts.length + opts.style + opts.polish);
  const sections = buildSections(opts, topic, rng);
  return sectionsToHtml(sections);
}

// ============ 多轮润色 ============

export type RefineOp = 'expand' | 'shorten' | 'casual' | 'formal' | 'polish';

/** 从指令里识别润色操作 */
export function detectRefineOp(instruction: string): RefineOp {
  const t = instruction || '';
  if (/(扩写|展开|补充|详细|丰富|加一段|再写)/.test(t)) return 'expand';
  if (/(缩短|精简|精简一点|提炼|压缩|删减|更短|少一点)/.test(t)) return 'shorten';
  if (/(口语|轻松|活泼|通俗|大白话|接地气|说人话)/.test(t)) return 'casual';
  if (/(正式|专业|严谨|商务|官方)/.test(t)) return 'formal';
  return 'polish';
}

const FORMALIZE_MAP: [RegExp, string][] = [
  [/其实/g, '事实上'],
  [/说白了/g, '换言之'],
  [/搞定/g, '妥善解决'],
  [/挺/g, '颇为'],
  [/咱们/g, '各方'],
  [/有点/g, '一定程度上'],
];

const CASUALIZE_MAP: [RegExp, string][] = [
  [/因此/g, '所以'],
  [/综上所述/g, '总的来说'],
  [/鉴于/g, '考虑到'],
  [/旨在/g, '就是为了'],
  [/需/g, '得'],
  [/切勿/g, '别'],
];

const POLISH_SYN: [RegExp, string][] = [
  [/非常重要/g, '至关重要'],
  [/很多/g, '大量'],
  [/问题/g, '挑战'],
  [/做好/g, '做扎实'],
  [/帮助/g, '助力'],
  [/快速/g, '高效'],
];

/** 仅对 HTML 中「标签之间的文本片段」做替换，避免破坏标签结构 */
function mapTextSegments(html: string, fn: (text: string) => string): string {
  return html.replace(/>([^<]+)</g, (m, text: string) => `>${fn(text)}<`);
}

function applyConnectors(text: string, map: [RegExp, string][]): string {
  let out = text;
  for (const [re, rep] of map) out = out.replace(re, rep);
  return out;
}

function shortenParagraph(text: string): string {
  // 标题、功能/验收行、列表短句不压缩，仅压缩较长的段落正文
  if (text.length < 24) return text;
  if (/^([FAC]\d|一、|二、|三、|四、|五、|六、)/.test(text)) return text;
  const parts = text.split(/(?<=[。！？；])/);
  const keep = Math.max(1, Math.ceil(parts.length * 0.6));
  return parts.slice(0, keep).join('').trim();
}

function refineDoc(currentHtml: string, op: RefineOp, instruction: string): string {
  switch (op) {
    case 'expand': {
      const block = `<h2>补充与延展</h2><p>针对你的要求「${escapeHtml(instruction)}」，这里再展开说明：在原有基础上，可以进一步细化执行颗粒度，并补充可量化的验收口径，让方案更经得起推敲。</p><p>同时建议增加一段复盘机制，确保相关动作能持续沉淀为经验。</p>`;
      return currentHtml + block;
    }
    case 'shorten': {
      return mapTextSegments(currentHtml, (text) => shortenParagraph(text));
    }
    case 'formal': {
      return mapTextSegments(currentHtml, (text) => applyConnectors(text, FORMALIZE_MAP));
    }
    case 'casual': {
      return mapTextSegments(currentHtml, (text) => applyConnectors(text, CASUALIZE_MAP));
    }
    case 'polish':
    default: {
      return mapTextSegments(currentHtml, (text) => applyConnectors(text, POLISH_SYN));
    }
  }
}

// ============ 流式输出 ============

function streamText(full: string, handlers: AgentHandlers): AgentController {
  let cancelled = false;
  let i = 0;
  let acc = '';

  const step = () => {
    if (cancelled) return;
    if (i >= full.length) {
      handlers.onDone(acc, estimateTokens(acc));
      return;
    }
    const chunk = 1 + Math.floor(Math.random() * 3);
    const end = Math.min(full.length, i + chunk);
    const delta = full.slice(i, end);
    i = end;
    acc += delta;
    handlers.onToken(delta, acc);
    const last = delta[delta.length - 1];
    const pause = /[。！？；\n]/.test(last) ? 40 : 10;
    setTimeout(step, pause + Math.random() * 18);
  };

  setTimeout(step, 160);
  return { cancel: () => { cancelled = true; } };
}

/** 流式生成文档（默认走 DeepSeek 真实大模型，VITE_AI_MOCK=1 时回退本地模拟） */
export function generateStream(opts: GenerateOptions, handlers: AgentHandlers): AgentController {
  if (USE_MOCK) {
    try {
      const full = buildDoc(opts);
      return streamText(full, handlers);
    } catch (e: any) {
      handlers.onError?.(e?.message || '生成失败');
      return { cancel: () => {} };
    }
  }
  return deepseekGenerate(opts, handlers);
}

/** 流式润色当前文档（默认走 DeepSeek 真实大模型，VITE_AI_MOCK=1 时回退本地模拟） */
export function refineStream(currentHtml: string, instruction: string, handlers: AgentHandlers): AgentController {
  if (USE_MOCK) {
    try {
      const op = detectRefineOp(instruction);
      const full = refineDoc(currentHtml, op, instruction);
      return streamText(full, handlers);
    } catch (e: any) {
      handlers.onError?.(e?.message || '润色失败');
      return { cancel: () => {} };
    }
  }
  return deepseekRefine(currentHtml, instruction, handlers);
}

/** 供 UI 展示的操作中文名 */
export function refineOpLabel(op: RefineOp): string {
  return { expand: '扩写', shorten: '缩短', casual: '口语化', formal: '正式化', polish: '润色' }[op];
}
