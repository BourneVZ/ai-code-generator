<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'

import { deleteAppByAdmin, listAppVoByPageByAdmin, updateAppByAdmin } from '@/api/appController'
import CodeGenTypeSelector from '@/components/CodeGenTypeSelector.vue'
import CodeGenTypeTag from '@/components/CodeGenTypeTag.vue'
import { FEATURED_PRIORITY } from '@/constants/app'
import { asApiLong, formatDateTime, sanitizeAppQueryRequest } from '@/utils/app'

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
  { title: 'ID', dataIndex: 'id', width: 130, ellipsis: true },
  { title: '应用名称', dataIndex: 'appName', width: 180, ellipsis: true },
  { title: '类型', dataIndex: 'codeGenType', width: 92 },
  { title: '封面', dataIndex: 'cover', width: 88 },
  { title: '优先级', dataIndex: 'priority', width: 78 },
  { title: '创建者', dataIndex: 'user', width: 120, ellipsis: true },
  { title: '部署标识', dataIndex: 'deployKey', width: 120, ellipsis: true },
  { title: '创建时间', dataIndex: 'createTime', width: 168 },
  { title: '操作', key: 'action', width: 220 },
]

const pagination = computed(() => ({
  current: searchParams.pageNum ?? 1,
  pageSize: searchParams.pageSize ?? 10,
  total: total.value,
  showSizeChanger: true,
  showTotal: (value: number) => `共 ${value} 条`,
}))

const isFeatured = (record: API.AppVO) => (record.priority ?? 0) >= FEATURED_PRIORITY

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
  const apiId = asApiLong(id)
  if (apiId === undefined) {
    return
  }
  const response = await deleteAppByAdmin({ id: apiId })
  if (response.data.code === 0) {
    message.success('应用已删除')
    fetchData()
    return
  }
  message.error(response.data.message || '删除失败')
}

async function toggleFeatured(record: API.AppVO) {
  const id = asApiLong(record.id)
  if (id === undefined) {
    return
  }

  const nextFeatured = !isFeatured(record)
  const response = await updateAppByAdmin({
    id,
    appName: record.appName,
    cover: record.cover,
    priority: nextFeatured ? FEATURED_PRIORITY : 0,
  })

  if (response.data.code === 0) {
    message.success(nextFeatured ? '已设为精选' : '已取消精选')
    fetchData()
    return
  }
  message.error(response.data.message || (nextFeatured ? '设置精选失败' : '取消精选失败'))
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
        <p class="manage-page__subtitle">管理员可以查询、编辑、删除任意应用，并切换精选状态。</p>
      </div>

      <a-form layout="vertical" class="manage-page__form">
        <div class="manage-page__grid">
          <a-form-item label="应用名称">
            <a-input v-model:value="searchParams.appName" placeholder="按名称搜索" allow-clear />
          </a-form-item>
          <a-form-item label="代码类型">
            <CodeGenTypeSelector
              v-model:value="searchParams.codeGenType"
              mode="select"
              placeholder="全部"
              allow-clear
            />
          </a-form-item>
          <a-form-item label="创建者 ID">
            <a-input v-model:value="searchParams.userId" placeholder="输入创建者 ID" allow-clear />
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
        table-layout="fixed"
        @change="handleTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'codeGenType'">
            <CodeGenTypeTag :type="record.codeGenType" />
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
              <a-button type="link" @click="toggleFeatured(record)">
                {{ isFeatured(record) ? '取消精选' : '设为精选' }}
              </a-button>
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
  display: grid;
  grid-template-columns: repeat(2, max-content);
  gap: 2px 14px;
  align-items: start;
}

.manage-page__table-actions :deep(.ant-btn) {
  padding-inline: 0;
}

@media (max-width: 960px) {
  .manage-page__grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
