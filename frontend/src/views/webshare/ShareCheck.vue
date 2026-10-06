<template>
    <div class="share">
        <div class="body-content">
            <div class="logo">
                <span class="iconfont icon-pan"></span>
                <span class="name">易云盘</span>
            </div>
            <div class="code-panel">
                <div class="file-info">
                    <div class="avatar">
                        <Avatar
                          :user-id="shareInfo.userId"
                          :avatar="shareInfo.avatar"
                          :width="50"
                        ></Avatar>
                    </div>
                    <div class="share-info">
                        <div class="user-info">
                            <span class="nick-name">{{ shareInfo.nickName }}</span>
                            <span class="share-time">分享于 {{ shareInfo.shareTime }}</span>
                        </div>
                        <div class="file-name">分享文件：{{ shareInfo.fileName }}</div>
                    </div>
                </div>
                <div class="code-body">
                    <div class="tips">请输入提取码: </div>
                    <div class="input-area">
                        <el-form
                          ref="formDataRef"
                          :model="formData"
                          :rules="rules"
                          @submit.prevent
                        >
                          <!-- 输入框 -->
                          <el-form-item prop="code">
                            <el-input
                              v-model.trim="formData.code"
                              class="input"
                              clearable
                              @keyup.enter="checkShare"
                            ></el-input>
                            <el-button class="get-btn" type="primary" @click="checkShare"
                              >提取文件</el-button
                            >
                          </el-form-item>
                        </el-form>
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>

<script setup>
import { ref } from "vue";
import { useRouter, useRoute } from "vue-router";
import * as shareService from "@/services/shareService";
const router = useRouter();
const route = useRoute();


const shareId = route.params.shareId;
const shareInfo = ref({});
const getShareInfo = async () => {
    const result = await shareService.getShareInfo(shareId);
    if (!result) {
        return;
    }
    shareInfo.value = result;
};
getShareInfo();

const formData = ref({});
const formDataRef = ref();
const rules = {
    code: [
        { required: true, message: "请输入提取码" },
        { min: 5, message: "提取码为5位" },
        { max: 5, message: "提取码为5位" },
    ],
};

const checkShare = async () => {
    formDataRef.value.validate(async (valid) => {
        if (!valid) {
            return;
        }
        const result = await shareService.checkShareCode({
            shareId: shareId,
            code: formData.value.code,
        });
        if (!result) {
            return;
        }
        router.push(`/share/${shareId}`);
    });
};
</script>

<style lang="scss" scoped>
.share {
    min-height: 100vh;
    display: flex;
    justify-content: center;
    background:
      radial-gradient(620px 420px at 10% 0%, rgba(43, 137, 169, 0.2), rgba(43, 137, 169, 0)),
      radial-gradient(620px 420px at 96% 100%, rgba(58, 115, 145, 0.14), rgba(58, 115, 145, 0)),
      linear-gradient(180deg, #f7f8fb 0%, #f1f3f8 100%);

    .body-content {
        margin-top: 10vh;
        width: min(560px, calc(100% - 32px));
        animation: riseIn 0.4s cubic-bezier(0.22, 1, 0.36, 1);

        .logo {
            display: flex;
            align-items: center;
            justify-content: center;
            user-select: none;

            .icon-pan {
                width: 62px;
                height: 62px;
                border-radius: 18px;
                display: inline-flex;
                align-items: center;
                justify-content: center;
                font-size: 34px;
                color: #fff;
                background: var(--primary);
                box-shadow: 0 16px 28px rgba(31, 79, 104, 0.24);
            }

            .name {
                margin-left: 10px;
                font-size: 24px;
                font-weight: 700;
                letter-spacing: 0.06em;
                color: var(--text-main);
                font-family: var(--font-heading);
            }
        }

        .code-panel {
            margin-top: 20px;
            border-radius: 24px;
            overflow: hidden;
            border: 1px solid rgba(194, 204, 220, 0.78);
            background: rgba(255, 255, 255, 0.82);
            box-shadow: var(--shadow-lg);
            backdrop-filter: blur(8px);

            .file-info {
                padding: 14px 20px;
                display: flex;
                align-items: center;
                color: var(--text-main);
                background: linear-gradient(100deg, rgba(31, 79, 104, 0.14) 0%, rgba(58, 115, 145, 0.1) 100%);

                .avatar {
                    margin-right: 8px;
                }

                .share-info {
                    .user-info {
                        display: flex;
                        align-items: center;
                        flex-wrap: wrap;

                        .nick-name {
                            font-size: 15px;
                            font-weight: 700;
                        }

                        .share-time {
                            margin-left: 16px;
                            font-size: 12px;
                            color: var(--text-secondary);
                        }
                    }

                    .file-name {
                        margin-top: 8px;
                        font-size: 12px;
                        color: var(--text-secondary);
                    }
                }
            }

            .code-body {
                padding: 24px 20px 28px;

                .tips {
                    font-size: 13px;
                    font-weight: 700;
                    letter-spacing: 0.06em;
                    color: var(--text-main);
                }

                .input-area {
                    margin-top: 14px;

                    .input {
                        flex: 1;
                        margin-right: 10px;
                    }
                }
            }
        }
    }
}

@media screen and (max-width: 768px) {
    .share .body-content {
        margin-top: 8vh;
    }

    .share .body-content .code-panel .file-info .share-info .user-info .share-time {
        margin-left: 0;
        width: 100%;
        margin-top: 4px;
    }
}
</style>

