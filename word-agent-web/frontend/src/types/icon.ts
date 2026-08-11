/**
 * IconPark 类型收口。
 *
 * @icon-park/react 的主入口只 re-export 了 IconProvider / DEFAULT_ICON_CONFIGS，
 * 图标组件的 props 类型（IIconProps）仅存在于 es/runtime 子路径。
 * 这里统一收口，避免各处散落深层导入路径。
 */
import type { ComponentType } from 'react';
import type { IIconProps } from '@icon-park/react/es/runtime';

export type { IIconProps };

/** 可作为图标使用的组件类型 */
export type IconComp = ComponentType<IIconProps>;
