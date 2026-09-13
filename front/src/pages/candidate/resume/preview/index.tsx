import React, { useState, useEffect, useRef } from 'react';
import { useNavigate, useParams } from 'umi';
import { Button, Input, Modal, Spin, Upload, message } from 'antd';
import {
  DeleteOutlined,
  StarOutlined,
  EditOutlined,
  CloseOutlined,
  PlusOutlined,
  FormOutlined,
  UserOutlined,
  CameraOutlined,
} from '@ant-design/icons';
import { ROUTES } from '@/constants/routes';
import {
  getResumeDetail,
  getResumeList,
  updateResume,
  deleteResume,
  setDefaultResume,
  uploadFacePhoto,
} from '@/services/resume';
import type { ResumeDetail, CardSection } from '@/services/resume';
import styles from './index.less';

/** 基本信息字段（后端独立列存储，非 cardStructure） */
interface BasicInfo {
  candidateName: string;
  phone: string;
  email: string;
  wechat: string;
}

/**
 * 个人信息类章节标题：与顶部"基本信息"卡片（独立列）重复展示，
 * sections 渲染时跳过，额外信息（求职意向/年龄/地址等）并入顶部卡片
 */
const PERSONAL_SECTION_TITLES = ['基本信息', '个人信息', '基本资料', '个人资料', '联系方式'];

/** 额外信息原始文本：个人信息章节 points 中非 4 个独立字段（姓名/手机/邮箱/微信）的条目 */
const getExtraTexts = (d: ResumeDetail | null): string[] =>
  d?.cardStructure?.sections
    ?.find((s) => PERSONAL_SECTION_TITLES.includes(s.title))
    ?.points?.filter((p) => !/姓名|电话|手机|邮箱|微信|email|phone|wechat/i.test(p.text))
    .map((p) => p.text) ?? [];

/**
 * 按第一个中英文冒号拆分「标签：值」，用于额外信息展示（求职意向/年龄/地址等，
 * 避免一律显示"其他"）；无冒号或冒号在开头时整条归"其他"
 */
const splitExtra = (text: string): { label: string; value: string } => {
  const idx = text.search(/[：:]/);
  if (idx <= 0) return { label: '其他', value: text };
  return { label: text.slice(0, idx).trim(), value: text.slice(idx + 1).trim() };
};

const ResumePreviewPage: React.FC = () => {
  const navigate = useNavigate();
  const { id } = useParams<{ id: string }>();
  // 简历 ID 为字符串：雪花 ID（19 位）超出 JS Number 安全整数范围，不可 Number() 转换
  const resumeId = id ?? '';
  const isMountedRef = useRef(true);

  const [detail, setDetail] = useState<ResumeDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const [saving, setSaving] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [isDefault, setIsDefault] = useState(false);
  /** 头像上传中 */
  const [avatarUploading, setAvatarUploading] = useState(false);

  // 编辑态：基本信息整体编辑 + 单章节编辑
  const [basicEditing, setBasicEditing] = useState(false);
  const [editingSectionIdx, setEditingSectionIdx] = useState<number | null>(null);
  const [draftBasic, setDraftBasic] = useState<BasicInfo>({
    candidateName: '',
    phone: '',
    email: '',
    wechat: '',
  });
  const [draftSections, setDraftSections] = useState<CardSection[]>([]);
  /** 额外信息编辑草稿（求职意向/年龄/地址等整行文本，保存时回写个人信息章节 points） */
  const [draftExtra, setDraftExtra] = useState<string[]>([]);
  /** 章节增删变更标志：删除/新增章节后 draftSections 有未保存变更。
   *  置 true 使底部操作栏显示「取消编辑/保存修改」，渲染源切换到 draftSections，
   *  保存后卡片列表实时反映增删（保存前不提交后端） */
  const [sectionEditActive, setSectionEditActive] = useState(false);
  /** 新增章节弹窗（自由文本输入标题） */
  const [addingSection, setAddingSection] = useState(false);
  const [newSectionTitle, setNewSectionTitle] = useState('');

  /** 加载简历详情（简历不存在时自动跳转默认简历——入口硬编码 id=1 的兜底） */
  const fetchDetail = async () => {
    setLoading(true);
    setLoadError(false);
    try {
      const data = await getResumeDetail(resumeId).catch(() => null);
      if (!isMountedRef.current) return;
      if (!data) {
        // 简历不存在（URL 可能是硬编码 id=1）：尝试自动跳转到默认简历
        const listData = await getResumeList({ page: 1, pageSize: 5 }).catch(() => null);
        const defaultResume =
          listData?.list?.find((i) => i.isDefault === 1) ?? listData?.list?.[0];
        if (defaultResume && defaultResume.id !== resumeId) {
          navigate(`${ROUTES.CANDIDATE_RESUME_PREVIEW}/${defaultResume.id}/preview`, {
            replace: true,
          });
          return; // 等待重定向后的加载
        }
      }
      setDetail(data);
      setIsDefault(data?.isDefault === 1);
    } catch {
      // 错误已由请求拦截器统一提示
      if (isMountedRef.current) setLoadError(true);
    } finally {
      if (isMountedRef.current) setLoading(false);
    }
  };

  useEffect(() => {
    isMountedRef.current = true;
    fetchDetail();
    return () => {
      isMountedRef.current = false;
    };
  }, [resumeId]);

  /** 解析中自动刷新：PENDING/PARSING 期间每 3s 重拉详情，终态后停止（解析完成自动展示内容） */
  useEffect(() => {
    if (!detail || (detail.parseStatus !== 'PENDING' && detail.parseStatus !== 'PARSING')) {
      return;
    }
    const timer = window.setInterval(async () => {
      const data = await getResumeDetail(resumeId).catch(() => null);
      if (!isMountedRef.current) return;
      if (data && data.parseStatus !== 'PENDING' && data.parseStatus !== 'PARSING') {
        setDetail(data);
        setIsDefault(data?.isDefault === 1);
        window.clearInterval(timer);
      }
    }, 3000);
    return () => window.clearInterval(timer);
  }, [detail?.parseStatus, resumeId]);

  // ==================== 编辑态进入/退出 ====================

  /** 深拷贝完整 sections（编辑草稿与原始数据隔离，保存时整体回写） */
  const deepCopySections = (sections: CardSection[]): CardSection[] =>
    sections.map((s) => ({ ...s, points: s.points.map((p) => ({ ...p })) }));

  const enterBasicEdit = () => {
    if (!detail) return;
    setDraftBasic({
      candidateName: detail.candidateName || '',
      phone: detail.phone || '',
      email: detail.email || '',
      wechat: detail.wechat || '',
    });
    setDraftExtra(getExtraTexts(detail));
    setBasicEditing(true);
    setEditingSectionIdx(null);
    setSectionEditActive(false);
  };

  const enterSectionEdit = (idx: number) => {
    if (!detail?.cardStructure) return;
    // 草稿为空时深拷贝一份供编辑，保存时整体回写；草稿已有变更（章节增删后）
    // 不覆盖——否则会丢掉已删除/新增章节的草稿
    if (draftSections.length === 0) {
      setDraftSections(deepCopySections(detail.cardStructure.sections));
    }
    setEditingSectionIdx(idx);
    setBasicEditing(false);
  };

  const cancelEdit = () => {
    setBasicEditing(false);
    setEditingSectionIdx(null);
    setDraftSections([]);
    setDraftExtra([]);
    setSectionEditActive(false);
  };

  // ==================== 章节要点增删改（编辑态内受控操作） ====================

  const handlePointChange = (sectionIdx: number, pointIdx: number, text: string) => {
    setDraftSections((prev) =>
      prev.map((s, i) =>
        i !== sectionIdx
          ? s
          : { ...s, points: s.points.map((p, j) => (j === pointIdx ? { ...p, text } : p)) },
      ),
    );
  };

  const handlePointAdd = (sectionIdx: number) => {
    setDraftSections((prev) =>
      prev.map((s, i) =>
        i !== sectionIdx
          ? s
          : {
              ...s,
              points: [
                ...s.points,
                { id: `new-${Date.now()}-${s.points.length}`, text: '' },
              ],
            },
      ),
    );
  };

  const handlePointRemove = (sectionIdx: number, pointIdx: number) => {
    setDraftSections((prev) =>
      prev.map((s, i) =>
        i !== sectionIdx
          ? s
          : { ...s, points: s.points.filter((_, j) => j !== pointIdx) },
      ),
    );
  };

  // ==================== 章节标题修改（编辑态内受控操作） ====================

  /**
   * 修改章节标题（随保存整体回写）。
   * 个人信息类标题会被渲染过滤且永不展示（该区域由顶部基本信息卡片管理），
   * 输入命中即拦截，避免改出"看不见的章节"
   */
  const handleSectionTitleChange = (sectionIdx: number, title: string) => {
    if (PERSONAL_SECTION_TITLES.includes(title)) {
      message.error(`「${title}」属于基本信息，由顶部卡片管理，不能作为章节标题`);
      return;
    }
    setDraftSections((prev) =>
      prev.map((s, i) => (i === sectionIdx ? { ...s, title } : s)),
    );
  };

  // ==================== 章节新增/删除（二次确认，编辑态内受控操作） ====================

  /**
   * 删除章节：从草稿中移除并进入编辑态（保存前不提交后端，可取消恢复）。
   * 非编辑态时草稿为空，先深拷贝原始 sections 再操作——渲染源已切换到草稿，
   * displaySections 的原始索引与草稿索引一致，filter 不影响其他章节的索引。
   */
  const handleSectionRemove = (sectionIdx: number) => {
    const title =
      (draftSections.length > 0 ? draftSections[sectionIdx] : undefined)
        ?.title
      ?? detail?.cardStructure?.sections[sectionIdx]?.title
      ?? '该章节';
    Modal.confirm({
      title: `确认删除「${title}」章节？`,
      content: '删除后该章节所有要点将从卡片中移除，保存后不可恢复',
      okText: '删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: () => {
        const base =
          draftSections.length > 0
            ? draftSections
            : deepCopySections(detail?.cardStructure?.sections ?? []);
        setDraftSections(base.filter((_, i) => i !== sectionIdx));
        setSectionEditActive(true);
        // 删除的恰是正在编辑的章节才退出其编辑框；删其他章节时保留当前编辑
        setEditingSectionIdx((cur) => (cur === sectionIdx ? null : cur));
        setBasicEditing(false);
      },
    });
  };

  /** 新增章节：标题自由输入 → 追加空章节并进入其编辑态（可直接填写要点） */
  const handleSectionAddConfirm = () => {
    const title = newSectionTitle.trim();
    if (!title) {
      message.error('章节标题不能为空');
      return;
    }
    if (PERSONAL_SECTION_TITLES.includes(title)) {
      message.error(`「${title}」属于基本信息，由顶部卡片管理，请换一个标题`);
      return;
    }
    const base =
      draftSections.length > 0
        ? draftSections
        : deepCopySections(detail?.cardStructure?.sections ?? []);
    setDraftSections([...base, { title, points: [], confidence: 'HIGH' }]);
    setEditingSectionIdx(base.length);
    setSectionEditActive(true);
    setBasicEditing(false);
    setAddingSection(false);
    setNewSectionTitle('');
  };

  // ==================== 额外信息（求职意向/年龄/地址等）增删改 ====================

  const handleExtraChange = (idx: number, text: string) => {
    setDraftExtra((prev) => prev.map((t, i) => (i === idx ? text : t)));
  };

  const handleExtraAdd = () => {
    setDraftExtra((prev) => [...prev, '']);
  };

  const handleExtraRemove = (idx: number) => {
    setDraftExtra((prev) => prev.filter((_, i) => i !== idx));
  };

  // ==================== 操作 ====================

  /** 保存修改（合并基本信息 + 额外信息 + 章节编辑态） */
  const handleSave = async () => {
    if (!detail) return;
    setSaving(true);
    try {
      const baseCard = detail.cardStructure ?? { sections: [], confidence: 'LOW' as const };
      let sections = baseCard.sections;
      if (basicEditing) {
        // 额外信息（求职意向/年龄/地址等）整行文本回写个人信息章节 points：
        // 4 个独立字段（姓名/手机/邮箱/微信）的条目保留原样，其余由草稿整体替换
        const extraPoints = draftExtra
          .filter((t) => t.trim() !== '')
          .map((text, i) => ({ id: `extra-${Date.now()}-${i}`, text }));
        const hasPersonal = sections.some((s) => PERSONAL_SECTION_TITLES.includes(s.title));
        if (hasPersonal) {
          sections = sections.map((s) =>
            PERSONAL_SECTION_TITLES.includes(s.title)
              ? {
                  ...s,
                  points: [
                    ...(s.points ?? []).filter((p) =>
                      /姓名|电话|手机|邮箱|微信|email|phone|wechat/i.test(p.text),
                    ),
                    ...extraPoints,
                  ],
                }
              : s,
          );
        } else if (extraPoints.length > 0) {
          // 防御：个人信息章节不存在时新建（正常解析总会生成，仅防数据异常）
          sections = [
            ...sections,
            { title: '基本信息', confidence: 'HIGH' as const, points: extraPoints },
          ];
        }
      } else if (editingSectionIdx !== null || sectionEditActive) {
        // 单章节编辑或章节增删（删除/新增后未进入具体章节编辑）→ 整体提交草稿
        sections = draftSections;
      }
      await updateResume(resumeId, {
        cardStructure: { ...baseCard, sections },
        candidateName: basicEditing ? draftBasic.candidateName : detail.candidateName,
        phone: basicEditing ? draftBasic.phone : detail.phone,
        email: basicEditing ? draftBasic.email : detail.email,
        wechat: basicEditing ? draftBasic.wechat : detail.wechat,
      });
      message.success('简历已保存');
      cancelEdit();
      fetchDetail();
    } catch {
      // 错误已由拦截器统一提示
    } finally {
      if (isMountedRef.current) setSaving(false);
    }
  };

  /** 设为默认简历（后端事务互斥，默认态下禁用） */
  const handleSetDefault = async () => {
    try {
      await setDefaultResume(resumeId);
      message.success('已设为默认简历');
      setIsDefault(true);
    } catch {
      // 错误已由拦截器统一提示
    }
  };

  /** 更换头像（校验 → 上传 → 更新展示） */
  const handleAvatarUpload = async (file: File) => {
    const isValidType = file.type === 'image/jpeg' || file.type === 'image/png';
    if (!isValidType) {
      message.error('头像仅支持 JPG/PNG 格式');
      return false;
    }
    if (file.size / 1024 / 1024 > 2) {
      message.error('头像大小不能超过 2MB');
      return false;
    }
    setAvatarUploading(true);
    try {
      const newUrl = await uploadFacePhoto(resumeId, file);
      setDetail((prev) => (prev ? { ...prev, facePhotoUrl: newUrl } : prev));
      message.success('头像已更新');
    } catch {
      // 错误已由拦截器统一提示
    } finally {
      if (isMountedRef.current) {
        setAvatarUploading(false);
      }
    }
    return false;
  };

  /** 删除简历（二次确认） */
  const handleDelete = () => {
    Modal.confirm({
      title: '确认删除这份简历？',
      content: '删除后不可恢复，且原件将被同时清除',
      okText: '删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        setDeleting(true);
        try {
          await deleteResume(resumeId);
          message.success('简历已删除');
          navigate(ROUTES.CANDIDATE_RESUME_UPLOAD);
        } catch {
          // 错误已由拦截器统一提示
        } finally {
          if (isMountedRef.current) setDeleting(false);
        }
      },
    });
  };

  // ==================== 渲染 ====================

  if (loading) {
    return (
      <div className={styles.pageCenter}>
        <Spin size="large" />
      </div>
    );
  }

  if (loadError || !detail) {
    return (
      <div className={styles.pageCenter}>
        {loadError ? (
          <div className={styles.emptyState}>
            <div className={styles.emptyIcon}>🌐</div>
            <div className={styles.emptyTitle}>网络开小差了</div>
            <div className={styles.emptyDesc}>请检查网络连接后重试</div>
            <div className={styles.emptyActions}>
              <Button type="primary" onClick={fetchDetail}>
                重新加载
              </Button>
            </div>
          </div>
        ) : (
          <div className={styles.emptyState}>
            <div className={styles.emptyIcon}>📄</div>
            <div className={styles.emptyTitle}>还没有可编辑的简历</div>
            <div className={styles.emptyDesc}>
              上传简历后，可在线编辑和优化简历内容
            </div>
            <div className={styles.emptyActions}>
              <Button
                type="primary"
                onClick={() => navigate(ROUTES.CANDIDATE_RESUME_UPLOAD)}
              >
                去上传简历
              </Button>
              <Button onClick={fetchDetail}>重新加载</Button>
            </div>
          </div>
        )}
      </div>
    );
  }

  // 渲染源：章节增删变更（sectionEditActive）时用草稿，否则用原始数据——
  // 删除/新增的章节在保存前就要从列表消失/出现（保存时才提交后端）
  const sourceSections = sectionEditActive
    ? draftSections
    : (detail.cardStructure?.sections ?? []);
  // 个人信息类章节：渲染跳过（与顶部卡片重复），其额外信息并入顶部卡片
  const extraBasicInfo = getExtraTexts(detail);
  // 携带原始索引：draftSections 是完整 sections 的深拷贝，编辑态必须按原始索引访问，
  // 否则过滤掉个人信息章节后索引错位，会编辑到别的章节（如编辑技能改到教育）
  const displaySections = sourceSections
    .map((s, i) => ({ s, rawIdx: i }))
    .filter(({ s }) => !PERSONAL_SECTION_TITLES.includes(s.title));
  const isEditing = basicEditing || editingSectionIdx !== null || sectionEditActive;

  return (
    <div className={styles.page}>
      {/* 顶部操作栏 */}
      <div className={styles.topBar}>
        <div className={styles.topBarLeft}>
          <Button
            icon={<StarOutlined />}
            type={isDefault ? 'primary' : 'default'}
            disabled={isDefault}
            onClick={handleSetDefault}
            loading={deleting}
            size="large"
            style={{
              height: 44,
              borderRadius: 'var(--radius-md)',
              fontWeight: 650,
              fontFamily: 'var(--font-title)',
            }}
          >
            {isDefault ? '当前默认' : '设为默认'}
          </Button>
          <Button
            danger
            icon={<DeleteOutlined />}
            onClick={handleDelete}
            size="large"
            style={{
              height: 44,
              borderRadius: 'var(--radius-md)',
              fontWeight: 650,
              fontFamily: 'var(--font-title)',
            }}
          >
            删除简历
          </Button>
        </div>
        <Button
          icon={<FormOutlined />}
          size="large"
          onClick={() => navigate(`${ROUTES.CANDIDATE_RESUME_DIAGNOSIS}/${resumeId}/diagnosis`)}
          style={{
            height: 44,
            borderRadius: 'var(--radius-md)',
            fontWeight: 650,
            fontFamily: 'var(--font-title)',
          }}
        >
          简历诊断
        </Button>
      </div>

      {/* 基本信息（后端独立列） */}
      <div className={styles.card}>
        <div className={styles.cardTitle}>
          基本信息
          {!basicEditing && (
            <Button
              type="link"
              size="small"
              icon={<EditOutlined />}
              onClick={enterBasicEdit}
            >
              编辑
            </Button>
          )}
        </div>

        {/* 头像区：解析自动提取，可手动更换（hover 显示"更换头像"） */}
        <div className={styles.basicHeader}>
          <Upload
            showUploadList={false}
            accept=".jpg,.jpeg,.png"
            beforeUpload={handleAvatarUpload}
          >
            <div className={styles.avatarWrap}>
              {detail.facePhotoUrl ? (
                <img src={detail.facePhotoUrl} alt="头像" className={styles.avatar} />
              ) : (
                <div className={`${styles.avatar} ${styles.avatarPlaceholder}`}>
                  <UserOutlined />
                </div>
              )}
              <div className={styles.avatarEditOverlay}>
                <CameraOutlined /> {avatarUploading ? '上传中…' : '更换头像'}
              </div>
            </div>
          </Upload>
          <div className={styles.basicName}>
            <div className={styles.basicNameText}>{detail.candidateName || '未填写'}</div>
            <div className={styles.basicNameHint}>
              头像可在简历解析时自动提取，也可手动更换
            </div>
          </div>
        </div>
        {basicEditing ? (
          <div className={styles.basicEditList}>
            {(
              [
                ['candidateName', '姓名'],
                ['phone', '手机号'],
                ['email', '邮箱'],
                ['wechat', '微信'],
              ] as const
            ).map(([key, label]) => (
              <div key={key} className={styles.basicEditRow}>
                <span className={styles.basicEditLabel}>{label}</span>
                <Input
                  value={draftBasic[key]}
                  onChange={(e) =>
                    setDraftBasic((prev) => ({ ...prev, [key]: e.target.value }))
                  }
                />
              </div>
            ))}
            {/* 额外信息（求职意向/年龄/地址等）：整行文本编辑，与章节要点交互一致 */}
            <div className={styles.basicEditDivider}>其他信息</div>
            <div className={styles.pointEditList}>
              {draftExtra.map((text, i) => (
                <div key={i} className={styles.pointEditRow}>
                  <Input.TextArea
                    value={text}
                    onChange={(e) => handleExtraChange(i, e.target.value)}
                    autoSize={{ minRows: 1, maxRows: 6 }}
                    placeholder="输入内容，如：求职意向：AI 工程研发实习生-长沙"
                  />
                  <Button
                    danger
                    size="small"
                    icon={<CloseOutlined />}
                    onClick={() => handleExtraRemove(i)}
                  />
                </div>
              ))}
              <Button
                size="small"
                icon={<PlusOutlined />}
                onClick={handleExtraAdd}
                style={{ marginTop: 4 }}
              >
                添加信息
              </Button>
            </div>
          </div>
        ) : (
          <div className={styles.basicInfoList}>
            <div className={styles.basicInfoRow}>
              <span className={styles.basicInfoLabel}>姓名</span>
              <span>{detail.candidateName || '—'}</span>
            </div>
            <div className={styles.basicInfoRow}>
              <span className={styles.basicInfoLabel}>手机号</span>
              <span>{detail.phone || '—'}</span>
            </div>
            <div className={styles.basicInfoRow}>
              <span className={styles.basicInfoLabel}>邮箱</span>
              <span>{detail.email || '—'}</span>
            </div>
            <div className={styles.basicInfoRow}>
              <span className={styles.basicInfoLabel}>微信</span>
              <span>{detail.wechat || '—'}</span>
            </div>
            {/* 个人信息类章节的额外信息（求职意向/年龄/地址等，独立列没有的）：
                按「标签：值」拆分展示，避免一律显示"其他" */}
            {extraBasicInfo.map((text, i) => {
              const { label, value } = splitExtra(text);
              return (
                <div key={i} className={styles.basicInfoRow}>
                  <span className={styles.basicInfoLabel}>{label}</span>
                  <span>{value}</span>
                </div>
              );
            })}
          </div>
        )}
      </div>

      {/* 章节卡片（cardStructure.sections，跳过个人信息类——已并入顶部基本信息卡片） */}
      {displaySections.length > 0 ? (
        displaySections.map(({ s: section, rawIdx: sectionIdx }) => {
          const isSectionEditing = editingSectionIdx === sectionIdx;
          const lowConf = section.confidence === 'LOW';
          return (
            <div
              key={sectionIdx}
              className={`${styles.card} ${lowConf ? styles.cardLowConf : ''}`}
            >
              <div className={styles.cardTitle}>
                {isSectionEditing ? (
                  <Input
                    size="small"
                    value={draftSections[sectionIdx]?.title ?? section.title}
                    onChange={(e) => handleSectionTitleChange(sectionIdx, e.target.value)}
                    maxLength={30}
                    placeholder="章节标题"
                    style={{ width: 260, fontWeight: 600, fontFamily: 'var(--font-title)' }}
                  />
                ) : (
                  section.title
                )}
                {lowConf && <span className={styles.lowConfBadge}>置信度低</span>}
                {!isSectionEditing && (
                  <>
                    <Button
                      type="link"
                      size="small"
                      icon={<EditOutlined />}
                      onClick={() => enterSectionEdit(sectionIdx)}
                    >
                      编辑
                    </Button>
                    <Button
                      type="link"
                      size="small"
                      danger
                      icon={<DeleteOutlined />}
                      onClick={() => handleSectionRemove(sectionIdx)}
                    >
                      删除
                    </Button>
                  </>
                )}
              </div>

              {isSectionEditing ? (
                <div className={styles.pointEditList}>
                  {draftSections[sectionIdx]?.points.map((point, pointIdx) => (
                    <div key={point.id || pointIdx} className={styles.pointEditRow}>
                      <Input.TextArea
                        value={point.text}
                        onChange={(e) =>
                          handlePointChange(sectionIdx, pointIdx, e.target.value)
                        }
                        autoSize={{ minRows: 1, maxRows: 6 }}
                        placeholder="输入内容..."
                      />
                      <Button
                        danger
                        size="small"
                        icon={<CloseOutlined />}
                        onClick={() => handlePointRemove(sectionIdx, pointIdx)}
                      />
                    </div>
                  ))}
                  <Button
                    size="small"
                    icon={<PlusOutlined />}
                    onClick={() => handlePointAdd(sectionIdx)}
                    style={{ marginTop: 8 }}
                  >
                    添加要点
                  </Button>
                </div>
              ) : (
                <div className={styles.cardContent}>
                  {section.points.length > 0 ? (
                    section.points.map((point, pointIdx) => (
                      <div key={point.id || pointIdx} className={styles.pointItem}>
                        {point.text}
                      </div>
                    ))
                  ) : (
                    <div className={styles.pointEmpty}>{section.raw_text || '—'}</div>
                  )}
                </div>
              )}
            </div>
          );
        })
      ) : (
        <div className={styles.card}>
          <div className={styles.cardTitle}>简历内容</div>
          <div className={styles.pointEmpty}>
            {detail?.parseStatus === 'PENDING' || detail?.parseStatus === 'PARSING'
              ? '简历解析中，完成后自动展示内容'
              : detail?.parseStatus === 'FAILED'
                ? '简历解析失败，请返回上传页删除后重新上传'
                : sectionEditActive
                  ? '已删除全部章节，可点击下方「新增章节」重新添加'
                  : '简历尚未解析出结构化内容，请返回上传页重新解析'}
          </div>
        </div>
      )}

      {/* 新增章节（标题自由输入；个人信息章节由顶部基本信息卡片管理，提交时校验拦截） */}
      {!basicEditing && (
        <Button
          type="dashed"
          block
          icon={<PlusOutlined />}
          onClick={() => {
            setNewSectionTitle('');
            setAddingSection(true);
          }}
          style={{
            marginTop: 16,
            height: 48,
            borderRadius: 'var(--radius-md)',
            fontFamily: 'var(--font-title)',
            fontWeight: 600,
          }}
        >
          新增章节
        </Button>
      )}

      {/* 底部操作栏 */}
      <div className={styles.bottomBar}>
        <Button
          size="large"
          disabled={saving}
          onClick={isEditing ? cancelEdit : () => navigate(-1)}
          style={{
            height: 44,
            borderRadius: 'var(--radius-md)',
            fontWeight: 650,
            fontFamily: 'var(--font-title)',
          }}
        >
          {isEditing ? '取消编辑' : '返回'}
        </Button>
        <Button
          type="primary"
          size="large"
          loading={saving}
          onClick={handleSave}
          style={{
            height: 44,
            borderRadius: 'var(--radius-md)',
            fontWeight: 650,
            fontFamily: 'var(--font-title)',
            background: 'var(--accent)',
            borderColor: 'var(--accent)',
            boxShadow: '0 4px 14px var(--accent-shadow)',
          }}
        >
          保存修改
        </Button>
      </div>

      {/* 新增章节弹窗：自由文本输入标题（个人信息类标题由校验拦截） */}
      <Modal
        title="新增章节"
        open={addingSection}
        onOk={handleSectionAddConfirm}
        onCancel={() => setAddingSection(false)}
        okText="添加"
        cancelText="取消"
        destroyOnClose
      >
        <Input
          value={newSectionTitle}
          onChange={(e) => setNewSectionTitle(e.target.value)}
          onPressEnter={handleSectionAddConfirm}
          placeholder="如：实习经历、项目经验、自我评价"
          maxLength={30}
          autoFocus
        />
      </Modal>
    </div>
  );
};

export default ResumePreviewPage;
