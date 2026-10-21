# AI 听书 token 瘦身

## 第二批：输出字段按能力裁剪

第一批压低的是输入，但实测显示**输入占 token 量 77.8%、输出只占 22.2%，输出却吃掉约 57% 的成本**（输出单价约为输入的 4 倍）。所以第二批动输出。

### 浪费点

协议原来要求「每项始终包含」「始终存在」，于是每个 unit 都要输出：

| 恒空字段 | 说明 |
|---|---|
| `performanceInstruction`、`performanceContext` | 旁白/其他根本没有演绎信息，仍要输出 `""` 和 `[]` |
| `styleConcepts` | 未启用风格模块时永远是 `[]` |
| `emotion`、`emotionIntensity`、`expressiveConfidence` | 未启用情绪模块时永远是 `null` |
| `mergeCastRoleIds` | 绝大多数 unit 永远是 `[]` |
| `characterName`/`characterId`/`castRoleId`/`speakerGender`/`identityType`/`nameType`/两个 evidence | 旁白固定为 `""`/`0`/`unknown`/`none`/`unknown` |

旁白在小说里通常占一半左右的 unit，等于一半输出是纯格式占位。

### 改法（只改提示词，不动解析代码）

- `modules/protocol.md` 新增「字段省略规则」：空字符串、空数组、`null` 一律省略；每个 unit 至少返回 `unitId`/`roleType`/`status`；`narrator`/`other` 只返回这三个。
- 把四处强制占位表述改成「有内容才返回，否则省略」：`protocol.md`、`base-routing.md`、`scene-context.md`、`style-tags.md`、`emotion.md`、`performance-instruction.md`。
- 协议示例改成两段：一个精简 character + 一个只含三字段的 narrator，避免模型照抄全量空字段。

**解析层不需要改**：`ModelUnitResult` 每个字段都带 Kotlin 默认值（`""`/`0L`/`emptyList()`/`null`），GSON 走无参构造器补默认值；`identityType`/`nameType`/两个 evidence 的默认值本身就是合法枚举；`normalizeModelUnit` 还会对 narrator/other 强制归一。

**唯二必须保留**：`roleType`、`status` —— 二者默认值是 `""`，不在合法集合里，缺了会让整章判无效并触发二分重试。

### 预期

| 项目 | 预期 |
|---|---|
| 输出 token | -25~30% |
| 总成本 | -14~17% |
| 请求次数 | 不变 |
| 分镜质量 | 不应变化（省的都是无信息字段） |

### 两个观察点

1. 模型可能**过度省略**：把 character/thought 的 `performanceContext`/`performanceInstruction` 也省了 → 解析层 1595-1600 的 check 会判整章无效 → 二分重试。协议里已写死「character/thought 必须返回」，但仍需实测确认。
2. 模型可能顺手把 `evidence` 写得更短 → 跨章认人的证据池变弱，表现为角色被重复创建、音色漂移。协议保留 `evidence` 必填。

---

## 第一批：输入侧

## 背景

一次实测（11 章分镜）：36 次请求，输入 378,689 + 输出 107,647 = 486.34K token。
输入占 77.8%，其中占比最大的是「同一份内容反复发送」，而不是输出膨胀。

## 本次改动（仅两处，均在 `AiTtsStoryboardHelper.kt`）

### 1. 分角色请求的正文裁剪

`toPayload()` 原来把整章 `contextParagraphs` 全量塞进分角色请求。但：

- 同一章已经先走过一次分场景请求，正文等于发两遍；
- 每个 unit 自带 `textPreview` + `cueBefore`/`cueAfter`（各 120 字）；
- `scenes` 单独下发。

改为 `contextParagraphsFor()`：**包含候选片段的段落发全文，其余段落只发段号 + 前 30 字**。
段落数 ≤ 8 的短章不裁剪（本来就没多少收益）。

### 2. 角色库下发字段收敛

`toKnownCastRole()` 原来每个角色下发：代表台词 3 条 × 120 字 + 身份证据 4 条 × 120 字 = 840 字/角色，
且每章全量重发。改为代表台词 2 条 × 60 字 + 证据 2 条 × 60 字 = 240 字/角色。

字段名、结构都没动，只是样本变短变少，避免改坏协议。

## 预期收益

| 项目 | 预期 |
|---|---|
| 分角色请求输入 | -35~50%（取决于本章候选段落占比） |
| 整章平均输入 | -25~40% |
| 输出 | 不变 |
| 请求次数 | 不变 |
| 分镜质量 | 应无明显变化，需 A/B 验证 |

## 未改动的部分（有意为之）

- `CACHE_VERSION` 未提升 → 已生成的章节不会因本次改动重烧 token
- 重试逻辑、合并请求、scene 请求正文：收益小或风险高，暂不动

## 验证清单

1. 同一本书、同一批章节（先清掉对应分镜缓存）重测，记录：请求数 / 输入 token / 输出 token
2. 对比每请求平均 input（改前 10.5K）
3. 抽查分镜质量，重点看两类退化：
   - 跨段落指代判错（"他/她"落到错误角色）
   - 本该有主的对白被判成旁白
4. 若质量下降：先调 `CONTEXT_PREVIEW_CHARS` 30 → 60，仍不行再调 `CONTEXT_FULL_CHAPTER_MAX`

## 回滚

```bash
git checkout main          # 分支上的改动不影响 main
# 或
git revert <本次提交>
```

两个改动都是独立常量控制，也可以只回滚其中一处。
