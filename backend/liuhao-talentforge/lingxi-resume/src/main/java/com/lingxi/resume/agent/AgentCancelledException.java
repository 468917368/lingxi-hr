package com.lingxi.resume.agent;

/**
 * Agent 执行被取消异常
 *
 * <p>客户端断开连接等取消场景抛出；编排层捕获后仅清理订阅，
 * 不将简历置为 FAILED（解析本身可能成功，只是无人等待）。
 *
 * @author 成员C
 * @since 2026-08-03
 */
public class AgentCancelledException extends RuntimeException {

    public AgentCancelledException(String message) {
        super(message);
    }
}
