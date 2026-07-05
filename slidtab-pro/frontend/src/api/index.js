import client from './client'

// ===== 用户认证 =====
export const login = (phone, studentId) =>
  client.post('/user/login', { phone, studentId })

// ===== 库存查询 =====
export const searchItems = (params) =>
  client.get('/query/inventory', { params })

export const getItem = (itemId) =>
  client.get(`/query/inventory/${itemId}`)

export const getSlots = (cabinetId) =>
  client.get(`/query/slots/${cabinetId}`)

// ===== 借还流程 =====
export const reserve = (userId, itemId) =>
  client.post('/borrow/reserve', { userId, itemId })

export const pickup = (recordId, userId) =>
  client.post('/borrow/pickup', { recordId, userId })

export const getPickupStatus = (recordId) =>
  client.get(`/borrow/pickup/${recordId}/status`)

export const returnItem = (userId, itemId, remark) =>
  client.post('/borrow/return', { userId, itemId, remark }, { timeout: 120000 })

export const borrowHistory = (userId) =>
  client.get(`/borrow/history/${userId}`)

// ===== 环境 =====
export const envLatest = (deviceId) =>
  client.get('/query/env/latest', { params: { deviceId } })

export const envHistory = (deviceId, start, end) =>
  client.get('/query/env/history', { params: { deviceId, start, end } })

// ===== 告警 =====
export const listAlarms = (status) =>
  client.get('/query/alarm', { params: status ? { status } : {} })

export const handleAlarm = (id, handler, description) =>
  client.post(`/alarm/${id}/handle`, null, { params: { handler, description } })

// ===== 设备 =====
export const listDevices = () => client.get('/query/device')

export const getDevice = (deviceId) => client.get(`/query/device/${deviceId}`)

export const heartbeat = (deviceId) =>
  client.post('/control/heartbeat', null, { params: { deviceId } })

// ===== TCP 设备调试 =====
export const tcpDevices = () =>
  client.get('/control/tcp/devices')

export const tcpSendRaw = (deviceId, content) =>
  client.post('/control/tcp/send-raw', { deviceId, content })

export const tcpBroadcast = (content) =>
  client.post('/control/tcp/broadcast', { content })

export const tcpResponses = (deviceId, limit) =>
  client.get('/control/tcp/responses', { params: { deviceId, limit: limit || 50 } })

export const tcpClearResponses = (deviceId) =>
  client.delete('/control/tcp/responses', { params: { deviceId } })

export const tcpTestConnection = (deviceId) =>
  client.post('/control/tcp/test', null, { params: { deviceId } })

export const simulatePickup = (itemId) =>
  client.post('/control/tcp/simulate-pickup', null, { params: { itemId } })

export const simulatePickupStatus = (jobId) =>
  client.get('/control/tcp/simulate-pickup/status', { params: { jobId } })

// ===== 动作模板 =====
export const listActionTemplates = () =>
  client.get('/action-template')

export const createActionTemplate = (data) =>
  client.post('/action-template', data)

export const updateActionTemplate = (id, data) =>
  client.put(`/action-template/${id}`, data)

export const deleteActionTemplate = (id) =>
  client.delete(`/action-template/${id}`)

export const runActionTemplate = (id) =>
  client.post(`/action-template/${id}/run`)

// ===== 统计 =====
export const dashboard = () => client.get('/query/stats')
