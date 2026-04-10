<script lang="ts" setup>
import { computed, onMounted, reactive, ref } from 'vue'
import dayjs from 'dayjs'
import { message } from 'ant-design-vue'

import { deleteUserByAdmin, listUserVoByPageByAdmin } from '@/api/userController'

const columns = [
  { title: 'ID', dataIndex: 'id', width: 90 },
  { title: '账号', dataIndex: 'userAccount', width: 180 },
  { title: '用户名', dataIndex: 'userName', width: 180 },
  { title: '头像', dataIndex: 'userAvatar', width: 120 },
  { title: '简介', dataIndex: 'userProfile' },
  { title: '角色', dataIndex: 'userRole', width: 120 },
  { title: '创建时间', dataIndex: 'createTime', width: 190 },
  { title: '操作', key: 'action', width: 120, fixed: 'right' },
]

const data = ref<API.UserVO[]>([])
const total = ref(0)
const loading = ref(false)

const searchParams = reactive<API.UserQueryRequest>({
  pageNum: 1,
  pageSize: 10,
  userAccount: '',
  userName: '',
})

const pagination = computed(() => ({
  current: searchParams.pageNum ?? 1,
  pageSize: searchParams.pageSize ?? 10,
  total: total.value,
  showSizeChanger: true,
  showTotal: (value: number) => `共 ${value} 条`,
}))

const fetchData = async () => {
  loading.value = true
  try {
    const response = await listUserVoByPageByAdmin({ ...searchParams })
    if (response.data.code === 0 && response.data.data) {
      data.value = response.data.data.records ?? []
      total.value = response.data.data.totalRow ?? 0
      return
    }
    message.error(response.data.message || '获取用户列表失败')
  } finally {
    loading.value = false
  }
}

const doTableChange = (page: { current: number; pageSize: number }) => {
  searchParams.pageNum = page.current
  searchParams.pageSize = page.pageSize
  fetchData()
}

const doSearch = () => {
  searchParams.pageNum = 1
  fetchData()
}

const doDelete = async (id?: number) => {
  if (!id) {
    return
  }
  const response = await deleteUserByAdmin({ id })
  if (response.data.code === 0) {
    message.success('删除成功')
    fetchData()
    return
  }
  message.error(response.data.message || '删除失败')
}

onMounted(() => {
  fetchData()
})
</script>

<template>
  <div class="manage-page">
    <section class="manage-page__toolbar">
      <div>
        <h1 class="manage-page__title">用户管理</h1>
        <p class="manage-page__subtitle">管理员可以分页查询用户信息，并进行删除操作。</p>
      </div>

      <a-form layout="inline" :model="searchParams" class="manage-page__search" @finish="doSearch">
        <a-form-item label="账号">
          <a-input v-model:value="searchParams.userAccount" placeholder="请输入账号" allow-clear />
        </a-form-item>
        <a-form-item label="用户名">
          <a-input v-model:value="searchParams.userName" placeholder="请输入用户名" allow-clear />
        </a-form-item>
        <a-form-item>
          <a-button type="primary" html-type="submit">搜索</a-button>
        </a-form-item>
      </a-form>
    </section>

    <section class="manage-page__table-card">
      <a-table
        row-key="id"
        :columns="columns"
        :data-source="data"
        :loading="loading"
        :pagination="pagination"
        :scroll="{ x: 1200 }"
        @change="doTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'userAvatar'">
            <a-avatar :src="record.userAvatar" :size="48">
              {{ record.userName?.slice(0, 1) }}
            </a-avatar>
          </template>
          <template v-else-if="column.dataIndex === 'userRole'">
            <a-tag :color="record.userRole === 'admin' ? 'green' : 'blue'">
              {{ record.userRole === 'admin' ? '管理员' : '普通用户' }}
            </a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'createTime'">
            {{ dayjs(record.createTime).format('YYYY-MM-DD HH:mm:ss') }}
          </template>
          <template v-else-if="column.key === 'action'">
            <a-popconfirm
              title="确认删除这个用户吗？"
              ok-text="删除"
              cancel-text="取消"
              @confirm="doDelete(record.id)"
            >
              <a-button danger>删除</a-button>
            </a-popconfirm>
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

.manage-page__search {
  margin-top: 20px;
}
</style>
