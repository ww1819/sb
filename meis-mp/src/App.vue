<script setup lang="ts">
import { onLaunch } from '@dcloudio/uni-app'
import { useAuthStore } from '@/stores/auth'

onLaunch(() => {
  useAuthStore().restore()
})
</script>

<style>
page {
  background-color: #f5f7fa;
  color: #1f2a37;
  font-size: 28rpx;
}
</style>
