<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'

import {
  deleteAppByAdmin,
  listAppVoByPageByAdmin,
  updateAppByAdmin,
} from '@/api/appController'
import { FEATURED_PRIORITY } from '@/constants/app'
import { formatDateTime, getCodeGenTypeLabel, sanitizeAppQueryRequest } from '@/utils/app'

const router = useRouter()

const data = ref<API.AppVO[]>([])
const total = ref(0)
const loading = ref(false)

const searchParams = reactive<API.AppQueryRequest>({
  pageNum: 1,
  pageSize: 10,
  appName: '',
  codeGenType: undefined,
  userId: undefined,
  priority: undefined,
  deployKey: '',
  cover: '',
  initPrompt: '',
})

const columns = [
  { title: 'ID', dataIndex: 'id', width: 90 },
  { title: '应用名称', dataIndex: 'appName', width: 220 },
  { title: '类型', dataIndex: 'codeGenType', width: 120 },
  { title: '封面', dataIndex: 'cover', width: 120 },
  { title: '优先级', dataIndex: 'priority', width: 100 },
  { title: '创建者', dataIndex: 'user', width: 140 },
  { title: '部署标识', dataIndex: 'deployKey', width: 180 },
  { title: '创建时间', dataIndex: 'createTime', width: 190 },
  { title: '操作', key: 'action', fixed: 'right', width: 250 },
]

const pagination = computed(() => ({
  current: searchParams.pageNum ?? 1,
  pageSize: searchParams.pageSize ?? 10,
  total: total.value,
  showSizeChanger: true,
  showTotal: (value: number) => `共 ${value} 条`,
}))

async function fetchData() {
  loading.value = true
  try {
    const response = await listAppVoByPageByAdmin(sanitizeAppQueryRequest({ ...searchParams }))
    if (response.data.code === 0 && response.data.data) {
      data.value = response.data.data.records ?? []
      total.value = response.data.data.totalRow ?? 0
      return
    }
    message.error(response.data.message || '获取应用列表失败')
  } finally {
    loading.value = false
  }
}

function doSearch() {
  searchParams.pageNum = 1
  fetchData()
}

function resetSearch() {
  searchParams.pageNum = 1
  searchParams.pageSize = 10
  searchParams.appName = ''
  searchParams.codeGenType = undefined
  searchParams.userId = undefined
  searchParams.priority = undefined
  searchParams.deployKey = ''
  searchParams.cover = ''
  searchParams.initPrompt = ''
  fetchData()
}

async function doDelete(id?: string | number) {
  if (!id) {
    return
  }
  const response = await deleteAppByAdmin({ id: id as unknown as number })
  if (response.data.code === 0) {
    message.success('应用已删除')
    fetchData()
    return
  }
  message.error(response.data.message || '删除失败')
}

async function setFeatured(record: API.AppVO) {
  if (!record.id) {
    return
  }
  const response = await updateAppByAdmin({
    id: record.id as unknown as number,
    appName: record.appName,
    cover: record.cover,
    priority: FEATURED_PRIORITY,
  })
  if (response.data.code === 0) {
    message.success('已设为精选')
    fetchData()
    return
  }
  message.error(response.data.message || '设置精选失败')
}

function handleTableChange(page: { current: number; pageSize: number }) {
  searchParams.pageNum = page.current
  searchParams.pageSize = page.pageSize
  fetchData()
}

onMounted(() => {
  fetchData()
})
</script>

<template>
  <div class="manage-page">
    <section class="manage-page__toolbar">
      <div>
        <h1 class="manage-page__title">应用管理</h1>
        <p class="manage-page__subtitle">管理员可以查询、编辑、删除任意应用，并将应用设为精选。</p>
      </div>

      <a-form layout="vertical" class="manage-page__form">
        <div class="manage-page__grid">
          <a-form-item label="应用名称">
            <a-input v-model:value="searchParams.appName" placeholder="按名称搜索" allow-clear />
          </a-form-item>
          <a-form-item label="代码类型">
            <a-select
              v-model:value="searchParams.codeGenType"
              placeholder="全部"
              allow-clear
              :options="[
                { label: '多文件', value: 'multi_file' },
                { label: 'HTML', value: 'html' },
              ]"
            />
          </a-form-item>
          <a-form-item label="创建者 ID">
            <a-input-number v-model:value="searchParams.userId" style="width: 100%" :min="1" />
          </a-form-item>
          <a-form-item label="优先级">
            <a-input-number v-model:value="searchParams.priority" style="width: 100%" :min="0" />
          </a-form-item>
          <a-form-item label="部署标识">
            <a-input v-model:value="searchParams.deployKey" placeholder="deployKey" allow-clear />
          </a-form-item>
          <a-form-item label="封面地址">
            <a-input v-model:value="searchParams.cover" placeholder="cover" allow-clear />
          </a-form-item>
          <a-form-item class="manage-page__grid-span" label="初始化提示词">
            <a-input v-model:value="searchParams.initPrompt" placeholder="按 initPrompt 搜索" allow-clear />
          </a-form-item>
        </div>

        <div class="manage-page__actions">
          <a-button @click="resetSearch">重置</a-button>
          <a-button type="primary" @click="doSearch">搜索</a-button>
        </div>
      </a-form>
    </section>

    <section class="manage-page__table-card">
      <a-table
        row-key="id"
        :columns="columns"
        :data-source="data"
        :loading="loading"
        :pagination="pagination"
        :scroll="{ x: 1400 }"
        @change="handleTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'codeGenType'">
            <a-tag color="blue">{{ getCodeGenTypeLabel(record.codeGenType) }}</a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'cover'">
            <a-image v-if="record.cover" :src="record.cover" :width="72" />
            <span v-else>-</span>
          </template>
          <template v-else-if="column.dataIndex === 'user'">
            {{ record.user?.userName || `用户 #${record.userId ?? '-'}` }}
          </template>
          <template v-else-if="column.dataIndex === 'createTime'">
            {{ formatDateTime(record.createTime) }}
          </template>
          <template v-else-if="column.key === 'action'">
            <div class="manage-page__table-actions">
              <a-button type="link" @click="router.push(`/app/chat/${record.id}`)">查看</a-button>
              <a-button type="link" @click="router.push(`/app/edit/${record.id}`)">编辑</a-button>
              <a-button type="link" @click="setFeatured(record)">精选</a-button>
              <a-popconfirm
                title="确认删除这个应用吗？"
                ok-text="删除"
                cancel-text="取消"
                @confirm="doDelete(record.id)"
              >
                <a-button danger type="link">删除</a-button>
              </a-popconfirm>
            </div>
          </template>
        </template>
      </a-table>
    </section>
  </div>
</template>

<style scoped>
.manage-page {
  display: grid;
  gap: 20px;
}

.manage-page__toolbar,
.manage-page__table-card {
  padding: 22px;
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid rgba(255, 255, 255, 0.9);
  border-radius: 26px;
}

.manage-page__title {
  margin: 0;
  color: #0f172a;
  font-size: 30px;
}

.manage-page__subtitle {
  margin: 8px 0 0;
  color: #64748b;
}

.manage-page__form {
  margin-top: 20px;
}

.manage-page__grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 4px 16px;
}

.manage-page__grid-span {
  grid-column: 1 / -1;
}

.manage-page__actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.manage-page__table-actions {
  display: flex;
  gap: 4px;
}

@media (max-width: 960px) {
  .manage-page__grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
