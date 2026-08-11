import React from 'react';

const DesignSystem: React.FC = () => {
  return (
    <div className="page-container">
      <div className="page-section" style={{ textAlign: 'center', maxWidth: 600, margin: '0 auto var(--space-2xl)' }}>
        <h2 className="page-section-title" style={{ fontSize: '2rem' }}>设计系统</h2>
        <p className="page-section-subtitle">
          Word Agent 设计系统的完整参考，包含色彩、字体、组件等规范
        </p>
      </div>

      {/* Color System */}
      <div className="page-section">
        <h3 className="page-section-title">色彩系统</h3>
        <p className="page-section-subtitle">温暖的中性色调，搭配克制的茶绿色强调色</p>
        <div className="color-grid" style={{ marginBottom: 'var(--space-lg)' }}>
          <div className="color-swatch">
            <div className="color-swatch-preview" style={{ background: '#F8F6F3' }} />
            <div className="color-swatch-info"><div className="color-swatch-name">背景色</div><div className="color-swatch-hex">#F8F6F3</div></div>
          </div>
          <div className="color-swatch">
            <div className="color-swatch-preview" style={{ background: '#F0EDE8' }} />
            <div className="color-swatch-info"><div className="color-swatch-name">背景交替色</div><div className="color-swatch-hex">#F0EDE8</div></div>
          </div>
          <div className="color-swatch">
            <div className="color-swatch-preview" style={{ background: '#FFFFFF', borderBottom: '1px solid #E5E1DB' }} />
            <div className="color-swatch-info"><div className="color-swatch-name">表面色</div><div className="color-swatch-hex">#FFFFFF</div></div>
          </div>
          <div className="color-swatch">
            <div className="color-swatch-preview" style={{ background: '#E5E1DB' }} />
            <div className="color-swatch-info"><div className="color-swatch-name">边框色</div><div className="color-swatch-hex">#E5E1DB</div></div>
          </div>
          <div className="color-swatch">
            <div className="color-swatch-preview" style={{ background: '#1A1D24' }} />
            <div className="color-swatch-info"><div className="color-swatch-name">文字主色</div><div className="color-swatch-hex">#1A1D24</div></div>
          </div>
          <div className="color-swatch">
            <div className="color-swatch-preview" style={{ background: '#7A7F8A' }} />
            <div className="color-swatch-info"><div className="color-swatch-name">文字次色</div><div className="color-swatch-hex">#7A7F8A</div></div>
          </div>
          <div className="color-swatch">
            <div className="color-swatch-preview" style={{ background: '#A0A5B0' }} />
            <div className="color-swatch-info"><div className="color-swatch-name">文字三级色</div><div className="color-swatch-hex">#A0A5B0</div></div>
          </div>
          <div className="color-swatch">
            <div className="color-swatch-preview" style={{ background: '#2D8C7F' }} />
            <div className="color-swatch-info"><div className="color-swatch-name">强调色</div><div className="color-swatch-hex">#2D8C7F</div></div>
          </div>
          <div className="color-swatch">
            <div className="color-swatch-preview" style={{ background: '#E8F3F1' }} />
            <div className="color-swatch-info"><div className="color-swatch-name">强调色浅</div><div className="color-swatch-hex">#E8F3F1</div></div>
          </div>
          <div className="color-swatch">
            <div className="color-swatch-preview" style={{ background: '#D95252' }} />
            <div className="color-swatch-info"><div className="color-swatch-name">危险色</div><div className="color-swatch-hex">#D95252</div></div>
          </div>
          <div className="color-swatch">
            <div className="color-swatch-preview" style={{ background: '#D9A840' }} />
            <div className="color-swatch-info"><div className="color-swatch-name">警告色</div><div className="color-swatch-hex">#D9A840</div></div>
          </div>
          <div className="color-swatch">
            <div className="color-swatch-preview" style={{ background: '#4A9E6E' }} />
            <div className="color-swatch-info"><div className="color-swatch-name">成功色</div><div className="color-swatch-hex">#4A9E6E</div></div>
          </div>
        </div>
      </div>

      {/* Typography */}
      <div className="page-section">
        <h3 className="page-section-title">字体系统</h3>
        <p className="page-section-subtitle">Instrument Serif (标题) + Instrument Sans (正文)</p>
        <div className="card">
          <div className="type-showcase">
            <div className="type-row">
              <span className="type-label">H1 / 40px</span>
              <span style={{ fontFamily: 'var(--font-heading)', fontSize: '2.5rem', fontWeight: 500, letterSpacing: '-0.02em' }}>克制即力量</span>
            </div>
            <div className="type-row">
              <span className="type-label">H2 / 32px</span>
              <span style={{ fontFamily: 'var(--font-heading)', fontSize: '2rem', fontWeight: 500, letterSpacing: '-0.01em' }}>设计系统概述</span>
            </div>
            <div className="type-row">
              <span className="type-label">H3 / 24px</span>
              <span style={{ fontFamily: 'var(--font-heading)', fontSize: '1.5rem', fontWeight: 500 }}>色彩系统</span>
            </div>
            <div className="type-row">
              <span className="type-label">H4 / 20px</span>
              <span style={{ fontFamily: 'var(--font-heading)', fontSize: '1.25rem', fontWeight: 500 }}>核心组件</span>
            </div>
            <div className="type-row">
              <span className="type-label">Body / 16px</span>
              <span style={{ fontFamily: 'var(--font-body)', fontSize: '1rem', color: 'var(--color-text-secondary)' }}>真正的力量来源于克制——简洁的界面、清晰的逻辑、优雅的交互。</span>
            </div>
            <div className="type-row">
              <span className="type-label">Small / 14px</span>
              <span style={{ fontFamily: 'var(--font-body)', fontSize: '0.875rem', color: 'var(--color-text-tertiary)' }}>辅助文字，用于注释说明和次要信息</span>
            </div>
            <div className="type-row" style={{ borderBottom: 'none' }}>
              <span className="type-label">Caption / 12px</span>
              <span style={{ fontFamily: 'var(--font-body)', fontSize: '0.75rem', color: 'var(--color-text-tertiary)' }}>标签和标注文字</span>
            </div>
          </div>
        </div>
      </div>

      {/* Spacing */}
      <div className="page-section">
        <h3 className="page-section-title">间距系统</h3>
        <p className="page-section-subtitle">基于 4px 的间距体系，确保视觉一致性</p>
        <div className="card">
          {[
            { name: '4px', var: '--space-xs', size: 4 },
            { name: '8px', var: '--space-sm', size: 8 },
            { name: '16px', var: '--space-md', size: 16 },
            { name: '24px', var: '--space-lg', size: 24 },
            { name: '32px', var: '--space-xl', size: 32 },
            { name: '48px', var: '--space-2xl', size: 48 },
            { name: '64px', var: '--space-3xl', size: 64 },
          ].map((s) => (
            <div key={s.var} className="flex items-center gap-md" style={{ marginBottom: 12 }}>
              <span style={{ width: 60, fontSize: '0.8125rem', color: 'var(--color-text-tertiary)', fontFamily: 'var(--font-mono)' }}>{s.name}</span>
              <span style={{ width: 100, fontSize: '0.75rem', color: 'var(--color-text-tertiary)' }}>{s.var}</span>
              <div style={{ height: 16, width: s.size, background: 'var(--color-accent)', borderRadius: 'var(--radius-sm)', opacity: 0.6 }} />
              <div style={{ height: 16, width: s.size, background: 'var(--color-accent)', borderRadius: 'var(--radius-sm)', opacity: 0.4 }} />
              <div style={{ height: 16, width: s.size, background: 'var(--color-accent)', borderRadius: 'var(--radius-sm)', opacity: 0.2 }} />
            </div>
          ))}
        </div>
      </div>

      {/* Buttons */}
      <div className="page-section">
        <h3 className="page-section-title">按钮</h3>
        <p className="page-section-subtitle">三种主要按钮样式，适用于不同场景</p>
        <div className="card">
          <div className="flex gap-md" style={{ marginBottom: 'var(--space-md)', flexWrap: 'wrap', alignItems: 'center' }}>
            <button className="btn btn-primary">主要按钮</button>
            <button className="btn btn-secondary">次要按钮</button>
            <button className="btn btn-ghost">幽灵按钮</button>
            <button className="btn btn-danger">危险按钮</button>
            <button className="btn btn-primary" disabled>禁用状态</button>
          </div>
          <div className="flex gap-md" style={{ flexWrap: 'wrap', alignItems: 'center' }}>
            <button className="btn btn-primary btn-lg">大按钮</button>
            <button className="btn btn-primary">默认</button>
            <button className="btn btn-primary btn-sm">小按钮</button>
            <button className="btn btn-icon btn-primary">
              <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round"><path d="M8 3v10M3 8h10" /></svg>
            </button>
          </div>
        </div>
      </div>

      {/* Tags */}
      <div className="page-section">
        <h3 className="page-section-title">标签</h3>
        <p className="page-section-subtitle">用于标记状态、分类和属性</p>
        <div className="card">
          <div className="flex gap-sm" style={{ flexWrap: 'wrap', alignItems: 'center' }}>
            <span className="tag tag-default">默认</span>
            <span className="tag tag-accent">强调</span>
            <span className="tag tag-success">成功</span>
            <span className="tag tag-warning">警告</span>
            <span className="tag tag-danger">危险</span>
            <span className="tag tag-default tag-sm">小标签</span>
          </div>
        </div>
      </div>

      {/* Cards */}
      <div className="page-section">
        <h3 className="page-section-title">卡片</h3>
        <p className="page-section-subtitle">内容容器，用于组织相关信息</p>
        <div className="card" style={{ marginBottom: 'var(--space-md)' }}>
          <div className="card-header">
            <span className="card-title">卡片标题</span>
            <button className="btn btn-ghost btn-sm">操作</button>
          </div>
          <p style={{ color: 'var(--color-text-secondary)', fontSize: '0.875rem' }}>卡片内容区域，用于展示相关信息和数据。</p>
        </div>
        <div className="stat-cards">
          <div className="stat-card"><div className="stat-value">128</div><div className="stat-label">文档总数</div></div>
          <div className="stat-card"><div className="stat-value">32</div><div className="stat-label">协作中</div></div>
          <div className="stat-card"><div className="stat-value">86</div><div className="stat-label">已发布</div></div>
        </div>
      </div>

      {/* Table Preview */}
      <div className="page-section">
        <h3 className="page-section-title">表格</h3>
        <p className="page-section-subtitle">用于展示结构化数据</p>
        <div className="table-wrapper">
          <table>
            <thead>
              <tr>
                <th>编号</th>
                <th>标题</th>
                <th>类型</th>
                <th>状态</th>
                <th>更新日期</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td><span className="table-cell-link">DOC-001</span></td>
                <td><span className="table-cell-link">年度技术总结报告</span></td>
                <td><span className="tag tag-default">报告</span></td>
                <td><span className="tag tag-success">已发布</span></td>
                <td style={{ color: 'var(--color-text-tertiary)', fontSize: '0.8125rem' }}>2024-12-20</td>
              </tr>
              <tr>
                <td><span className="table-cell-link">DOC-002</span></td>
                <td><span className="table-cell-link">产品需求文档 v3.2</span></td>
                <td><span className="tag tag-default">需求</span></td>
                <td><span className="tag tag-warning">审核中</span></td>
                <td style={{ color: 'var(--color-text-tertiary)', fontSize: '0.8125rem' }}>2024-12-19</td>
              </tr>
              <tr>
                <td><span className="table-cell-link">DOC-003</span></td>
                <td><span className="table-cell-link">用户手册-智能助手</span></td>
                <td><span className="tag tag-default">手册</span></td>
                <td><span className="tag tag-default">草稿</span></td>
                <td style={{ color: 'var(--color-text-tertiary)', fontSize: '0.8125rem' }}>2024-12-18</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};

export default DesignSystem;