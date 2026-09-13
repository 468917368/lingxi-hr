package com.lingxi.common.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * 阿里云短信发送工具类（使用号码认证服务）
 *
 * @author lingxi-team
 * @since 2026-08-10
 */
@Slf4j
@Component
public class AliyunSmsHelper {

    @Value("${aliyun.sms.access-key-id:}")
    private String accessKeyId;

    @Value("${aliyun.sms.access-key-secret:}")
    private String accessKeySecret;

    private Object client;
    private Method sendMethod;

    /**
     * 使用系统类加载器加载类
     */
    private static Class<?> loadClass(String className) throws ClassNotFoundException {
        return ClassLoader.getSystemClassLoader().loadClass(className);
    }

    /**
     * 发送验证码短信
     *
     * @param signName      签名
     * @param templateCode  模板编码
     * @param phone         手机号
     * @param templateParam 模板参数 JSON
     * @return 是否发送成功
     */
    public boolean sendSmsVerifyCode(String signName, String templateCode, String phone, String templateParam) {
        try {
            Object client = getClient();
            if (client == null) {
                log.error("阿里云短信客户端初始化失败，请检查accessKeyId/accessKeySecret配置");
                return false;
            }

            // 构建请求
            Class<?> requestClass = loadClass("com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeRequest");
            Object request = requestClass.newInstance();

            requestClass.getMethod("setSignName", String.class).invoke(request, signName);
            requestClass.getMethod("setTemplateCode", String.class).invoke(request, templateCode);
            requestClass.getMethod("setPhoneNumber", String.class).invoke(request, phone);
            requestClass.getMethod("setTemplateParam", String.class).invoke(request, templateParam);

            // 发送
            Class<?> runtimeOptionsClass = loadClass("com.aliyun.teautil.models.RuntimeOptions");
            Object response = sendMethod.invoke(client, request, runtimeOptionsClass.newInstance());

            // 解析响应
            Object body = response.getClass().getMethod("getBody").invoke(response);
            String code = (String) body.getClass().getMethod("getCode").invoke(body);
            String message = (String) body.getClass().getMethod("getMessage").invoke(body);

            log.info("短信发送结果: phone={}, code={}, message={}", maskPhone(phone), code, message);
            return "OK".equals(code);
        } catch (Exception e) {
            log.error("短信发送失败: phone={}, error={}", maskPhone(phone), e.getMessage(), e);
            return false;
        }
    }

    /**
     * 通过反射创建Client（懒加载）
     */
    private synchronized Object getClient() {
        if (client != null) {
            return client;
        }
        if (accessKeyId == null || accessKeyId.isEmpty()) {
            return null;
        }
        try {
            // Config config = new Config().setAccessKeyId(...).setAccessKeySecret(...)
            Class<?> configClass = loadClass("com.aliyun.teaopenapi.models.Config");
            Object config = configClass.newInstance();

            configClass.getMethod("setAccessKeyId", String.class).invoke(config, accessKeyId);
            configClass.getMethod("setAccessKeySecret", String.class).invoke(config, accessKeySecret);
            configClass.getField("endpoint").set(config, "dypnsapi.aliyuncs.com");

            // Client client = new Client(config)
            Class<?> clientClass = loadClass("com.aliyun.dypnsapi20170525.Client");
            client = clientClass.getConstructor(configClass).newInstance(config);

            // 获取发送方法
            sendMethod = clientClass.getMethod("sendSmsVerifyCodeWithOptions",
                    loadClass("com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeRequest"),
                    loadClass("com.aliyun.teautil.models.RuntimeOptions"));

            log.info("阿里云短信客户端初始化成功");
            return client;
        } catch (Exception e) {
            log.error("阿里云短信客户端初始化失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 手机号脱敏
     */
    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
