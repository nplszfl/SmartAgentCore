import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 120000
})

// 聊天
export const executeAgent = (data) => api.post('/agent/chat', data)

// 获取工具列表
export const getTools = () => api.get('/agent/tools')

export default api
