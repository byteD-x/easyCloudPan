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
            <!-- 修改密码 -->
            <el-form-item label="新密码" prop="password">
                <el-input
                  v-model.trim="formData.password"
                  type="password"
                  size="large"
                  placeholder="请输入密码"
                  show-password
                >
                <template #prefix>
                    <span class="iconfont icon-password"></span>
                </template>
              </el-input>
            </el-form-item>
            <el-form-item label="确认密码" prop="rePassword">
                <el-input
                  v-model.trim="formData.rePassword"
                  type="password"
                  size="large"
                  placeholder="请再次输入密码"
                  show-password
                >
                <template #prefix>
                    <span class="iconfont icon-password"></span>
                </template>
              </el-input>
            </el-form-item>
          </el-form>
        </Dialog>
    </div>
</template>

<script setup>
import { ref, getCurrentInstance, nextTick } from "vue";
import * as accountService from "@/services/accountService";
const { proxy } = getCurrentInstance();

const checkRePassword = (rule, value, callback) => {
    if ( value !== formData.value.password ) {
        callback(new Error(rule.message));
    } else {
        callback();
    }
};

const formData = ref({});
const formDataRef = ref();
const rules = {
    password: [
        { required: true, message: "请输入密码" },
        {
            validator: proxy.Verify.password,
        },
    ],
    rePassword: [
        { required: true, message: "请再次输入密码" },
        {
            validator: checkRePassword,
            message: "两次输入的密码不一致",
        },
    ],
};

const show = () => {
    dialogConfig.value.show = true;
    nextTick(() => {
        formDataRef.value.resetFields();
        formData.value = {};
    });
};

defineExpose({ show });

const dialogConfig = ref ({
    show: false,
    title: "修改密码",
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

const submitForm = async () => {
    formDataRef.value.validate(async (valid) => {
        if (!valid) {
            return;
        }
        const result = await accountService.updatePassword(formData.value.password);

        if (!result) {
            return;
        }
        dialogConfig.value.show = false;
        proxy.Message.success("密码修改成功");
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
