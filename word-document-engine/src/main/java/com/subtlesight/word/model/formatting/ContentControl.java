package com.subtlesight.word.model.formatting;

import java.util.*;

/**
 * 内容控制/结构化文档标签 (SDT - Structured Document Tag)。
 * <p>
 * 支持：
 * <ul>
 *   <li>纯文本、富文本、下拉列表、日期选择器、图片、复选框、组合框</li>
 *   <li>占位符文本</li>
 *   <li>锁定（无法编辑/删除）</li>
 *   <li>标签/别名</li>
 *   <li>数据绑定</li>
 * </ul>
 * </p>
 */
public class ContentControl {

    private String id;
    private String tag;
    private String alias;
    private SdtType type;
    private boolean lockContent;
    private boolean lockDeletion;

    // ======== 占位符 ========
    private String placeholderText;

    // ======== 数据绑定 ========
    private String dataBindingXpath;
    private String dataBindingStoreItemId;
    private String dataBindingPrefixMappings;

    // ======== 下拉列表/组合框选项 ========
    private List<ListItem> listItems;

    // ======== 日期格式 ========
    private String dateFormat;
    private String dateLocaleId;
    private String calendarType;

    // ======== 复选框状态 ========
    private Boolean checked;

    // ======== 图片控件 ========
    private String imageDataId;

    // ======== 额外属性 ========
    private Map<String, Object> additionalProperties;

    public ContentControl() {
        this.listItems = new ArrayList<>();
        this.additionalProperties = new LinkedHashMap<>();
    }

    // Getters & Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTag() { return tag; }
    public void setTag(String tag) { this.tag = tag; }
    public String getAlias() { return alias; }
    public void setAlias(String alias) { this.alias = alias; }
    public SdtType getType() { return type; }
    public void setType(SdtType type) { this.type = type; }
    public boolean isLockContent() { return lockContent; }
    public void setLockContent(boolean lockContent) { this.lockContent = lockContent; }
    public boolean isLockDeletion() { return lockDeletion; }
    public void setLockDeletion(boolean lockDeletion) { this.lockDeletion = lockDeletion; }
    public String getPlaceholderText() { return placeholderText; }
    public void setPlaceholderText(String placeholderText) { this.placeholderText = placeholderText; }
    public String getDataBindingXpath() { return dataBindingXpath; }
    public void setDataBindingXpath(String dataBindingXpath) { this.dataBindingXpath = dataBindingXpath; }
    public String getDataBindingStoreItemId() { return dataBindingStoreItemId; }
    public void setDataBindingStoreItemId(String dataBindingStoreItemId) { this.dataBindingStoreItemId = dataBindingStoreItemId; }
    public String getDataBindingPrefixMappings() { return dataBindingPrefixMappings; }
    public void setDataBindingPrefixMappings(String dataBindingPrefixMappings) { this.dataBindingPrefixMappings = dataBindingPrefixMappings; }
    public List<ListItem> getListItems() { return listItems; }
    public void setListItems(List<ListItem> listItems) { this.listItems = listItems; }
    public String getDateFormat() { return dateFormat; }
    public void setDateFormat(String dateFormat) { this.dateFormat = dateFormat; }
    public String getDateLocaleId() { return dateLocaleId; }
    public void setDateLocaleId(String dateLocaleId) { this.dateLocaleId = dateLocaleId; }
    public String getCalendarType() { return calendarType; }
    public void setCalendarType(String calendarType) { this.calendarType = calendarType; }
    public Boolean getChecked() { return checked; }
    public void setChecked(Boolean checked) { this.checked = checked; }
    public String getImageDataId() { return imageDataId; }
    public void setImageDataId(String imageDataId) { this.imageDataId = imageDataId; }
    public Map<String, Object> getAdditionalProperties() { return additionalProperties; }
    public void setAdditionalProperties(Map<String, Object> additionalProperties) { this.additionalProperties = additionalProperties; }

    public enum SdtType {
        PLAIN_TEXT,
        RICH_TEXT,
        DROP_DOWN_LIST,
        COMBO_BOX,
        DATE_PICKER,
        CHECKBOX,
        PICTURE,
        REPEATING_SECTION
    }

    public static class ListItem {
        private String displayText;
        private String value;

        public String getDisplayText() { return displayText; }
        public void setDisplayText(String displayText) { this.displayText = displayText; }
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
    }
}