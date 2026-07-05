import axios from 'axios'
import { ElMessage } from 'element-plus'

const client = axios.create({
  baseURL: '/api',
  timeout: 10000
})

client.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 0) {
        return body.data
      }
      if (body.code === 1003) {
        return Promise.reject(new Error(body.message || '设备离线'))
      }
      ElMessage.error(body.message || `请求失败 (${body.code})`)
      return Promise.reject(new Error(body.message || `code=${body.code}`))
    }
    return body
  },
  (error) => {
    const msg = error.response?.data?.message || error.message || '网络异常'
    ElMessage.error(msg)
    return Promise.reject(error)
  }
)

export default client
