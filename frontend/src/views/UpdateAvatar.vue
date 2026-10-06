
<template>
    <div>
        <Dialog
          :show="dialogConfig.show"
          :title="dialogConfig.title"
          :buttons="dialogConfig.buttons"
          width="500px"
          :show-cancel="false"
          @close="dialogConfig.show = false"
          >
        <el-form
          ref="formDataRef"
          class="profile-form"
          :model="formData"
          label-width="80px"
          @submit.prevent
        >
        <!-- 头像上传修改 -->
        <el-form-item label="昵称">
            {{ formData.nickName }}
        </el-form-item>

        <el-form-item label="头像" prop="">
          <AvatarUpload v-model="formData.avatar"></AvatarUpload>
        </el-form-item>
       </el-form>
      </Dialog>
    </div>
</template>

<script setup>
import AvatarUpload from "@/components/AvatarUpload.vue";
import { ref } from "vue";
import { useUserInfoStore } from "@/stores/userInfoStore";
import * as accountService from "@/services/accountService";
const userInfoStore = useUserInfoStore();

const formData = ref({});
const formDataRef = ref();

  const show = (data) => {
    formData.value = { ...data };
    formData.value.avatar = { userId: data.userId, qqAvatar: data.avatar };
    dialogConfig.value.show = true;
  };

defineExpose({ show });

const dialogConfig = ref({
    show: false,
    title: "修改头像",
    buttons: [
        {
            type: "primary",
            text: "确定",
            click: () => {
                submitForm();
            },
        },
    ],
});
const emit = defineEmits(["updateAvatar"]);
const submitForm = async () => {
    if (!(formData.value.avatar instanceof File)) {
        dialogConfig.value.show = false;
        return;
    }

    const result = await accountService.updateUserAvatar(formData.value.avatar);
    if (!result) {
        return;
    }
    dialogConfig.value.show = false;
    const currentInfo = { ...(userInfoStore.userInfo || {}) };
    delete currentInfo.avatar;
    userInfoStore.setUserInfo(currentInfo);
    emit("updateAvatar");
};
</script>

<style lang="scss" scoped>
.profile-form {
  padding-top: 6px;

  :deep(.el-form-item__label) {
    color: var(--text-secondary);
    font-weight: 600;
  }
}

:deep(.avatar-upload) {
  justify-content: flex-start;
}
</style>
