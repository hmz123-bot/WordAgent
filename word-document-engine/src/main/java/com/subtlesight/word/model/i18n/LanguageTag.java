package com.subtlesight.word.model.i18n;

import java.util.Locale;
import java.util.Objects;

/**
 * BCP-47 语言标签，支持每个脚本槽独立指定。
 * <p>
 * 对应 OOXML 中的 {@code <w:lang>} 属性：
 * <ul>
 *   <li>{@code w:val} - 默认语言（Latin）</li>
 *   <li>{@code w:eastAsia} - 东亚语言</li>
 *   <li>{@code w:bidi} - 复杂文字语言（如阿拉伯语、希伯来语）</li>
 * </ul>
 * </p>
 */
public class LanguageTag {

    private final String tag;
    private final ScriptType scriptType;

    public LanguageTag(String tag, ScriptType scriptType) {
        this.tag = tag != null ? tag : "en-US";
        this.scriptType = scriptType != null ? scriptType : ScriptType.LATIN;
    }

    public LanguageTag(String tag) {
        this(tag, ScriptType.LATIN);
    }

    public String getTag() {
        return tag;
    }

    public ScriptType getScriptType() {
        return scriptType;
    }

    public Locale toLocale() {
        String[] parts = tag.split("[-_]");
        if (parts.length >= 2) {
            return new Locale(parts[0], parts[1]);
        }
        return new Locale(parts[0]);
    }

    public String getLanguage() {
        String[] parts = tag.split("[-_]");
        return parts[0];
    }

    public String getRegion() {
        String[] parts = tag.split("[-_]");
        return parts.length >= 2 ? parts[1] : "";
    }

    /**
     * 判断是否为 RTL 语言。
     */
    public boolean isRtl() {
        String lang = getLanguage().toLowerCase();
        return "ar".equals(lang) || "he".equals(lang) || "fa".equals(lang)
                || "ur".equals(lang) || "yi".equals(lang) || "dv".equals(lang)
                || "ps".equals(lang) || "sd".equals(lang) || "ku".equals(lang)
                || "ckb".equals(lang);
    }

    /**
     * 判断是否为复杂文字语言（Indic、Thai、Arabic 等）。
     */
    public boolean isComplexScript() {
        String lang = getLanguage().toLowerCase();
        return isRtl() || "hi".equals(lang) || "bn".equals(lang) || "ta".equals(lang)
                || "te".equals(lang) || "mr".equals(lang) || "gu".equals(lang)
                || "kn".equals(lang) || "ml".equals(lang) || "pa".equals(lang)
                || "th".equals(lang) || "lo".equals(lang) || "km".equals(lang)
                || "si".equals(lang) || "my".equals(lang) || "am".equals(lang);
    }

    /**
     * 判断是否为 CJK 语言。
     */
    public boolean isCjk() {
        String lang = getLanguage().toLowerCase();
        return "zh".equals(lang) || "ja".equals(lang) || "ko".equals(lang);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LanguageTag that = (LanguageTag) o;
        return Objects.equals(tag, that.tag) && scriptType == that.scriptType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(tag, scriptType);
    }

    @Override
    public String toString() {
        return tag + "[" + scriptType + "]";
    }

    // ======== 常用预设 ========

    public static LanguageTag EN_US = new LanguageTag("en-US", ScriptType.LATIN);
    public static LanguageTag AR_SA = new LanguageTag("ar-SA", ScriptType.COMPLEX_SCRIPT);
    public static LanguageTag HI_IN = new LanguageTag("hi-IN", ScriptType.COMPLEX_SCRIPT);
    public static LanguageTag TH_TH = new LanguageTag("th-TH", ScriptType.COMPLEX_SCRIPT);
    public static LanguageTag ZH_CN = new LanguageTag("zh-CN", ScriptType.EAST_ASIA);
    public static LanguageTag ZH_TW = new LanguageTag("zh-TW", ScriptType.EAST_ASIA);
    public static LanguageTag JA_JP = new LanguageTag("ja-JP", ScriptType.EAST_ASIA);
    public static LanguageTag KO_KR = new LanguageTag("ko-KR", ScriptType.EAST_ASIA);
    public static LanguageTag HE_IL = new LanguageTag("he-IL", ScriptType.COMPLEX_SCRIPT);
    public static LanguageTag FA_IR = new LanguageTag("fa-IR", ScriptType.COMPLEX_SCRIPT);
}