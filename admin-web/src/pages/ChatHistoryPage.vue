<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import { requestAdmin, type HistoryBatch, type HistoryConversation, type HistoryMessagePage, type HistoryConversationPage } from '../api'

const conversations = ref<HistoryConversation[]>([])
const conversationQuery = ref('')
const conversationType = ref('all')
const scanCursor = ref('0')
const scanDone = ref(false)
const listing = ref(false)
const listError = ref('')
const selectedId = ref('')
const page = ref<HistoryMessagePage | null>(null)
const draftQuery = ref('')
const appliedQuery = ref('')
const range = ref('all')
const loading = ref(false)
const loadingOlder = ref(false)
const error = ref('')
const timeline = ref<HTMLElement>()
const jsonBatch = ref<HistoryBatch | null>(null)
const jsonOpen = ref(false)
let messageGeneration = 0
let listGeneration = 0
let messageAbort: AbortController | undefined
let listAbort: AbortController | undefined

const visibleConversations = computed(() => conversations.value.filter(item =>
  (conversationType.value === 'all' || item.type === conversationType.value)
  && item.conversationId.toLowerCase().includes(conversationQuery.value.trim().toLowerCase())))
const selected = computed(() => conversations.value.find(item => item.conversationId === selectedId.value))
const jsonText = computed(() => jsonBatch.value ? JSON.stringify({
  sequence: jsonBatch.value.sequence,
  messages: jsonBatch.value.messages,
  decision: jsonBatch.value.decision,
}, null, 2) : '')

function title(item: HistoryConversation) {
  return `${item.type === 'group' ? '群聊' : item.type === 'private' ? '私聊' : '会话'} ${item.targetId}`
}

async function loadConversations(reset = false) {
  const generation = ++listGeneration
  listAbort?.abort()
  const controller = new AbortController()
  listAbort = controller
  listing.value = true
  listError.value = ''
  if (reset) { conversations.value = []; scanCursor.value = '0'; scanDone.value = false }
  try {
    // SCAN 可能返回空页但尚未结束；每次最多连续读取十页，避免在共享 Redis 上无限扫描。
    let cursor = scanCursor.value
    for (let attempt = 0; attempt < 10; attempt++) {
      const result = await requestAdmin<HistoryConversationPage>(`/chat-history/conversations?cursor=${encodeURIComponent(cursor)}`, { signal: controller.signal })
      if (generation !== listGeneration) return
      const merged = new Map(conversations.value.map(item => [item.conversationId, item]))
      result.items.forEach(item => merged.set(item.conversationId, item))
      conversations.value = [...merged.values()].sort((a, b) => a.conversationId.localeCompare(b.conversationId, 'zh-CN', { numeric: true }))
      cursor = result.nextCursor
      scanCursor.value = cursor
      scanDone.value = cursor === '0'
      if (!selectedId.value && conversations.value.length) selectConversation(conversations.value[0]!)
      if (scanDone.value || result.items.length) break
    }
  } catch (cause) {
    if (generation === listGeneration && !controller.signal.aborted) listError.value = cause instanceof Error ? cause.message : '加载会话失败'
  } finally {
    if (generation === listGeneration) listing.value = false
  }
}

function selectConversation(item: HistoryConversation) {
  selectedId.value = item.conversationId
  draftQuery.value = ''; appliedQuery.value = ''; range.value = 'all'
  page.value = null; jsonOpen.value = false; jsonBatch.value = null
  void loadMessages()
}

async function loadMessages(older = false) {
  if (!selectedId.value || (older && !page.value?.nextBeforeSequence)) return
  const generation = ++messageGeneration
  messageAbort?.abort()
  const controller = new AbortController()
  messageAbort = controller
  const id = selectedId.value
  const before = older ? page.value!.nextBeforeSequence : null
  const previous = older ? page.value!.batches : []
  const oldHeight = timeline.value?.scrollHeight ?? 0
  const oldTop = timeline.value?.scrollTop ?? 0
  loading.value = !older; loadingOlder.value = older; error.value = ''
  const params = new URLSearchParams({ conversationId: id, limit: '20', query: appliedQuery.value, range: range.value })
  if (before !== null) params.set('beforeSequence', String(before))
  try {
    const result = await requestAdmin<HistoryMessagePage>(`/chat-history/messages?${params}`, { signal: controller.signal })
    // 中止请求和代次校验同时保护切换会话、搜索与分页，迟到的旧响应不能串群。
    if (generation !== messageGeneration || id !== selectedId.value) return
    const batches = new Map(previous.map(batch => [batch.sequence, batch]))
    result.batches.forEach(batch => batches.set(batch.sequence, batch))
    page.value = { ...result, batches: [...batches.values()].sort((a, b) => a.sequence - b.sequence) }
    await nextTick()
    if (generation !== messageGeneration || !timeline.value) return
    // 向上加载后保持原阅读位置；初次加载和刷新定位到最新记录。
    timeline.value.scrollTop = older ? oldTop + timeline.value.scrollHeight - oldHeight : timeline.value.scrollHeight
  } catch (cause) {
    if (generation === messageGeneration && !controller.signal.aborted) error.value = cause instanceof Error ? cause.message : '加载记录失败'
  } finally {
    if (generation === messageGeneration) { loading.value = false; loadingOlder.value = false }
  }
}

function applySearch() {
  appliedQuery.value = draftQuery.value.trim()
  page.value = null; jsonOpen.value = false; jsonBatch.value = null
  void loadMessages()
}

function openJson(batch: HistoryBatch) { jsonBatch.value = batch; jsonOpen.value = true }

async function copyJson() {
  try {
    await navigator.clipboard.writeText(jsonText.value)
    ElMessage.success('JSON 已复制')
  } catch {
    ElMessage.info('无法自动复制，请在下方选中文本复制')
  }
}

onMounted(() => { void loadConversations() })
onBeforeUnmount(() => {
  messageGeneration++; listGeneration++
  messageAbort?.abort(); listAbort?.abort()
})
</script>

<template>
  <div class="page history-page">
    <div class="page-heading">
      <div><p class="eyebrow">QQ / 聊天记录</p><h1>聊天记录</h1><p class="muted">按接收顺序查看 Agent 保存的原文、历史摘要和回复决策。</p></div>
      <el-button :icon="Refresh" :loading="listing" @click="loadConversations(true)">刷新会话</el-button>
    </div>
    <div class="history-layout">
      <el-card shadow="never" class="content-card conversation-card">
        <div class="card-heading"><h2>会话</h2><span class="muted">{{ conversations.length }} 个已加载</span></div>
        <el-input v-model="conversationQuery" placeholder="筛选群号 / QQ / 会话 ID" clearable aria-label="筛选会话" />
        <el-select v-model="conversationType" aria-label="会话类型" class="conversation-type">
          <el-option label="全部会话" value="all" /><el-option label="群聊" value="group" /><el-option label="私聊" value="private" />
        </el-select>
        <el-alert v-if="listError" :title="listError" type="error" :closable="false" show-icon />
        <div v-loading="listing" class="conversation-list">
          <button v-for="item in visibleConversations" :key="item.conversationId" type="button"
                  class="conversation-item" :class="{ selected: selectedId === item.conversationId }"
                  :aria-pressed="selectedId === item.conversationId" @click="selectConversation(item)">
            <strong>{{ title(item) }}</strong><small v-if="item.selfId">机器人 QQ {{ item.selfId }}</small>
            <small v-else>{{ item.conversationId }}</small>
          </button>
          <p v-if="!listing && !visibleConversations.length" class="muted">{{ scanDone ? '暂无匹配会话' : '尚未找到匹配会话，可继续加载' }}</p>
        </div>
        <el-button v-if="!scanDone" class="scan-more" :loading="listing" @click="loadConversations()">加载更多会话</el-button>
      </el-card>

      <el-card shadow="never" class="content-card history-content">
        <template v-if="selectedId">
          <div class="history-heading"><div><h2>{{ selected ? title(selected) : selectedId }}</h2><p class="muted">{{ selectedId }}</p></div>
            <el-button :icon="Refresh" :disabled="loading || loadingOlder" @click="loadMessages()">刷新记录</el-button>
          </div>
          <div class="history-toolbar">
            <el-input v-model="draftQuery" :prefix-icon="Search" maxlength="200" placeholder="搜索昵称、QQ、事件 ID、原文或回复" clearable aria-label="搜索聊天记录" @keyup.enter="applySearch" />
            <el-select v-model="range" aria-label="记录范围" @change="applySearch">
              <el-option label="全部记录" value="all" /><el-option label="当前活跃" value="active" /><el-option label="已压缩" value="compressed" />
            </el-select>
            <el-button type="primary" @click="applySearch">搜索</el-button>
          </div>
          <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon class="error-alert" />
          <section v-if="page" class="summary-box">
            <div class="summary-title"><strong>历史摘要</strong><span>版本 {{ page.summaryVersion }} · {{ page.lastCompressedSequence ? '覆盖至批次 ' + page.lastCompressedSequence : '尚无压缩记录' }}</span></div>
            <!-- 摘要和所有消息均使用 Vue 文本插值，绝不执行群友提供的 HTML。 -->
            <div class="summary-text">{{ page.summary || '尚未生成历史摘要，原始消息按顺序保存在下方。' }}</div>
          </section>
          <div ref="timeline" v-loading="loading" class="history-timeline" aria-label="聊天时间线">
            <div class="older-control" v-if="page">
              <el-button v-if="page.nextBeforeSequence !== null" :loading="loadingOlder" @click="loadMessages(true)">加载更早记录</el-button>
              <span v-else class="muted">{{ page.batches.length ? '已到最早匹配记录' : '暂无匹配记录' }}</span>
            </div>
            <article v-for="batch in page?.batches ?? []" :key="selectedId + ':' + batch.sequence" class="history-batch">
              <div class="batch-heading"><span>批次 {{ batch.sequence }}</span>
                <el-tag size="small" :type="batch.sequence <= (page?.lastCompressedSequence ?? 0) ? 'info' : 'success'" effect="plain">{{ batch.sequence <= (page?.lastCompressedSequence ?? 0) ? '已压缩 · 原文保留' : '当前活跃' }}</el-tag>
                <el-button text type="primary" @click="openJson(batch)">查看 JSON</el-button>
              </div>
              <div v-for="message in batch.messages" :key="message.eventId" class="history-message">
                <div class="message-identity"><strong>{{ message.senderName || '未知昵称' }}</strong><span v-if="message.senderId">QQ {{ message.senderId }}</span></div>
                <div class="message-bubble">{{ message.text || '（无文字内容）' }}</div>
                <div v-for="(image, index) in message.images" :key="index" class="message-media">图片：{{ image.summary || image.file || '未提供描述' }}</div>
                <div class="event-id">事件 {{ message.eventId }}</div>
              </div>
              <div v-if="batch.decision?.shouldReply" class="history-message decision-message">
                <div class="message-identity"><strong>机器人回复决策</strong><span>未确认发送</span></div>
                <div v-for="(reply, index) in batch.decision.replies" :key="index" class="message-bubble">{{ reply }}</div>
                <div v-if="batch.decision.meme" class="decision-detail">表情语义：{{ batch.decision.meme }}</div>
                <div v-if="batch.decision.replyToEventId" class="event-id">引用事件 {{ batch.decision.replyToEventId }}</div>
              </div>
              <p v-else class="decision-status">{{ batch.completed ? '本批无回复（可能为静默兜底）' : '决策未完成' }}</p>
            </article>
          </div>
          <div v-if="page" class="history-foot">已加载 {{ page.batches.length }} / {{ page.matchingBatches }} 批匹配记录 · 会话共 {{ page.nextSequence }} 批</div>
        </template>
        <el-empty v-else description="选择左侧会话查看聊天记录" />
      </el-card>
    </div>
    <el-drawer v-model="jsonOpen" :title="'批次 ' + (jsonBatch?.sequence ?? '') + ' · 原始 JSON'" size="min(780px, 95vw)">
      <div class="json-actions"><el-button type="primary" @click="copyJson">复制 JSON</el-button></div>
      <pre class="history-json">{{ jsonText }}</pre>
    </el-drawer>
  </div>
</template>

<style scoped>
.history-layout{display:grid;grid-template-columns:270px minmax(0,1fr);gap:20px;align-items:start}
.card-heading{margin-bottom:16px;gap:8px;align-items:center}.card-heading h2,.history-heading h2{font-size:17px;margin:0;color:#263d59}
.conversation-type{width:100%;margin:12px 0}.conversation-list{min-height:120px;max-height:65vh;overflow:auto}
.conversation-item{display:flex;flex-direction:column;gap:7px;width:100%;padding:15px 12px;margin:4px 0;border:1px solid transparent;border-radius:9px;background:transparent;text-align:left;color:#30435b;cursor:pointer}
.conversation-item:hover{background:#f4f8fd}.conversation-item.selected{background:#edf5ff;border-color:#b8d6ff}.conversation-item small{font-size:11px;color:#7d8ba0;overflow-wrap:anywhere}.scan-more{width:100%;margin-top:14px}
.history-content{min-width:0}.history-heading{display:flex;justify-content:space-between;gap:12px;margin-bottom:18px}.history-heading p{font-size:11px;margin:8px 0 0;overflow-wrap:anywhere}
.history-toolbar{display:flex;gap:10px;margin-bottom:16px}.history-toolbar .el-input{flex:1;min-width:0}.history-toolbar .el-select{width:140px}
.summary-box{padding:15px 17px;border:1px solid #dce9f9;background:#f3f8ff;border-radius:9px;margin-bottom:16px}.summary-title{display:flex;justify-content:space-between;gap:12px;font-size:13px;color:#315f94}.summary-title span{font-size:11px;color:#6a82a0}
.summary-text{white-space:pre-wrap;overflow-wrap:anywhere;font-size:13px;line-height:1.7;color:#40516a;max-height:180px;overflow:auto;margin-top:9px}
.history-timeline{height:calc(100vh - 435px);min-height:340px;overflow:auto;overscroll-behavior:contain;padding:0 8px 10px 0}.older-control{text-align:center;padding:12px 0 20px}
.history-batch{padding:0 0 12px;margin-bottom:12px;border-bottom:1px solid #edf1f6}.batch-heading{display:flex;align-items:center;gap:10px;font-size:11px;color:#7c8ca3;margin-bottom:12px}.batch-heading .el-button{margin-left:auto}
.history-message{max-width:90%;margin-bottom:16px}.message-identity{display:flex;align-items:baseline;gap:10px;font-size:12px;color:#30435b;margin-bottom:7px}.message-identity span{font-size:11px;color:#8a98aa}
.message-bubble{white-space:pre-wrap;overflow-wrap:anywhere;line-height:1.7;font-size:13px;padding:12px 15px;border-radius:0 11px 11px;background:#f3f5f9;color:#30435b;margin-bottom:6px}
.event-id{font-size:10px;color:#8d9aae;margin-top:6px;overflow-wrap:anywhere}.message-media,.decision-detail{font-size:12px;color:#617b98;margin-top:8px;white-space:pre-wrap;overflow-wrap:anywhere}
.decision-message{margin-left:auto}.decision-message .message-identity{justify-content:flex-end}.decision-message .message-bubble{background:#eaf4ff;border-radius:11px 0 11px 11px}.decision-message .event-id{text-align:right}
.decision-status{font-size:11px;color:#8a98aa;text-align:right;margin:8px 0}.history-foot{border-top:1px solid #edf1f6;padding-top:13px;font-size:11px;color:#8291a5}
.json-actions{display:flex;justify-content:flex-end;margin-bottom:14px}.history-json{white-space:pre-wrap;overflow-wrap:anywhere;background:#f6f8fc;border:1px solid #e6edf5;border-radius:9px;padding:18px;font-size:12px;line-height:1.7;color:#31465e}
@media(max-width:1100px){.history-layout{grid-template-columns:220px minmax(0,1fr)}.summary-title{flex-direction:column;gap:6px}}
@media(max-width:760px){.history-layout{grid-template-columns:1fr}.conversation-list{max-height:180px}.history-toolbar{flex-wrap:wrap}.history-toolbar .el-input{flex-basis:100%}.history-timeline{height:500px}.history-message{max-width:100%}}
</style>
