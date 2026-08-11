package com.subtlesight.word.model.formatting;

import java.time.Instant;
import java.util.*;

/**
 * 修订/跟踪变更模型。
 * <p>
 * 支持 OOXML 中的修订类型：
 * <ul>
 *   <li>ins（插入）</li>
 *   <li>del（删除）</li>
 *   <li>format（格式变更）</li>
 *   <li>moveFrom（移动来源）</li>
 *   <li>moveTo（移动目标）</li>
 * </ul>
 * 支持按作者筛选和接受/拒绝操作。
 * </p>
 */
public class RevisionModel {

    private String revisionId;
    private RevisionType type;
    private String author;
    private Instant date;
    private String annotationId;
    private String comment;

    // ======== 修订内容 ========
    private String originalText;       // 原始文本（del 类型）
    private String newText;            // 新文本（ins 类型）
    private Map<String, Object> oldFormat;   // 旧格式（format 类型）
    private Map<String, Object> newFormat;   // 新格式（format 类型）

    // ======== 移动修订 ========
    private String moveTargetId;       // moveFrom → moveTo 的目标 ID
    private String moveSourceId;       // moveTo → moveFrom 的源 ID

    // ======== 额外属性 ========
    private Map<String, Object> additionalProperties;

    public RevisionModel() {
        this.additionalProperties = new LinkedHashMap<>();
    }

    // Getters & Setters
    public String getRevisionId() { return revisionId; }
    public void setRevisionId(String revisionId) { this.revisionId = revisionId; }
    public RevisionType getType() { return type; }
    public void setType(RevisionType type) { this.type = type; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    public Instant getDate() { return date; }
    public void setDate(Instant date) { this.date = date; }
    public String getAnnotationId() { return annotationId; }
    public void setAnnotationId(String annotationId) { this.annotationId = annotationId; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public String getOriginalText() { return originalText; }
    public void setOriginalText(String originalText) { this.originalText = originalText; }
    public String getNewText() { return newText; }
    public void setNewText(String newText) { this.newText = newText; }
    public Map<String, Object> getOldFormat() { return oldFormat; }
    public void setOldFormat(Map<String, Object> oldFormat) { this.oldFormat = oldFormat; }
    public Map<String, Object> getNewFormat() { return newFormat; }
    public void setNewFormat(Map<String, Object> newFormat) { this.newFormat = newFormat; }
    public String getMoveTargetId() { return moveTargetId; }
    public void setMoveTargetId(String moveTargetId) { this.moveTargetId = moveTargetId; }
    public String getMoveSourceId() { return moveSourceId; }
    public void setMoveSourceId(String moveSourceId) { this.moveSourceId = moveSourceId; }
    public Map<String, Object> getAdditionalProperties() { return additionalProperties; }
    public void setAdditionalProperties(Map<String, Object> additionalProperties) { this.additionalProperties = additionalProperties; }

    public enum RevisionType {
        INSERTION("ins"),
        DELETION("del"),
        FORMAT_CHANGE("format"),
        MOVE_FROM("moveFrom"),
        MOVE_TO("moveTo");

        private final String ooxmlTag;

        RevisionType(String ooxmlTag) { this.ooxmlTag = ooxmlTag; }
        public String getOoxmlTag() { return ooxmlTag; }

        public static RevisionType fromOoxml(String tag) {
            if (tag == null) return null;
            for (RevisionType t : values()) {
                if (t.ooxmlTag.equals(tag)) return t;
            }
            return null;
        }
    }

    /**
     * 修订操作动作。
     */
    public enum RevisionAction {
        ACCEPT,
        REJECT
    }

    /**
     * 修订筛选器，用于按条件查询修订。
     */
    public static class RevisionFilter {
        private RevisionType type;
        private RevisionAction action;
        private String author;
        private Instant since;
        private Instant until;
        private String nodeId;

        public RevisionType getType() { return type; }
        public void setType(RevisionType type) { this.type = type; }
        public RevisionAction getAction() { return action; }
        public void setAction(RevisionAction action) { this.action = action; }
        public String getAuthor() { return author; }
        public void setAuthor(String author) { this.author = author; }
        public Instant getSince() { return since; }
        public void setSince(Instant since) { this.since = since; }
        public Instant getUntil() { return until; }
        public void setUntil(Instant until) { this.until = until; }
        public String getNodeId() { return nodeId; }
        public void setNodeId(String nodeId) { this.nodeId = nodeId; }
    }

    /**
     * 跟踪查找与替换操作。
     */
    public static class TrackedFindReplace {
        private String findText;
        private String replaceText;
        private boolean matchCase;
        private boolean matchWholeWord;
        private boolean useWildcards;
        private boolean trackChanges;
        private String author;

        public String getFindText() { return findText; }
        public void setFindText(String findText) { this.findText = findText; }
        public String getReplaceText() { return replaceText; }
        public void setReplaceText(String replaceText) { this.replaceText = replaceText; }
        public boolean isMatchCase() { return matchCase; }
        public void setMatchCase(boolean matchCase) { this.matchCase = matchCase; }
        public boolean isMatchWholeWord() { return matchWholeWord; }
        public void setMatchWholeWord(boolean matchWholeWord) { this.matchWholeWord = matchWholeWord; }
        public boolean isUseWildcards() { return useWildcards; }
        public void setUseWildcards(boolean useWildcards) { this.useWildcards = useWildcards; }
        public boolean isTrackChanges() { return trackChanges; }
        public void setTrackChanges(boolean trackChanges) { this.trackChanges = trackChanges; }
        public String getAuthor() { return author; }
        public void setAuthor(String author) { this.author = author; }
    }
}