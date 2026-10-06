<template>
  <div class="music">
    <div class="body-content">
      <div class="cover">
        <img src="@/assets/music_cover.webp">
      </div>
      <div ref="playerRef" class="music-player"></div>
    </div>
  </div>
</template>

<script setup lang="ts">
import APlayer from "aplayer";
import "aplayer/dist/APlayer.min.css";

import { ref, onMounted, onUnmounted } from "vue";
import { toApiPath } from "@/utils/url";

const props = defineProps({
    url: {
        type: String,
    },
    fileName: {
        type: String,
    },
});

const playerRef = ref<HTMLElement | null>(null);
const player = ref<APlayer | null>(null);

onMounted(() => {
    if (!props.url || !playerRef.value) {
        return;
    }
    const audioUrl = toApiPath(props.url);

    player.value = new APlayer({
        container: playerRef.value,
        audio: {
            url: audioUrl,
            name: props.fileName,
            cover: new URL("@/assets/music_icon.png", import.meta.url).href,
            artist: "",
        },
    });
});

onUnmounted(() => {
    player.value?.destroy();
});
</script>

<style lang="scss" scoped>
.music {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 100%;

    .body-content {
        text-align: center;
        width: min(740px, 90%);
        padding: 22px;
        border-radius: 18px;
        border: 1px solid rgba(189, 208, 202, 0.76);
        background: rgba(255, 255, 255, 0.86);
        box-shadow: var(--shadow-sm);

        .cover {
            margin: 0 auto;
            width: 200px;
            text-align: center;

            img {
                width: 100%;
                border-radius: 16px;
                box-shadow: var(--shadow-sm);
            }
        }

        .music-player {
            margin-top: 20px;
        }
    }
}
</style>
