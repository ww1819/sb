<template>
  <el-dialog
    v-model="visible"
    title="搜索菜单"
    width="520px"
    destroy-on-close
    append-to-body
    class="menu-search-dialog"
    @opened="focusInput"
    @closed="reset"
  >
    <el-input
      ref="inputRef"
      v-model="keyword"
      placeholder="输入菜单名称，如：采购计划、设备台账"
      clearable
      :prefix-icon="Search"
      @keydown.down.prevent="move(1)"
      @keydown.up.prevent="move(-1)"
      @keydown.enter.prevent="confirm"
    />
    <div class="result-list">
      <button
        v-for="(item, index) in results"
        :key="item.path"
        type="button"
        class="result-item"
        :class="{ active: index === activeIndex }"
        @mouseenter="activeIndex = index"
        @click="select(item)"
      >
        <div class="result-title">{{ item.title }}</div>
        <div class="result-path">
          {{ item.moduleTitle }}<template v-if="item.groupTitle"> / {{ item.groupTitle }}</template>
        </div>
      </button>
      <div v-if="keyword && !results.length" class="result-empty">未找到匹配菜单</div>
      <div v-if="!keyword" class="result-hint">输入关键词快速跳转，支持 Ctrl+K 唤起</div>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Search } from '@element-plus/icons-vue'
import type { ElInput } from 'element-plus'
import { filterMenuItems, flattenMenus, type FlatMenuItem, type NavModule } from '@/utils/menuNav'

const props = defineProps<{
  modules: NavModule[]
}>()

const emit = defineEmits<{
  select: [path: string, title: string]
}>()

const visible = defineModel<boolean>({ default: false })
const keyword = ref('')
const activeIndex = ref(0)
const inputRef = ref<InstanceType<typeof ElInput>>()

const flatItems = computed(() => flattenMenus(props.modules))
const results = computed(() => filterMenuItems(flatItems.value, keyword.value))

watch(keyword, () => {
  activeIndex.value = 0
})

function focusInput() {
  inputRef.value?.focus()
}

function reset() {
  keyword.value = ''
  activeIndex.value = 0
}

function move(step: number) {
  if (!results.value.length) return
  activeIndex.value = (activeIndex.value + step + results.value.length) % results.value.length
}

function confirm() {
  const item = results.value[activeIndex.value]
  if (item) select(item)
}

function select(item: FlatMenuItem) {
  visible.value = false
  emit('select', item.path, item.title)
}
</script>

<style scoped>
.result-list {
  margin-top: 12px;
  max-height: 360px;
  overflow: auto;
}

.result-item {
  display: block;
  width: 100%;
  padding: 10px 12px;
  margin-bottom: 4px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  text-align: left;
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease;
}

.result-item:hover,
.result-item.active {
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary-light-7);
}

.result-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--meis-text-primary);
}

.result-path {
  margin-top: 2px;
  font-size: 12px;
  color: var(--meis-text-secondary);
}

.result-empty,
.result-hint {
  padding: 24px 8px;
  text-align: center;
  font-size: 13px;
  color: var(--meis-text-secondary);
}
</style>
