import { EducationRequirement } from '@/constants/enums';
import type {
  CoreSkill,
  SoftSkill,
  HiddenRequirement,
  HrJobDetail,
  JobCreateRequest,
  JobProfileDTO,
  JobUpdateRequest,
  ProfileSource,
  SalaryInfo,
} from '@/services/job';

/**
 * 岗位表单映射纯函数
 * 将表单值 ↔ 后端 DTO 相互转换，组件只调用不内联组装逻辑。
 * 纯函数不依赖组件状态，为将来单元测试打基础。
 */

/** 表单值（对应 create/index.tsx 的 Form 字段） */
export interface JobFormValues {
  title: string;
  cityCode: string;
  industryGroupCode: string;
  minExperienceYears: number;
  educationRequirement: string;
  salaryMin?: number | null;
  salaryMax?: number | null;
  salaryMonths?: number | null;
  /** 面议开关：开启时薪资区不填、提交 negotiable=true */
  salaryNegotiable?: boolean;
  totalHc: number;
  jdText: string;
  coreSkills: string[];
  softSkills: string[];
  industryExperience: string;
  interviewFocus: string; // 顿号分隔
  profileConfirmed: boolean;
  // 隐藏字段（无表单控件，从详情承载，编辑时回填）
  jobType?: string;
  hiddenRequirements?: HiddenRequirement[];
  profileSource?: ProfileSource;
}

/** 薪资组装结果：成功返回 SalaryInfo，失败返回校验提示 */
export type SalaryPayloadResult =
  | { ok: true; salary: SalaryInfo }
  | { ok: false; message: string };

/** 详情 → 表单初始值（薪资分→元、画像字段、行业、确认态、隐藏字段） */
export function mapDetailToFormValues(detail: HrJobDetail): Partial<JobFormValues> {
  const negotiable = detail.salary.negotiable || detail.salary.minAmount === null;
  return {
    title: detail.title,
    cityCode: detail.cityCode,
    industryGroupCode: detail.industryGroupCode,
    minExperienceYears: detail.minExperienceYears,
    // PHD→DOCTOR 映射：后端岗位保存枚举为 DOCTOR（EducationRequirementEnum），解析响应 LLM 可能返回 PHD（FE3-001）
    educationRequirement:
      detail.educationRequirement === 'PHD'
        ? EducationRequirement.DOCTOR
        : detail.educationRequirement,
    salaryMin: negotiable ? null : detail.salary.minAmount !== null ? detail.salary.minAmount / 100 : null,
    salaryMax: negotiable ? null : detail.salary.maxAmount !== null ? detail.salary.maxAmount / 100 : null,
    salaryMonths: negotiable ? null : detail.salary.months,
    salaryNegotiable: negotiable,
    totalHc: detail.totalHc,
    jdText: detail.jdText,
    coreSkills: (detail.coreSkills || []).map((s) => s.name),
    softSkills: (detail.softSkills || []).map((s) => s.name),
    industryExperience: detail.industryExperience || '',
    interviewFocus: (detail.interviewFocus || []).join('、'),
    profileConfirmed: detail.profileConfirmed,
    jobType: detail.jobType || '',
    hiddenRequirements: detail.hiddenRequirements || [],
    profileSource: detail.profileSource || 'MANUAL',
  };
}

/**
 * 薪资组装（元→分，Math.round 防浮点误差）
 * - negotiable=true → 面议（三字段 null，不校验金额）
 * - 否则两侧都空 → 面议（兼容旧调用）
 * - 两侧都有值 → 校验 max >= min + 薪数
 * - 只填一侧 → 校验失败
 */
export function buildSalaryPayload(
  salaryMin?: number | null,
  salaryMax?: number | null,
  salaryMonths?: number | null,
  negotiable?: boolean,
): SalaryPayloadResult {
  // 面议开关开启：直接返回面议薪资，不校验金额
  if (negotiable) {
    return {
      ok: true,
      salary: {
        minAmount: null,
        maxAmount: null,
        currency: 'CNY',
        period: 'MONTH',
        months: null,
        negotiable: true,
        rawText: '面议',
      },
    };
  }

  const hasMin = typeof salaryMin === 'number' && salaryMin !== 0;
  const hasMax = typeof salaryMax === 'number' && salaryMax !== 0;

  if (!hasMin && !hasMax) {
    // 面议（兼容旧调用：未传 negotiable 时两侧为空仍视为面议）
    return {
      ok: true,
      salary: {
        minAmount: null,
        maxAmount: null,
        currency: 'CNY',
        period: 'MONTH',
        months: null,
        negotiable: true,
        rawText: '面议',
      },
    };
  }

  if (hasMin !== hasMax) {
    return { ok: false, message: '请同时填写最低和最高薪资，或开启面议' };
  }

  if (salaryMax! < salaryMin!) {
    return { ok: false, message: '最高薪资不能低于最低薪资' };
  }

  // 非面议薪资要求薪数有效（1~24），否则前端拦截（避免后端 400）
  if (typeof salaryMonths !== 'number' || salaryMonths < 1 || salaryMonths > 24) {
    return { ok: false, message: '请填写薪数（1-24）' };
  }
  const months = salaryMonths;
  return {
    ok: true,
    salary: {
      minAmount: Math.round(salaryMin! * 100),
      maxAmount: Math.round(salaryMax! * 100),
      currency: 'CNY',
      period: 'MONTH',
      months,
      negotiable: false,
      rawText: `${salaryMin!}-${salaryMax!}元/月`,
    },
  };
}

/** 技能名 → CoreSkill（优先复用原画像的 level/required，否则默认值） */
function buildCoreSkill(name: string, original?: CoreSkill[]): CoreSkill {
  const orig = original?.find((s) => s.name === name);
  return orig
    ? { ...orig, name }
    : { name, level: 'PROFICIENT', required: false };
}

/** 软技能名 → SoftSkill（优先复用原画像 importance，否则默认） */
function buildSoftSkill(name: string, original?: SoftSkill[]): SoftSkill {
  const orig = original?.find((s) => s.name === name);
  return orig
    ? { ...orig, name }
    : { name, importance: 'MEDIUM' };
}

/** 组装完整画像 DTO（核心技能/软技能合并原画像保留 level/required/importance，过滤空串） */
function buildProfileDTO(values: JobFormValues, original?: HrJobDetail): JobProfileDTO {
  const coreSkills = (values.coreSkills || [])
    .map((n) => n.trim())
    .filter(Boolean)
    .map((n) => buildCoreSkill(n, original?.coreSkills));
  const softSkills = (values.softSkills || [])
    .map((n) => n.trim())
    .filter(Boolean)
    .map((n) => buildSoftSkill(n, original?.softSkills));
  const interviewFocus = (values.interviewFocus || '')
    .split('、')
    .map((s) => s.trim())
    .filter(Boolean);

  return {
    jobType: values.jobType || '',
    coreSkills,
    softSkills,
    industryExperience: values.industryExperience || '',
    hiddenRequirements: values.hiddenRequirements || [],
    interviewFocus,
    profileSource: values.profileSource || 'MANUAL',
  };
}

/** 组装薪资（需先通过 buildSalaryPayload 校验成功后再传入） */
function buildSalaryInfo(values: JobFormValues): SalaryInfo {
  const result = buildSalaryPayload(values.salaryMin, values.salaryMax, values.salaryMonths);
  if (!result.ok) {
    // 正常情况下组件会先校验拦截，这里兜底为面议，避免 undefined 金额
    return {
      minAmount: null,
      maxAmount: null,
      currency: 'CNY',
      period: 'MONTH',
      months: null,
      negotiable: true,
      rawText: '面议',
    };
  }
  return result.salary;
}

/** 组装创建请求（无 version/profileVersion） */
export function buildCreatePayload(
  values: JobFormValues,
  opts: { original?: HrJobDetail },
): JobCreateRequest {
  return {
    title: values.title,
    industryGroupCode: values.industryGroupCode,
    industryCode: values.industryGroupCode, // 后端要求 group 与 code 同值
    cityCode: values.cityCode,
    minExperienceYears: values.minExperienceYears || 0,
    educationRequirement: values.educationRequirement,
    salary: buildSalaryInfo(values),
    totalHc: values.totalHc || 1,
    jdText: values.jdText,
    profileConfirmed: values.profileConfirmed,
    profile: buildProfileDTO(values, opts.original),
  };
}

/** 组装更新请求（version 必填；画像三件套按是否修改全带/全不带） */
export function buildUpdatePayload(
  values: JobFormValues,
  opts: { version: number; profileVersion?: number; profileChanged: boolean; original?: HrJobDetail },
): JobUpdateRequest {
  const base: JobUpdateRequest = {
    title: values.title,
    industryGroupCode: values.industryGroupCode,
    industryCode: values.industryGroupCode,
    cityCode: values.cityCode,
    minExperienceYears: values.minExperienceYears || 0,
    educationRequirement: values.educationRequirement,
    salary: buildSalaryInfo(values),
    totalHc: values.totalHc || 1,
    jdText: values.jdText,
    version: opts.version,
  };

  if (opts.profileChanged) {
    // 画像已修改 → 三件套全带（profile 必须完整，否则后端配对校验 400）
    return {
      ...base,
      profileConfirmed: values.profileConfirmed,
      profileVersion: opts.profileVersion,
      profile: buildProfileDTO(values, opts.original),
    };
  }
  // 画像未修改 → 三件套全不带（后端不改画像、不递增画像版本）
  return base;
}
