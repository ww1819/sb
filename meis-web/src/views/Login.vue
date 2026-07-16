<template>
  <div class="login-page">
    <div class="login-brand">
      <div class="brand-inner">
        <h1 class="brand-logo">MEIS</h1>
        <p class="brand-title">医院设备管理系统</p>
        <p class="brand-desc">Medical Equipment Information System</p>
        <ul class="brand-features">
          <li>全生命周期设备管理</li>
          <li>多租户 SaaS 架构</li>
          <li>精细化权限与数据范围</li>
        </ul>
      </div>
    </div>
    <div class="login-form-panel">
      <el-card class="login-card" shadow="never">
        <h2 class="form-title">欢迎登录</h2>
        <p class="form-subtitle">请选择登录方式并输入凭据</p>
        <el-tabs v-model="mode" class="login-tabs">
          <el-tab-pane label="租户登录" name="tenant">
            <el-form :model="tenantForm" label-position="top" @submit.prevent="onTenantSubmit">
              <el-form-item label="医院编码">
                <el-input v-model="tenantForm.tenantCode" placeholder="demo" size="large" />
              </el-form-item>
              <el-form-item label="用户名">
                <el-input v-model="tenantForm.username" placeholder="admin" size="large" />
              </el-form-item>
              <el-form-item label="密码">
                <el-input
                  v-model="tenantForm.password"
                  type="password"
                  placeholder="admin123"
                  show-password
                  size="large"
                />
              </el-form-item>
              <el-button type="primary" :loading="loading" size="large" class="submit-btn" @click="onTenantSubmit">
                登录
              </el-button>
            </el-form>
          </el-tab-pane>
          <el-tab-pane label="平台管理员" name="platform">
            <el-form :model="platformForm" label-position="top" @submit.prevent="onPlatformSubmit">
              <el-form-item label="用户名">
                <el-input v-model="platformForm.username" placeholder="platform" size="large" />
              </el-form-item>
              <el-form-item label="密码">
                <el-input
                  v-model="platformForm.password"
                  type="password"
                  placeholder="admin123"
                  show-password
                  size="large"
                />
              </el-form-item>
              <el-button type="primary" :loading="loading" size="large" class="submit-btn" @click="onPlatformSubmit">
                登录
              </el-button>
            </el-form>
            <p class="hint">平台管理员负责开户、套餐与菜单授权，不进入医院业务数据。</p>
          </el-tab-pane>
        </el-tabs>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { useTabsStore } from '@/stores/tabs'
import { getHomePath } from '@/utils/home'

const router = useRouter()
const auth = useAuthStore()
const tabs = useTabsStore()
const loading = ref(false)
const mode = ref<'tenant' | 'platform'>('tenant')
const tenantForm = reactive({ tenantCode: 'demo', username: 'admin', password: 'admin123' })
const platformForm = reactive({ username: 'platform', password: 'admin123' })

async function onTenantSubmit() {
  loading.value = true
  try {
    await auth.login(tenantForm.tenantCode, tenantForm.username, tenantForm.password)
    tabs.reset()
    ElMessage.success('登录成功')
    router.push(getHomePath())
  } catch (e: any) {
    if (!e?.isBizError) {
      ElMessage.error(e?.response?.data?.message || e.message || '登录失败')
    } else {
      ElMessage.error(e.message || '登录失败')
    }
  } finally {
    loading.value = false
  }
}

async function onPlatformSubmit() {
  loading.value = true
  try {
    await auth.platformLogin(platformForm.username, platformForm.password)
    tabs.reset()
    ElMessage.success('平台管理员登录成功')
    router.push(getHomePath())
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e.message || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
}

.login-brand {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, var(--meis-header-gradient-start) 0%, var(--meis-header-gradient-end) 60%, #1677ff 100%);
  color: #fff;
  padding: 48px;
}

.brand-inner {
  max-width: 420px;
}

.brand-logo {
  margin: 0 0 8px;
  font-size: 48px;
  font-weight: 700;
  letter-spacing: 4px;
}

.brand-title {
  margin: 0 0 8px;
  font-size: 22px;
  font-weight: 500;
}

.brand-desc {
  margin: 0 0 32px;
  font-size: 14px;
  opacity: 0.75;
}

.brand-features {
  margin: 0;
  padding: 0;
  list-style: none;
}

.brand-features li {
  position: relative;
  padding: 8px 0 8px 20px;
  font-size: 14px;
  opacity: 0.9;
}

.brand-features li::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.6);
  transform: translateY(-50%);
}

.login-form-panel {
  width: 480px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px;
  background: var(--meis-page-bg);
}

.login-card {
  width: 100%;
  border-radius: var(--meis-card-radius);
  border: 1px solid var(--meis-border-light);
}

.form-title {
  margin: 0 0 4px;
  font-size: 22px;
  font-weight: 600;
  color: var(--meis-text-primary);
}

.form-subtitle {
  margin: 0 0 20px;
  font-size: 13px;
  color: var(--meis-text-secondary);
}

.login-tabs {
  margin-top: 4px;
}

.submit-btn {
  width: 100%;
  margin-top: 8px;
}

.hint {
  margin-top: 16px;
  font-size: 12px;
  color: var(--meis-text-secondary);
  line-height: 1.6;
}

@media (max-width: 900px) {
  .login-page {
    flex-direction: column;
  }
  .login-brand {
    padding: 32px 24px;
  }
  .login-form-panel {
    width: 100%;
  }
}
</style>
