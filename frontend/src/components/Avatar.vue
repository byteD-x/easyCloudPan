<template>
    <span class="avatar" :style="{ width: width + 'px', height: width + 'px' }">
        <img
        v-if="userId"
        :src="
          avatar && avatar != ''
          ? avatar
          : `${proxy.globalInfo.avatarUrl}${userId}?${timestamp}`
        "
      />
    </span>
</template>

<script setup>
import { getCurrentInstance } from 'vue';
const { proxy } = getCurrentInstance();

defineProps({
    userId: {
        type: String,
    },
    avatar: {
        type: String,
    },
    timestamp: {
        type: Number,
        default: 0,
    },
    width: {
        type: Number,
        default: 40,
    },
});
</script>

<style lang="scss" scoped>
.avatar {
    display: flex;
    width: 40px;
    height: 40px;
    border-radius: 50%;
    overflow: hidden;
    border: 2px solid rgba(255, 255, 255, 0.7);
    box-shadow: 0 8px 16px rgba(13, 57, 54, 0.16);
    img {
        width: 100%;
        object-fit: cover;
    }
}
</style>
