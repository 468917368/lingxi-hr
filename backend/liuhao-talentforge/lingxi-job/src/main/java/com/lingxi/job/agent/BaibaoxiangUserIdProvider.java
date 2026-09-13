package com.lingxi.job.agent;

/**
 * 百宝箱 userId 伪标识提供者（阶段4 真实协议修订）
 * <p>
 * 对外出站禁止使用内部用户 ID/手机号/账号名，统一由本接口生成伪标识：
 * <ul>
 *   <li>{@code ai.agent.mock=true}（默认）→ {@code MockBaibaoxiangUserIdProvider}：固定本地伪标识，不读取真实密钥</li>
 *   <li>{@code ai.agent.mock=false} → {@code RealBaibaoxiangUserIdProvider}：服务端 HMAC-SHA256(内部用户ID + ":" + appId)</li>
 * </ul>
 * 同一内部用户 + 同一 AppID 下结果稳定，不同 AppID 之间隔离（不同应用不可串用）。
 * </p>
 * <p>
 * 调用时机：必须在 HTTP 请求线程内生成后<b>显式传入</b>客户端；异步 SSE 线程不得读取
 * {@code UserContext}，客户端不接收原始内部用户 ID。
 * </p>
 *
 * @author 成员B
 * @since 2026-08-04
 */
public interface BaibaoxiangUserIdProvider {

    /**
     * 生成百宝箱 userId 伪标识
     *
     * @param internalUserId 内部用户 ID（请求线程捕获，不回传百宝箱）
     * @param appId          目标百宝箱应用 ID（按 AppID 隔离）
     * @return 伪标识（HMAC hex 或本地 mock 值）
     */
    String provide(Long internalUserId, String appId);
}
