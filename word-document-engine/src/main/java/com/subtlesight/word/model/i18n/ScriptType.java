package com.subtlesight.word.model.i18n;

/**
 * Word 文档中使用的脚本类型，对应 OOXML 中每个 run/paragraph 的字体槽。
 * <p>
 * 每个文字槽可以独立指定字体、字号、语言标签。
 * </p>
 */
public enum ScriptType {
    /** 拉丁文字（西文） - rFonts/@ascii / @hAnsi */
    LATIN,
    /** 东亚文字（中日韩） - rFonts/@eastAsia */
    EAST_ASIA,
    /** 复杂文字（阿拉伯语、印地语、泰语等） - rFonts/@cs */
    COMPLEX_SCRIPT,
    /** 全部（快捷设置，同时作用于所有槽） */
    ALL
}