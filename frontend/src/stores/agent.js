import { defineStore } from 'pinia'
import { executeAgent, getTools } from '../api'

export const useAgentStore = defineStore('agent', {
  state: () => ({
    messages: [],
    isLoading: false,
    tools: [],
    currentToolCalls: [],
    config: {
      agentName: 'assistant',
      systemPrompt: '你是一个有用的AI助手，可以帮助用户完成各种任务。',
      maxIterations: 10,
      tools: []
    }
  }),

  actions: {
    async loadTools() {
      try {
        const res = await getTools()
        this.tools = res.data
      } catch (e) {
        console.error('加载工具失败', e)
      }
    },

    async sendMessage(input, imageFile = null) {
      // 添加用户消息
      let displayContent = input
      if (imageFile) {
        displayContent = input + ' [📷 图片已上传]'
      }
      this.messages.push({
        role: 'user',
        content: displayContent,
        timestamp: Date.now()
      })

      // 添加助手消息占位
      const assistantMsg = {
        role: 'assistant',
        content: '',
        timestamp: Date.now(),
        loading: true,
        toolCalls: []
      }
      this.messages.push(assistantMsg)

      this.isLoading = true
      this.currentToolCalls = []

      try {
        const res = await executeAgent({
          input,
          agentName: this.config.agentName,
          systemPrompt: this.config.systemPrompt,
          maxIterations: this.config.maxIterations,
          tools: this.config.tools,
          hasImage: !!imageFile
        })

        // 更新助手消息
        assistantMsg.content = res.data.content || res.data.error || '处理完成'
        assistantMsg.loading = false
        assistantMsg.iterations = res.data.iterations
        assistantMsg.usage = res.data.usage

      } catch (e) {
        assistantMsg.content = '请求失败: ' + (e.message || '未知错误')
        assistantMsg.loading = false
      } finally {
        this.isLoading = false
      }
    },

    clearMessages() {
      this.messages = []
    }
  }
})
