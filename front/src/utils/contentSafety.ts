/**
 * 岗位内容安全检测（广告 / 外部联系方式）
 * 纯函数、无 React/UI 依赖，供 HR 创建/编辑岗位发布前校验使用（体验层）。
 * ⚠️ 前端检测可被绕过（直调接口），真正的安全边界在后端存储层校验（后续演进）。
 */

/** 检测结果：highItems=外部联系方式 / lowItems=广告词。
 *  双数组独立收集：同一段文本既有联系方式又有广告词时两类都保留；无命中时两个数组均为空。 */
export interface ContentRiskResult {
  highItems: string[];
  lowItems: string[];
}

// ===== HIGH：外部联系方式（模式匹配，带边界防误伤） =====

/** 手机号：11 位、1 开头第二位 3-9，前后非数字（薪资数字等不命中） */
const PHONE_RE = /(?<!\d)1[3-9]\d{9}(?!\d)/g;

/** 邮箱 */
const EMAIL_RE = /[\w.+-]+@[\w-]+\.[a-zA-Z]{2,}/g;

/** 外链 URL */
const URL_RE = /(https?:\/\/|www\.)\S+/gi;

/** 微信/QQ：必须带前缀，避免裸数字误伤薪资 */
const WECHAT_QQ_RE = /(微信|vx|weixin|wx|qq|QQ)[：:号]?\s*[\w-]{6,}/g;

/** 添加类导流：加/扫/添加 + 我/个/下/一 等插字 + 微信/VX/v信/QQ 等变体。
 *  精确词库覆盖不了"加我微信"这类词序插字（"加微信"不连续），故用模式匹配；
 *  V/v 单独出现要求后非字母数字（避免"添加Vue组件""加V2"等技术用语误报）。 */
const ADD_CONTACT_RE = /(?:加|扫|添加)[我个下一下]*?(?:微信|vx|v信|weixin|wx|qq|QQ|[Vv](?!\w))/i;

/** 导流组合词：仅明确引流动作命中，避免"扫码支付""二维码识别"等合法内容误报 */
const DRAIN_PHRASES = ['扫码联系', '扫码加群', '扫码添加', '二维码加群', '二维码联系', '加群咨询'];

// ===== LOW：广告词（精确词库；"兼职""急招"等合法招聘词不收录）
// "加微信""加V"已由 ADD_CONTACT_RE 按外部联系方式（HIGH）拦截，不重复收录 =====
const AD_WORDS = ['代做', '包过', '刷单', '日结', 'v信', '扫码加', '联系客服', '先交费', '押金'];

/** 对单段自由文本做内容风险检测（独立匹配全部模式，不因命中 HIGH 提前返回） */
export function detectContentRisk(text: string): ContentRiskResult {
  const highItems: string[] = [];
  const lowItems: string[] = [];

  if (text.match(PHONE_RE)) highItems.push('手机号');
  if (text.match(EMAIL_RE)) highItems.push('邮箱');
  if (text.match(URL_RE)) highItems.push('外链地址');
  if (text.match(WECHAT_QQ_RE)) highItems.push('微信/QQ');
  if (text.match(ADD_CONTACT_RE)) highItems.push('微信/QQ');
  if (DRAIN_PHRASES.some((phrase) => text.includes(phrase))) highItems.push('导流引导');

  // 广告词独立收集（即使已命中 HIGH 也继续匹配，保证 LOW 提示不丢失）
  lowItems.push(...AD_WORDS.filter((word) => text.includes(word)));

  return { highItems, lowItems };
}

/** 聚合多字段检测：条目格式「字段名：风险类型」，不回显完整内容（避免敏感信息二次放大） */
export function checkJobContent(
  fields: { label: string; value: string | undefined }[],
): { highItems: string[]; lowItems: string[] } {
  const highItems: string[] = [];
  const lowItems: string[] = [];

  for (const field of fields) {
    if (!field.value) continue;
    const result = detectContentRisk(field.value);
    if (result.highItems.length > 0) highItems.push(`${field.label}：外部联系方式`);
    if (result.lowItems.length > 0) lowItems.push(`${field.label}：疑似广告词`);
  }

  return { highItems, lowItems };
}
