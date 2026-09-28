<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { requestAdmin, type GroupReplyConfig } from '../api'

const groups = ref<GroupReplyConfig[]>([])
const loading = ref(false)
const error = ref('')
const pending = ref(new Set<number>())

async function load() {
  loading.value = true
  error.value = ''
  try {
    groups.value = await requestAdmin<GroupReplyConfig[]>('/group-reply-config/list')
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '加载失败'
  } finally {
    loading.value = false
  }
}

async function change(row: GroupReplyConfig, enabled: boolean) {
  if (pending.value.has(row.groupId)) return
  const previous = row.enabled
  row.enabled = enabled
  pending.value.add(row.groupId)
  try {
    const updated = await requestAdmin<GroupReplyConfig>('/group-reply-config/status', {
      method: 'PUT', body: JSON.stringify({ groupId: row.groupId, enabled }),
    })
    row.enabled = updated.enabled
    ElMessage.success('回复状态已更新')
  } catch (cause) {
    row.enabled = previous
    ElMessage.error(cause instanceof Error ? cause.message : '修改失败')
  } finally {
    pending.value.delete(row.groupId)
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-heading">
      <div><p class="eyebrow">QQ / AI 回复</p><h1>回复控制</h1><p class="muted">控制已记录 QQ 群是否允许 AI 回复。</p></div>
      <el-button :icon="Refresh" :disabled="loading" @click="load">刷新</el-button>
    </div>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="error-alert" />
    <el-card shadow="never" class="content-card">
      <el-table v-loading="loading" :data="groups" empty-text="暂无已记录的 QQ 群">
        <el-table-column prop="groupId" label="群号" min-width="180" />
        <el-table-column prop="groupName" label="群名称" min-width="220" />
        <el-table-column label="AI 回复状态" width="150">
          <template #default="{ row }"><el-tag :type="row.enabled ? 'success' : 'info'" effect="plain">{{ row.enabled ? '开启' : '关闭' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="开关" width="120">
          <template #default="{ row }">
            <el-switch :model-value="row.enabled" :loading="pending.has(row.groupId)"
                       :disabled="pending.has(row.groupId)" @change="change(row, $event as boolean)" />
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>
