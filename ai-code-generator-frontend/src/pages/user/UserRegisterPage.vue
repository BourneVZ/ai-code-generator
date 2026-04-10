<script lang="ts" setup>
import { reactive } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'

import { userRegister } from '@/api/userController'

const router = useRouter()

const formState = reactive<API.UserRegisterRequest>({
  userAccount: '',
  userPassword: '',
  checkPassword: '',
})

const validateCheckPassword = async () => {
  if (formState.userPassword !== formState.checkPassword) {
    return Promise.reject('两次输入的密码不一致')
  }
  return Promise.resolve()
}

const handleSubmit = async (values: API.UserRegisterRequest) => {
  const response = await userRegister(values)
  if (response.data.code === 0 && response.data.data) {
    message.success('注册成功，请登录')
    await router.push('/user/login')
    return
  }
  message.error(response.data.message || '注册失败')
}
</script>

<template>
  <div class="auth-page">
    <div class="auth-card">
      <img class="auth-card__logo" src="@/assets/logo.png" alt="logo" />
      <h1 class="auth-card__title">创建账号</h1>
      <p class="auth-card__desc">注册后即可通过与 AI 对话创建网站应用、查看效果并继续迭代。</p>

      <a-form :model="formState" layout="vertical" autocomplete="off" @finish="handleSubmit">
        <a-form-item name="userAccount" label="账号" :rules="[{ required: true, message: '请输入账号' }]">
          <a-input v-model:value="formState.userAccount" placeholder="请输入账号" size="large" />
        </a-form-item>

        <a-form-item
          name="userPassword"
          label="密码"
          :rules="[
            { required: true, message: '请输入密码' },
            { min: 8, message: '密码不能少于 8 位' },
          ]"
        >
          <a-input-password
            v-model:value="formState.userPassword"
            placeholder="请输入密码"
            size="large"
          />
        </a-form-item>

        <a-form-item
          name="checkPassword"
          label="确认密码"
          :rules="[
            { required: true, message: '请再次输入密码' },
            { min: 8, message: '确认密码不能少于 8 位' },
            { validator: validateCheckPassword },
          ]"
        >
          <a-input-password
            v-model:value="formState.checkPassword"
            placeholder="请再次输入密码"
            size="large"
          />
        </a-form-item>

        <div class="auth-card__tips">
          已有账号？
          <RouterLink to="/user/login">立即登录</RouterLink>
        </div>

        <a-form-item>
          <a-button type="primary" html-type="submit" size="large" block>注册</a-button>
        </a-form-item>
      </a-form>
    </div>
  </div>
</template>

<style scoped>
.auth-page {
  display: grid;
  place-items: center;
  min-height: calc(100vh - 180px);
}

.auth-card {
  width: min(100%, 420px);
  padding: 32px 28px;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(255, 255, 255, 0.92);
  border-radius: 28px;
  box-shadow: 0 24px 60px rgba(15, 23, 42, 0.08);
}

.auth-card__logo {
  display: block;
  width: 58px;
  height: 58px;
  margin: 0 auto 16px;
  border-radius: 50%;
}

.auth-card__title {
  margin: 0;
  color: #0f172a;
  font-size: 30px;
  text-align: center;
}

.auth-card__desc {
  margin: 12px 0 24px;
  color: #64748b;
  line-height: 1.7;
  text-align: center;
}

.auth-card__tips {
  margin-bottom: 18px;
  color: #64748b;
  font-size: 13px;
  text-align: right;
}
</style>
