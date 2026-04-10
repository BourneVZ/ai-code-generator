<script setup lang="ts">
import AppCard from '@/components/AppCard.vue'

withDefaults(
  defineProps<{
    title: string
    description?: string
    searchValue?: string
    searchPlaceholder?: string
    loading?: boolean
    apps: API.AppVO[]
    total?: number
    current?: number
    pageSize?: number
    emptyDescription?: string
    editable?: boolean
    deletable?: boolean
  }>(),
  {
    description: '',
    searchValue: '',
    searchPlaceholder: '',
    loading: false,
    total: 0,
    current: 1,
    pageSize: 8,
    emptyDescription: '暂无内容',
    editable: false,
    deletable: false,
  },
)

const emit = defineEmits<{
  'update:searchValue': [value: string]
  'update:current': [value: number]
  'update:pageSize': [value: number]
  search: []
  pageChange: []
  open: [app: API.AppVO]
  viewWork: [app: API.AppVO]
  edit: [app: API.AppVO]
  delete: [app: API.AppVO]
}>()

const handlePageChange = (page: number, pageSize: number) => {
  emit('update:current', page)
  emit('update:pageSize', pageSize)
  emit('pageChange')
}

const handlePageSizeChange = (page: number, pageSize: number) => {
  emit('update:current', page)
  emit('update:pageSize', pageSize)
  emit('pageChange')
}
</script>

<template>
  <section class="gallery-shell">
    <div class="gallery-shell__header">
      <div class="gallery-shell__title-wrap">
        <h2 class="gallery-shell__title">{{ title }}</h2>
        <p v-if="description" class="gallery-shell__description">{{ description }}</p>
      </div>

      <a-input-search
        v-if="searchPlaceholder"
        :value="searchValue"
        class="gallery-shell__search"
        :placeholder="searchPlaceholder"
        allow-clear
        @update:value="emit('update:searchValue', $event)"
        @search="emit('search')"
      />
    </div>

    <div class="gallery-shell__grid">
      <a-spin :spinning="loading" class="gallery-shell__spin">
        <div v-if="apps.length" class="gallery-shell__list">
          <AppCard
            v-for="app in apps"
            :key="app.id"
            :app="app"
            :editable="editable"
            :deletable="deletable"
            @open="emit('open', $event)"
            @view-work="emit('viewWork', $event)"
            @edit="emit('edit', $event)"
            @delete="emit('delete', $event)"
          />
        </div>
        <a-empty v-else :description="emptyDescription" />
      </a-spin>
    </div>

    <div v-if="total > pageSize" class="gallery-shell__pagination">
      <a-pagination
        :current="current"
        :page-size="pageSize"
        :total="total"
        :page-size-options="['4', '8', '12', '20']"
        show-size-changer
        @change="handlePageChange"
        @showSizeChange="handlePageSizeChange"
      />
    </div>
  </section>
</template>

<style scoped>
.gallery-shell {
  display: grid;
  gap: 24px;
  padding: 32px;
  background: rgba(255, 255, 255, 0.88);
  border: 1px solid rgba(255, 255, 255, 0.82);
  border-radius: 34px;
  box-shadow: 0 26px 60px rgba(15, 23, 42, 0.08);
}

.gallery-shell__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
}

.gallery-shell__title {
  margin: 0;
  color: #101828;
  font-size: clamp(28px, 3vw, 36px);
  line-height: 1.1;
}

.gallery-shell__description {
  margin: 10px 0 0;
  color: #667085;
  line-height: 1.7;
}

.gallery-shell__search {
  width: min(100%, 320px);
}

.gallery-shell__grid {
  min-height: 220px;
}

.gallery-shell__spin {
  width: 100%;
}

.gallery-shell__spin :deep(.ant-spin-container) {
  width: 100%;
}

.gallery-shell__list {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 22px;
}

.gallery-shell__pagination {
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 1320px) {
  .gallery-shell__list {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 1024px) {
  .gallery-shell__list {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .gallery-shell {
    padding: 22px 16px;
    border-radius: 26px;
  }

  .gallery-shell__header {
    flex-direction: column;
    align-items: stretch;
  }

  .gallery-shell__search {
    width: 100%;
  }

  .gallery-shell__list {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
