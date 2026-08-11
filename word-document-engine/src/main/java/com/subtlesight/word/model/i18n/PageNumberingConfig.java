package com.subtlesight.word.model.i18n;

/**
 * 区域感知的页码配置。
 * <p>
 * 支持印地语、阿拉伯语、泰语、中日韩文等不同语言区域
 * 的不同页码数字格式。
 * </p>
 */
public class PageNumberingConfig {

    private PageNumberFormat format;
    private String chapterStyle;
    private String chapterSeparator;
    private LanguageTag locale;
    private int startAt;

    public PageNumberingConfig() {
        this.format = PageNumberFormat.DECIMAL;
    }

    public PageNumberFormat getFormat() { return format; }
    public void setFormat(PageNumberFormat format) { this.format = format; }

    public String getChapterStyle() { return chapterStyle; }
    public void setChapterStyle(String chapterStyle) { this.chapterStyle = chapterStyle; }

    public String getChapterSeparator() { return chapterSeparator; }
    public void setChapterSeparator(String chapterSeparator) { this.chapterSeparator = chapterSeparator; }

    public LanguageTag getLocale() { return locale; }
    public void setLocale(LanguageTag locale) { this.locale = locale; }

    public int getStartAt() { return startAt; }
    public void setStartAt(int startAt) { this.startAt = startAt; }

    /**
     * 根据区域获取合适的页码格式。
     */
    public static PageNumberFormat getDefaultForLocale(LanguageTag locale) {
        if (locale == null) return PageNumberFormat.DECIMAL;
        String lang = locale.getLanguage();
        switch (lang) {
            case "ar": return PageNumberFormat.ARABIC_INDIC;
            case "hi": return PageNumberFormat.DEVANAGARI;
            case "th": return PageNumberFormat.THAI;
            case "zh": case "ja": case "ko": return PageNumberFormat.DECIMAL;
            default: return PageNumberFormat.DECIMAL;
        }
    }

    public enum PageNumberFormat {
        /** 标准十进制（1, 2, 3...） */
        DECIMAL("decimal"),
        /** 阿拉伯数字（1, 2, 3...） */
        UPPER_ROMAN("upperRoman"),
        /** 罗马数字小写（i, ii, iii...） */
        LOWER_ROMAN("lowerRoman"),
        /** 大写字母（A, B, C...） */
        UPPER_LETTER("upperLetter"),
        /** 小写字母（a, b, c...） */
        LOWER_LETTER("lowerLetter"),
        /** 阿拉伯-印度数字（١, ٢, ٣...） */
        ARABIC_INDIC("arabicIndic"),
        /** 天城文数字（१, २, ३...） */
        DEVANAGARI("devanagari"),
        /** 泰语数字（๑, ๒, ๓...） */
        THAI("thai"),
        /** 中文字符（一、二、三...） */
        CHINESE("chinese"),
        /** 序数 */
        ORDINAL("ordinal"),
        /** 十六进制 */
        HEX("hex");

        private final String ooxmlValue;

        PageNumberFormat(String ooxmlValue) {
            this.ooxmlValue = ooxmlValue;
        }

        public String getOoxmlValue() {
            return ooxmlValue;
        }

        public static PageNumberFormat fromOoxml(String value) {
            if (value == null) return DECIMAL;
            for (PageNumberFormat f : values()) {
                if (f.ooxmlValue.equals(value)) return f;
            }
            return DECIMAL;
        }
    }
}