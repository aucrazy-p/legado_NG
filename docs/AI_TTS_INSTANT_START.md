# AI 听书即时起播（Instant Start）实施方案

> 目标：把「点击朗读 → 首声」从 30–60 秒压缩到 1–3 秒。
> 做法：本地规则先出声，模型分镜在后台生成，回来后从「已入队位置」接管后续段落。

## 1. 问题

`HttpReadAloudService.downloadAndPlayAudios` 在出声前必须同步完成：

1. `AiTtsStoryboardHelper.generate` → `requestSceneRanges` + `requestModelUnits`（两次串行 LLM，整章一次性，输出 maxTokens 8k–16k）
2. `BookTtsCastingCoordinator.prepareGenerated` → 自动选角 LLM + 场景选音 LLM
3. 首句 TTS 合成

任一步失败直接 `pauseReadAloud()`。全链路无耗时打点。

## 2. 关键约束（改动必须守住）

| 编号 | 不变量 |
|---|---|
| I1 | `MediaItem.mediaId` 里的 `itemIndex` 必须等于该 item 在 `speakItems` 中的下标 |
| I2 | 已经 `exoPlayer.addMediaItem` 的内容一律不回滚、不修改 |
| I3 | `speakItems` 只允许「保留前缀 + 替换尾部」，且替换点落在段落边界之后 |
| I4 | 进度回传只依赖 `paragraphIndex`，替换尾部不得改变已播段落的 `paragraphIndex` |
| I5 | 所有对 `speakItems` / `exoPlayer` 的写操作在主线程执行 |

## 3. 设计

### 3.1 本地兜底分镜（新增 `LocalStoryboardFallback.kt`）

复用 `AiTtsStoryboardHelper` 已有的纯本地正则（`findQuoteSpans` / `findColonUnits` / `quoteRoleHint`），把每段切成
旁白 / 对白 / 心声三类 segment，产出单场景 `ChapterStoryboard`。

- 不设置 `speakerName`（避免猜错名字导致绑定到错误角色），只填 `speakerGender`
- 性别推断顺序：引号前 20 字的「她 / 他」→ 关注语中的女称 / 男称 → 未知
- 未知性别的对白走 `defaultDialogueBinding`（多角色引擎默认对白音色）；
  明确性别的对白走 `defaultDialogueMale/FemaleTtsVoiceId`
- 纯 CPU 正则，单章毫秒级，不产生任何网络请求

### 3.2 即时分镜入口（`AiTtsStoryboardHelper.getOrGenerateInstant`）

```
命中任意一层缓存 → prepareCached → 返回 (真分镜, fallback=false, upgrade=null)
未命中           → 本地兜底 → 返回 (兜底分镜, fallback=true, upgrade=Deferred)
                                  upgrade 后台跑 getOrGenerate（内部会写三层缓存）
```

`upgrade` 运行在独立的 application scope，播放取消不影响它继续生成并落盘。

### 3.3 分批合成 + 接管（`HttpReadAloudService`）

合成从「一次性整章」改为「每批 N 条循环」：

```
while (已合成 < speakItems.size) {
    合成 [已合成, 已合成+N)
    已合成 += N
    if (未接管 && upgrade 已完成) 尝试接管
}
```

接管算法（主线程）：

```
queued   = exoPlayer.mediaItemCount            // 已入队条数
lastPara = speakItems[queued-1].paragraphIndex // 已入队最后一段
takeover = speakItems.indexOfLast { it.paragraphIndex == lastPara } + 1
newTail  = buildSpeakItemsForContent(startParagraphIndex = lastPara + 1, storyboard = 真分镜)
speakItems = speakItems.take(takeover) + newTail
```

- 接管点天然在段落边界之后，满足 I3
- `[0, takeover)` 完全不动，满足 I1 / I2 / I4
- 同段内已入队或待入队的兜底 item 保留，不会造成同段音色跳变

### 3.4 降级策略

| 场景 | 行为 |
|---|---|
| 整章已全部入队 | 放弃接管，只写缓存，下次听该章直接命中 |
| `productionToken` 失效 / 已切章 / 已暂停 | 放弃接管 |
| `upgrade` 失败 | 保留兜底分镜继续播，不暂停 |
| 兜底分镜为空 | 回退到原有阻塞路径 |

## 4. 改动清单

| 文件 | 改动 |
|---|---|
| `help/tts/LocalStoryboardFallback.kt` | 新增：本地规则分镜 |
| `help/ai/AiTtsStoryboardHelper.kt` | `findQuoteSpans` / `findColonUnits` 改 internal；新增 `localUnitSpans`、`getOrGenerateInstant` |
| `service/HttpReadAloudService.kt` | `loadCurrentAiStoryboard` 返回即时结果；分批合成；接管逻辑 |
| `help/config/AppConfig.kt` + `PreferKey` | 新增 `readAloudInstantStart` 开关（默认开） |

不涉及：TTS 引擎、ExoPlayer 内核、LLM Provider、Script 引擎、数据库结构。

## 5. 验证清单

- [ ] 无缓存新章：点击朗读 3 秒内出声
- [ ] 出声后 `AppLog` 出现「分镜接管」，且接管点段落正确
- [ ] 接管后进度条、目录高亮、章节末尾自动下一章正常
- [ ] 手动拖动进度、暂停恢复、切换朗读页模式（按页/整章）不崩
- [ ] 缓存命中章：行为与改造前一致（不退化）
- [ ] 关闭开关后：回到原有阻塞路径
- [ ] release 混淆包（`.rc`）上同样通过
