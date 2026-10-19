<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Search, Document } from '@element-plus/icons-vue'
import { requestAdmin, type StyleCatalog, type StyleFragmentPage, type StyleFragmentView, type StyleSource } from '../api'

const catalog = ref<StyleCatalog | null>(null)
const selectedId = ref('')
const version = ref('')
const draftQuery = ref('')
const query = ref('')
const intent = ref('')
const seriousness = ref('')
const page = ref<StyleFragmentPage | null>(null)
const catalogLoading = ref(false)
const loading = ref(false)
const loadingMore = ref(false)
const catalogError = ref('')
const error = ref('')
const detail = ref<StyleFragmentView | null>(null)
const detailOpen = ref(false)
let catalogGeneration = 0
let fragmentGeneration = 0
let catalogAbort: AbortController | undefined
let fragmentAbort: AbortController | undefined

const selectedGroup = computed(() => catalog.value?.conversations.find(item => item.conversationId === selectedId.value))
const selectedVersion = computed(() => selectedGroup.value?.versions.find(item => item.version === version.value))
const current = computed(() => version.value === catalog.value?.currentVersion)
const jsonText = computed(() => detail.value ? JSON.stringify({ version: version.value, ...detail.value }, null, 2) : '')
const sourceSections = computed(() => detail.value ? [
  { key: 'context', label: '触发上下文', messages: detail.value.fragment.context },
  { key: 'replies', label: '人类回应', messages: detail.value.fragment.replies },
] : [])

const statusLabels: Record<string, string> = {
  done: '向量已就绪', pending: '等待生成向量', running: '向量生成中', retry: '等待重试', failed: '向量生成失败', not_generated: '当前配置未生成向量',
}
function statusType(status: string): 'success' | 'info' | 'warning' | 'danger' {
  return status === 'done' ? 'success' : status === 'failed' ? 'danger' : ['running', 'retry'].includes(status) ? 'warning' : 'info'
}
function messageTime(source: StyleSource) {
  if (source.timestamp === null || !Number.isFinite(source.timestamp)) return '时间未知'
  const date = new Date(source.timestamp * 1000)
  return Number.isNaN(date.getTime()) ? '时间未知' : date.toLocaleString('zh-CN', { timeZone: 'Asia/Hong_Kong', hour12: false })
}
function participants(item: StyleFragmentView) {
  const people = new Map<string, string>()
  for (const source of [...item.fragment.context, ...item.fragment.replies]) {
    people.set(source.senderId || source.eventId, source.senderName || source.senderId || '未知发送者')
  }
  return [...people.values()].join('、')
}
function excerpt(item: StyleFragmentView) { return item.fragment.replies.map(source => source.text).filter(Boolean).join('\n') }

function clearFragments() {
  // 切换筛选立即清空内容和抽屉；旧请求即使迟到也不能串群或串版本。
  fragmentGeneration++; fragmentAbort?.abort()
  loading.value = false; loadingMore.value = false; page.value = null
  detailOpen.value = false; detail.value = null; error.value = ''
}
async function loadCatalog() {
  const generation = ++catalogGeneration
  catalogAbort?.abort(); clearFragments()
  const controller = new AbortController(); catalogAbort = controller
  catalogLoading.value = true; catalogError.value = ''
  try {
    const result = await requestAdmin<StyleCatalog>('/style-learning/catalog', { signal: controller.signal })
    if (generation !== catalogGeneration) return
    catalog.value = result
    const group = result.conversations.find(item => item.conversationId === selectedId.value) ?? result.conversations[0]
    selectedId.value = group?.conversationId ?? ''
    if (!group?.versions.some(item => item.version === version.value)) version.value = result.currentVersion
    if (selectedId.value) await loadFragments()
  } catch (cause) {
    if (generation === catalogGeneration && !controller.signal.aborted) catalogError.value = cause instanceof Error ? cause.message : '加载群聊与版本失败'
  } finally { if (generation === catalogGeneration) catalogLoading.value = false }
}
function changeGroup() {
  version.value = catalog.value?.currentVersion ?? ''
  draftQuery.value = ''; query.value = ''; intent.value = ''; seriousness.value = ''
  clearFragments(); void loadFragments()
}
function applySearch() {
  query.value = draftQuery.value.trim()
  clearFragments(); void loadFragments()
}
function changeVersion() { clearFragments(); void loadFragments() }

async function loadFragments(more = false) {
  if (!selectedId.value || loading.value || loadingMore.value || (more && page.value?.nextAfterId == null)) return
  const generation = ++fragmentGeneration
  fragmentAbort?.abort()
  const controller = new AbortController(); fragmentAbort = controller
  const id = selectedId.value, selected = version.value
  const previous = more ? page.value!.items : []
  const afterId = more ? page.value!.nextAfterId! : 0
  loading.value = !more; loadingMore.value = more; error.value = ''
  const params = new URLSearchParams({ conversationId: id, version: selected, afterId: String(afterId), limit: '20', query: query.value })
  if (intent.value) params.set('intent', intent.value)
  if (seriousness.value) params.set('seriousness', seriousness.value)
  try {
    const result = await requestAdmin<StyleFragmentPage>(`/style-learning/fragments?${params}`, { signal: controller.signal })
    if (generation !== fragmentGeneration || id !== selectedId.value || selected !== version.value) return
    const merged = new Map(previous.map(item => [item.id, item]))
    result.items.forEach(item => merged.set(item.id, item))
    page.value = { ...result, items: [...merged.values()].sort((a, b) => a.id - b.id) }
  } catch (cause) {
    if (generation === fragmentGeneration && !controller.signal.aborted) error.value = cause instanceof Error ? cause.message : '加载片段失败'
  } finally { if (generation === fragmentGeneration) { loading.value = false; loadingMore.value = false } }
}
function openDetail(item: StyleFragmentView) { detail.value = item; detailOpen.value = true }
async function copyJson() {
  try { await navigator.clipboard.writeText(jsonText.value); ElMessage.success('片段 JSON 已复制') }
  catch { ElMessage.info('无法自动复制，请展开 JSON 后手动复制') }
}
onMounted(() => { void loadCatalog() })
onBeforeUnmount(() => { catalogGeneration++; fragmentGeneration++; catalogAbort?.abort(); fragmentAbort?.abort() })
</script>

<template>
  <div class="page fragment-page">
    <div class="page-heading">
      <div><p class="eyebrow">QQ / 真实互动</p><h1>互动片段</h1><p class="muted">查看小鱼从群聊中收集的互动场景，以及对应的人类原话。</p></div>
      <el-button :icon="Refresh" :loading="catalogLoading" @click="loadCatalog">刷新</el-button>
    </div>
    <el-card shadow="never" class="content-card filter-card">
      <div class="selector-row">
        <label>群聊<el-select v-model="selectedId" aria-label="选择学习群聊" :disabled="catalogLoading" @change="changeGroup">
          <el-option v-for="group in catalog?.conversations ?? []" :key="group.conversationId" :value="group.conversationId" :label="`群 ${group.groupId} · 机器人 ${group.selfId}`" />
        </el-select></label>
        <label>提取版本<el-select v-model="version" aria-label="选择提取版本" :disabled="catalogLoading || !selectedGroup" @change="changeVersion">
          <el-option v-for="option in selectedGroup?.versions ?? []" :key="option.version" :value="option.version" :label="`${option.version} · ${option.current ? '当前使用' : '历史，仅供查看'}（${option.fragmentCount} 个）`" />
        </el-select></label>
        <div v-if="selectedGroup" class="version-summary"><el-tag :type="current ? 'success' : 'info'" effect="plain">{{ current ? '当前有效版本' : '历史版本，仅供查看' }}</el-tag>
          <strong>{{ selectedVersion?.fragmentCount ?? 0 }}<small> 个片段</small></strong></div>
      </div>
      <div class="filter-row">
        <el-input :disabled="catalogLoading || !selectedId" v-model="draftQuery" :prefix-icon="Search" maxlength="200" clearable placeholder="搜索场景、回应方式、昵称、QQ、事件 ID 或原文" aria-label="搜索互动片段" @keyup.enter="applySearch" @clear="applySearch" />
        <el-select :disabled="catalogLoading || !selectedId" v-model="intent" clearable placeholder="全部意图" aria-label="筛选意图" @change="applySearch"><el-option v-for="tag in catalog?.intents ?? []" :key="tag" :label="tag" :value="tag" /></el-select>
        <el-select :disabled="catalogLoading || !selectedId" v-model="seriousness" clearable placeholder="全部认真程度" aria-label="筛选认真程度" @change="applySearch"><el-option v-for="tag in catalog?.seriousnessValues ?? []" :key="tag" :label="tag" :value="tag" /></el-select>
        <el-button type="primary" :disabled="!selectedId || catalogLoading" @click="applySearch">筛选</el-button>
      </div>
      <p v-if="selectedId" class="scope-note">{{ selectedId }}<span v-if="!current"> · 浏览历史不会切换正在使用的规则</span></p>
    </el-card>
    <el-alert v-if="catalogError || error" :title="catalogError || error" type="error" :closable="false" show-icon class="error-alert" />
    <div v-loading="loading || catalogLoading" class="fragment-results" aria-label="互动片段列表">
      <div v-if="page" class="results-heading"><span>找到 <strong>{{ page.total }}</strong> 个片段</span><span class="muted">按收录顺序 · 已加载 {{ page.items.length }} 个</span></div>
      <div class="fragment-grid">
        <el-card v-for="item in page?.items ?? []" :key="selectedId + ':' + version + ':' + item.id" shadow="never" class="fragment-card" @click="openDetail(item)">
          <div class="fragment-top"><span class="fragment-number">片段 #{{ item.id }}</span><el-tag size="small" :type="statusType(item.embeddingStatus)" effect="plain">{{ statusLabels[item.embeddingStatus] ?? '状态未知' }}</el-tag></div>
          <h2>{{ item.fragment.scene }}</h2>
          <div class="fragment-tags"><el-tag size="small">{{ item.fragment.intent }}</el-tag><el-tag size="small" type="info">{{ item.fragment.emotion }}</el-tag><el-tag size="small" type="info">{{ item.fragment.seriousness }}</el-tag></div>
          <p class="interaction-label">回应方式</p><p class="interaction-text">{{ item.fragment.interaction }}</p>
          <blockquote class="reply-excerpt">{{ excerpt(item) || '（无可展示的文字回应）' }}</blockquote>
          <div class="fragment-footer"><span :title="participants(item)">{{ participants(item) }}</span><el-button text type="primary" :icon="Document" @click.stop="openDetail(item)">查看完整片段</el-button></div>
        </el-card>
      </div>
      <el-empty v-if="!loading && !catalogLoading && !catalogError && !error && (!page || page.total === 0)" :description="selectedId ? '当前群聊、版本或筛选条件下暂无片段' : '暂无已配置的学习群聊'" />
      <div v-if="page && page.items.length" class="load-more"><el-button v-if="page.nextAfterId !== null" :loading="loadingMore" @click="loadFragments(true)">加载更多片段</el-button><span v-else class="muted">已显示全部匹配片段</span></div>
    </div>
    <el-drawer v-model="detailOpen" :title="`片段 #${detail?.id ?? ''} · ${version}${current ? '（当前）' : '（历史）'}`" size="min(920px, 96vw)">
      <template v-if="detail">
        <section class="detail-overview"><h2>场景概述</h2><p>{{ detail.fragment.scene }}</p>
          <div class="fragment-tags"><el-tag>{{ detail.fragment.intent }}</el-tag><el-tag type="info">{{ detail.fragment.emotion }}</el-tag><el-tag type="info">{{ detail.fragment.seriousness }}</el-tag></div>
          <h3>回应方式</h3><p>{{ detail.fragment.interaction }}</p><p class="muted">时间按 UTC+8 显示 · {{ statusLabels[detail.embeddingStatus] ?? '向量状态未知' }}</p>
        </section>
        <section v-for="section in sourceSections" :key="section.key" class="source-section" :class="section.key">
          <h2>{{ section.label }}<small>{{ section.messages.length }} 条</small></h2>
          <!-- 原事件数组顺序不变，不按时间或昵称重排；正文与元数据均按文本渲染。 -->
          <article v-for="(source, index) in section.messages" :key="source.eventId + ':' + index" class="source-message">
            <div class="source-identity"><strong>{{ source.senderName || '未知昵称' }}</strong><span>{{ source.senderId ? 'QQ ' + source.senderId : 'QQ 未知' }}</span><time>{{ messageTime(source) }}</time></div>
            <div class="source-bubble">{{ source.text || '（无可确定的本人文字，可展开原始消息描述）' }}</div>
            <div class="source-event">事件 {{ source.eventId }} · 批次 {{ source.sequence }}</div>
            <details v-if="source.background" class="source-background"><summary>原始消息描述</summary><pre>{{ source.background }}</pre></details>
          </article>
        </section>
        <details class="fragment-json"><summary>查看片段 JSON</summary><el-button text type="primary" @click="copyJson">复制 JSON</el-button><pre>{{ jsonText }}</pre></details>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.filter-card{margin-bottom:20px}.selector-row{display:grid;grid-template-columns:minmax(220px,1fr) minmax(220px,1fr) auto;gap:20px;align-items:end}.selector-row label{display:flex;flex-direction:column;gap:9px;font-size:12px;color:#6b7d95}.selector-row .el-select{width:100%}.version-summary{display:flex;gap:15px;align-items:center;min-height:32px}.version-summary strong{font-size:24px;color:#263d59;white-space:nowrap}.version-summary small{font-size:12px;font-weight:400;color:#7d8ba0}.filter-row{display:flex;gap:12px;margin-top:20px}.filter-row .el-input{flex:1;min-width:0}.filter-row .el-select{width:150px}.scope-note{font-size:11px;color:#8694a6;margin:13px 0 0;overflow-wrap:anywhere}.error-alert{margin-bottom:18px}.fragment-results{min-height:240px}.results-heading{display:flex;justify-content:space-between;font-size:13px;color:#516580;margin-bottom:14px}.fragment-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:18px}.fragment-card{cursor:pointer;border-radius:12px;min-width:0}.fragment-top{display:flex;justify-content:space-between;gap:10px}.fragment-number{font-size:12px;color:#7e8da2}.fragment-card h2{font-size:16px;line-height:1.7;color:#263d59;margin:16px 0 13px;display:-webkit-box;-webkit-box-orient:vertical;-webkit-line-clamp:3;overflow:hidden;overflow-wrap:anywhere}.fragment-tags{display:flex;flex-wrap:wrap;gap:7px}.interaction-label{font-size:11px;color:#8a98aa;margin:18px 0 6px}.interaction-text{font-size:13px;line-height:1.7;color:#52647d;margin:0;display:-webkit-box;-webkit-box-orient:vertical;-webkit-line-clamp:2;overflow:hidden;overflow-wrap:anywhere}.reply-excerpt{white-space:pre-wrap;overflow-wrap:anywhere;background:#f4f8fe;border-left:3px solid #a7c9f7;border-radius:0 7px 7px 0;margin:16px 0;padding:12px 14px;font-size:14px;line-height:1.7;color:#365477;max-height:100px;overflow:hidden}.fragment-footer{display:flex;justify-content:space-between;align-items:center;gap:12px}.fragment-footer>span{font-size:11px;color:#8a98aa;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.load-more{text-align:center;margin:25px 0;font-size:12px}.detail-overview{padding:0 0 20px;border-bottom:1px solid #edf1f6}.detail-overview h2,.source-section h2{font-size:16px;color:#263d59;margin:0 0 12px}.detail-overview h3{font-size:13px;margin:18px 0 8px;color:#7b8da4}.detail-overview p{font-size:14px;line-height:1.8;color:#475d78;white-space:pre-wrap;overflow-wrap:anywhere}.detail-overview p.muted{font-size:11px;color:#8a98aa}.source-section{margin-top:26px}.source-section h2{display:flex;align-items:center;gap:10px}.source-section h2 small{font-size:11px;color:#8a98aa;font-weight:400}.source-message{margin:0 0 19px}.source-identity{display:flex;flex-wrap:wrap;gap:8px;align-items:baseline;font-size:12px;color:#30435b;margin-bottom:8px}.source-identity span,.source-identity time{font-size:11px;color:#8a98aa}.source-identity time{margin-left:auto}.source-bubble{padding:12px 15px;border:1px solid #e8edf5;border-radius:8px;background:#f7f9fc;color:#344b67;white-space:pre-wrap;overflow-wrap:anywhere;line-height:1.8;font-size:14px}.replies .source-bubble{background:#edf6ff;border-color:#d2e7ff}.source-event{font-size:10px;color:#97a3b4;overflow-wrap:anywhere;margin-top:7px}.source-background,.fragment-json{font-size:12px;color:#7a8ca4;margin-top:9px}.source-background summary,.fragment-json summary{cursor:pointer;padding:6px 0}.source-background pre,.fragment-json pre{white-space:pre-wrap;overflow-wrap:anywhere;background:#f6f8fb;border-radius:7px;padding:12px;color:#5d6f86;font:12px/1.8 Consolas,monospace}.fragment-json{border-top:1px solid #edf1f6;padding-top:16px;margin-top:20px}
@media(max-width:1100px){.selector-row{grid-template-columns:1fr 1fr}.version-summary{grid-column:1/-1}.fragment-grid{grid-template-columns:1fr}}@media(max-width:650px){.selector-row{grid-template-columns:1fr}.filter-row{flex-wrap:wrap}.filter-row .el-input{flex-basis:100%}.filter-row .el-select{flex:1;min-width:110px}.results-heading{gap:8px;flex-wrap:wrap}.source-identity time{margin-left:0}}
</style>
