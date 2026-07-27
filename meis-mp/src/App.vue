<script setup lang="ts">
import { onLaunch } from '@dcloudio/uni-app'
import { useAuthStore } from '@/stores/auth'

onLaunch(() => {
  useAuthStore().restore()
})
</script>

<style lang="scss">
@import './uni.scss';

page {
  background-color: $meis-page-bg;
  color: $meis-text;
  font-size: 28rpx;
}
</style>
