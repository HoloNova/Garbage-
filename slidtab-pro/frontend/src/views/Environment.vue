<template>
  <div class="page-container">
    <h2 class="page-title">环境监测</h2>

    <el-card shadow="never" class="filter-card">
      <div class="filter-row">
        <el-select v-model="deviceId" @change="loadAll" class="device-select" placeholder="选择传感器设备">
          <el-option v-for="d in devices" :key="d.deviceId" :label="`${d.deviceId}`" :value="d.deviceId" />
        </el-select>
        <el-button class="refresh-btn" @click="loadAll">
          <AppIcon name="Refresh" :size="16" :spin="loading" />
        </el-button>
      </div>
    </el-card>

    <el-empty v-if="!latest" description="暂无环境数据，请确认传感器设备已连接并上报" :image-size="80" />
    <div v-else class="metric-grid">
      <div v-for="m in metrics" :key="m.key" class="metric-item" :class="{ warn: isWarn(m) }">
        <div class="metric-icon" :style="{ background: m.color }">
          <AppIcon :name="m.icon" :size="12" theme="outline" :fill="'#fff'" />
        </div>
        <div class="metric-info">
          <div class="metric-value">{{ latest[m.key] ?? '-' }}<span class="metric-unit">{{ m.unit }}</span></div>
          <div class="metric-label">{{ m.label }}<span class="metric-threshold">阈值 {{ m.threshold }}</span></div>
        </div>
      </div>
    </div>

    <el-card shadow="never" class="chart-card">
      <div class="section-head">
        <AppIcon name="Report" :size="18" />
        <span>历史趋势（最近 24 小时）</span>
      </div>
      <div ref="chartRef" class="chart"></div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import { listDevices, envLatest, envHistory } from '@/api'

const devices = ref([])
const deviceId = ref('')
const latest = ref(null)
const loading = ref(false)
const chartRef = ref(null)
let chart = null
let latestTimer = null
let historyTimer = null

const metrics = [
  { key: 'temperature', label: '温度', unit: '°C', icon: 'Thermometer', threshold: '≤35', color: '#f56c6c' },
  { key: 'humidity', label: '湿度', unit: '%', icon: 'Water', threshold: '30~80', color: '#409eff' },
  { key: 'light', label: '光照', unit: 'lux', icon: 'Light', threshold: '≤5000', color: '#ffb300' },
  { key: 'weight', label: '称重', unit: 'g', icon: 'Scale', threshold: '≤50000', color: '#9c27b0' },
  { key: 'smoke', label: '烟雾', unit: '', icon: 'Fog', threshold: '≤500', color: '#909399' }
]

const thresholds = { temperature: 35, light: 5000, weight: 50000, smoke: 500 }
function isWarn(m) {
  if (!latest.value) return false
  const v = latest.value[m.key]
  if (v == null) return false
  if (m.key === 'humidity') return v > 80 || v < 30
  return v > thresholds[m.key]
}

async function loadDevices() {
  try {
    const data = await listDevices() || []
    const sensors = data.filter((d) =>
      (d.nodeType && d.nodeType === 'SENSOR') ||
      (d.deviceId && d.deviceId.toLowerCase().includes('sensor'))
    )
    devices.value = sensors
    if ((!deviceId.value || !devices.value.find(d => d.deviceId === deviceId.value)) && devices.value.length) {
      deviceId.value = devices.value[0].deviceId
    }
  } catch (e) {
    ElMessage.warning('加载设备列表失败: ' + (e.message || ''))
  }
}

async function loadLatest() {
  if (!deviceId.value) return
  loading.value = true
  try {
    latest.value = await envLatest(deviceId.value)
  } catch (e) {
    ElMessage.warning('加载最新环境数据失败: ' + (e.message || ''))
  } finally {
    loading.value = false
  }
}

// 后端 LocalDateTime 不接受 'Z' 后缀，需要本地时间无时区字符串
function toLocalISO(date) {
  const pad = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` +
    `T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

async function loadHistory() {
  if (!deviceId.value) return
  const end = new Date()
  const start = new Date(end.getTime() - 24 * 60 * 60 * 1000)
  try {
    const data = await envHistory(deviceId.value, toLocalISO(start), toLocalISO(end)) || []
    renderChart(bucketize(data))
  } catch (e) {
    ElMessage.warning('加载历史趋势失败: ' + (e.message || ''))
    renderChart([])
  }
}

/**
 * 动态分桶：根据数据时间跨度选择桶大小，桶内取均值。
 * - ≤2h：1 分钟桶
 * - ≤24h：1 小时桶
 * - >24h：3 小时桶
 */
function bucketize(data) {
  if (!data.length) return []
  const times = data.map(d => new Date(d.recordedAt).getTime())
  const spanMs = Math.max(1, times[times.length - 1] - times[0])
  const HOUR = 60 * 60 * 1000
  let bucketMs
  if (spanMs <= 2 * HOUR) bucketMs = 60 * 1000
  else if (spanMs <= 24 * HOUR) bucketMs = HOUR
  else bucketMs = 3 * HOUR

  const buckets = new Map()
  for (const d of data) {
    const key = Math.floor(new Date(d.recordedAt).getTime() / bucketMs) * bucketMs
    if (!buckets.has(key)) buckets.set(key, [])
    buckets.get(key).push(d)
  }
  const fields = ['temperature', 'humidity', 'light', 'weight', 'smoke']
  return Array.from(buckets.entries()).map(([key, items]) => {
    const out = { recordedAt: new Date(key).toISOString() }
    for (const f of fields) {
      const vals = items.map(x => x[f]).filter(v => v != null)
      out[f] = vals.length ? vals.reduce((s, v) => s + v, 0) / vals.length : null
    }
    return out
  })
}

function initChart() {
  if (!chart && chartRef.value) {
    chart = echarts.init(chartRef.value)
  }
}

function renderChart(data) {
  initChart()
  if (!chart) return
  const spanMs = data.length >= 2
    ? new Date(data[data.length - 1].recordedAt).getTime() - new Date(data[0].recordedAt).getTime()
    : 0
  const timeFmt = spanMs <= 2 * 60 * 60 * 1000
    ? { hour: '2-digit', minute: '2-digit' }
    : { month: '2-digit', day: '2-digit', hour: '2-digit' }
  const times = data.map((d) => new Date(d.recordedAt).toLocaleString('zh-CN', timeFmt))
  const series = [
    { name: '温度', type: 'line', smooth: true, yAxisIndex: 0, data: data.map((d) => d.temperature) },
    { name: '湿度', type: 'line', smooth: true, yAxisIndex: 0, data: data.map((d) => d.humidity) },
    { name: '烟雾', type: 'line', smooth: true, yAxisIndex: 0, data: data.map((d) => d.smoke) },
    { name: '光照', type: 'line', smooth: true, yAxisIndex: 1, data: data.map((d) => d.light) },
    { name: '称重', type: 'line', smooth: true, yAxisIndex: 1, data: data.map((d) => d.weight) }
  ]
  chart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: series.map((s) => s.name), top: 0, type: 'scroll' },
    grid: { left: 48, right: 56, top: 44, bottom: 28 },
    xAxis: { type: 'category', data: times, boundaryGap: false, axisLabel: { fontSize: 10 } },
    yAxis: [
      { type: 'value', name: '°C / % / ppm', nameLocation: 'middle', nameGap: 36, axisLabel: { fontSize: 10 } },
      { type: 'value', name: 'lux / g', nameLocation: 'middle', nameGap: 36, axisLabel: { fontSize: 10 }, position: 'right' }
    ],
    series
  }, true)
}

async function loadAll() {
  await Promise.all([loadLatest(), loadHistory()])
}

function resize() {
  initChart()
  chart && chart.resize()
}

onMounted(async () => {
  await loadDevices()
  await nextTick()
  initChart()
  if (chart) chart.resize()
  window.addEventListener('resize', resize)
  await loadAll()
  latestTimer = setInterval(loadLatest, 10000)
  historyTimer = setInterval(loadHistory, 30000)
})

onUnmounted(() => {
  window.removeEventListener('resize', resize)
  if (latestTimer) clearInterval(latestTimer)
  if (historyTimer) clearInterval(historyTimer)
  if (chart) chart.dispose()
})
</script>

<style scoped>
.filter-card {
  margin-bottom: 12px;
}
.filter-row {
  display: flex;
  gap: 8px;
}
.device-select {
  flex: 1;
}
.refresh-btn {
  flex-shrink: 0;
  width: 40px;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}
.metric-grid {
  display: grid;
  grid-template-columns: repeat(3, auto);
  gap: 8px;
  margin-bottom: 12px;
  justify-content: center;
}
@media (max-width: 700px) {
  .metric-grid {
    grid-template-columns: repeat(2, auto);
  }
}
@media (max-width: 480px) {
  .metric-grid {
    grid-template-columns: repeat(2, 1fr);
    justify-content: stretch;
  }
  .metric-item {
    min-width: 0;
    padding: 12px 12px;
  }
}
.metric-item {
  background: #fff;
  border-radius: 8px;
  padding: 14px 16px;
  display: flex;
  align-items: center;
  gap: 10px;
  border: 1px solid var(--bc-border);
  min-width: 160px;
}
.metric-item.warn {
  border-color: #f56c6c;
  background: #fef0f0;
}
.metric-icon {
  width: 22px;
  height: 22px;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.metric-info {
  flex: 1;
  min-width: 0;
}
.metric-value {
  font-size: 13px;
  font-weight: 700;
  color: var(--bc-text);
  line-height: 1.2;
}
.metric-item.warn .metric-value {
  color: #f56c6c;
}
.metric-unit {
  font-size: 10px;
  color: var(--bc-text-3);
  margin-left: 2px;
  font-weight: 400;
}
.metric-label {
  font-size: 11px;
  color: var(--bc-text-3);
  margin-top: 1px;
  display: flex;
  justify-content: space-between;
  gap: 6px;
}
.metric-threshold {
  font-size: 10px;
  color: var(--bc-text-4);
}
.chart-card .section-head {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  font-size: 14px;
  color: var(--bc-text);
  margin-bottom: 8px;
}
.chart {
  width: 100%;
  height: 240px;
}
</style>
