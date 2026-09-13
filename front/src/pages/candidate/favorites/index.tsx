import React, { useState, useCallback, useEffect, useRef } from 'react';
import { useNavigate } from 'umi';
import { Button, Tag, Pagination, Empty, message } from 'antd';
import { DeleteOutlined, EnvironmentOutlined, StarOutlined } from '@ant-design/icons';
import PageHeader from '@/components/PageHeader';
import { getFavorites, setJobFavorite } from '@/services/favorite';
import type { FavoriteJobItem } from '@/services/favorite';
import { ROUTES } from '@/constants/routes';
import styles from './index.less';

const PAGE_SIZE = 10;

/** 学历编码 → 展示（覆盖后端 7 档，未知编码兜底原始值） */
const educationLabelMap: Record<string, string> = {
  NONE: '学历不限',
  JUNIOR_HIGH: '初中',
  HIGH_SCHOOL: '高中',
  ASSOCIATE: '大专',
  BACHELOR: '本科',
  MASTER: '硕士',
  DOCTOR: '博士',
};

/** 薪资展示：复用 JobCard 的分→k 换算（salaryNegotiable 0/1 归一为 boolean） */
const fmtSalary = (item: FavoriteJobItem) => {
  if (!!item.salaryNegotiable || item.salaryMinAmount === null || item.salaryMaxAmount === null) {
    return item.salaryRawText || '薪资面议';
  }
  const minK = (item.salaryMinAmount / 100000).toFixed(1).replace('.0', '');
  const maxK = (item.salaryMaxAmount / 100000).toFixed(1).replace('.0', '');
  const suffix = item.salaryMonths && item.salaryMonths > 12 ? `·${item.salaryMonths}薪` : '';
  return `${minK}k-${maxK}k${suffix}`;
};

/** 格式化收藏时间 */
const fmtTime = (iso: string) => {
  if (!iso) return '-';
  return iso.replace('T', ' ').substring(0, 16);
};

const FavoritesPage: React.FC = () => {
  const navigate = useNavigate();
  const [list, setList] = useState<FavoriteJobItem[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState(false);
  const [removingId, setRemovingId] = useState<number | null>(null);
  // 请求序号：翻页/删除并发防乱序覆盖
  const requestSeq = useRef(0);

  const fetchList = useCallback(async (targetPage: number) => {
    const seq = ++requestSeq.current;
    setLoading(true);
    setLoadError(false);
    try {
      const res = await getFavorites({ page: targetPage, size: PAGE_SIZE });
      // 竞态：只接受最新一次请求
      if (seq !== requestSeq.current) return;
      setList(res.list);
      setTotal(res.total);
      // 空页越界回退：当前页无数据但前面还有收藏 → 回退一页重新拉取
      // （统一覆盖单次删空/并发删空/翻页后删空，避免停在超界空页）
      if (res.list.length === 0 && targetPage > 1 && res.total > 0) {
        setPage(targetPage - 1);
      }
    } catch {
      if (seq !== requestSeq.current) return;
      setLoadError(true);
      setList([]);
      setTotal(0);
    } finally {
      if (seq === requestSeq.current) setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchList(page);
  }, [fetchList, page]);

  const handlePageChange = (next: number) => setPage(next);

  /**
   * 取消收藏：成功后立即从本地列表移除 + total-1，并重新拉取当前页。
   * 空页回退统一交给 fetchList 基于服务端返回判断（见 fetchList），
   * 并发取消时由 requestSeq 保证最后一次请求生效，不会停在超界空页。
   */
  const handleRemove = async (item: FavoriteJobItem) => {
    setRemovingId(item.jobId);
    try {
      await setJobFavorite(item.jobId, false);
      message.success('已取消收藏');
      setList((prev) => prev.filter((it) => it.jobId !== item.jobId));
      setTotal((t) => Math.max(t - 1, 0));
      fetchList(page);
    } catch {
      // 取消失败保留卡片（拦截器已提示）
    } finally {
      setRemovingId(null);
    }
  };

  const goDetail = (item: FavoriteJobItem) => {
    navigate(`${ROUTES.CANDIDATE_JOB_DETAIL}/${item.jobId}`);
  };

  const renderCard = (item: FavoriteJobItem) => {
    // 下线/删除岗位不允许跳详情，但保留取消收藏
    const canView = !item.isOffline && !item.deleted;
    return (
      <div
        key={item.jobId}
        className={`${styles.favCard} ${canView ? styles.clickable : ''}`}
        onClick={canView ? () => goDetail(item) : undefined}
        role={canView ? 'button' : undefined}
        tabIndex={canView ? 0 : undefined}
        onKeyDown={
          canView
            ? (e) => {
                if (e.key === 'Enter' || e.key === ' ') {
                  e.preventDefault();
                  goDetail(item);
                }
              }
            : undefined
        }
        aria-label={canView ? `查看 ${item.title || ''} 详情` : undefined}
      >
        <div className={styles.favHeader}>
          <div className={styles.favTitle}>
            {item.deleted ? '岗位已删除' : item.title || '—'}
          </div>
          {item.deleted ? (
            <Tag color="red">岗位已删除</Tag>
          ) : item.isOffline ? (
            <Tag color="orange">岗位已下线</Tag>
          ) : null}
        </div>

        {item.companyName && <div className={styles.favCompany}>{item.companyName}</div>}

        <div className={styles.favSalary}>{fmtSalary(item)}</div>

        <div className={styles.favMeta}>
          {item.cityName && (
            <span className={styles.favMetaItem}><EnvironmentOutlined /> {item.cityName}</span>
          )}
          {typeof item.minExperienceYears === 'number' && item.minExperienceYears > 0 ? (
            <span className={styles.favMetaItem}>{item.minExperienceYears}年以上</span>
          ) : (
            <span className={styles.favMetaItem}>经验不限</span>
          )}
          <span className={styles.favMetaItem}>
            {educationLabelMap[item.educationRequirement || ''] || item.educationRequirement || '学历不限'}
          </span>
          {item.industryName && <span className={styles.favMetaItem}>{item.industryName}</span>}
        </div>

        <div className={styles.favFooter}>
          <span className={styles.favTime}>收藏于 {fmtTime(item.favoritedAt)}</span>
          <Button
            type="link"
            size="small"
            danger
            icon={<DeleteOutlined />}
            loading={removingId === item.jobId}
            onClick={(e) => {
              e.stopPropagation();
              handleRemove(item);
            }}
          >
            取消收藏
          </Button>
        </div>
      </div>
    );
  };

  return (
    <div className={styles.page}>
      <PageHeader title="岗位收藏" description="你收藏的岗位列表" />

      {loadError ? (
        <div className={styles.emptyState}>
          <Empty description="加载失败，请重试">
            <Button type="primary" onClick={() => fetchList(page)}>重新加载</Button>
          </Empty>
        </div>
      ) : loading && list.length === 0 ? (
        <div className={styles.emptyState}>
          <Empty description="正在加载收藏列表..." />
        </div>
      ) : list.length === 0 ? (
        <div className={styles.emptyState}>
          <div className={styles.emptyIcon}><StarOutlined /></div>
          <Empty description="暂未收藏岗位" />
          <div className={styles.emptyHint}>
            去岗位探索页收藏心仪的岗位吧
          </div>
          <Button type="primary" className={styles.goJobBtn} onClick={() => navigate(ROUTES.CANDIDATE_JOB)}>
            去逛逛
          </Button>
        </div>
      ) : (
        <>
          <div className={styles.grid}>
            {list.map((item) => renderCard(item))}
          </div>

          {total > PAGE_SIZE && (
            <div className={styles.pagination}>
              <Pagination
                current={page}
                pageSize={PAGE_SIZE}
                total={total}
                onChange={handlePageChange}
                showSizeChanger={false}
              />
            </div>
          )}
        </>
      )}
    </div>
  );
};

export default FavoritesPage;
