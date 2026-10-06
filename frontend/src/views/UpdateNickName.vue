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
            :rules="rules"
            label-width="80px"
            @submit.prevent
          >
            <!-- 修改昵称 -->
            <el-form-item label="昵称" prop="nickName">
                <el-input
                  v-model.trim="formData.nickName"
                  placeholder="请输入昵称"
                  :maxlength="20"
                >
              </el-input>
            </el-form-item>
          </el-form>
        </Dialog>
    </div>
</template>

<script setup>
import { ref, getCurrentInstance, nextTick } from "vue";
import { useUserInfoStore } from "@/stores/userInfoStore";
import * as accountService from "@/services/accountService";
const { proxy } = getCurrentInstance();
const userInfoStore = useUserInfoStore();

const formData = ref({});
const formDataRef = ref();
const rules = {
    nickName: [
        { required: true, message: "请输入昵称" },
        { max: 20, message: "昵称长度不能超过20个字符" },
    ],
};

const show = (data) => {
    formData.value = { nickName: data.nickName };
    dialogConfig.value.show = true;
    nextTick(() => {
        formDataRef.value.clearValidate();
    });
};

defineExpose({ show });

const dialogConfig = ref ({
    show: false,
    title: "修改昵称",
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

const emit = defineEmits(["updateNickName"]);
const submitForm = async () => {
    formDataRef.value.validate(async (valid) => {
        if (!valid) {
            return;
        }
        const result = await accountService.updateNickName(formData.value.nickName);

        if (!result) {
            return;
        }
        dialogConfig.value.show = false;
        proxy.Message.success("昵称修改成功");
        
        // 同步更新 Store
        const currentInfo = { ...userInfoStore.userInfo };
        currentInfo.nickName = formData.value.nickName;
        userInfoStore.setUserInfo(currentInfo);
        
        emit("updateNickName");
    });
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
</style>
