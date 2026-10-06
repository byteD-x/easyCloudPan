<template>
    <div class="avatar-upload">
        <div class="avatar-show">
          <template v-if="localFile">
            <img :src="localFile" />
          </template>
          <template v-else>
            <img
              v-if="modelValue && modelValue.qqAvatar"
              :src="`${modelValue.qqAvatar}`"
            />
            <img v-else :src="`/api/getAvatar/${modelValue.userId}`" />
          </template>
        </div>
        <div class="select-btn">
            <el-upload
              name="file"
              :show-file-list="false"
              accept=".png,.PNG,.jpg,.JPG,.jpeg,.JPEG,.gif,.GIF,.bmp,.BMP"
              :multiple="false"
              :http-request="uploadImage"
            >
              <el-button class="select-button" type="primary">选择</el-button>
            </el-upload>
        </div>
    </div>
</template>

<script setup>
import { ref } from "vue";

defineProps({
    modelValue: {
        type: Object,
        default: null,
    },
});

const localFile = ref(null);
const emit = defineEmits(["update:modelValue"]);
const uploadImage = async (file) => {
    file = file.file;
    const img = new FileReader();
    img.readAsDataURL(file);
    img.onload = ({ target }) => {
        localFile.value = target.result;
    };
    emit("update:modelValue", file);
};
</script>

<style lang="scss" scoped>
.avatar-upload {
    display: flex;
    justify-content: center;
    align-items: flex-end;

    .avatar-show {
        background: rgba(250, 252, 252, 0.86);
        border: 1px solid rgba(189, 208, 202, 0.78);
        border-radius: 16px;
        width: 150px;
        height: 150px;
        display: flex;
        align-items: center;
        justify-content: center;
        overflow: hidden;
        position: relative;
        box-shadow: var(--shadow-xs);

        .iconfont {
            font-size: 50px;
            color: var(--text-light);
        }

        img {
            width: 100%;
            height: 100%;
            object-fit: cover;
        }

        .op {
            position: absolute;
            color: var(--primary);
            top: 80px;
        }
    }

    .select-btn {
        margin-left: 12px;

        .select-button {
            letter-spacing: 0.04em;
        }
    }
}

@media screen and (max-width: 768px) {
    .avatar-upload {
        flex-direction: column;
        align-items: center;
        gap: 10px;

        .select-btn {
            margin-left: 0;
        }
    }
}
</style>
