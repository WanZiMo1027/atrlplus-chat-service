import { createRouter, createWebHistory } from 'vue-router'
import LoginPage from './pages/LoginPage.vue'
import MemoriesPage from './pages/MemoriesPage.vue'
import PromptPage from './pages/PromptPage.vue'
import GroupReplyConfigPage from './pages/GroupReplyConfigPage.vue'

export const router = createRouter({
  history: createWebHistory('/admin/'),
  routes: [
    { path: '/', redirect: '/tencentdb/l1/memories' },
    { path: '/login', component: LoginPage },
    { path: '/tencentdb/l1/memories', component: MemoriesPage },
    { path: '/tencentdb/l1/prompts', component: PromptPage },
    { path: '/group-reply-config', component: GroupReplyConfigPage },
  ],
})
