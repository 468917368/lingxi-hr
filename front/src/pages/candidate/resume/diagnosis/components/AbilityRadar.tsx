import React from 'react';

interface AbilityRadarProps {
  /** 维度数据（顺序即雷达图顺时针分布顺序） */
  data: { label: string; value: number }[];
  /** 画布尺寸（px） */
  size?: number;
  /** 满分值 */
  maxValue?: number;
  /** 数据线颜色 */
  color?: string;
}

/**
 * 能力维度雷达图（SVG 手写实现）
 *
 * <p>替代 G2：维度顺序、起始角度、网格、尺寸全部可控，不依赖图表库的
 * 分类轴排序/坐标行为（G2 polar 会打乱维度顺序、角度偏移不可控）。
 *
 * <p>结构：多圈正多边形网格 + 中心辐条线 + 数据多边形（半透明填充）
 * + 顶点圆点 + 维度标签。第一个维度从正上方（-90°）开始顺时针分布。
 */
const AbilityRadar: React.FC<AbilityRadarProps> = ({
  data,
  size = 260,
  maxValue = 100,
  color = '#FF6B6B',
}) => {
  const cx = size / 2;
  const cy = size / 2;
  const radius = size / 2 - 42; // 预留维度标签空间
  const count = data.length;
  const startAngle = -Math.PI / 2; // 正上方开始

  /** 第 i 个维度的角度 */
  const angleOf = (i: number) => startAngle + (i * 2 * Math.PI) / count;

  /** 半径 r 下第 i 个顶点的坐标 */
  const pointOf = (i: number, r: number) => ({
    x: cx + r * Math.cos(angleOf(i)),
    y: cy + r * Math.sin(angleOf(i)),
  });

  if (count === 0) {
    return null;
  }

  // 网格层（外圈 + 3 层内圈）
  const gridLevels = [0.25, 0.5, 0.75, 1];

  return (
    <svg
      width={size}
      height={size}
      viewBox={`0 0 ${size} ${size}`}
      style={{ display: 'block' }}
    >
      {/* 网格多边形 */}
      {gridLevels.map((level) => (
        <polygon
          key={level}
          points={data
            .map((_, i) => {
              const p = pointOf(i, radius * level);
              return `${p.x},${p.y}`;
            })
            .join(' ')}
          fill="none"
          stroke="#E5E7EB"
          strokeWidth={1}
        />
      ))}

      {/* 数据多边形（维度顺序连接，分值映射半径） */}
      <polygon
        points={data
          .map((d, i) => {
            const p = pointOf(i, radius * (Math.min(d.value, maxValue) / maxValue));
            return `${p.x},${p.y}`;
          })
          .join(' ')}
        fill={`${color}33`}
        stroke={color}
        strokeWidth={2}
        strokeLinejoin="round"
      />

      {/* 中心辐条线（在数据多边形之上，从中心点清晰辐射到每个顶点） */}
      {data.map((_, i) => {
        const p = pointOf(i, radius);
        return (
          <line
            key={`spoke-${i}`}
            x1={cx}
            y1={cy}
            x2={p.x}
            y2={p.y}
            stroke="#D1D5DB"
            strokeWidth={1}
          />
        );
      })}

      {/* 中心圆点（辐条汇聚点） */}
      <circle cx={cx} cy={cy} r={3} fill="#D1D5DB" />

      {/* 顶点圆点 + 维度标签 */}
      {data.map((d, i) => {
        const vp = pointOf(i, radius * (Math.min(d.value, maxValue) / maxValue));
        const lp = pointOf(i, radius + 24);
        return (
          <g key={d.label}>
            <circle cx={vp.x} cy={vp.y} r={4} fill={color} />
            <text
              x={lp.x}
              y={lp.y + 4}
              textAnchor="middle"
              fontSize={12}
              fill="#666"
            >
              {d.label}
            </text>
          </g>
        );
      })}
    </svg>
  );
};

export default AbilityRadar;
