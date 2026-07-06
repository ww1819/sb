import { computed } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { DASHBOARD_PROFILES, resolveDashboardProfile } from '@/config/dashboardProfiles'

export function useDashboardProfile() {
  const auth = useAuthStore()
  const profileId = computed(() => resolveDashboardProfile(auth.user?.roles))
  const profile = computed(() => DASHBOARD_PROFILES[profileId.value])
  return { profileId, profile }
}
