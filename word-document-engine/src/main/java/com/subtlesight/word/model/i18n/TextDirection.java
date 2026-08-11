package com.subtlesight.word.model.i18n;

/**
 * Word 文档文本方向枚举。
 * <p>
 * 对应 OOXML 中的 {@code <w:textDirection>} 属性，
 * 以及 {@code <w:docGrid>} 的 {@code charSpace} 等。
 * </p>
 */
public enum TextDirection {
    /** 从左到右（默认） - lrtb */
    LEFT_TO_RIGHT("lrtb"),
    /** 从右到左 - rltb */
    RIGHT_TO_LEFT("rltb"),
    /** 从上到下，从左到右 - tbrl */
    TOP_TO_BOTTOM_LEFT_TO_RIGHT("tbrl"),
    /** 从上到下，从右到左 - tbrlv */
    TOP_TO_BOTTOM_RIGHT_TO_LEFT("tbrlv"),
    /** 垂直旋转 270 度 - vert270 */
    VERTICAL_270("vert270"),
    /** 东亚垂直文字 - eaVert */
    EAST_ASIAN_VERTICAL("eaVert"),
    /** 垂直 - vert */
    VERTICAL("vert");

    private final String ooxmlValue;

    TextDirection(String ooxmlValue) {
        this.ooxmlValue = ooxmlValue;
    }

    public String getOoxmlValue() {
        return ooxmlValue;
    }

    public static TextDirection fromOoxml(String value) {
        if (value == null) return LEFT_TO_RIGHT;
        for (TextDirection d : values()) {
            if (d.ooxmlValue.equals(value)) return d;
        }
        return LEFT_TO_RIGHT;
    }

    public boolean isRtl() {
        return this == RIGHT_TO_LEFT;
    }

    public boolean isVertical() {
        return this == VERTICAL || this == VERTICAL_270 || this == EAST_ASIAN_VERTICAL
                || this == TOP_TO_BOTTOM_LEFT_TO_RIGHT || this == TOP_TO_BOTTOM_RIGHT_TO_LEFT;
    }
}