<template>
  <div class="image-viewer"></div>
  <el-image-viewer
    v-if="previewImgIndex != null"
    :initial-index="previewImgIndex"
    hide-on-click-modal
    :url-list="imageList"
    @close="closeImgViewer"
  />
</template>

<script setup lang="ts">
import { ref } from 'vue'

defineProps({
  imageList: {
    type: Array as () => string[],
  },
})

const previewImgIndex = ref<number | null>(null)

const show = (index: number) => {
  previewImgIndex.value = index
}

defineExpose({ show })

const closeImgViewer = () => {
  previewImgIndex.value = null
}
</script>

<style lang="scss" scoped>
.image-viewer {
  :deep(.el-image-viewer__mask) {
    opacity: 0.86;
    backdrop-filter: blur(4px);
    background: rgba(7, 28, 27, 0.72);
  }
}
</style>
