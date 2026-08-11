package com.subtlesight.word.model.enums;

/**
 * 变更集支持的原子操作类型。
 */
public enum ChangeOperation {
    REPLACE_TEXT,
    INSERT_PARAGRAPH,
    INSERT_HEADING,
    INSERT_LIST,
    DELETE_NODE,
    MOVE_NODE,
    UPDATE_FORMAT,
    TABLE_INSERT_ROW,
    TABLE_DELETE_ROW,
    TABLE_UPDATE_CELL,
    INSERT_IMAGE,
    INSERT_FOOTNOTE,
    REPLACE_NODE
}