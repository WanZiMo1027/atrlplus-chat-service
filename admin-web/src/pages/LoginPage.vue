<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { loadCsrf } from '../api'

const csrf = ref('')
const password = ref('')
const error = ref(new URLSearchParams(window.location.search).has('error') ? '密码错误，请重试' : '')
onMounted(async () => {
  try { csrf.value = await loadCsrf() }
  catch (cause) { error.value = cause instanceof Error ? cause.message : '无法获取登录令牌' }
})
</script>

<template>
  <div class="login-wrap">
    <div class="login-card">
      <div class="login-logo">A</div>
      <p class="eyebrow">ATRLPLUS · ADMIN</p>
      <h1>欢迎回来</h1>
      <p class="muted">登录后管理 TencentDB L1 记忆与抽取规则。</p>
      <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="login-error" />
      <form action="/api/admin/login" method="post" class="login-form">
        <input type="hidden" name="_csrf" :value="csrf" />
        <input type="hidden" name="username" value="admin" />
        <label>管理员密码</label>
        <el-input v-model="password" name="password" type="password" show-password size="large" placeholder="输入管理员密码" autocomplete="current-password" />
        <el-button native-type="submit" type="primary" size="large" :disabled="!csrf">进入控制面板</el-button>
      </form>
    </div>
  </div>
</template>
