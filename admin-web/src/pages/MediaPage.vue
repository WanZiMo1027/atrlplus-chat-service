<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { requestAdmin, type MediaDescription, type MediaItem, type MediaKind, type MediaPage } from '../api'

const props = defineProps<{ kind: MediaKind }>()
const title = computed(() => props.kind === 'meme' ? '表情包' : '图片')
const items = ref<MediaItem[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 20
const query = ref('')
const appliedQuery = ref('')
const status = ref('')
const loading = ref(false)
const error = ref('')
const dialog = ref(false)
const editing = ref<MediaItem | null>(null)
const saving = ref(false)
const removing = ref(new Set<string>())
const failedPreview = reactive(new Set<string>())
const preview = ref('')
const form = reactive({ fileId: '', sourceUrl: '', content: '', text: '', emotion: '', memeMeaning: '', confidence: 0 })
let loadSequence = 0

const recognitionLabels = { processing: '识别中', ready: '已保存描述', failed: '识别失败' }

async function load() {
  const sequence = ++loadSequence
  loading.value = true
  error.value = ''
  try {
    const params = new URLSearchParams({ kind: props.kind, query: appliedQuery.value, status: status.value,
      page: String(page.value), pageSize: String(pageSize) })
    const result = await requestAdmin<MediaPage>(`/media?${params}`)
    // 切换子菜单时可能有旧请求未完成，只展示当前类型的结果。
    if (sequence !== loadSequence) return
    items.value = result.items
    total.value = result.total
    failedPreview.clear()
    if (!items.value.length && page.value > 1 && total.value <= (page.value - 1) * pageSize) {
      page.value = Math.max(1, Math.ceil(total.value / pageSize))
      await load()
    }
  } catch (cause) {
    if (sequence === loadSequence) error.value = cause instanceof Error ? cause.message : '加载失败'
  } finally {
    if (sequence === loadSequence) loading.value = false
  }
}

function search() { page.value = 1; appliedQuery.value = query.value.trim(); load() }

function imageUrl(item: MediaItem) {
  if (failedPreview.has(item.id)) return ''
  // 浏览器直接预览原始地址；加载失败只显示占位，不发起后台下载。
  return item.sourceUrl || ''
}

function imageError(item: MediaItem) {
  failedPreview.add(item.id)
}

function openForm(item: MediaItem | null) {
  editing.value = item
  Object.assign(form, { fileId: item?.fileId || '', sourceUrl: item?.sourceUrl || '',
    content: item?.description?.content || '', text: item?.description?.text || '',
    emotion: item?.description?.emotion || '', memeMeaning: item?.description?.memeMeaning || '',
    confidence: item?.description?.confidence ?? 0 })
  dialog.value = true
}

async function save() {
  if (!form.content.trim() || (!editing.value && !form.sourceUrl.trim())) return
  const description: MediaDescription = { content: form.content.trim() }
  if (props.kind === 'meme') Object.assign(description, { text: form.text, emotion: form.emotion,
    memeMeaning: form.memeMeaning, confidence: form.confidence })
  if (form.sourceUrl.trim() && !/^https?:\/\//i.test(form.sourceUrl.trim())) {
    ElMessage.error('请输入 HTTP/HTTPS 图片地址'); return
  }
  saving.value = true
  try {
    await requestAdmin<MediaItem>(editing.value ? `/media/${editing.value.id}` : '/media', {
      method: editing.value ? 'PUT' : 'POST',
      body: JSON.stringify(editing.value
        ? { revision: editing.value.revision, sourceUrl: form.sourceUrl.trim() || null, description }
        : { kind: props.kind, fileId: form.fileId.trim() || null, sourceUrl: form.sourceUrl.trim(), description }),
    })
    ElMessage.success('描述已保存')
    dialog.value = false
    await load()
  } catch (cause) { ElMessage.error(cause instanceof Error ? cause.message : '保存失败') }
  finally { saving.value = false }
}

async function remove(item: MediaItem) {
  if (removing.value.has(item.id)) return
  try {
    await ElMessageBox.confirm('删除后将永久忽略这个文件 ID。QQ 再次收到相同文件也不会入库，此操作无法从面板恢复。',
      `删除${title.value}`, { type: 'warning', confirmButtonText: '永久删除', cancelButtonText: '取消',
        confirmButtonClass: 'el-button--danger' })
    removing.value.add(item.id)
    await requestAdmin(`/media/${item.id}`, { method: 'DELETE', body: JSON.stringify({ revision: item.revision }) })
    ElMessage.success('已删除并永久忽略该文件')
    await load()
  } catch (cause) {
    if (cause !== 'cancel' && cause !== 'close') ElMessage.error(cause instanceof Error ? cause.message : '删除失败')
  } finally { removing.value.delete(item.id) }
}

watch(() => props.kind, () => {
  page.value = 1; query.value = ''; appliedQuery.value = ''; status.value = ''
  items.value = []; dialog.value = false; preview.value = ''; load()
}, { immediate: true })
</script>

<template>
  <div class="page">
    <div class="page-heading">
      <div><p class="eyebrow">QQ / 接收媒体</p><h1>{{ title }}管理</h1><p class="muted">查看接收媒体、预览原图并修正描述。修改后的描述用于后续消息。</p></div>
      <div><el-button :icon="Refresh" :disabled="loading" @click="load">刷新</el-button><el-button type="primary" :icon="Plus" @click="openForm(null)">新增{{ title }}</el-button></div>
    </div>
    <el-card shadow="never" class="content-card">
      <div class="toolbar">
        <el-input v-model="query" placeholder="搜索描述关键词" clearable :prefix-icon="Search" @keyup.enter="search" @clear="search" />
        <el-select v-model="status" placeholder="全部识别状态" clearable @change="search">
          <el-option label="识别中" value="processing" /><el-option label="已保存描述" value="ready" /><el-option label="识别失败" value="failed" />
        </el-select>
        <el-button type="primary" @click="search">搜索</el-button>
      </div>
      <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="error-alert" />
      <el-table v-loading="loading" :data="items" :empty-text="`暂无${title}`">
        <el-table-column label="预览" width="125">
          <template #default="{ row }">
            <el-image v-if="imageUrl(row)" :key="imageUrl(row)" :src="imageUrl(row)" fit="contain"
                      class="media-thumbnail" referrerpolicy="no-referrer" @error="imageError(row)" @click="preview = imageUrl(row)" />
            <span v-else class="muted">{{ row.sourceUrl ? '预览不可用' : '暂无预览地址' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="描述 / 文件 ID" min-width="300">
          <template #default="{ row }">
            <div class="memory-text">{{ row.description?.content || '暂无描述' }}</div>
            <template v-if="kind === 'meme' && row.description">
              <div v-if="row.description.text" class="muted">文字：{{ row.description.text }}</div>
              <div v-if="row.description.emotion" class="muted">情绪：{{ row.description.emotion }}</div>
              <div v-if="row.description.memeMeaning" class="muted">含义：{{ row.description.memeMeaning }}</div>
              <div v-if="row.description.confidence != null" class="muted">置信度：{{ row.description.confidence }}</div>
            </template>
            <div class="item-id media-file-id">{{ row.fileId }}</div>
          </template>
        </el-table-column>
        <el-table-column label="识别状态" width="120"><template #default="{ row }"><el-tag :type="row.status === 'failed' ? 'danger' : row.status === 'ready' ? 'success' : 'info'">{{ recognitionLabels[row.status as keyof typeof recognitionLabels] }}</el-tag></template></el-table-column>
        <el-table-column label="更新时间" width="172"><template #default="{ row }">{{ new Date(row.updatedAt).toLocaleString('zh-CN') }}</template></el-table-column>
        <el-table-column label="操作" width="132" fixed="right"><template #default="{ row }"><el-button text type="primary" @click="openForm(row)">编辑</el-button><el-button text type="danger" :loading="removing.has(row.id)" @click="remove(row)">删除</el-button></template></el-table-column>
      </el-table>
      <div class="pagination"><el-pagination v-model:current-page="page" background layout="prev, pager, next, total" :page-size="pageSize" :total="total" @current-change="load" /></div>
    </el-card>
    <el-dialog v-model="dialog" :title="`${editing ? '编辑' : '新增'}${title}`" width="min(680px, 94vw)" :close-on-click-modal="!saving" :show-close="!saving" :close-on-press-escape="!saving">
      <el-form label-position="top" :disabled="saving" @submit.prevent="save">
        <el-form-item label="文件 ID"><el-input v-model="form.fileId" :disabled="!!editing" maxlength="2048" placeholder="可填写 OneBot image.file；留空自动生成" /></el-form-item>
        <el-form-item label="图片 URL"><el-input v-model="form.sourceUrl" maxlength="8192" placeholder="HTTP/HTTPS 图片地址" /><div class="muted">预览直接使用此地址；旧记录可先只修改描述。</div></el-form-item>
        <el-form-item label="画面描述（必填）"><el-input v-model="form.content" type="textarea" :rows="4" maxlength="2048" show-word-limit /></el-form-item>
        <template v-if="kind === 'meme'">
          <el-form-item label="图中文字"><el-input v-model="form.text" type="textarea" :rows="2" maxlength="512" show-word-limit /></el-form-item>
          <el-form-item label="情绪"><el-input v-model="form.emotion" maxlength="256" show-word-limit /></el-form-item>
          <el-form-item label="聊天含义"><el-input v-model="form.memeMeaning" type="textarea" :rows="3" maxlength="1024" show-word-limit /></el-form-item>
          <el-form-item label="置信度"><el-input-number v-model="form.confidence" :min="0" :max="1" :step="0.1" :precision="2" /></el-form-item>
        </template>
      </el-form>
      <template #footer><el-button :disabled="saving" @click="dialog = false">取消</el-button><el-button type="primary" :loading="saving" :disabled="!form.content.trim() || (!editing && !form.sourceUrl.trim())" @click="save">保存</el-button></template>
    </el-dialog>
    <el-image-viewer v-if="preview" :url-list="[preview]" :hide-on-click-modal="true" @close="preview = ''" />
  </div>
</template>

<style scoped>
.media-thumbnail { width: 92px; height: 76px; cursor: zoom-in; border-radius: 6px; background: #f5f7fa; }
.media-file-id { overflow-wrap: anywhere; }
</style>
