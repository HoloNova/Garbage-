<template>
  <el-dialog v-model="visible" :title="title" width="680px" :close-on-click-modal="false" @closed="onClosed">
    <el-form label-position="top" class="tpl-form">
      <el-form-item label="模板名称" required>
        <el-input v-model="form.name" placeholder="如：标准取件序列" maxlength="40" show-word-limit />
      </el-form-item>
      <el-form-item label="描述">
        <el-input v-model="form.description" type="textarea" :rows="2" placeholder="模板用途说明（可选）" maxlength="200" />
      </el-form-item>
      <el-form-item label="动作序列（拖拽行可调整顺序）">
        <div class="step-list">
          <div class="step-head">
            <span class="col-idx">#</span>
            <span class="col-dev">设备</span>
            <span class="col-cmd">cmd</span>
            <span class="col-blk">阻塞</span>
            <span class="col-act">操作</span>
          </div>
          <div
            v-for="(step, idx) in form.steps"
            :key="idx"
            class="step-row"
            draggable="true"
            :class="{ dragging: dragIdx === idx }"
            @dragstart="onDragStart(idx)"
            @dragover.prevent="onDragOver(idx)"
            @drop="onDrop(idx)"
            @dragend="dragIdx = -1"
          >
            <span class="col-idx">{{ idx + 1 }}</span>
            <el-select
              v-model="step.device"
              class="col-dev"
              filterable
              allow-create
              default-first-option
              placeholder="选择或输入设备 ID"
            >
              <el-option v-for="d in deviceOptions" :key="d" :label="d" :value="d" />
            </el-select>
            <el-input-number v-model="step.cmd" class="col-cmd" :min="0" :max="99" controls-position="right" />
            <el-switch v-model="step.blocking" class="col-blk" />
            <div class="col-act">
              <el-button text type="danger" @click="removeStep(idx)" :disabled="form.steps.length <= 1">
                <AppIcon name="Delete" :size="16" />
              </el-button>
            </div>
          </div>
          <div v-if="!form.steps.length" class="empty-step">暂无步骤，点击下方按钮添加</div>
        </div>
        <el-button class="add-btn" @click="addStep" type="primary" plain>
          <AppIcon name="Add" :size="14" /> 添加步骤
        </el-button>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      <el-button type="success" :loading="running" :disabled="!form.id" @click="onRun">试运行</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  createActionTemplate, updateActionTemplate, runActionTemplate, tcpDevices
} from '@/api'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  template: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue', 'saved'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const title = computed(() => (form.id ? '编辑模板' : '新建模板'))
const form = reactive({ id: null, name: '', description: '', steps: [] })
const deviceOptions = ref([])
const dragIdx = ref(-1)
const saving = ref(false)
const running = ref(false)

watch(() => props.modelValue, async (open) => {
  if (!open) return
  await loadDevices()
  if (props.template) {
    form.id = props.template.id
    form.name = props.template.name || ''
    form.description = props.template.description || ''
    form.steps = parseSteps(props.template.sequenceJson)
  } else {
    form.id = null
    form.name = ''
    form.description = ''
    form.steps = [makeEmptyStep()]
  }
  if (!form.steps.length) form.steps = [makeEmptyStep()]
})

async function loadDevices() {
  try {
    const list = await tcpDevices() || []
    deviceOptions.value = list.map((d) => d.deviceId).filter(Boolean)
  } catch {
    deviceOptions.value = []
  }
}

function parseSteps(json) {
  if (!json) return []
  try {
    const arr = JSON.parse(json)
    if (!Array.isArray(arr)) return []
    return arr.map((s) => ({
      device: s.device || '',
      cmd: Number(s.cmd) || 0,
      blocking: s.blocking !== false
    }))
  } catch {
    return []
  }
}

function makeEmptyStep() {
  return { device: '', cmd: 0, blocking: true }
}

function addStep() {
  form.steps.push(makeEmptyStep())
}

function removeStep(idx) {
  if (form.steps.length <= 1) return
  form.steps.splice(idx, 1)
}

function onDragStart(idx) { dragIdx.value = idx }
function onDragOver(idx) { /* prevent default only */ }
function onDrop(idx) {
  if (dragIdx.value < 0 || dragIdx.value === idx) return
  const moved = form.steps.splice(dragIdx.value, 1)[0]
  form.steps.splice(idx, 0, moved)
  dragIdx.value = -1
}

function buildSequenceJson() {
  return JSON.stringify(form.steps.map((s) => ({
    device: s.device,
    cmd: Number(s.cmd),
    blocking: !!s.blocking
  })))
}

function validate() {
  if (!form.name.trim()) { ElMessage.warning('请填写模板名称'); return false }
  if (form.steps.length === 0) { ElMessage.warning('至少需要 1 个步骤'); return false }
  for (let i = 0; i < form.steps.length; i++) {
    const s = form.steps[i]
    if (!s.device || !String(s.device).trim()) {
      ElMessage.warning(`第 ${i + 1} 步未选择设备`)
      return false
    }
  }
  return true
}

async function onSave() {
  if (!validate()) return
  saving.value = true
  const payload = {
    name: form.name.trim(),
    description: form.description?.trim() || null,
    sequenceJson: buildSequenceJson()
  }
  try {
    if (form.id) {
      await updateActionTemplate(form.id, payload)
      ElMessage.success('模板已更新')
    } else {
      await createActionTemplate(payload)
      ElMessage.success('模板已创建')
    }
    emit('saved')
    visible.value = false
  } catch (e) {
    ElMessage.error('保存失败: ' + (e.message || ''))
  } finally {
    saving.value = false
  }
}

async function onRun() {
  if (!form.id) { ElMessage.warning('请先保存模板'); return }
  if (!validate()) return
  running.value = true
  try {
    // 先保存最新编辑内容，再试运行
    const payload = {
      name: form.name.trim(),
      description: form.description?.trim() || null,
      sequenceJson: buildSequenceJson()
    }
    await updateActionTemplate(form.id, payload)
    const data = await runActionTemplate(form.id)
    ElMessage.success(`已派发 ${data.steps} 步动作序列，请观察设备动作`)
    emit('saved')
  } catch (e) {
    ElMessage.error('试运行失败: ' + (e.message || ''))
  } finally {
    running.value = false
  }
}

function onClosed() {
  form.id = null
  form.name = ''
  form.description = ''
  form.steps = []
  dragIdx.value = -1
}
</script>

<style scoped>
.tpl-form :deep(.el-form-item) {
  margin-bottom: 14px;
}
.step-list {
  width: 100%;
  border: 1px solid var(--bc-border);
  border-radius: 8px;
  overflow: hidden;
}
.step-head, .step-row {
  display: grid;
  grid-template-columns: 36px 1fr 110px 70px 50px;
  gap: 8px;
  align-items: center;
  padding: 6px 10px;
  font-size: 13px;
}
.step-head {
  background: #f5f7fa;
  color: var(--bc-text-3);
  font-size: 12px;
  font-weight: 600;
}
.step-row {
  border-top: 1px solid var(--bc-border);
  cursor: grab;
  background: #fff;
  transition: background 0.15s;
}
.step-row:hover {
  background: #f9fafc;
}
.step-row.dragging {
  opacity: 0.5;
  cursor: grabbing;
}
.col-idx {
  color: var(--bc-text-3);
  font-family: monospace;
}
.col-blk {
  display: flex;
  justify-content: center;
}
.col-act {
  display: flex;
  justify-content: center;
}
.empty-step {
  padding: 16px;
  text-align: center;
  color: var(--bc-text-3);
  font-size: 13px;
}
.add-btn {
  margin-top: 10px;
  width: 100%;
  border-style: dashed;
}
</style>
