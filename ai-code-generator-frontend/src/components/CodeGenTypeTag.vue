<script setup lang="ts">
import { computed } from 'vue'

import { formatCodeGenType } from '@/utils/codeGenTypes'

const props = withDefaults(
  defineProps<{
    type?: string
    color?: string
  }>(),
  {
    type: undefined,
    color: 'blue',
  },
)

const label = computed(() => formatCodeGenType(props.type))
</script>

<template>
  <a-tag :color="color">{{ label }}</a-tag>
</template>
