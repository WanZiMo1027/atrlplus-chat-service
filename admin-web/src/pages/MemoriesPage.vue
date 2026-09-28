<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import { request, type MemoryItem } from '../api'

const items = ref<MemoryItem[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 20
const type = ref('')
const searchText = ref('')
const appliedSearch = ref('')
const loading = ref(false)
const error = ref('')
const editing = ref<MemoryItem | null>(null)
const editContent = ref('')
const saving = ref(false)
const isSearch = computed(() => appliedSearch.value.length > 0)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const params = new URLSearchParams({ limit: String(pageSize) })
    if (type.value) params.set('type', type.value)
    if (isSearch.value) {
      params.set('query', appliedSearch.value)
      params.set('offset', String((page.value - 1) * pageSize))
      const result = await request<{ items: MemoryItem[]; total: number }>(`/memories/search?${params}`)
      items.value = result.items ?? []
      total.value = result.total ?? 0
    } else {
      params.set('offset', String((page.value - 1) * pageSize))
      const result = await request<{ items: MemoryItem[]; total: number }>(`/memories?${params}`)
      items.value = result.items ?? []
      total.value = result.total ?? 0
    }
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '加载失败'
  } finally {
    loading.value = false
  }
}

function applySearch() {
  page.value = 1
  appliedSearch.value = searchText.value.trim()
  load()
}

function openEdit(item: MemoryItem) {
  editing.value = item
  editContent.value = item.content
}

async function saveEdit() {
  if (!editing.value || !editContent.value.trim()) return
  saving.value = true
  try {
    await request(`/memories/${encodeURIComponent(editing.value.id)}`, {
      method: 'PUT', body: JSON.stringify({ content: editContent.value }),
    })
    ElMessage.success('记忆已更新')
    editing.value = null
    await load()
  } catch (cause) {
    ElMessage.error(cause instanceof Error ? cause.message : '保存失败')
  } finally {
    saving.value = false
  }
}

async function remove(item: MemoryItem) {
  try {
    await ElMessageBox.confirm(`确定删除这条 L1 记忆吗？此操作无法从 TencentDB 恢复。\n\n${item.content.slice(0, 160)}`,
      '确认删除', { type: 'warning', confirmButtonText: '删除记忆', cancelButtonText: '取消',
        confirmButtonClass: 'el-button--danger' })
    await request(`/memories/${encodeURIComponent(item.id)}`, { method: 'DELETE' })
    ElMessage.success('记忆已删除')
    await load()
  } catch (cause) {
    if (cause !== 'cancel') ElMessage.error(cause instanceof Error ? cause.message : '删除失败')
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-heading"><div><p class="eyebrow">TENCENTDB / L1</p><h1>记忆页</h1><p class="muted">查看与修正当前 Agent 的原子记忆。列表跨会话展示。</p></div>
      <el-button :icon="Refresh" @click="load">刷新</el-button></div>
    <div class="stat-row"><div class="stat-card"><span>当前记忆</span><strong>{{ isSearch ? '搜索结果' : total }}</strong><small>{{ isSearch ? `${total} 条匹配` : '条原子记忆' }}</small></div>
      <div class="stat-note">仅管理当前配置的 TencentDB Agent。内容变更会影响后续检索。</div></div>
    <el-card shadow="never" class="content-card">
      <div class="toolbar">
        <el-input v-model="searchText" placeholder="搜索记忆内容" clearable :prefix-icon="Search" @keyup.enter="applySearch" @clear="applySearch" />
        <el-select v-model="type" placeholder="全部类型" clearable @change="() => { page = 1; load() }">
          <el-option label="全部类型" value="" /><el-option label="事件" value="episodic" /><el-option label="画像" value="persona" /><el-option label="指令" value="instruction" />
        </el-select>
        <el-button type="primary" @click="applySearch">搜索</el-button>
      </div>
      <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="error-alert" />
      <el-table v-loading="loading" :data="items" class="memory-table" empty-text="暂无记忆">
        <el-table-column label="记忆内容" min-width="370"><template #default="{ row }"><div class="memory-text">{{ row.content }}</div><div class="item-id">{{ row.id }}</div></template></el-table-column>
        <el-table-column label="类型" width="105"><template #default="{ row }"><el-tag effect="plain" size="small">{{ row.type || '未知' }}</el-tag></template></el-table-column>
        <el-table-column label="版本" width="74"><template #default="{ row }">v{{ row.version }}</template></el-table-column>
        <el-table-column label="更新时间" width="172"><template #default="{ row }">{{ row.updated_at ? new Date(row.updated_at).toLocaleString('zh-CN') : '—' }}</template></el-table-column>
        <el-table-column label="操作" width="132" fixed="right"><template #default="{ row }"><el-button text type="primary" @click="openEdit(row)">编辑</el-button><el-button text type="danger" @click="remove(row)">删除</el-button></template></el-table-column>
      </el-table>
      <div class="pagination"><el-pagination v-model:current-page="page" background layout="prev, pager, next, total" :page-size="pageSize" :total="total" @current-change="load" /></div>
    </el-card>
    <el-dialog v-model="editing" title="编辑 L1 记忆" width="620px" :before-close="(done: () => void) => { editing = null; done() }">
      <p class="muted dialog-hint">保存后 TencentDB 会为这条记忆生成新版本。</p>
      <el-input v-model="editContent" type="textarea" :rows="9" maxlength="8192" show-word-limit resize="vertical" />
      <template #footer><el-button @click="editing = null">取消</el-button><el-button type="primary" :loading="saving" :disabled="!editContent.trim()" @click="saveEdit">保存修改</el-button></template>
    </el-dialog>
  </div>
</template>
