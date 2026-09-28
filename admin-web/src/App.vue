<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ChatDotRound, DataAnalysis, Document, Expand, Fold, Key, SwitchButton } from '@element-plus/icons-vue'
import { loadCsrf } from './api'

const route = useRoute()
const router = useRouter()
const collapsed = ref(false)
const token = ref('')
const isLogin = computed(() => route.path === '/login')
const activeMenu = computed(() => route.path)
const breadcrumb = computed(() => route.path === '/group-reply-config'
  ? '回复控制'
  : `TencentDB / ${route.path.endsWith('prompts') ? '提示词页' : '记忆页'}`)

async function logout() {
  try {
    token.value = await loadCsrf()
    const form = document.createElement('form')
    form.method = 'POST'
    form.action = '/api/admin/logout'
    const input = document.createElement('input')
    input.name = '_csrf'
    input.value = token.value
    form.appendChild(input)
    document.body.appendChild(form)
    form.submit()
  } catch {
    window.location.href = '/admin/login'
  }
}
</script>

<template>
  <router-view v-if="isLogin" />
  <el-container v-else class="shell">
    <el-aside :width="collapsed ? '70px' : '236px'" class="sidebar">
      <div class="brand" :class="{ compact: collapsed }">
        <span class="brand-mark">A</span>
        <span v-if="!collapsed" class="brand-name">ATRLPLUS <small>控制面板</small></span>
      </div>
      <el-menu :default-active="activeMenu" :collapse="collapsed" :collapse-transition="false"
               class="side-menu" :default-openeds="['tencentdb']" @select="(path: string) => router.push(path)">
        <el-sub-menu index="tencentdb">
          <template #title><el-icon><DataAnalysis /></el-icon><span>TencentDB</span></template>
          <el-menu-item index="/tencentdb/l1/memories"><el-icon><Document /></el-icon><span>记忆页</span></el-menu-item>
          <el-menu-item index="/tencentdb/l1/prompts"><el-icon><Key /></el-icon><span>提示词页</span></el-menu-item>
        </el-sub-menu>
        <el-menu-item index="/group-reply-config"><el-icon><ChatDotRound /></el-icon><span>回复控制</span></el-menu-item>
      </el-menu>
      <div class="sidebar-foot"><span v-if="!collapsed">L1 管理 · 内网模式</span></div>
    </el-aside>
    <el-container>
      <el-header class="topbar">
        <el-button text circle @click="collapsed = !collapsed"><el-icon :size="18"><Expand v-if="collapsed" /><Fold v-else /></el-icon></el-button>
        <span class="breadcrumb">{{ breadcrumb }}</span>
        <div class="top-actions"><span class="admin-badge">管理员</span><el-button text @click="logout"><el-icon><SwitchButton /></el-icon>退出</el-button></div>
      </el-header>
      <el-main class="main"><router-view /></el-main>
    </el-container>
  </el-container>
</template>
