<script setup lang="ts">
import { CODE_GEN_TYPE_OPTIONS } from '@/utils/codeGenTypes'

type SelectorMode = 'radio' | 'select'
type SelectorSize = 'small' | 'middle' | 'large'

withDefaults(
  defineProps<{
    value?: string
    mode?: SelectorMode
    placeholder?: string
    allowClear?: boolean
    disabled?: boolean
    size?: SelectorSize
    buttonStyle?: 'outline' | 'solid'
  }>(),
  {
    value: undefined,
    mode: 'select',
    placeholder: '请选择代码生成类型',
    allowClear: false,
    disabled: false,
    size: 'middle',
    buttonStyle: 'solid',
  },
)

const emit = defineEmits<{
  'update:value': [value: string | undefined]
}>()

const handleChange = (value: string | undefined) => {
  emit('update:value', value)
}
</script>

<template>
  <a-select
    v-if="mode === 'select'"
    :value="value"
    :options="CODE_GEN_TYPE_OPTIONS"
    :placeholder="placeholder"
    :allow-clear="allowClear"
    :disabled="disabled"
    :size="size"
    @update:value="handleChange"
  />
  <a-radio-group
    v-else
    :value="value"
    :button-style="buttonStyle"
    :disabled="disabled"
    :size="size"
    @update:value="handleChange"
  >
    <a-radio-button
      v-for="option in CODE_GEN_TYPE_OPTIONS"
      :key="option.value"
      :value="option.value"
    >
      {{ option.label }}
    </a-radio-button>
  </a-radio-group>
</template>
