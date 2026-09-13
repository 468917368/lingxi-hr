import React, { useState, useEffect, useCallback, useRef } from 'react';
import { Input, Typography, message, Tabs, Modal } from 'antd';
import { PhoneOutlined, LockOutlined } from '@ant-design/icons';
import { useNavigate } from 'umi';
import { useAuth } from '@/hooks/useAuth';
import { sendCode, loginByCode, loginByPassword } from '@/services/auth';
import { isApiError } from '@/utils/apiError';
import type { LoginResponse } from '@/constants/apiTypes';
import styles from './index.less';

const { Text } = Typography;

const isValidPhone = (phone: string) => /^1[3-9]\d{9}$/.test(phone);

/* Animated counter hook */
function useCounter(target: number, duration = 2000, startDelay = 1200) {
  const [value, setValue] = useState(0);
  const started = useRef(false);

  useEffect(() => {
    if (started.current) return;
    started.current = true;
    const timer = setTimeout(() => {
      const startTime = Date.now();
      const tick = () => {
        const elapsed = Date.now() - startTime;
        const progress = Math.min(elapsed / duration, 1);
        const eased = 1 - Math.pow(1 - progress, 3);
        setValue(Math.round(eased * target));
        if (progress < 1) requestAnimationFrame(tick);
      };
      requestAnimationFrame(tick);
    }, startDelay);
    return () => clearTimeout(timer);
  }, [target, duration, startDelay]);

  return value;
}

// 登录页加载时立即清除旧token（同步执行，不等渲染）
if (typeof window !== 'undefined') {
  localStorage.removeItem('lingxi_accessToken');
  localStorage.removeItem('lingxi_refreshToken');
  localStorage.removeItem('lingxi_tokenExpiry');
  localStorage.removeItem('lingxi_tokenExpireTime');
}

const LoginPage: React.FC = () => {
  const navigate = useNavigate();
  const { login } = useAuth();
  const formPanelRef = useRef<HTMLDivElement>(null);

  const [loginType, setLoginType] = useState<'code' | 'password'>('code');
  const [phone, setPhone] = useState('');
  const [code, setCode] = useState('');
  const [password, setPassword] = useState('');
  const [countdown, setCountdown] = useState(0);
  const [sending, setSending] = useState(false);
  const [loading, setLoading] = useState(false);

  // Animated counters
  const jobCount = useCounter(5000, 2000, 1300);
  const companyCount = useCounter(1200, 2000, 1500);
  const satisfaction = useCounter(98, 1800, 1700);

  // Mouse spotlight tracking
  const handleMouseMove = useCallback((e: React.MouseEvent<HTMLDivElement>) => {
    const rect = e.currentTarget.getBoundingClientRect();
    const x = ((e.clientX - rect.left) / rect.width) * 100;
    const y = ((e.clientY - rect.top) / rect.height) * 100;
    e.currentTarget.style.setProperty('--mouse-x', `${x}%`);
    e.currentTarget.style.setProperty('--mouse-y', `${y}%`);
  }, []);

  useEffect(() => {
    if (countdown <= 0) return;
    const timer = setInterval(() => {
      setCountdown((prev) => (prev <= 1 ? 0 : prev - 1));
    }, 1000);
    return () => clearInterval(timer);
  }, [countdown]);

  const handleSendCode = useCallback(async () => {
    if (!isValidPhone(phone)) {
      message.warning('请输入正确的手机号');
      return;
    }
    setSending(true);
    try {
      await sendCode({ phone });
      message.success('验证码已发送');
      setCountdown(60);
    } catch (error) {
      if (isApiError(error)) {
        switch (error.code) {
          case 1007: message.error('发送过于频繁，请60秒后重试'); break;
          case 1008: message.error('今日发送次数已达上限'); break;
          case 1009: message.error('请求过于频繁，请稍后重试'); break;
          default: break;
        }
      }
    } finally {
      setSending(false);
    }
  }, [phone]);

  const handleLogin = useCallback(async () => {
    if (!isValidPhone(phone)) {
      message.warning('请输入正确的手机号');
      return;
    }
    if (loginType === 'code') {
      if (!code || code.length !== 6) {
        message.warning('请输入6位验证码');
        return;
      }
    } else {
      if (!password) {
        message.warning('请输入密码');
        return;
      }
    }

    setLoading(true);
    try {
      let response: LoginResponse;
      if (loginType === 'code') {
        response = await loginByCode({ phone, code });
      } else {
        response = await loginByPassword({ phone, password });
      }
      message.success('登录成功');
      login(response);
    } catch (error) {
      if (isApiError(error) && error.code === 1018) {
        Modal.confirm({
          title: '用户不存在',
          content: '该手机号未注册，是否前往注册？',
          onOk: () => navigate('/register'),
        });
      }
    } finally {
      setLoading(false);
    }
  }, [phone, code, password, loginType, login, navigate]);

  return (
    <div className={`c-mode ${styles.wrapper}`}>
      {/* Left: Brand Panel */}
      <div className={styles.brandPanel}>
        <div className={styles.brandPhoto} />
        <div className={styles.brandOverlay} />
        <div className={styles.brandGrain} />

        <div className={styles.particles}>
          <div className={styles.particle} />
          <div className={styles.particle} />
          <div className={styles.particle} />
          <div className={styles.particle} />
          <div className={styles.particle} />
        </div>

        <div className={`${styles.brandShape} ${styles.shape1}`} />
        <div className={`${styles.brandShape} ${styles.shape2}`} />
        <div className={`${styles.brandShape} ${styles.shape3}`} />
        <div className={styles.panelEdge} />

        <div className={styles.brandTop}>
          <div className={styles.brandIcon}>灵</div>
          <span className={styles.brandName}>灵犀互聘</span>
        </div>

        <div className={styles.brandContent}>
          <h1 className={styles.brandHeadline}>
            让求职招聘<br />更简单高效
          </h1>
          <p className={styles.brandDesc}>
            AI 驱动的智能招聘平台，精准匹配人才与岗位，
            为求职者和企业搭建高效沟通桥梁。
          </p>

          <div className={styles.brandFeatures}>
            <div className={styles.brandFeature}>
              <span className={styles.featureDot} />
              <span>AI 智能岗位推荐，精准匹配你的技能</span>
            </div>
            <div className={styles.brandFeature}>
              <span className={styles.featureDot} />
              <span>实时消息沟通，与 HR 高效对接</span>
            </div>
            <div className={styles.brandFeature}>
              <span className={styles.featureDot} />
              <span>简历智能诊断，提升求职竞争力</span>
            </div>
          </div>
        </div>

        <div className={styles.brandStats}>
          <div className={styles.brandStat}>
            <span className={styles.statNumber}>{jobCount}+</span>
            <span className={styles.statLabel}>在招岗位</span>
          </div>
          <div className={styles.brandStat}>
            <span className={styles.statNumber}>{companyCount}+</span>
            <span className={styles.statLabel}>合作企业</span>
          </div>
          <div className={styles.brandStat}>
            <span className={styles.statNumber}>{satisfaction}%</span>
            <span className={styles.statLabel}>用户满意度</span>
          </div>
        </div>
      </div>

      {/* Right: Form Panel */}
      <div
        ref={formPanelRef}
        className={styles.formPanel}
        onMouseMove={handleMouseMove}
      >
        {/* Aurora orbs */}
        <div className={`${styles.auroraOrb} ${styles.auroraOrb1}`} />
        <div className={`${styles.auroraOrb} ${styles.auroraOrb2}`} />

        {/* Mouse spotlight */}
        <div className={styles.mouseSpotlight} />
        <div className={styles.formPanelBg} />

        {/* Accent bar */}
        <div className={styles.formAccentBar} />

        <div className={styles.panelEdge} />

        <div className={styles.formWrapper}>
          <div className={styles.formBrandMobile}>
            <div className={styles.formBrandIcon}>灵</div>
            <span style={{ fontFamily: 'var(--font-title)', fontWeight: 700, fontSize: 18, color: 'var(--text)' }}>
              灵犀互聘
            </span>
          </div>

          <div className={styles.formHeader}>
            <h1 className={styles.formTitle}>欢迎回来</h1>
            <p className={styles.formSubtitle}>登录灵犀互聘，发现理想工作</p>
          </div>

          <div className={styles.form}>
            <Tabs
              activeKey={loginType}
              onChange={(key) => setLoginType(key as 'code' | 'password')}
              centered
              items={[
                { key: 'code', label: '验证码登录' },
                { key: 'password', label: '密码登录' },
              ]}
            />

            <div className={styles.field}>
              <Input
                size="large"
                placeholder="请输入手机号"
                prefix={<PhoneOutlined style={{ color: 'var(--text-tertiary)' }} />}
                value={phone}
                onChange={(e) => setPhone(e.target.value)}
                maxLength={11}
                className={styles.input}
              />
            </div>

            {loginType === 'code' ? (
              <div className={styles.field}>
                <Input
                  size="large"
                  placeholder="请输入验证码"
                  value={code}
                  onChange={(e) => setCode(e.target.value)}
                  maxLength={6}
                  className={styles.input}
                  suffix={
                    <button
                      className={styles.codeBtn}
                      disabled={countdown > 0 || sending}
                      onClick={handleSendCode}
                    >
                      {sending ? '发送中...' : countdown > 0 ? `${countdown}s` : '获取验证码'}
                    </button>
                  }
                />
              </div>
            ) : (
              <div className={styles.field}>
                <Input.Password
                  size="large"
                  placeholder="请输入密码"
                  prefix={<LockOutlined style={{ color: 'var(--text-tertiary)' }} />}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  className={styles.input}
                />
              </div>
            )}

            <button
              className={styles.loginBtn}
              disabled={loading}
              onClick={handleLogin}
            >
              {loading ? '登录中...' : '登录'}
            </button>
          </div>

          <div className={styles.footer}>
            <Text className={styles.footerLink} onClick={() => navigate('/register')}>
              还没有账号？立即注册
            </Text>
            <span className={styles.footerDivider} />
            <Text className={styles.footerLink} onClick={() => navigate('/login/admin')}>
              管理员登录
            </Text>
          </div>
        </div>
      </div>
    </div>
  );
};

export default LoginPage;
