import React from 'react';
import { Tag } from 'antd';

// ================================================================
// 题库岗位类型展示配置（唯一权威来源）
// 后端 job_question.job_type 为自由字符串，不允许限制为固定枚举：
// - 命中 JOB_TYPE_META 的已知编码 → 展示中文名称 + 指定颜色 Tag
// - 未命中的历史数据 / HR 自定义文本 → 原样展示为灰色 Tag
// 提交接口时始终传编码或原始文本，不传中文展示名（见《前端题库岗位类型展示设计方案.md》）
// ================================================================

/** 岗位类型展示配置项 */
export interface JobTypeMeta {
  /** 岗位类型编码（提交给后端/数据库存储的原始值） */
  code: string;
  /** 中文展示名称 */
  label: string;
  /** Tag 颜色 */
  color: string;
}

/** 唯一权威的岗位类型展示配置（页面/详情/表单/筛选统一复用，禁止各自维护数组/映射） */
export const JOB_TYPE_META: JobTypeMeta[] = [
  { code: 'FRONTEND', label: '前端开发', color: 'blue' },
  { code: 'JAVA_BACKEND', label: 'Java 后端开发', color: 'geekblue' },
  { code: 'GO_BACKEND', label: 'Go 后端开发', color: 'cyan' },
  { code: 'DATA_ENGINEERING', label: '数据工程', color: 'purple' },
  { code: 'PRODUCT', label: '产品经理', color: 'gold' },
  { code: 'UI_DESIGN', label: 'UI 设计', color: 'magenta' },
  { code: 'DATA', label: '数据岗位', color: 'violet' },
  { code: 'OPERATION', label: '运营', color: 'green' },
  { code: 'OTHER', label: '其他', color: 'default' },
];

/** AutoComplete 选项（预设可选，仍允许自由输入自定义值） */
export const JOB_TYPE_OPTIONS: Array<{ value: string; label: string }> = JOB_TYPE_META.map(
  (item) => ({ value: item.code, label: item.label }),
);

/**
 * 岗位类型展示 Tag：
 * 已知编码 → 中文名称 + 指定颜色；未知/自定义 → 原始文本 + 灰色 Tag；空值返回 null。
 * 注意：本文件为 .ts（非 .tsx），不能使用 JSX 语法，用 React.createElement 构建 Tag。
 */
export function renderJobTypeTag(jobType?: string): React.ReactNode {
  if (!jobType) return null;
  const meta = JOB_TYPE_META.find((item) => item.code === jobType);
  if (meta) {
    return React.createElement(Tag, { color: meta.color }, meta.label);
  }
  return React.createElement(Tag, { color: 'default' }, jobType);
}
