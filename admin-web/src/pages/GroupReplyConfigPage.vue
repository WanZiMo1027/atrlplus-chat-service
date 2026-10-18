<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { requestAdmin, type GroupReplyConfig } from '../api'

const props = defineProps<{ mode: 'receive' | 'reply' }>()
const field = computed(() => props.mode === 'receive' ? 'receiveEnabled' : 'enabled')
const title = computed(() => props.mode === 'receive' ? '消息接收' : 'AI 回复')
const description = computed(() => props.mode === 'receive'
  ? '关闭后不再将该群消息送到 Agent，待处理消息会被丢弃。'
  : '关闭后仍接收并保存聊天记录，Agent 不生成回复。只有消息接收开启时才生效。')

const groups = ref<GroupReplyConfig[]>([])
const loading = ref(false)
const error = ref('')
const pending = ref(new Set<number>())
let loadVersion = 0

async function load() {
  const version = ++loadVersion
  loading.value = true
  error.value = ''
  try {
    const result = await requestAdmin<GroupReplyConfig[]>('/group-reply-config/list')
    if (version === loadVersion) groups.value = result
  } catch (cause) {
    if (version === loadVersion) error.value = cause instanceof Error ? cause.message : '加载失败'
  } finally {
    if (version === loadVersion) loading.value = false
  }
}

async function change(row: GroupReplyConfig, enabled: boolean) {
  if (pending.value.has(row.groupId)) return
  // 请求开始时固定目标字段，切换子菜单不会把未完成的修改写到另一个开关。
  const targetField = field.value
  const mode = props.mode
  const previous = row[targetField]
  row[targetField] = enabled
  pending.value.add(row.groupId)
  try {
    const endpoint = mode === 'receive' ? 'receive-status' : 'status'
    const updated = await requestAdmin<GroupReplyConfig>(`/group-reply-config/${endpoint}`, {
      method: 'PUT', body: JSON.stringify({ groupId: row.groupId, enabled }),
    })
    row[targetField] = updated[targetField]
    ElMessage.success(mode === 'receive' ? '接收状态已更新' : '回复状态已更新')
  } catch (cause) {
    row[targetField] = previous
    ElMessage.error(cause instanceof Error ? cause.message : '修改失败')
  } finally {
    pending.value.delete(row.groupId)
  }
}

watch(() => props.mode, () => { groups.value = []; load() }, { immediate: true })
</script>

<template>
  <div class="page">
    <div class="page-heading">
      <div><p class="eyebrow">QQ / 回复控制</p><h1>{{ title }}</h1><p class="muted">{{ description }}</p></div>
      <el-button :icon="Refresh" :disabled="loading || pending.size > 0" @click="load">刷新</el-button>
    </div>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="error-alert" />
    <el-card shadow="never" class="content-card">
      <el-table v-loading="loading" :data="groups" empty-text="暂无已记录的 QQ 群">
        <el-table-column prop="groupId" label="群号" min-width="180" />
        <el-table-column prop="groupName" label="群名称" min-width="220" />
        <el-table-column :label="`${title}状态`" width="150">
          <template #default="{ row }"><el-tag :type="row[field] ? 'success' : 'info'" effect="plain">{{ row[field] ? '开启' : '关闭' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="开关" width="120">
          <template #default="{ row }">
            <el-switch :model-value="row[field]" :loading="pending.has(row.groupId)"
                       :disabled="pending.has(row.groupId)" @change="change(row, $event as boolean)" />
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>
