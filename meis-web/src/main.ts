import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import '@/styles/variables.css'
import '@/styles/themes/dark.css'
import '@/styles/global.css'
import * as Icons from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'

import { setupPermissionDirective } from './directives/permission'
import { useLayoutStore } from './stores/layout'

const app = createApp(App)
for (const [key, component] of Object.entries(Icons)) {
  app.component(key, component)
}
const pinia = createPinia()
app.use(pinia).use(router).use(ElementPlus)
setupPermissionDirective(app)
useLayoutStore().initTheme()
app.mount('#app')
