<template>
    <div class="progressive-image" :style="{ width: width + 'px', height: height + 'px' }">
        <img
            v-if="thumbnailUrl"
            class="thumbnail"
            :class="{ loaded: showFull }"
            :src="thumbnailUrl"
            style="object-fit: cover"
        />
        <img
            v-lazy="fullImageUrl"
            class="full-image"
            :class="{ loaded: showFull }"
            style="object-fit: cover"
            @load="onFullLoad"
        />
        <div v-if="loading" class="loading-placeholder">
            <span class="loading-spinner"></span>
        </div>
    </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'

const props = withDefaults(defineProps<{
    src: string
    width?: number
    height?: number
    thumbnailQuality?: number
}>(), {
    width: 100,
    height: 100,
    thumbnailQuality: 20
})

const showFull = ref(false)
const loading = ref(true)

const thumbnailUrl = computed(() => {
    if (!props.src) return ''
    const separator = props.src.includes('?') ? '&' : '?'
    return `${props.src}${separator}thumbnail=1&q=${props.thumbnailQuality}`
})

const fullImageUrl = computed(() => props.src || '')

const onFullLoad = () => {
    showFull.value = true
    loading.value = false
}

onMounted(() => {
    if (!props.src) {
        loading.value = false
    }
})
</script>

<style lang="scss" scoped>
.progressive-image {
    position: relative;
    overflow: hidden;
    background: rgba(246, 249, 252, 0.9);
    border-radius: 12px;
    border: 1px solid var(--border-color);

    img {
        position: absolute;
        top: 0;
        left: 0;
        width: 100%;
        height: 100%;
        transition: opacity 0.3s ease-in-out;
    }

    .thumbnail {
        filter: blur(10px);
        transform: scale(1.1);
        opacity: 1;

        &.loaded {
            opacity: 0;
        }
    }

    .full-image {
        opacity: 0;

        &.loaded {
            opacity: 1;
        }
    }

    .loading-placeholder {
        position: absolute;
        top: 0;
        left: 0;
        width: 100%;
        height: 100%;
        display: flex;
        align-items: center;
        justify-content: center;
        background: rgba(246, 249, 252, 0.9);

        .loading-spinner {
            width: 24px;
            height: 24px;
            border: 2px solid rgba(31, 79, 104, 0.18);
            border-top-color: var(--primary);
            border-radius: 50%;
            animation: spin 0.8s linear infinite;
        }
    }
}

@keyframes spin {
    to {
        transform: rotate(360deg);
    }
}
</style>

