package com.lingxi.hr.domain.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * 企业认证申请 DTO
 *
 * @author 成员D
 * @since 2026-08-02
 */
@Data
public class HrCompanyCertificationDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 营业执照文件 */
    @NotNull(message = "营业执照不能为空")
    private Object businessLicenseFile;

    /** 其他证明材料文件 */
    private Object certMaterialFile;
}
