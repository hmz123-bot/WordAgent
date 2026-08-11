import React from 'react';
import { EditTwo, Left, Right, Check, Close, DoubleRight, CloseOne } from '@icon-park/react';

/**
 * Accept/Reject Bar — AI 修改后的接受/拒绝控制栏。
 *
 * 显示在文档顶部，包含：
 * - 待处理的修改数量
 * - "全部接受" / "全部拒绝" 按钮
 * - 逐条接受/拒绝的导航
 */

interface AcceptRejectBarProps {
  visible: boolean;
  pendingCount: number;
  totalCount: number;
  currentIndex: number;
  onAccept: () => void;
  onReject: () => void;
  onAcceptAll: () => void;
  onRejectAll: () => void;
  onPrev: () => void;
  onNext: () => void;
}

const AcceptRejectBar: React.FC<AcceptRejectBarProps> = ({
  visible,
  pendingCount,
  totalCount,
  currentIndex,
  onAccept,
  onReject,
  onAcceptAll,
  onRejectAll,
  onPrev,
  onNext,
}) => {
  if (!visible) return null;

  return (
    <div className="accept-reject-bar">
      <div className="ar-info">
        <span className="ar-icon"><EditTwo theme="outline" size="16" /></span>
        <span className="ar-text">
          {pendingCount > 0
            ? `AI 已提出 ${totalCount} 项修改建议（${pendingCount} 项待处理）`
            : '所有修改建议已处理'}
        </span>
      </div>

      <div className="ar-controls">
        {totalCount > 1 && (
          <div className="ar-nav">
            <button className="ar-nav-btn" onClick={onPrev} disabled={currentIndex <= 0}>
              <Left theme="outline" size="14" />上一条
            </button>
            <span className="ar-nav-info">
              {currentIndex + 1} / {totalCount}
            </span>
            <button className="ar-nav-btn" onClick={onNext} disabled={currentIndex >= totalCount - 1}>
              下一条<Right theme="outline" size="14" />
            </button>
          </div>
        )}

        <div className="ar-actions">
          <button className="ar-accept-btn" onClick={onAccept} title="接受当前修改">
            <Check theme="outline" size="14" />接受
          </button>
          <button className="ar-reject-btn" onClick={onReject} title="拒绝当前修改">
            <Close theme="outline" size="14" />拒绝
          </button>
        </div>

        <div className="ar-bulk">
          <button className="ar-accept-all-btn" onClick={onAcceptAll} title="接受所有修改">
            <DoubleRight theme="outline" size="14" />全部接受
          </button>
          <button className="ar-reject-all-btn" onClick={onRejectAll} title="拒绝所有修改，回到修改前">
            <CloseOne theme="outline" size="14" />全部拒绝
          </button>
        </div>
      </div>
    </div>
  );
};

export default AcceptRejectBar;
