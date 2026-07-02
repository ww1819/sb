import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import '@/styles/variables.css'
import '@/styles/global.css'
import * as Icons from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'

import { setupPermissionDirective } from './directives/permission'

const app = createApp(App)
for (const [key, component] of Object.entries(Icons)) {
  app.component(key, component)
}
app.use(createPinia()).use(router).use(ElementPlus)
setupPermissionDirective(app)
app.mount('#app')
