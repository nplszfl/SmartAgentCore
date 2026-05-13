<template>
  <div class="chat-container">
    <!-- 侧边栏 -->
    <aside class="sidebar">
      <div class="sidebar-header">
        <h2>🤖 SmartAgent</h2>
        <el-button type="primary" @click="clearChat" size="small">新对话</el-button>
      </div>

      <div class="tools-section">
        <h3>🔧 工具</h3>
        <div class="tool-list">
          <el-checkbox 
            v-for="tool in tools" 
            :key="tool.name" 
            v-model="selectedTools"
            :label="tool.name"
            :value="tool.name"
          >
            {{ tool.name }}
          </el-checkbox>
        </div>
      </div>

      <div class="config-section">
        <h3>⚙️ 配置</h3>
        <el-input
          v-model="agentName"
          placeholder="Agent名称"
          size="small"
        />
        <el-input
          v-model="systemPrompt"
          type="textarea"
          :rows="3"
          placeholder="系统提示词"
          size="small"
        />
      </div>
    </aside>

    <!-- 主聊天区域 -->
    <main class="chat-main">
      <div class="chat-header">
        <h1>{{ agentName || '智能助手' }}</h1>
        <span class="iteration-info" v-if="lastIterations">
          迭代次数: {{ lastIterations }}
        </span>
      </div>

      <div class="messages" ref="messagesRef">
        <div v-if="messages.length === 0" class="empty-state">
          <div class="empty-icon">💬</div>
          <p>开始对话吧！</p>
          <p class="tip">尝试说："帮我计算 2+2" 或 "今天星期几？"</p>
        </div>

        <div
          v-for="(msg, index) in messages"
          :key="index"
          :class="['message', msg.role]"
        >
          <div class="message-avatar">
            {{ msg.role === 'user' ? '👤' : '🤖' }}
          </div>
          <div class="message-content">
            <div class="message-text" v-html="renderMarkdown(msg.content)"></div>
            <div v-if="msg.loading" class="loading-dots">
              <span></span><span></span><span></span>
            </div>
            <div v-if="msg.iterations" class="message-meta">
              迭代: {{ msg.iterations }}次
            </div>
          </div>
        </div>
      </div>

      <!-- 输入区域 -->
      <div class="input-area">
        <el-input
          v-model="inputText"
          type="textarea"
          :rows="2"
          placeholder="输入消息... (Ctrl+Enter 发送)"
          @keydown.enter.ctrl="sendMessage"
          :disabled="isLoading"
        />
        <div class="input-actions">
          <el-upload
            ref="uploadRef"
            :auto-upload="false"
            :show-file-list="false"
            accept="image/*"
            @change="handleImageChange"
          >
            <el-button :disabled="isLoading">📷 图片</el-button>
          </el-upload>
          <el-button 
            type="primary" 
            @click="sendMessage" 
            :loading="isLoading"
            :disabled="!inputText.trim() && !selectedImage"
          >
            发送
          </el-button>
        </div>
        <div v-if="selectedImage" class="image-preview">
          <img :src="imagePreviewUrl" alt="preview" />
          <el-button size="small" @click="removeImage">✕</el-button>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick, watch } from 'vue'
import { useAgentStore } from '../stores/agent'
import MarkdownIt from 'markdown-it'

const md = new MarkdownIt()
const agentStore = useAgentStore()

const inputText = ref('')
const messagesRef = ref(null)
const agentName = ref('assistant')
const systemPrompt = ref('你是一个有用的AI助手，可以帮助用户完成各种任务。')
const selectedTools = ref(['search', 'calculator', 'datetime', 'ocr'])
const uploadRef = ref(null)
const selectedImage = ref(null)
const imagePreviewUrl = ref('')

const messages = computed(() => agentStore.messages)
const isLoading = computed(() => agentStore.isLoading)
const tools = computed(() => agentStore.tools)
const lastIterations = computed(() => {
  const assistantMsgs = messages.value.filter(m => m.role === 'assistant' && m.iterations)
  return assistantMsgs.length > 0 ? assistantMsgs[assistantMsgs.length - 1].iterations : null
})

onMounted(async () => {
  await agentStore.loadTools()
})

const renderMarkdown = (text) => {
  if (!text) return ''
  return md.render(text)
}

const sendMessage = async () => {
  const text = inputText.value.trim()
  if (!text && !selectedImage.value || isLoading.value) return

  // 更新配置
  agentStore.config.agentName = agentName.value
  agentStore.config.systemPrompt = systemPrompt.value
  agentStore.config.tools = selectedTools.value

  const messageText = text
  inputText.value = ''
  await agentStore.sendMessage(messageText, selectedImage.value)
  removeImage()
  scrollToBottom()
}

const handleImageChange = (uploadFile) => {
  const file = uploadFile.raw
  if (file) {
    selectedImage.value = file
    imagePreviewUrl.value = URL.createObjectURL(file)
  }
}

const removeImage = () => {
  selectedImage.value = null
  imagePreviewUrl.value = ''
  if (uploadRef.value) {
    uploadRef.value.clearFiles()
  }
}

const clearChat = () => {
  agentStore.clearChat()
}

const scrollToBottom = () => {
  nextTick(() => {
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
  })
}

watch(messages, scrollToBottom, { deep: true })
</script>

<style scoped>
.chat-container {
  display: flex;
  height: 100vh;
}

.sidebar {
  width: 280px;
  background: #161b22;
  border-right: 1px solid #30363d;
  padding: 20px;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
}

.sidebar-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.sidebar-header h2 {
  font-size: 18px;
  color: #58a6ff;
}

.tools-section,
.config-section {
  margin-bottom: 20px;
}

.tools-section h3,
.config-section h3 {
  font-size: 14px;
  color: #8b949e;
  margin-bottom: 10px;
}

.tool-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.config-section .el-input,
.config-section .el-textarea {
  margin-bottom: 10px;
}

.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #0d1117;
}

.chat-header {
  padding: 20px;
  border-bottom: 1px solid #30363d;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.chat-header h1 {
  font-size: 20px;
  color: #c9d1d9;
}

.iteration-info {
  font-size: 12px;
  color: #8b949e;
  background: #21262d;
  padding: 4px 8px;
  border-radius: 4px;
}

.messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
}

.empty-state {
  text-align: center;
  padding: 100px 20px;
  color: #8b949e;
}

.empty-icon {
  font-size: 64px;
  margin-bottom: 20px;
}

.tip {
  font-size: 14px;
  margin-top: 10px;
  color: #6e7681;
}

.message {
  display: flex;
  margin-bottom: 20px;
}

.message.user {
  flex-direction: row-reverse;
}

.message-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: #21262d;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  flex-shrink: 0;
}

.message.user .message-avatar {
  background: #238636;
}

.message.assistant .message-avatar {
  background: #1f6feb;
}

.message-content {
  max-width: 70%;
  margin: 0 12px;
}

.message.user .message-content {
  text-align: right;
}

.message-text {
  background: #21262d;
  padding: 12px 16px;
  border-radius: 12px;
  line-height: 1.6;
  word-break: break-word;
}

.message.user .message-text {
  background: #238636;
}

.message-text code {
  background: #161b22;
  padding: 2px 6px;
  border-radius: 4px;
  font-family: 'Fira Code', monospace;
}

.loading-dots {
  display: inline-flex;
  gap: 4px;
  margin-left: 12px;
}

.loading-dots span {
  width: 8px;
  height: 8px;
  background: #58a6ff;
  border-radius: 50%;
  animation: bounce 1.4s infinite ease-in-out both;
}

.loading-dots span:nth-child(1) { animation-delay: -0.32s; }
.loading-dots span:nth-child(2) { animation-delay: -0.16s; }

@keyframes bounce {
  0%, 80%, 100% { transform: scale(0); }
  40% { transform: scale(1); }
}

.message-meta {
  font-size: 12px;
  color: #6e7681;
  margin-top: 4px;
}

.input-area {
  padding: 20px;
  border-top: 1px solid #30363d;
  display: flex;
  flex-direction: column;
  gap: 12px;
  align-items: flex-end;
}

.input-actions {
  display: flex;
  gap: 12px;
  width: 100%;
  justify-content: flex-end;
}

.image-preview {
  position: relative;
  display: inline-block;
}

.image-preview img {
  max-width: 200px;
  max-height: 150px;
  border-radius: 8px;
  border: 2px solid #30363d;
}

.image-preview .el-button {
  position: absolute;
  top: -8px;
  right: -8px;
  padding: 4px;
  min-width: 20px;
}

.input-area .el-textarea {
  flex: 1;
}

.input-area .el-button {
  height: 66px;
}
</style>
