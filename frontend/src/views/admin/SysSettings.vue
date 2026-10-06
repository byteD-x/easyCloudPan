<template>
    <div class="sys-setting-panel">
      <el-form
        ref="formDataRef"
        :model="formData"
        :rules="rules"
        label-width="150px"
        @submit.prevent
      >
        <div class="panel-title">用户注册设置</div>
        <!-- 用户配置 -->
        <el-form-item label="注册邮箱标题" prop="registerEmailTitle">
          <el-input
            v-model.trim="formData.registerEmailTitle"
            clearable
            placeholder="请输入注册邮件验证码邮件标题"
          ></el-input>
        </el-form-item>
        <!-- 用户配置 -->
        <el-form-item label="注册邮箱内容" prop="registerEmailContent">
          <el-input
            v-model.trim="formData.registerEmailContent"
            clearable
            placeholder="请输入注册邮件验证码邮件内容%s占位符为验证码内容"
          ></el-input>
        </el-form-item>
        <!-- 用户配置 -->
        <el-form-item label="初始空间大小" prop="userInitUseSpace">
          <el-input
            v-model.trim="formData.userInitUseSpace"
            clearable
            placeholder="初始化空间大小"
          >
            <template #suffix>MB</template>
          </el-input>
        </el-form-item>
        <!-- 保存按钮 -->
        <el-form-item label="" prop="">
          <el-button class="save-btn" type="primary" @click="saveSettings">保存</el-button>
        </el-form-item>
      </el-form>
    </div>
</template>

<script setup>
import { ref, getCurrentInstance } from "vue";
import * as adminService from "@/services/adminService";
const { proxy } = getCurrentInstance();

const formData = ref({});
const formDataRef = ref();
const rules = {
    registerEmailTitle: [
      { required: true, message: "请输入注册邮件验证码邮件标题" },
    ],
    registerEmailContent: [
      { required: true, message: "请输入注册邮件验证码邮件内容" },
    ],
    userInitUseSpace: [
      { required: true, message: "请输入初始化空间大小" },
      {
        validator: proxy.Verify.number,
        message: "空间大小只能是数字",
      },
    ],
};

const getSysSettings = async () => {
    const result = await adminService.getSysSettings();
    if (!result) {
        return;
    }
    formData.value = result;
};
getSysSettings();

const saveSettings = async () => {
    formDataRef.value.validate(async (valid) => {
        if (!valid) {
            return;
        }
        const params = {};
        Object.assign(params, formData.value);
        const result = await adminService.saveSysSettings(params);
        if (!result) {
            return;
        }
        proxy.Message.success("保存成功");
    });
};
</script>

<style lang="scss" scoped>
.sys-setting-panel {
    margin-top: 14px;
    padding: 18px;
    border-radius: 18px;
    border: 1px solid rgba(194, 204, 220, 0.82);
    background: rgba(255, 255, 255, 0.76);
    box-shadow: var(--shadow-xs);
    width: min(600px, 100%);

    .save-btn {
        width: min(280px, 100%);
        display: block;
        margin: 0 auto;
    }
    
    .panel-title {
        font-size: 16px;
        font-weight: 700;
        margin-bottom: 20px;
        letter-spacing: 0.06em;
        padding-left: 10px;
        border-left: 4px solid var(--primary);
    }
}
</style>
