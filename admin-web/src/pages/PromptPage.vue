<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { request, type Draft, type PromptPage } from '../api'

const data = ref<PromptPage | null>(null)
const editor = ref('')
const loading = ref(false)
const working = ref(false)
const error = ref('')
const changed = computed(() => editor.value !== (data.value?.draft.content ?? ''))
const builtinEmpty = computed(() => data.value?.active?.source === 'builtin' && !data.value.active.content.trim())
const stale = computed(() => !!data.value?.active && !!data.value?.draft.saved && (
  data.value.draft.basePromptId !== data.value.active.memoryPromptId ||
  data.value.draft.baseVersion !== data.value.active.version))
const lines = computed(() => {
  const oldLines = (data.value?.active?.content ?? '').split('\n')
  const newLines = editor.value.split('\n')
  return Array.from({ length: Math.max(oldLines.length, newLines.length) }, (_, index) => ({
    old: oldLines[index] ?? '', next: newLines[index] ?? '', changed: oldLines[index] !== newLines[index],
  }))
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    data.value = await request<PromptPage>('/prompt')
    editor.value = data.value.draft.content
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '加载失败'
  } finally { loading.value = false }
}

async function save() {
  if (!data.value) return
  working.value = true
  try {
    const draft = await request<Draft>('/prompt/draft', { method: 'PUT', body: JSON.stringify({
      content: editor.value, expectedRevision: data.value.draft.revision,
    }) })
    data.value.draft = draft
    editor.value = draft.content
    ElMessage.success('草稿已保存，线上提示词尚未改变')
  } catch (cause) { ElMessage.error(cause instanceof Error ? cause.message : '保存失败') }
  finally { working.value = false }
}

async function loadTemplate() {
  try {
    if (changed.value) {
      await ElMessageBox.confirm('载入项目模板会替换编辑区中尚未保存的内容。已保存的草稿和线上规则不会改变。',
        '载入项目模板', { type: 'warning', confirmButtonText: '载入模板' })
    }
    working.value = true
    const template = await request<{ content: string }>('/prompt/template')
    editor.value = template.content
    ElMessage.success('项目模板已载入编辑区；保存草稿并发布后才会在线上生效')
  } catch (cause) { if (cause !== 'cancel') ElMessage.error(cause instanceof Error ? cause.message : '载入模板失败') }
  finally { working.value = false }
}

async function publish() {
  if (!data.value) return
  try {
    await ElMessageBox.confirm('发布后，新的 L1 抽取规则会立即用于后续消息。确定发布当前草稿？',
      '发布提示词', { type: 'warning', confirmButtonText: '确认发布' })
    working.value = true
    data.value = await request<PromptPage>('/prompt/publish', { method: 'POST' })
    editor.value = data.value.draft.content
    ElMessage.success('提示词已发布')
  } catch (cause) { if (cause !== 'cancel') ElMessage.error(cause instanceof Error ? cause.message : '发布失败') }
  finally { working.value = false }
}

async function rebase() {
  try {
    await ElMessageBox.confirm('将当前远端版本设为草稿基准，保留你的草稿正文。请先检查差异再发布。',
      '更新发布基准', { type: 'warning', confirmButtonText: '更新基准' })
    const draft = await request<Draft>('/prompt/rebase', { method: 'POST' })
    if (data.value) data.value.draft = draft
    ElMessage.success('基准已更新')
  } catch (cause) { if (cause !== 'cancel') ElMessage.error(cause instanceof Error ? cause.message : '操作失败') }
}

async function restore(id: number) {
  try {
    await ElMessageBox.confirm('将这个历史版本复制到草稿？当前未保存的编辑会被替换。',
      '载入历史版本', { confirmButtonText: '载入草稿' })
    const draft = await request<Draft>(`/prompt/history/${id}/restore`, { method: 'POST' })
    if (data.value) data.value.draft = draft
    editor.value = draft.content
    ElMessage.success('已载入草稿，发布前可继续编辑')
  } catch (cause) { if (cause !== 'cancel') ElMessage.error(cause instanceof Error ? cause.message : '载入失败') }
}

onMounted(load)
</script>

<template>
  <div class="page" v-loading="loading">
    <div class="page-heading"><div><p class="eyebrow">TENCENTDB / L1</p><h1>提示词页</h1><p class="muted">编辑长期记忆的抽取规则。草稿与发布分开，避免误改线上行为。</p></div>
      <el-button :icon="Refresh" @click="load">刷新</el-button></div>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="error-alert" />
    <el-alert v-if="data && !data.remoteAvailable" title="Agent 或 TencentDB 暂时不可用；仍可编辑已有草稿，发布需等待服务恢复。" type="warning" show-icon :closable="false" class="error-alert" />
    <el-alert v-if="builtinEmpty" title="当前使用 TencentDB 内置 L1 规则；接口不提供内置正文。项目模板可以载入草稿，保存并发布后才会成为生效版本。" type="info" show-icon :closable="false" class="error-alert" />
    <el-alert v-if="stale" title="远端提示词已变化。请核对差异，再更新发布基准。" type="warning" show-icon :closable="false" class="error-alert" />
    <div v-if="data" class="prompt-grid">
      <el-card shadow="never" class="content-card prompt-card">
        <div class="card-heading"><div><h2>当前生效版本</h2><p class="muted">{{ data.active?.memoryPromptId || '远端不可用' }}</p></div><el-tag v-if="data.active" effect="plain">{{ data.active.source === 'builtin' ? '内置规则' : `v${data.active.version}` }}</el-tag></div>
        <pre class="prompt-content">{{ data.active?.content || (builtinEmpty ? '内置规则正文不可读取；此处只展示线上可查询的 Prompt 内容。' : '远端暂时不可用，或当前 Prompt 正文为空。') }}</pre>
      </el-card>
      <el-card shadow="never" class="content-card prompt-card">
        <div class="card-heading"><div><h2>编辑草稿</h2><p class="muted">修订 {{ data.draft.revision }} · {{ data.draft.saved ? '已保存' : '尚未保存' }}</p></div><el-tag :type="changed ? 'warning' : 'success'" effect="plain">{{ changed ? '有未保存修改' : '已同步草稿' }}</el-tag></div>
        <el-input v-model="editor" type="textarea" :rows="17" maxlength="10000" show-word-limit resize="vertical" placeholder="输入 L1 记忆抽取提示词" />
        <div class="prompt-actions"><el-button :disabled="working" @click="loadTemplate">载入项目模板</el-button><el-button :loading="working" :disabled="!editor.trim()" @click="save">保存草稿</el-button><el-button v-if="stale" @click="rebase">更新基准</el-button><el-button type="primary" :loading="working" :disabled="changed || !data.draft.saved || !data.remoteAvailable || stale" @click="publish">发布到 TencentDB</el-button></div>
      </el-card>
    </div>
    <el-card v-if="data" shadow="never" class="content-card below-card"><div class="card-heading"><div><h2>差异预览</h2><p class="muted">逐行对照当前生效内容和编辑区内容</p></div></div>
      <div class="diff"><div v-for="(line, index) in lines" :key="index" class="diff-row" :class="{ modified: line.changed }"><span>{{ index + 1 }}</span><code>{{ line.old }}</code><code>{{ line.next }}</code></div></div></el-card>
    <el-card v-if="data" shadow="never" class="content-card below-card"><div class="card-heading"><div><h2>发布历史</h2><p class="muted">最近 30 次发布尝试；历史内容可重新载入草稿</p></div></div>
      <el-table :data="data.history" empty-text="暂无发布记录"><el-table-column prop="requestedAt" label="时间" width="205" /><el-table-column prop="remoteVersion" label="版本" width="80"><template #default="{ row }">v{{ row.remoteVersion }}</template></el-table-column><el-table-column prop="content" label="内容摘要" min-width="280" show-overflow-tooltip /><el-table-column prop="status" label="状态" width="110"><template #default="{ row }"><el-tag :type="row.status === 'published' ? 'success' : 'warning'" size="small">{{ row.status === 'published' ? '已发布' : row.status === 'failed' ? '失败' : '待确认' }}</el-tag></template></el-table-column><el-table-column label="操作" width="110"><template #default="{ row }"><el-button text type="primary" :disabled="row.status !== 'published'" @click="restore(row.id)">载入草稿</el-button></template></el-table-column></el-table>
    </el-card>
  </div>
</template>
