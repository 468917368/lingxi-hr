package com.lingxi.user.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingxi.common.domain.Result;
import com.lingxi.user.feign.HrCompanyFeignClient;
import com.lingxi.user.feign.JobFeignClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 获取企业岗位工具
 *
 * @author 成员A
 * @since 2026-08-05
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GetCompanyJobsTool {

    private final JobFeignClient jobFeignClient;
    private final HrCompanyFeignClient hrCompanyFeignClient;
    private final ObjectMapper objectMapper;

    public String execute(Map<String, Object> params) {
        try {
            Long companyId = resolveCompanyId(params);
            if (companyId == null) {
                return errorJson("未找到对应企业");
            }

            log.info("获取企业岗位: companyId={}", companyId);

            Result<List<Map<String, Object>>> result = jobFeignClient.listCompanyJobs(companyId);
            List<Map<String, Object>> jobs = result.getData();

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("total", jobs != null ? jobs.size() : 0);
            response.put("list", jobs != null ? jobs : Collections.emptyList());
            response.put("companyId", companyId);

            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            log.error("获取企业岗位失败", e);
            return errorJson("查询失败: " + e.getMessage());
        }
    }

    /**
     * 解析企业ID：优先用 companyId，没有则用 company 名称查
     */
    private Long resolveCompanyId(Map<String, Object> params) {
        // 1. 直接传 companyId
        Object companyIdObj = params.get("companyId");
        if (companyIdObj instanceof Number) {
            return ((Number) companyIdObj).longValue();
        }
        if (companyIdObj instanceof String) {
            try {
                return Long.parseLong((String) companyIdObj);
            } catch (NumberFormatException ignored) {
            }
        }

        // 2. 传 company 名称，通过 Feign 查 ID
        Object companyObj = params.get("company");
        if (companyObj instanceof String) {
            String companyName = ((String) companyObj).trim();
            if (!companyName.isEmpty()) {
                try {
                    Long id = hrCompanyFeignClient.getIdByName(companyName).getData();
                    if (id != null) {
                        return id;
                    }
                    log.warn("按名称查找企业失败: company={}", companyName);
                } catch (Exception e) {
                    log.warn("Feign 查找企业失败: company={}", companyName, e);
                }
            }
        }

        return null;
    }

    private String errorJson(String message) {
        try {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("error", message);
            error.put("total", 0);
            error.put("list", Collections.emptyList());
            return objectMapper.writeValueAsString(error);
        } catch (Exception e) {
            return "{\"error\":\"" + message + "\",\"total\":0,\"list\":[]}";
        }
    }
}
