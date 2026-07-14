<template>
  <el-drawer v-model="visible" title="帮助中心" direction="rtl" size="min(400px, 92vw)" append-to-body>
    <div class="help-body">
      <section v-for="section in helpSections" :key="section.title" class="help-section">
        <h3 class="help-title">{{ section.title }}</h3>
        <ul class="help-list">
          <li v-for="item in section.items" :key="item">{{ item }}</li>
        </ul>
      </section>

      <section class="help-section">
        <h3 class="help-title">快捷键</h3>
        <ul class="help-list">
          <li><kbd>Ctrl</kbd> + <kbd>K</kbd> — 搜索菜单</li>
          <li>Tab 右键 — 关闭 / 关闭其他 / 关闭全部</li>
        </ul>
      </section>

      <section class="help-section">
        <h3 class="help-title">文档</h3>
        <p class="help-text">
          完整用户手册位于项目仓库 <code>docs/user-manual.md</code>，部署说明见
          <code>docs/local-dev-deploy.md</code>。
        </p>
      </section>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
const visible = defineModel<boolean>({ default: false })

const helpSections = [
  {
    title: '登录',
    items: ['使用医院编码 + 账号 + 密码登录 Web 管理端', '平台管理员使用平台入口登录']
  },
  {
    title: '模块导航',
    items: [
      '采购管理：计划、项目、合同',
      '资产管理：台账、入库、流转、盘点、报废',
      '维修管理：工单、工程师、备件',
      '保养管理：模板、计划、记录',
      '质控管理：风险评估、不良事件、计量',
      '数据分析：看板与报表',
      '系统管理：院区、科室、用户、角色、供应商、设备分类、系统配置'
    ]
  },
  {
    title: '常用操作',
    items: [
      '顶部搜索或 Ctrl+K 快速跳转菜单',
      '消息铃铛查看未读通知',
      '偏好设置中切换布局与主题',
      '内容区全屏适合查看大表格与报表'
    ]
  }
]
</script>

<style scoped>
.help-body {
  padding: 0 4px 16px;
}

.help-section {
  margin-bottom: 24px;
}

.help-title {
  margin: 0 0 10px;
  font-size: 15px;
  font-weight: 600;
  color: var(--meis-text-primary);
}

.help-list {
  margin: 0;
  padding-left: 18px;
  color: var(--meis-text-secondary);
  font-size: 13px;
  line-height: 1.8;
}

.help-text {
  margin: 0;
  font-size: 13px;
  line-height: 1.8;
  color: var(--meis-text-secondary);
}

code {
  padding: 2px 6px;
  border-radius: 4px;
  background: var(--meis-surface-muted);
  font-size: 12px;
}

kbd {
  padding: 2px 6px;
  border-radius: 4px;
  border: 1px solid var(--meis-border-light);
  background: var(--meis-surface-muted);
  font-size: 12px;
}
</style>
