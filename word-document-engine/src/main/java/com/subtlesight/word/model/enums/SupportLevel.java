package com.subtlesight.word.model.enums;

/**
 * Word 元素支持级别，用于导入/导出报告。
 */
public enum SupportLevel {
    /** 可直接编辑 */
    EDITABLE,
    /** 只读保留，不被修改 */
    READ_ONLY,
    /** 降级表示 */
    DEGRADED,
    /** 不支持 */
    UNSUPPORTED
}