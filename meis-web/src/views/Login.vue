<template>
  <div class="login-page">
    <div class="login-brand">
      <div class="brand-header">
        <h1 class="brand-logo">MEIS</h1>
        <p class="brand-title">医院设备管理系统</p>
        <p class="brand-desc">Medical Equipment Information System</p>
      </div>
      <div class="login-hero" aria-hidden="true">
        <div class="login-hero-stage">
          <div class="f2c-animate-img-bottom login-hero-layer" style="bottom: 14%; right: 12%">
            <img src="/login-banner/banner-bottom.png?v=size-up" height="400" alt="" />
          </div>
          <div class="f2c-animate-img-block login-hero-layer" style="top: 6%; left: 38%">
            <img src="/login-banner/banner-block.png" height="260" alt="" />
          </div>
          <div class="f2c-animate-img-left login-hero-layer" style="top: 18%; left: 20%">
            <img src="/login-banner/banner-left.png" height="142" alt="" />
          </div>
          <div class="f2c-animate-img-right login-hero-layer" style="top: 18%; right: 23%">
            <img src="/login-banner/banner-right.png" height="82" alt="" />
          </div>
          <div class="f2c-animate-img-AI login-hero-layer" style="top: 8%; left: 47%">
            <img src="/login-banner/banner-AI.png" height="130" alt="" />
          </div>
          <img class="login-hero-base" src="/login-banner/banner-bg.png" height="493" alt="" />
        </div>
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
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: linear-gradient(180deg, #f0f4ff 0%, #f7f9ff 55%, #eef2ff 100%);
  padding: 40px 24px 48px;
  overflow: hidden;
}

.brand-header {
  position: absolute;
  top: 40px;
  left: 48px;
  z-index: 2;
  color: #1f2a44;
}

.brand-logo {
  margin: 0 0 6px;
  font-size: 36px;
  font-weight: 700;
  letter-spacing: 4px;
  color: #1677ff;
}

.brand-title {
  margin: 0 0 4px;
  font-size: 18px;
  font-weight: 600;
  color: #1f2a44;
}

.brand-desc {
  margin: 0;
  font-size: 13px;
  color: #64748b;
}

.login-hero {
  width: 100%;
  max-width: 1040px;
  margin-top: 28px;
  display: flex;
  justify-content: center;
}

/*
 * 官网：底图 height=493（约宽 793），叠加层 height 分别为 400/260/142/82/130。
 * 按容器宽度等比缩放；放宽 max-width，使左侧展示接近官网体量。
 */
.login-hero-stage {
  position: relative;
  width: 100%;
  container-type: inline-size;
  line-height: 0;
}

.login-hero-base {
  display: block;
  width: 100%;
  height: auto;
  object-fit: contain;
}

.login-hero-layer {
  position: absolute;
  z-index: 1;
  line-height: 0;
}

.login-hero-layer img {
  display: block;
  width: auto;
  max-width: none;
}

.f2c-animate-img-bottom img {
  height: calc(100cqw * 400 / 793);
}

.f2c-animate-img-block img {
  height: calc(100cqw * 260 / 793);
}

.f2c-animate-img-left img {
  height: calc(100cqw * 142 / 793);
}

.f2c-animate-img-right img {
  height: calc(100cqw * 82 / 793);
}

.f2c-animate-img-AI img {
  height: calc(100cqw * 130 / 793);
}

.f2c-animate-img-bottom {
  animation: float-slow 4s ease-in-out 0.9s infinite;
}

.f2c-animate-img-block {
  opacity: 0;
  transform: translateY(-30px);
  animation: slideDown 1s ease-out 0.1s forwards;
}

.f2c-animate-img-left {
  opacity: 0;
  transform: translateY(30px);
  animation:
    slideUp 0.8s ease-out 0.5s forwards,
    float 4s ease-in-out 1.2s infinite;
}

.f2c-animate-img-right {
  opacity: 0;
  transform: translateY(30px);
  animation:
    slideUp 0.8s ease-out 0.5s forwards,
    float-delayed 5s ease-in-out 1.2s infinite;
}

.f2c-animate-img-AI {
  opacity: 0;
  transform: translateY(30px);
  animation:
    slideUp 0.8s ease-out 0.5s forwards,
    float-slow 4s ease-in-out 1.2s infinite;
}

@keyframes float {
  0%,
  100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-15px);
  }
}

@keyframes float-delayed {
  0%,
  100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-25px);
  }
}

@keyframes float-slow {
  0%,
  100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(10px);
  }
}

@keyframes slideUp {
  from {
    opacity: 0;
    transform: translateY(30px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes slideDown {
  from {
    opacity: 0;
    transform: translateY(-30px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.login-form-panel {
  width: 480px;
  flex-shrink: 0;
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
    padding: 28px 24px 16px;
    min-height: auto;
  }

  .brand-header {
    position: static;
    text-align: center;
    margin-bottom: 8px;
  }

  .login-hero {
    display: none;
  }

  .login-form-panel {
    width: 100%;
  }
}
</style>
