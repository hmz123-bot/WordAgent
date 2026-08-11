import React from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Right,
  Calendar,
  DocDetail,
  Notes,
  Mail,
  BookOne,
  IdCard,
  Speaker,
  DocSearchTwo,
  Notebook,
  SpeakerOne,
  CodeBrackets,
  Communication,
} from '@icon-park/react';
import type { IconComp } from '../types/icon';

interface TemplateItem {
  t: string;
  a: string;
  tag: string;
  c: [string, string];
  Icon: IconComp;
}

const templates: TemplateItem[] = [
  { t: '自动周报生成器', a: 'Aurora', tag: '模板', c: ['#3a1c0a', '#a8551b'], Icon: Calendar },
  { t: '产品需求文档 PRD 模板', a: 'Nova', tag: '模板', c: ['#4a1530', '#ff5e8a'], Icon: DocDetail },
  { t: '会议纪要智能整理', a: 'Pixel', tag: '模板', c: ['#5a2a0e', '#ff8a3c'], Icon: Notes },
  { t: '商务邮件撰写助手', a: 'Kira', tag: '模板', c: ['#3a2410', '#d98a2b'], Icon: Mail },
  { t: '学术论文草稿框架', a: 'Lune', tag: '模板', c: ['#4a1a12', '#ff7043'], Icon: BookOne },
  { t: '简历排版与优化', a: 'Miau', tag: '模板', c: ['#2e1a0a', '#c9772b'], Icon: IdCard },
  { t: '营销文案一键生成', a: 'Forge', tag: '模板', c: ['#5a1e0e', '#ff6b3d'], Icon: Speaker },
  { t: '合同审查要点清单', a: '墨白', tag: '模板', c: ['#3a2a14', '#b8862f'], Icon: DocSearchTwo },
  { t: '读书笔记思维导图', a: 'Neo', tag: '模板', c: ['#4a2018', '#ff8a5c'], Icon: Notebook },
  { t: '演讲稿结构生成', a: 'Lia', tag: '模板', c: ['#2a1208', '#e08a3c'], Icon: SpeakerOne },
  { t: '代码文档自动注释', a: 'Elf', tag: '模板', c: ['#3a1810', '#ff9a5c'], Icon: CodeBrackets },
  { t: '社交媒体推文', a: 'Bit', tag: '模板', c: ['#4a2410', '#d98a55'], Icon: Communication },
];

const HomePage: React.FC = () => {
  const navigate = useNavigate();

  return (
    <div>
      {/* Banner */}
      <div className="wa-banner">
        <div className="glow" />
        <h2>让文档创作更高效</h2>
        <p>Agent 模式自动调用技能，从提纲到成稿一步到位。</p>
        <button className="btn" onClick={() => navigate('/write')}>
          体验 Agent 模式 <Right theme="outline" size="15" />
        </button>
      </div>

      {/* Template masonry grid */}
      <div className="wa-grid">
        {templates.map((w, i) => {
          const h = 180 + ((i * 37) % 120);
          const { Icon } = w;
          return (
            <div
              key={w.t}
              className="wa-card"
              onClick={() => navigate(`/write?t=${encodeURIComponent(w.t)}`)}
            >
              <div
                className="thumb"
                style={{ height: `${h}px`, background: `linear-gradient(150deg,${w.c[0]},${w.c[1]})` }}
              >
                <Icon theme="outline" size="44" fill="rgba(255,255,255,.9)" strokeWidth={2} />
              </div>
              <div className="wa-tag">{w.tag}</div>
              <div className="overlay">
                <div>
                  <div className="t">{w.t}</div>
                  <div className="a"><span className="av" />{w.a}</div>
                </div>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};

export default HomePage;
