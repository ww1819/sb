<template>
  <div ref="hostRef" class="twin-scene">
    <div v-if="!devices.length" class="twin-empty">当前库房暂无设备台账</div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue'
import * as THREE from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'

export interface TwinDevice {
  id: string
  device_code?: string
  device_name?: string
  device_status?: string
  dept_name?: string
}

export interface TwinShelfSelect {
  status: string
  statusLabel: string
  zoneCode: string
  zoneName: string
  zoneColorName: string
  devices: TwinDevice[]
}

const props = defineProps<{
  devices: TwinDevice[]
  warehouseName?: string
}>()

const emit = defineEmits<{
  select: [payload: TwinShelfSelect | null]
  'zones-change': [zones: ZoneStat[]]
}>()

export interface ZoneStat {
  code: string
  name: string
  colorName: '黄' | '绿' | '红'
  colorHex: string
  count: number
}

const hostRef = ref<HTMLElement>()

/** 医药仓储标准：五区三色 */
const ZONE_DEFS = [
  {
    code: 'pending_inspect',
    name: '待验区',
    colorName: '黄' as const,
    color: 0xfcc419,
    floor: 0xf9e079,
    desc: '到货验收 / 状态未确认'
  },
  {
    code: 'qualified',
    name: '合格品区',
    colorName: '绿' as const,
    color: 0x40c057,
    floor: 0x8ce99a,
    desc: '验收合格、可放行'
  },
  {
    code: 'shipping',
    name: '发货区',
    colorName: '绿' as const,
    color: 0x37b24d,
    floor: 0x69db7c,
    desc: '出库复核 / 待发货'
  },
  {
    code: 'return_pending',
    name: '退货区',
    colorName: '黄' as const,
    color: 0xf59f00,
    floor: 0xffd43b,
    desc: '销后退回 / 待处理'
  },
  {
    code: 'unqualified',
    name: '不合格品区',
    colorName: '红' as const,
    color: 0xfa5252,
    floor: 0xff8787,
    desc: '不合格 / 禁流通 / 待销毁'
  }
]

/** 实拍：浅银灰开孔立柱货架 */
const CELLS_PER_COLUMN = 10
const SHELF_LEVELS = 5
const RACK_WIDTH = 1.95
const RACK_DEPTH = 0.75
const LEVEL_HEIGHT = 0.55
const POST_W = 0.052
const POST_D = 0.045

const COLORS = {
  frame: 0xb8c0c8,
  frameDark: 0x9aa3ad,
  frameEdge: 0x8b949e,
  slot: 0x6c757d,
  deck: 0xc5ccd4,
  floor: 0xa8b8c8,
  cardboard: [0xc4a574, 0xb8956a, 0xd2b48c, 0xa8885c, 0xc9b896, 0xbfa07a]
} as const

let renderer: THREE.WebGLRenderer | null = null
let scene: THREE.Scene | null = null
let camera: THREE.PerspectiveCamera | null = null
let controls: OrbitControls | null = null
let animationId = 0
let shelfGroup: THREE.Group | null = null
let raycaster: THREE.Raycaster | null = null
let pointer = new THREE.Vector2()
let hovered: THREE.Object3D | null = null
let selected: THREE.Object3D | null = null
let resizeObserver: ResizeObserver | null = null

function hashId(id: string) {
  let h = 0
  for (let i = 0; i < id.length; i++) h = (h * 31 + id.charCodeAt(i)) | 0
  return Math.abs(h)
}

/** 设备状态 → 五区（演示映射，无库位主数据时） */
function assignZoneCode(d: TwinDevice): string {
  const s = String(d.device_status || '')
  if (s === 'pending_verify') return 'pending_inspect'
  if (s === 'scrap') return 'unqualified'
  if (s === 'maintenance' || s === 'idle') return 'return_pending'
  if (s === 'normal') {
    // 约 22% 进入发货区，其余合格品区
    return hashId(String(d.id)) % 9 < 2 ? 'shipping' : 'qualified'
  }
  // 未知等归待验
  return 'pending_inspect'
}

function buildZones(devices: TwinDevice[]) {
  const buckets = new Map<string, TwinDevice[]>()
  for (const z of ZONE_DEFS) buckets.set(z.code, [])
  for (const d of devices) {
    const code = assignZoneCode(d)
    buckets.get(code)!.push(d)
  }
  return ZONE_DEFS.map((z) => ({
    ...z,
    devices: buckets.get(z.code) || []
  }))
}

function clearShelves() {
  if (!scene || !shelfGroup) return
  scene.remove(shelfGroup)
  shelfGroup.traverse((obj) => {
    const mesh = obj as THREE.Mesh
    if (mesh.geometry) mesh.geometry.dispose()
    if (mesh.material) {
      const mats = Array.isArray(mesh.material) ? mesh.material : [mesh.material]
      mats.forEach((m) => m.dispose())
    }
  })
  shelfGroup = null
  hovered = null
  selected = null
}

function paint(color: number, metalness = 0.35, roughness = 0.55) {
  return new THREE.MeshStandardMaterial({ color, metalness, roughness })
}

function addSlottedUpright(
  rack: THREE.Group,
  x: number,
  z: number,
  totalH: number,
  frameMat: THREE.MeshStandardMaterial,
  slotMat: THREE.MeshStandardMaterial,
  towardAisle: boolean
) {
  const post = new THREE.Mesh(new THREE.BoxGeometry(POST_W, totalH, POST_D), frameMat)
  post.position.set(x, totalH / 2, z)
  rack.add(post)
  const wing = new THREE.Mesh(new THREE.BoxGeometry(0.012, totalH, POST_D + 0.01), frameMat)
  wing.position.set(x + (x > 0 ? POST_W * 0.45 : -POST_W * 0.45), totalH / 2, z)
  rack.add(wing)
  const holeCount = Math.floor(totalH / 0.055)
  const faceZ = z + (towardAisle ? POST_D * 0.52 : -POST_D * 0.52)
  for (let i = 2; i < holeCount - 1; i++) {
    const hole = new THREE.Mesh(new THREE.BoxGeometry(0.012, 0.028, 0.008), slotMat)
    hole.position.set(x, 0.08 + i * 0.055, faceZ)
    rack.add(hole)
  }
  const foot = new THREE.Mesh(new THREE.BoxGeometry(0.1, 0.02, 0.09), paint(COLORS.frameEdge, 0.4, 0.5))
  foot.position.set(x, 0.01, z)
  rack.add(foot)
}

function addSideBracing(
  rack: THREE.Group,
  x: number,
  totalH: number,
  depth: number,
  braceMat: THREE.MeshStandardMaterial
) {
  const bayH = (totalH - 0.4) / 3
  for (let i = 0; i < 3; i++) {
    const y0 = 0.22 + i * bayH
    const y1 = y0 + bayH * 0.85
    const dy = y1 - y0
    const dz = depth - 0.14
    const len = Math.hypot(dy, dz)
    const brace = new THREE.Mesh(new THREE.BoxGeometry(0.018, 0.018, len), braceMat)
    brace.position.set(x, (y0 + y1) / 2, 0)
    brace.rotation.x = (i % 2 === 0 ? 1 : -1) * Math.atan2(dy, dz)
    rack.add(brace)
    const link = new THREE.Mesh(new THREE.BoxGeometry(0.016, 0.016, depth - 0.12), braceMat)
    link.position.set(x, y0, 0)
    rack.add(link)
  }
  for (const y of [0.18, totalH - 0.12]) {
    const bar = new THREE.Mesh(new THREE.BoxGeometry(0.02, 0.02, depth - 0.1), braceMat)
    bar.position.set(x, y, 0)
    rack.add(bar)
  }
}

function addSolidLevel(
  rack: THREE.Group,
  y: number,
  w: number,
  d: number,
  beamMat: THREE.MeshStandardMaterial,
  deckMat: THREE.MeshStandardMaterial
) {
  const hx = w / 2
  const hz = d / 2
  for (const z of [-hz + 0.02, hz - 0.02]) {
    const beam = new THREE.Mesh(new THREE.BoxGeometry(w - POST_W, 0.06, 0.035), beamMat)
    beam.position.set(0, y, z)
    rack.add(beam)
  }
  for (const x of [-hx + POST_W * 0.5, hx - POST_W * 0.5]) {
    const side = new THREE.Mesh(new THREE.BoxGeometry(0.035, 0.05, d - POST_D), beamMat)
    side.position.set(x, y, 0)
    rack.add(side)
  }
  const deck = new THREE.Mesh(new THREE.BoxGeometry(w - 0.1, 0.022, d - 0.1), deckMat)
  deck.position.set(0, y + 0.04, 0)
  rack.add(deck)
}

function createRackUnit(
  devices: TwinDevice[],
  zone: (typeof ZONE_DEFS)[number]
): THREE.Group {
  const rack = new THREE.Group()
  const levels = SHELF_LEVELS
  const totalH = levels * LEVEL_HEIGHT + 0.35
  const w = RACK_WIDTH
  const d = RACK_DEPTH
  const hx = w / 2 - POST_W * 0.5
  const hz = d / 2 - POST_D * 0.5

  const frameMat = paint(COLORS.frame, 0.32, 0.52)
  const beamMat = paint(COLORS.frameDark, 0.34, 0.5)
  const deckMat = paint(COLORS.deck, 0.28, 0.58)
  const braceMat = paint(COLORS.frameDark, 0.3, 0.55)
  const slotMat = paint(COLORS.slot, 0.25, 0.65)

  addSlottedUpright(rack, -hx, -hz, totalH, frameMat, slotMat, false)
  addSlottedUpright(rack, hx, -hz, totalH, frameMat, slotMat, false)
  addSlottedUpright(rack, -hx, hz, totalH, frameMat, slotMat, true)
  addSlottedUpright(rack, hx, hz, totalH, frameMat, slotMat, true)
  addSideBracing(rack, -hx, totalH, d, braceMat)
  addSideBracing(rack, hx, totalH, d, braceMat)

  const slotsPerLevel = Math.max(1, Math.ceil(Math.max(devices.length, 1) / levels))
  for (let lv = 0; lv < levels; lv++) {
    const y = 0.2 + lv * LEVEL_HEIGHT
    addSolidLevel(rack, y, w, d, beamMat, deckMat)

    const start = lv * slotsPerLevel
    const slice = devices.slice(start, start + slotsPerLevel)
    const n = slice.length
    slice.forEach((_dev, i) => {
      const boxW = Math.min(0.5, (w - 0.35) / Math.max(n, 1) - 0.05)
      const bx = -w / 2 + 0.28 + (i + 0.5) * ((w - 0.4) / Math.max(n, 1))
      const tall = lv === levels - 1
      const boxH = tall ? 0.38 + (i % 2) * 0.1 : 0.2 + (i % 3) * 0.07
      const boxD = 0.4 + (i % 2) * 0.08
      const cardboard = COLORS.cardboard[i % COLORS.cardboard.length]
      const box = new THREE.Mesh(
        new THREE.BoxGeometry(boxW, boxH, boxD),
        new THREE.MeshStandardMaterial({ color: cardboard, metalness: 0.02, roughness: 0.92 })
      )
      box.position.set(bx, y + 0.055 + boxH / 2, i % 2 === 0 ? -0.06 : 0.08)
      box.userData = { isShelf: true, isCargo: true }
      rack.add(box)
      const tape = new THREE.Mesh(
        new THREE.BoxGeometry(Math.max(0.03, boxW * 0.1), boxH + 0.004, boxD + 0.004),
        new THREE.MeshStandardMaterial({ color: 0xd4c4a0, roughness: 0.75, metalness: 0.02 })
      )
      tape.position.copy(box.position)
      rack.add(tape)
    })
  }

  for (const z of [-hz, hz]) {
    const top = new THREE.Mesh(new THREE.BoxGeometry(w - POST_W, 0.045, 0.03), beamMat)
    top.position.set(0, totalH - 0.05, z)
    rack.add(top)
  }

  // 货架端头：三色区标牌
  const plate = new THREE.Mesh(
    new THREE.BoxGeometry(0.7, 0.28, 0.03),
    new THREE.MeshStandardMaterial({ color: 0xffffff, metalness: 0.05, roughness: 0.4 })
  )
  plate.position.set(0, totalH - 0.28, hz + 0.04)
  rack.add(plate)
  const colorBar = new THREE.Mesh(
    new THREE.BoxGeometry(0.62, 0.1, 0.02),
    new THREE.MeshStandardMaterial({
      color: zone.color,
      emissive: zone.color,
      emissiveIntensity: 0.35,
      metalness: 0.1,
      roughness: 0.45
    })
  )
  colorBar.position.set(0, totalH - 0.22, hz + 0.055)
  colorBar.userData = { isStatusTag: true }
  rack.add(colorBar)

  const hit = new THREE.Mesh(
    new THREE.BoxGeometry(w + 0.12, totalH + 0.08, d + 0.12),
    new THREE.MeshBasicMaterial({ transparent: true, opacity: 0.001, depthWrite: false })
  )
  hit.position.y = totalH / 2
  hit.userData = {
    isShelf: true,
    isHitProxy: true,
    status: zone.code,
    statusLabel: zone.name,
    zoneCode: zone.code,
    zoneName: zone.name,
    zoneColorName: zone.colorName,
    devices,
    baseEmissive: 0
  }
  rack.add(hit)

  rack.userData = {
    isShelf: true,
    status: zone.code,
    statusLabel: zone.name,
    zoneCode: zone.code,
    zoneName: zone.name,
    zoneColorName: zone.colorName,
    devices,
    baseEmissive: 0,
    highlightMats: [frameMat, beamMat]
  }
  return rack
}

function addZoneSign(
  group: THREE.Group,
  zone: (typeof ZONE_DEFS)[number],
  x: number,
  z: number,
  width: number
) {
  const pole = new THREE.Mesh(
    new THREE.CylinderGeometry(0.025, 0.025, 2.4, 8),
    paint(0x868e96, 0.4, 0.45)
  )
  pole.position.set(x - width / 2 + 0.15, 1.2, z + RACK_DEPTH * 0.65)
  group.add(pole)

  const board = new THREE.Mesh(
    new THREE.BoxGeometry(1.1, 0.45, 0.04),
    new THREE.MeshStandardMaterial({
      color: zone.color,
      emissive: zone.color,
      emissiveIntensity: 0.25,
      metalness: 0.15,
      roughness: 0.5
    })
  )
  board.position.set(x - width / 2 + 0.15, 2.35, z + RACK_DEPTH * 0.65)
  board.userData = { isStatusTag: true }
  group.add(board)

  const strip = new THREE.Mesh(
    new THREE.BoxGeometry(1.0, 0.08, 0.02),
    new THREE.MeshStandardMaterial({ color: 0xffffff, roughness: 0.4, metalness: 0.05 })
  )
  strip.position.set(x - width / 2 + 0.15, 2.2, z + RACK_DEPTH * 0.68)
  group.add(strip)
}

function buildSceneContent() {
  if (!scene) return
  clearShelves()
  shelfGroup = new THREE.Group()
  scene.add(shelfGroup)

  const zones = buildZones(props.devices)
  emit(
    'zones-change',
    zones.map((z) => ({
      code: z.code,
      name: z.name,
      colorName: z.colorName,
      colorHex: `#${z.color.toString(16).padStart(6, '0')}`,
      count: z.devices.length
    }))
  )

  const floorMat = paint(COLORS.floor, 0.08, 0.88)
  const concrete = new THREE.Mesh(new THREE.CircleGeometry(22, 64), floorMat)
  concrete.rotation.x = -Math.PI / 2
  concrete.position.y = 0.002
  shelfGroup.add(concrete)

  // 五区平面布局：上排 待验|合格|发货，下排 退货|不合格
  const layout: Array<{ code: string; x: number; z: number }> = [
    { code: 'pending_inspect', x: -5.2, z: -3.2 },
    { code: 'qualified', x: 0, z: -3.2 },
    { code: 'shipping', x: 5.2, z: -3.2 },
    { code: 'return_pending', x: -3.2, z: 3.0 },
    { code: 'unqualified', x: 3.2, z: 3.0 }
  ]

  for (const pos of layout) {
    const zone = zones.find((z) => z.code === pos.code)!
    const rackCount = Math.max(1, Math.ceil(Math.max(zone.devices.length, 1) / CELLS_PER_COLUMN))
    const capped = Math.min(rackCount, zone.code === 'qualified' ? 4 : 3)
    const rowW = capped * (RACK_WIDTH + 0.1) + 0.6

    // 三色地坪色块
    const pad = new THREE.Mesh(
      new THREE.BoxGeometry(rowW + 0.4, 0.02, RACK_DEPTH + 1.4),
      new THREE.MeshStandardMaterial({
        color: zone.floor,
        metalness: 0.05,
        roughness: 0.85,
        transparent: true,
        opacity: 0.55
      })
    )
    pad.position.set(pos.x, 0.012, pos.z)
    shelfGroup.add(pad)

    // 色标线
    const edge = new THREE.Mesh(
      new THREE.BoxGeometry(rowW + 0.5, 0.015, 0.08),
      paint(zone.color, 0.1, 0.5)
    )
    edge.position.set(pos.x, 0.022, pos.z + RACK_DEPTH * 0.75)
    shelfGroup.add(edge)

    addZoneSign(shelfGroup, zone, pos.x, pos.z, rowW)

    for (let c = 0; c < capped; c++) {
      const slice = zone.devices.slice(c * CELLS_PER_COLUMN, (c + 1) * CELLS_PER_COLUMN)
      const x = pos.x + (c - (capped - 1) / 2) * (RACK_WIDTH + 0.08)
      const unit = createRackUnit(slice, zone)
      unit.position.set(x, 0, pos.z)
      shelfGroup.add(unit)
    }
  }
}

function setShelfHighlight(obj: THREE.Object3D | null, intensity: number) {
  if (!obj) return
  const rack = resolveShelf(obj)
  if (!rack) return
  const mats = (rack.userData.highlightMats as THREE.MeshStandardMaterial[]) || []
  for (const mat of mats) {
    if (!mat) continue
    if (intensity > 0.15) {
      mat.emissive = new THREE.Color(0xadb5bd)
      mat.emissiveIntensity = intensity * 0.22
    } else {
      mat.emissive = new THREE.Color(0x000000)
      mat.emissiveIntensity = 0
    }
  }
  rack.traverse((child) => {
    if ((child as THREE.Mesh).isMesh && child.userData?.isStatusTag) {
      const mat = (child as THREE.Mesh).material as THREE.MeshStandardMaterial
      if (mat?.emissiveIntensity != null) {
        mat.emissiveIntensity = intensity > 0.15 ? 0.55 : 0.3
      }
    }
  })
}

function resolveShelf(obj: THREE.Object3D | null): THREE.Object3D | null {
  if (!obj) return null
  let cur: THREE.Object3D | null = obj
  while (cur) {
    if (cur.userData?.isShelf && cur.userData.devices && cur.type === 'Group') return cur
    if (cur.userData?.isHitProxy && cur.parent?.userData?.devices) return cur.parent
    if (cur.userData?.isShelf && Array.isArray(cur.userData.devices)) return cur
    cur = cur.parent
  }
  return null
}

function onPointerMove(e: PointerEvent) {
  if (!hostRef.value || !camera || !raycaster || !shelfGroup) return
  const rect = hostRef.value.getBoundingClientRect()
  pointer.x = ((e.clientX - rect.left) / rect.width) * 2 - 1
  pointer.y = -((e.clientY - rect.top) / rect.height) * 2 + 1
  raycaster.setFromCamera(pointer, camera)
  const hits = raycaster.intersectObjects(shelfGroup.children, true)
  const next = resolveShelf(hits[0]?.object ?? null)
  if (hovered !== next) {
    if (hovered && hovered !== selected) setShelfHighlight(hovered, hovered.userData.baseEmissive ?? 0)
    hovered = next
    if (hovered && hovered !== selected) setShelfHighlight(hovered, 0.4)
    if (hostRef.value) hostRef.value.style.cursor = next ? 'pointer' : 'grab'
  }
}

function onPointerClick() {
  if (!hovered) {
    if (selected) setShelfHighlight(selected, selected.userData.baseEmissive ?? 0)
    selected = null
    emit('select', null)
    return
  }
  if (selected && selected !== hovered) setShelfHighlight(selected, selected.userData.baseEmissive ?? 0)
  selected = hovered
  setShelfHighlight(selected, 0.55)
  emit('select', {
    status: String(selected.userData.status),
    statusLabel: String(selected.userData.statusLabel),
    zoneCode: String(selected.userData.zoneCode || ''),
    zoneName: String(selected.userData.zoneName || selected.userData.statusLabel),
    zoneColorName: String(selected.userData.zoneColorName || ''),
    devices: (selected.userData.devices as TwinDevice[]) || []
  })
}

function resize() {
  if (!hostRef.value || !renderer || !camera) return
  const w = hostRef.value.clientWidth
  const h = hostRef.value.clientHeight
  if (w < 2 || h < 2) return
  camera.aspect = w / h
  camera.updateProjectionMatrix()
  renderer.setSize(w, h, false)
}

function animate() {
  animationId = requestAnimationFrame(animate)
  controls?.update()
  if (renderer && scene && camera) renderer.render(scene, camera)
}

function init() {
  if (!hostRef.value) return
  const w = hostRef.value.clientWidth || 600
  const h = hostRef.value.clientHeight || 320

  scene = new THREE.Scene()
  scene.fog = new THREE.FogExp2(0x2a3440, 0.012)

  camera = new THREE.PerspectiveCamera(38, w / h, 0.1, 200)
  camera.position.set(12, 9, 14)

  renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true })
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2))
  renderer.setSize(w, h, false)
  renderer.setClearColor(0x000000, 0)
  hostRef.value.appendChild(renderer.domElement)

  controls = new OrbitControls(camera, renderer.domElement)
  controls.enableDamping = true
  controls.dampingFactor = 0.06
  controls.autoRotate = true
  controls.autoRotateSpeed = 0.3
  controls.minDistance = 6
  controls.maxDistance = 32
  controls.maxPolarAngle = Math.PI * 0.48
  controls.target.set(0, 1.2, 0)

  scene.add(new THREE.AmbientLight(0xf4f7fa, 0.75))
  const key = new THREE.DirectionalLight(0xffffff, 1.05)
  key.position.set(8, 16, 10)
  scene.add(key)
  const fill = new THREE.DirectionalLight(0xe8eef5, 0.5)
  fill.position.set(-10, 6, -6)
  scene.add(fill)
  scene.add(new THREE.HemisphereLight(0xffffff, 0x9aa8b5, 0.4))

  const hall = new THREE.Mesh(
    new THREE.BoxGeometry(28, 7, 18),
    new THREE.MeshStandardMaterial({
      color: 0xeef2f6,
      transparent: true,
      opacity: 0.38,
      side: THREE.BackSide,
      metalness: 0.04,
      roughness: 0.88
    })
  )
  hall.position.y = 3.2
  scene.add(hall)

  raycaster = new THREE.Raycaster()
  buildSceneContent()

  renderer.domElement.addEventListener('pointermove', onPointerMove)
  renderer.domElement.addEventListener('click', onPointerClick)
  renderer.domElement.addEventListener('pointerdown', () => {
    if (controls) controls.autoRotate = false
  })

  resizeObserver = new ResizeObserver(() => resize())
  resizeObserver.observe(hostRef.value)
  animate()
}

function dispose() {
  cancelAnimationFrame(animationId)
  resizeObserver?.disconnect()
  resizeObserver = null
  clearShelves()
  if (renderer) {
    renderer.domElement.removeEventListener('pointermove', onPointerMove)
    renderer.domElement.removeEventListener('click', onPointerClick)
    renderer.dispose()
    renderer.domElement.remove()
  }
  controls?.dispose()
  renderer = null
  scene = null
  camera = null
  controls = null
  raycaster = null
}

onMounted(() => init())
onUnmounted(() => dispose())

watch(
  () => [props.devices, props.warehouseName] as const,
  () => {
    if (!scene) return
    buildSceneContent()
    emit('select', null)
    if (controls) controls.autoRotate = true
  },
  { deep: true }
)
</script>

<style scoped>
.twin-scene {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 300px;
  overflow: hidden;
  border-radius: 4px;
  cursor: grab;
}
.twin-scene:active {
  cursor: grabbing;
}
.twin-scene :deep(canvas) {
  display: block;
  width: 100% !important;
  height: 100% !important;
}
.twin-empty {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  pointer-events: none;
  z-index: 2;
  font-size: 13px;
  color: rgba(180, 190, 200, 0.55);
  letter-spacing: 1px;
}
</style>
