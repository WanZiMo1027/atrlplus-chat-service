export interface MemoryItem {
  id: string
  content: string
  type: string
  background?: string
  version: number
  created_at?: string
  updated_at?: string
  score?: number
}

export interface PromptView {
  memoryPromptId: string
  version: number
  content: string
  source: string
}

export interface Draft {
  content: string
  revision: number
  basePromptId: string
  baseVersion: number
  saved: boolean
}

export interface PromptHistory {
  id: number
  content: string
  remotePromptId: string
  remoteVersion: number
  status: string
  requestedAt: string
}

export interface PromptPage {
  active: PromptView | null
  draft: Draft
  history: PromptHistory[]
  remoteAvailable: boolean
}

export interface GroupReplyConfig {
  groupId: number
  groupName: string
  enabled: boolean
  receiveEnabled: boolean
}

export type MediaKind = 'image' | 'meme'
export interface MediaDescription {
  content: string
  text?: string
  emotion?: string
  memeMeaning?: string
  confidence?: number
}
export interface MediaItem {
  id: string
  fileId: string
  kind: MediaKind
  status: 'processing' | 'ready' | 'failed'
  description: MediaDescription | null
  sourceUrl: string | null
  revision: number
  createdAt: string
  updatedAt: string
}
export interface MediaPage { items: MediaItem[]; total: number }

let csrfToken = ''

async function adminFetch(url: string, init?: RequestInit): Promise<Response> {
  try {
    return await fetch(url, { credentials: 'same-origin', ...init })
  } catch {
    throw new Error('无法连接管理 API，请确认 chat-service 已启动')
  }
}

function unavailableMessage(response: Response): string {
  if (response.status === 403) return '管理功能未启用：请设置 ADMIN_ENABLED=true 并重启 chat-service'
  if (import.meta.env.DEV && response.status >= 500) {
    return 'Vite 无法连接管理 API，请确认 chat-service 已在 127.0.0.1:8080 启动'
  }
  return `请求失败 (${response.status})`
}

export async function loadCsrf(): Promise<string> {
  const response = await adminFetch('/api/admin/csrf')
  if (!response.ok) throw new Error(unavailableMessage(response))
  csrfToken = (await response.json()).token
  return csrfToken
}

export async function requestAdmin<T>(path: string, init: RequestInit = {}): Promise<T> {
  const method = init.method?.toUpperCase() ?? 'GET'
  if (method !== 'GET' && !csrfToken) await loadCsrf()
  const response = await adminFetch(`/api/admin${path}`, {
    ...init,
    headers: {
      ...(init.body ? { 'Content-Type': 'application/json' } : {}),
      ...(method !== 'GET' ? { 'X-XSRF-TOKEN': csrfToken } : {}),
      ...(init.headers ?? {}),
    },
  })
  if (response.redirected && response.url.includes('/admin/login')) {
    window.location.href = '/admin/login'
    throw new Error('登录已过期')
  }
  const body = await response.json().catch(() => null)
  if (!response.ok) {
    if (response.status === 403 && method !== 'GET') {
      throw new Error('操作被拒绝，请刷新页面并重新登录后重试')
    }
    throw new Error(response.status === 403 ? unavailableMessage(response)
      : body?.message || unavailableMessage(response))
  }
  return body as T
}

export function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  return requestAdmin<T>(`/tencentdb/l1${path}`, init)
}

export interface HistoryConversation { conversationId: string; type: 'group' | 'private' | 'other'; targetId: string; selfId: string | null }
export interface HistoryConversationPage { items: HistoryConversation[]; nextCursor: string }
export interface HistoryMessage {
  eventId: string; senderName: string; senderId?: string | null; text: string | null
  images: { file: string | null; summary: string | null; url: string | null }[]
}
export interface HistoryDecision { shouldReply: boolean; replies: string[]; replyToEventId: string | null; meme: string | null }
export interface HistoryBatch { sequence: number; messages: HistoryMessage[]; decision: HistoryDecision | null; completed: boolean; compressed: boolean }
export interface HistoryMessagePage {
  conversationId: string; revision: number; nextSequence: number; summary: string; summaryVersion: number
  lastCompressedSequence: number; matchingBatches: number; batches: HistoryBatch[]; nextBeforeSequence: number | null
}


export interface StyleSource {
  sequence: number; eventId: string; senderId: string | null; senderName: string | null
  timestamp: number | null; text: string; background: string; human: boolean; eligible: boolean
}
export interface StyleFragment {
  fingerprint: string; scene: string; intent: string; emotion: string; seriousness: string; interaction: string
  context: StyleSource[]; replies: StyleSource[]
}
export interface StyleFragmentView {
  id: number; conversationId: string; throughSequence: number; fragment: StyleFragment; embeddingStatus: string
}
export interface StyleFragmentPage { items: StyleFragmentView[]; nextAfterId: number | null; version: string; total: number }
export interface StyleVersionOption { version: string; fragmentCount: number; current: boolean }
export interface StyleConversationCatalog { conversationId: string; groupId: string; selfId: string; versions: StyleVersionOption[] }
export interface StyleCatalog { currentVersion: string; conversations: StyleConversationCatalog[]; intents: string[]; seriousnessValues: string[] }
