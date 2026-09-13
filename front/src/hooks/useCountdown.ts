import { useState, useEffect, useCallback } from 'react';

/**
 * 倒计时 Hook
 * @param initialCount 初始倒计时秒数
 * @returns [countdown, startCountdown, resetCountdown]
 */
export function useCountdown(initialCount = 60) {
  const [countdown, setCountdown] = useState(0);

  useEffect(() => {
    if (countdown <= 0) return;
    const timer = setInterval(() => {
      setCountdown((prev) => (prev <= 1 ? 0 : prev - 1));
    }, 1000);
    return () => clearInterval(timer);
  }, [countdown]);

  const startCountdown = useCallback(() => {
    setCountdown(initialCount);
  }, [initialCount]);

  const resetCountdown = useCallback(() => {
    setCountdown(0);
  }, []);

  return { countdown, startCountdown, resetCountdown };
}
