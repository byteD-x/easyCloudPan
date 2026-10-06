<template>
    <div class="others">
      <div class="body-content">
        <div>
          <Icon
            :icon-name="fileInfo.fileType == 9 ? 'zip' : 'others'"
            :width="80"
          ></Icon>
        </div>
        <div class="file-name">{{ fileInfo.fileName }}</div>
        <div class="tips">该类型文件暂不支持预览，请下载后查看</div>
        <div class="download-btn">
          <el-button type="primary" @click="download"
            >点击下载{{ proxy.Utils.size2Str(fileInfo.fileSize) }}</el-button
          >
        </div>
      </div>
    </div>
  </template>
  
<script setup>
import { createDownloadCode } from "@/services";
import { resolveDownloadTarget } from "@/utils/url";
import { getCurrentInstance } from "vue";

const { proxy } = getCurrentInstance();

const props = defineProps({
    createDownloadUrl: {
        type: String,
    },
    downloadUrl: {
        type: String,
    },
    fileInfo: {
    type: Object,
    },
});

const download = async () => {
    const codeOrUrl = await createDownloadCode(props.createDownloadUrl);
    if (!codeOrUrl) {
        return;
    }
    const target = resolveDownloadTarget(props.downloadUrl, codeOrUrl);
    if (!target) {
        return;
    }
    window.location.href = target;
};
</script>

<style lang="scss" scoped>
.others {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 100%;

    .body-content {
        width: min(460px, 100%);
        padding: 24px;
        border-radius: 18px;
        border: 1px solid rgba(189, 208, 202, 0.8);
        background: rgba(255, 255, 255, 0.84);
        box-shadow: var(--shadow-sm);
        text-align: center;

        .file-name {
            margin-top: 10px;
            font-weight: 700;
            color: var(--text-main);
        }

        .tips {
            color: var(--text-secondary);
            margin-top: 6px;
            font-size: 13px;
        }

        .download-btn {
            margin-top: 20px;
        }
    }
}
</style>
