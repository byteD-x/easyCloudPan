<template>
  <div class="header">
    <div class="header-left">
      <button
        class="mobile-menu-btn"
        type="button"
        aria-label="打开菜单"
        @click="emit('update:mobileMenuOpen', true)"
      >
        <span class="iconfont icon-more"></span>
      </button>
      <div class="logo" @click="goHome">
          <span class="iconfont icon-pan"></span>
          <div class="name">易云盘</div>
      </div>
    </div>
    <div class="right-panel">
        <el-tooltip
          :content="`已用 ${proxy.Utils.size2Str(useSpaceInfo.useSpace)} / 总量 ${proxy.Utils.size2Str(useSpaceInfo.totalSpace)}  ·  剩余 ${remainSpaceLabel} (${remainSpacePercent}%)`"
          placement="bottom"
          :show-after="300"
        >
          <div :class="['space-chip', `state-${useSpaceState}`, spaceChipUpdating ? 'updating' : '']">
            <span class="label">空间</span>
            <span class="value">{{ useSpacePercent }}%</span>
            <div class="space-meter">
              <span :class="['fill', `fill-${useSpaceState}`]" :style="{ width: `${useSpacePercent}%` }"></span>
            </div>
            <button
              type="button"
              class="refresh-space"
              :aria-busy="refreshingUseSpace"
              aria-label="刷新空间状态"
              @click.stop="getUseSpace"
            >
              <span :class="['iconfont', 'icon-refresh', refreshingUseSpace ? 'spin' : '']"></span>
            </button>
          </div>
        </el-tooltip>
        <el-popover
          v-model:visible="showUploader"
          :width="uploaderPopoverWidth"
          trigger="click"
          :offset="isMobileDevice ? 10 : 20"
          transition="none"
          :hide-after="0"
          :popper-style="{ padding: '0px' }"
          :placement="isMobileDevice ? 'bottom-start' : 'bottom'"
          :teleported="true"
        >
        <template #reference>
          <div class="uploader-trigger">
            <span class="iconfont icon-transfer"></span>
            <span v-if="activeTaskCount > 0" class="count-tag">{{ activeTaskCount }}</span>
          </div>
        </template>
        <template #default>
          <Uploader
            ref="uploaderRef"
            @upload-callback="uploadCallbackHandler"
            @update:active-task-count="updateActiveTaskCount"
        ></Uploader>
        </template>
      </el-popover>

      <el-dropdown>
        <div class="user-info">
            <div class="avatar">
                <Avatar
                  :user-id="userInfo.userId"
                  :avatar="userInfo.avatar"
                  :timestamp="timestamp"
                  :width="36"
                  ></Avatar>
            </div>
            <span class="nick-name">{{ userInfo.nickName }}</span>
        </div>
        <template #dropdown>
            <el-dropdown-menu>
                <el-dropdown-item @click="updateAvatar">修改头像</el-dropdown-item>
                <el-dropdown-item @click="updateNickName">修改昵称</el-dropdown-item>
                <el-dropdown-item @click="updatePassword">修改密码</el-dropdown-item>
                <el-dropdown-item @click="logout">退出</el-dropdown-item>
            </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
    
    <!-- 修改头像 -->
    <UpdateAvatar ref="updateAvatarRef" @update-avatar="reloadAvatar"></UpdateAvatar>
    <!-- 修改昵称 -->
    <UpdateNickName ref="updateNickNameRef" @update-nick-name="reloadNickName"></UpdateNickName>
    <!-- 修改密码 -->
    <UpdatePassword ref="updatePasswordRef"></UpdatePassword>
  </div>
</template>

<script setup>
import { ref, getCurrentInstance, nextTick, watch, onMounted, onUnmounted, computed } from "vue";
import { useRouter } from "vue-router";
import { useUserInfoStore } from "@/stores/userInfoStore";
import * as accountService from "@/services/accountService";
import EventBus from "@/utils/EventBus";
import Uploader from "@/views/main/Uploader.vue";
import UpdateAvatar from "@/views/UpdateAvatar.vue";
import UpdateNickName from "@/views/UpdateNickName.vue";
import UpdatePassword from "@/views/UpdatePassword.vue";
import { useSpaceMonitor } from "@/composables/useSpaceMonitor";

defineProps({
  mobileMenuOpen: Boolean
});
const emit = defineEmits(['update:mobileMenuOpen']);

const { proxy } = getCurrentInstance();
const router = useRouter();
const userInfoStore = useUserInfoStore();

const {
  useSpaceInfo,
  spaceChipUpdating,
  refreshingUseSpace,
  useSpacePercent,
  remainSpaceLabel,
  remainSpacePercent,
  useSpaceState,
  getUseSpace,
  ensureSpaceAutoRefresh,
  disposeSpaceMonitor
} = useSpaceMonitor();

const goHome = () => {
  router.push("/");
};

//显示上传窗口
const showUploader = ref(false);

// 添加文件
const uploaderRef = ref();
const addFile = (file, filePid) => {
  showUploader.value = true;
  uploaderRef.value.addFile(file, filePid);
};
defineExpose({ addFile, getUseSpace: () => getUseSpace() });

// 上传文件回调
const uploadCallbackHandler = () => {
    nextTick(() => {
    });
};

// 事件总线监听
const handleGlobalReload = () => {
    getUseSpace();
};

onMounted(() => {
    EventBus.on('reload_data', handleGlobalReload);
});

onUnmounted(() => {
    EventBus.off('reload_data', handleGlobalReload);
    disposeSpaceMonitor();
});

const activeTaskCount = ref(0);
const updateActiveTaskCount = (count) => {
  activeTaskCount.value = count;
};

watch(activeTaskCount, (count) => {
    ensureSpaceAutoRefresh(count);
});

const timestamp = ref(0);
//获取用户信息
const userInfo = computed(() => userInfoStore.userInfo);

// 修改头像
const updateAvatarRef = ref();
const updateAvatar = () => {
    updateAvatarRef.value.show(userInfo.value);
};

const reloadAvatar = () => {
    // 状态仓库已具备响应式；这里通过更新时间戳强制刷新头像缓存
    timestamp.value = new Date().getTime();
};

// 修改昵称
const updateNickNameRef = ref();
const updateNickName = () => {
    updateNickNameRef.value.show(userInfo.value);
};

const reloadNickName = () => {
    // 由 Store 响应式更新，无需额外逻辑
};

// 修改密码
const updatePasswordRef = ref();
const updatePassword = () => {
    updatePasswordRef.value.show();
};

// 退出
const logout = async () => {
    proxy.Confirm(`你确定要退出吗`, async () => {
        const result = await accountService.logout();
        if (!result){
            return;
        }
        userInfoStore.clearUserInfo();
        router.push("/login");
    });
};

// 移动端检测
const windowWidth = ref(window.innerWidth);
const isMobileDevice = computed(() => windowWidth.value < 768);
const uploaderPopoverWidth = computed(() => {
    if (isMobileDevice.value) {
        return Math.min(windowWidth.value - 24, 360);
    }
    return Math.min(800, windowWidth.value - 100);
});

const updateWindowWidth = () => {
    windowWidth.value = window.innerWidth;
};

onMounted(() => {
    window.addEventListener('resize', updateWindowWidth);
});

onUnmounted(() => {
    window.removeEventListener('resize', updateWindowWidth);
});

getUseSpace();
</script>

<style lang="scss" scoped>
.header {
    position: relative;
    width: 100vw;
    height: 56px;
    padding: 0 24px;
    z-index: 999;
    display: flex;
    align-items: center;
    justify-content: space-between;
    background: #fff;
    border-bottom: 1px solid var(--border-color);
    box-shadow: 0 2px 6px rgba(0, 0, 0, 0.04);
    transition: background 0.3s ease;

    .header-left {
        display: flex;
        align-items: center;
        gap: 12px;
    }

    .mobile-menu-btn {
        display: none;
        width: 42px;
        height: 42px;
        border-radius: 14px;
        background: rgba(255, 255, 255, 0.76);
        border: 1px solid var(--border-color);
        cursor: pointer;
        color: var(--text-secondary);
        transition: var(--transition-fast);

        &:hover {
            color: var(--text-main);
            border-color: var(--primary-light);
            box-shadow: 0 8px 16px rgba(31, 79, 104, 0.16);
        }

        .iconfont {
            font-size: 20px;
        }
    }

    .logo {
        display: flex;
        align-items: center;
        cursor: pointer;
        gap: 10px;

        .icon-pan {
            font-size: 22px;
            width: 34px;
            height: 34px;
            border-radius: 10px;
            display: flex;
            align-items: center;
            justify-content: center;
            color: #fff;
            background: var(--primary);
            box-shadow: 0 8px 16px rgba(31, 79, 104, 0.26);
            transition: var(--transition-normal);
        }

        .name {
            font-size: 19px;
            font-weight: 700;
            letter-spacing: 0.03em;
            color: var(--text-main);
            font-family: var(--font-heading);
        }

        &:hover .icon-pan {
            box-shadow: 0 10px 20px rgba(6, 167, 255, 0.3);
        }
    }

    .right-panel {
        display: flex;
        align-items: center;
        gap: 10px;

        .space-chip {
            display: inline-flex;
            align-items: center;
            gap: 8px;
            height: 36px;
            padding: 0 12px;
            border-radius: 999px;
            background: rgba(36, 95, 124, 0.08);
            border: 1px solid rgba(31, 79, 104, 0.24);
            color: var(--text-secondary);
            cursor: default;
            transition: box-shadow 0.24s ease, border-color 0.24s ease, background 0.24s ease;

            &.updating {
                border-color: rgba(31, 79, 104, 0.5);
                box-shadow: 0 0 0 2px rgba(31, 79, 104, 0.12);
            }

            &.state-safe {
                background: rgba(36, 95, 124, 0.08);
                border-color: rgba(31, 79, 104, 0.24);
            }

            &.state-warning {
                background: rgba(154, 118, 83, 0.12);
                border-color: rgba(154, 118, 83, 0.3);
            }

            &.state-danger {
                background: rgba(178, 81, 81, 0.12);
                border-color: rgba(178, 81, 81, 0.32);
            }

            .label {
                font-size: 11px;
                letter-spacing: 0.03em;
                opacity: 0.7;
            }

            .value {
                font-size: 13px;
                font-weight: 700;
                color: var(--text-main);
            }

            .space-meter {
                width: 48px;
                height: 3px;
                border-radius: 999px;
                background: rgba(122, 147, 163, 0.28);
                overflow: hidden;
                flex-shrink: 0;

                .fill {
                    display: block;
                    height: 100%;
                    border-radius: 999px;
                    transition: width 0.34s cubic-bezier(0.22, 1, 0.36, 1), background 0.2s ease;
                }

                .fill-safe {
                    background: linear-gradient(90deg, #2f6d8c 0%, #1f4f68 100%);
                }

                .fill-warning {
                    background: linear-gradient(90deg, #2f6d8c 0%, #9a7653 100%);
                }

                .fill-danger {
                    background: linear-gradient(90deg, #bf6666 0%, #b25151 100%);
                }
            }

            .refresh-space {
                width: 22px;
                height: 22px;
                border-radius: 6px;
                background: transparent;
                border: none;
                color: var(--text-light);
                display: inline-flex;
                align-items: center;
                justify-content: center;
                cursor: pointer;
                transition: var(--transition-fast);
                margin-left: -2px;

                .iconfont {
                    font-size: 12px;
                }

                &:hover {
                    color: var(--primary-dark);
                    background: rgba(31, 79, 104, 0.1);
                }

                .spin {
                    animation: rotateSpaceRefresh 0.8s linear infinite;
                }
            }
        }

        .uploader-trigger {
            position: relative;
            cursor: pointer;
            width: 38px;
            height: 38px;
            display: flex;
            align-items: center;
            justify-content: center;
            border-radius: 12px;
            background: rgba(255, 255, 255, 0.8);
            border: 1px solid var(--border-color);
            transition: var(--transition-fast);

            &:hover {
                border-color: var(--primary-light);
                box-shadow: 0 4px 12px rgba(6, 167, 255, 0.15);

                .icon-transfer {
                    transform: rotate(12deg);
                }
            }
        }

        .icon-transfer {
            font-size: 18px;
            color: var(--text-secondary);
            transition: transform 0.2s ease-out;
        }

        .count-tag {
            position: absolute;
            top: -3px;
            right: -3px;
            min-width: 18px;
            height: 18px;
            line-height: 18px;
            padding: 0 5px;
            border-radius: 9px;
            text-align: center;
            font-size: 11px;
            font-weight: 700;
            color: #fff;
            background: var(--primary);
            box-shadow: 0 8px 14px rgba(31, 79, 104, 0.24);
        }

        .user-info {
            display: flex;
            align-items: center;
            gap: 8px;
            cursor: pointer;
            padding: 4px 8px 4px 4px;
            border-radius: 999px;
            border: 1px solid transparent;
            transition: var(--transition-fast);

            &:hover {
                border-color: rgba(6, 167, 255, 0.2);
                background: rgba(6, 167, 255, 0.05);
            }

            .avatar {
                display: flex;
            }

            .nick-name {
                max-width: 120px;
                overflow: hidden;
                text-overflow: ellipsis;
                white-space: nowrap;
                font-size: 13px;
                font-weight: 600;
                color: var(--text-main);
            }
        }
    }
}

@keyframes rotateSpaceRefresh {
    from {
        transform: rotate(0deg);
    }
    to {
        transform: rotate(360deg);
    }
}

@media screen and (max-width: 768px) {
    .header {
        height: 62px;
        padding: 0 12px;

        .mobile-menu-btn {
            display: inline-flex;
            align-items: center;
            justify-content: center;
        }

        .space-chip {
            .label {
                display: none;
            }

            .space-meter {
                width: 32px;
            }
        }

        .logo {
            .icon-pan {
                width: 34px;
                height: 34px;
                font-size: 22px;
            }

            .name {
                font-size: 16px;
            }
        }

        .right-panel {
            gap: 8px;

            .user-info .nick-name {
                display: none;
            }
        }
    }
}
</style>
