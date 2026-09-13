import React, { useState, useEffect } from 'react';
import { Modal, Form, Input, Select, InputNumber, Button, AutoComplete, message } from 'antd';
import { PlusOutlined, DeleteOutlined } from '@ant-design/icons';
import {
  QuestionType,
  QuestionDifficulty,
} from '@/constants/enums';
import { JOB_TYPE_OPTIONS } from '@/constants/questionBank';
import { createQuestion, updateQuestion } from '@/services/questionBank';
import type {
  HrQuestionDetailVO,
  QuestionCreateRequest,
  EvaluationPointDTO,
} from '@/services/questionBank';
import { getErrorCode } from '@/utils/apiError';
import styles from './QuestionFormModal.less';

const { TextArea } = Input;

interface Props {
  /** 弹窗是否打开 */
  open: boolean;
  /** 编辑时传入完整详情（含 version 与敏感字段）；创建为 null */
  record: HrQuestionDetailVO | null;
  onClose: () => void;
  /** 提交成功（含 2402 版本冲突后关闭编辑态）回调 */
  onSuccess: () => void;
}

/** 题型 → 中文 */
const TYPE_LABEL: Record<QuestionType, string> = {
  [QuestionType.BASIC]: '基础验证',
  [QuestionType.PROJECT]: '项目深挖',
  [QuestionType.BOUNDARY]: '能力边界',
  [QuestionType.COMPREHENSIVE]: '综合素养',
};

const questionTypeOptions = Object.values(QuestionType).map((v) => ({
  value: v,
  label: TYPE_LABEL[v],
}));

const difficultyOptions = Object.values(QuestionDifficulty).map((v) => ({
  value: v,
  label: v === QuestionDifficulty.EASY ? '简单' : v === QuestionDifficulty.MEDIUM ? '中等' : '困难',
}));

/** 详情 → 表单值（字段名与请求体对齐，可直接 setFieldsValue） */
const mapDetailToForm = (record: HrQuestionDetailVO) => ({
  jobType: record.jobType,
  questionType: record.questionType,
  difficulty: record.difficulty,
  content: record.content,
  skillTags: record.skillTags ?? [],
  keyPoints: record.keyPoints,
  referenceAnswer: record.referenceAnswer,
  evaluationPoints: record.evaluationPoints ?? [],
});

/** 筛选评分要点空行（空 name 丢弃），权重归一为数字 */
const normalizeEvaluationPoints = (points: EvaluationPointDTO[]): EvaluationPointDTO[] =>
  (points ?? [])
    .filter((p) => p?.name?.trim())
    .map((p) => ({ name: p.name.trim(), weight: Number(p.weight) }));

const QuestionFormModal: React.FC<Props> = ({ open, record, onClose, onSuccess }) => {
  const [form] = Form.useForm();
  const [saving, setSaving] = useState(false);
  const isEdit = !!record;

  // 打开/切换题目时重置表单。
  // 不能只依赖 initialValues（AntD initialValues 仅在 Form 首次挂载生效），
  // 用 resetFields + setFieldsValue 保证连续编辑不同题目不残留上一题数据；
  // 动态评分要点（Form.List）随 evaluationPoints 数组一并回填。
  useEffect(() => {
    if (open) {
      form.resetFields();
      if (record) {
        form.setFieldsValue(mapDetailToForm(record));
      }
    }
  }, [open, record, form]);

  const handleSubmit = async () => {
    let values: Record<string, unknown>;
    try {
      values = await form.validateFields();
    } catch {
      return; // 表单校验失败，antd 已展示错误
    }

    const payload: QuestionCreateRequest = {
      jobType: (values.jobType as string)?.trim(),
      questionType: values.questionType as QuestionType,
      difficulty: values.difficulty as QuestionDifficulty | undefined,
      content: values.content as string,
      keyPoints: ((values.keyPoints as string) ?? '')?.trim() || undefined,
      referenceAnswer: ((values.referenceAnswer as string) ?? '')?.trim() || undefined,
    };
    const skillTags = ((values.skillTags as string[]) ?? []).filter((s) => s && s.trim());
    const evaluationPoints = normalizeEvaluationPoints(
      (values.evaluationPoints as EvaluationPointDTO[]) ?? [],
    );
    if (skillTags.length > 0) payload.skillTags = skillTags;
    if (evaluationPoints.length > 0) payload.evaluationPoints = evaluationPoints;

    setSaving(true);
    try {
      if (isEdit && record) {
        await updateQuestion(record.id, { ...payload, version: record.version });
      } else {
        await createQuestion(payload);
      }
      message.success(isEdit ? '题目更新成功' : '题目创建成功');
      onSuccess();
    } catch (error) {
      // 2402 版本冲突：关闭当前编辑态并刷新列表（提示由拦截器发出）
      if (getErrorCode(error) === 2402) {
        onSuccess();
      }
      // 2401/2404 等已由拦截器提示，弹窗保留供用户修改
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      title={isEdit ? '编辑题目' : '创建题目'}
      open={open}
      onCancel={onClose}
      onOk={handleSubmit}
      confirmLoading={saving}
      width={640}
      okText={isEdit ? '保存' : '创建'}
      destroyOnClose
      className={styles.modal}
    >
      <Form
        form={form}
        layout="vertical"
        initialValues={{ difficulty: QuestionDifficulty.MEDIUM }}
      >
        <Form.Item
          name="jobType"
          label="岗位类型"
          rules={[
            { required: true, message: '请选择或输入岗位类型' },
            {
              validator: (_: unknown, value: string) =>
                value && value.trim() ? Promise.resolve() : Promise.reject(new Error('岗位类型不能为空白')),
            },
          ]}
        >
          <AutoComplete
            options={JOB_TYPE_OPTIONS}
            placeholder="选择或输入岗位类型，如 Java后端"
            className={styles.formSelect}
          />
        </Form.Item>

        <Form.Item
          name="questionType"
          label="题目类型"
          rules={[{ required: true, message: '请选择题目类型' }]}
        >
          <Select
            placeholder="选择题目类型"
            options={questionTypeOptions}
            className={styles.formSelect}
          />
        </Form.Item>

        <Form.Item name="difficulty" label="难度">
          <Select
            placeholder="难度（默认中等）"
            options={difficultyOptions}
            className={styles.formSelect}
          />
        </Form.Item>

        <Form.Item
          name="content"
          label="题干"
          rules={[
            { required: true, message: '请输入题干' },
            { max: 2000, message: '题干不能超过2000字符' },
          ]}
        >
          <TextArea rows={4} placeholder="请输入题干内容" maxLength={2000} className={styles.formTextarea} />
        </Form.Item>

        <Form.Item
          name="skillTags"
          label="技能标签"
          rules={[{ type: 'array', max: 10, message: '技能标签最多10个' }]}
        >
          <Select
            mode="tags"
            placeholder="输入技能标签后回车添加，如 Java / Redis"
            tokenSeparators={[',', '、']}
            className={styles.formSelect}
          />
        </Form.Item>

        <Form.Item name="keyPoints" label="考察要点">
          <TextArea rows={3} placeholder="请输入考察要点（可选）" className={styles.formTextarea} />
        </Form.Item>

        <Form.Item name="referenceAnswer" label="参考答案">
          <TextArea rows={3} placeholder="请输入参考答案（可选）" className={styles.formTextarea} />
        </Form.Item>

        <Form.Item label="评分要点">
          <Form.List name="evaluationPoints">
            {(fields, { add, remove }) => (
              <>
                {fields.map(({ key, name, ...restField }) => (
                  <div key={key} className={styles.evalRow}>
                    <Form.Item
                      {...restField}
                      name={[name, 'name']}
                      rules={[{ required: true, message: '请输入评分维度' }]}
                      className={styles.evalName}
                    >
                      <Input placeholder="评分维度，如 技术深度" />
                    </Form.Item>
                    <Form.Item
                      {...restField}
                      name={[name, 'weight']}
                      rules={[
                        { required: true, message: '请输入权重' },
                        {
                          validator: (_: unknown, value: number) =>
                            value !== undefined && value >= 0 && value <= 1
                              ? Promise.resolve()
                              : Promise.reject(new Error('权重需在0~1之间')),
                        },
                      ]}
                      className={styles.evalWeight}
                    >
                      <InputNumber min={0} max={1} step={0.1} placeholder="0~1" />
                    </Form.Item>
                    <DeleteOutlined className={styles.evalDelete} onClick={() => remove(name)} />
                  </div>
                ))}
                <Button type="dashed" onClick={() => add({ name: '', weight: 0.5 })} icon={<PlusOutlined />} block>
                  添加评分要点
                </Button>
              </>
            )}
          </Form.List>
        </Form.Item>
      </Form>
    </Modal>
  );
};

export default QuestionFormModal;
