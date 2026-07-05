<template>
  <component
    v-if="comp"
    :is="comp"
    :theme="theme"
    :size="size"
    :spin="spin"
    :fill="fill"
    :stroke-width="strokeWidth"
    v-bind="$attrs"
  />
</template>

<script setup>
import { computed } from 'vue'
import {
  Home, Search, Book, BookOpen, Box, Monitor, User, Remind, Time, Setting,
  Seat, Return, Logout, Refresh, Camera, Scan, Iphone, ArrowLeft, ArrowRight,
  Plus, Close, Check, Caution, Info, Success, Computer, Down, Up, More, List,
  Send, People, Tag, Folder, Report, Thermometer, Water, Wind, Fog, Sound, Light, Scale
} from '@icon-park/vue-next'

defineOptions({ name: 'AppIcon', inheritAttrs: false })

const props = defineProps({
  name: { type: String, required: true },
  size: { type: [Number, String], default: '1em' },
  spin: { type: Boolean, default: false },
  theme: { type: String, default: 'outline' },
  fill: { type: [String, Array], default: undefined },
  strokeWidth: { type: [Number, String], default: undefined }
})

const registry = {
  Home, Search, Book, BookOpen, Box, Monitor, User, Remind, Time, Setting,
  Seat, Return, Logout, Refresh, Camera, Scan, Iphone, ArrowLeft, ArrowRight,
  Plus, Close, Check, Caution, Info, Success, Computer, Down, Up, More, List,
  Send, People, Tag, Folder, Report, Thermometer, Water, Wind, Fog, Sound, Light, Scale
}

const comp = computed(() => registry[props.name] ?? null)
</script>
