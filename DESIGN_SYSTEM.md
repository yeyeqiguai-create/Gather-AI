# Gather AI — Liquid Reality Design System

本文档与 `ui/liquid` 下的实际实现同步维护。所有界面共享同一套令牌与材质管线；页面内禁止硬编码颜色、圆角、透明度和动画参数。

## 主题（四套完整规格）

主题标识为 `domain.settings.VisualTheme`，持久化于加密偏好存储；每个主题在 `ui.liquid` 中映射为完整 `LiquidSpec`：

| 主题 | 定位 | 氛围 |
|---|---|---|
| `LIQUID_INDIGO` | 品牌默认 | 深空蓝紫、冷色边缘高光、适度虹彩 |
| `OBSIDIAN` | 克制专业 | 石墨黑、银色高光、低饱和强调色 |
| `ARCTIC` | 明亮通透 | 冰川浅色玻璃、深色文字、白色高光 |
| `AURORA` | 灵动 | 深青海蓝、极光色斑（可选缓慢漂移） |

切换主题改变整个视觉氛围（背景、玻璃参数、语义色、代码块配色），而不是只换主色。

## 令牌

- `LiquidSpec`：环境背景三段渐变、光斑颜色、纹理强度；文本/弱化文本；accent / accentBright / cyan；success / danger / warning；源内软表面（tint + alpha + edge）；代码块三色；五种玻璃材质参数；`LiquidMotion` 动效时长与曲线。
- 通过 `LocalLiquidSpec` 提供；兼容层 `FuturePalette`（`ui.theme`）委托到同一数据源。

## 材质（五种）

| 材质 | 参数特征 | 用途 |
|---|---|---|
| Clear | 低模糊、低 tint、细高光 | 低干扰表面 |
| Frosted | 中高模糊、较高 tint | 面板、菜单、内容卡片 |
| Liquid | 中模糊 + AGSL 边缘折射（API 33+） | 招牌表面：顶栏、输入区、原型页 |
| Dark | 深 tint、控亮背景 | 深色空间文字承载 |
| Accent | 主题强调色 tint | 关键操作、选中态，少量使用 |

## 渲染管线（真实玻璃）

`LiquidStage` 把页面分为两层：

- **source**（经 `Modifier.liquidSource`）：环境背景 + 滚动内容，记录进 `GraphicsLayer`；
- **overlay**：固定玻璃表面（顶栏、输入框、悬浮层），通过 `Modifier.glassSurface` 采样 source 层并真实模糊（`RenderEffect`，API 31+）。

`glassSurface` 节点结构：外层裁剪 → tint/镜面/边缘高光（不模糊）→ 模糊节点 → AGSL 折射节点（仅 Liquid，API 33+）→ 平移对齐的背景采样。滚动卡片位于 source 内部时自动退化为 `softSurface`（仅 tint），避免递归采样与逐卡片模糊的开销。

降级路径：API < 31、低性能模式、无 stage source（对话框）→ tint-only 表面；AGSL 折射在 API < 33 或着色器编译失败时静默关闭，只保留模糊 + 高光近似。

## 空间层级

- L0 环境背景（`AmbientBackdrop`：渐变 + 光斑 + 细纹理，可选极光漂移）
- L1 页面主体（source，软表面）
- L2 主要玻璃面板（overlay，Frosted/Liquid）
- L3 悬浮操作层（overlay，Clear/Liquid）
- L4 模态（Dialog，独立窗口 → tint 降级）

## 用户控制

设置 → 外观：主题选择（四套预览卡）、玻璃材质强度（0–100%，控制模糊半径与 tint）、低性能模式、动态背景、紧凑间距、过渡动画、字体缩放。全部真实生效并加密持久化。

## 可访问性

- 文本对比度在四套主题下均经过选择；状态同时用图标 + 文字 + 颜色表达（任务时间线、请求状态卡）。
- 字体缩放 85%–125%；减少动效（系统动画关闭或应用内关闭）时过渡时长归零、极光漂移停止。

## 验收入口

设置 → 外观 → 「液态玻璃原型 · 材质验收」：高对比滚动测试场，验证真实采样、滚动更新、五种材质、多层玻璃与折射。
