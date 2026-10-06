<template>
  <div 
      v-touch:swipe.left="() => emit('update:mobileMenuOpen', false)"
      :class="['left-sider', mobileMenuOpen ? 'open' : '']"
  >
      <div class="menu-list">
          <template v-for="item in menus" :key="item.menuCode">
          <div
          v-if="item.allShow || userInfo.admin"
          :class="[
              'menu-item',
              item.menuCode == currentMenu.menuCode ? 'active' : '',
              ]"
          @click="jump(item)"
          >
              <div :class="['iconfont', 'icon-' + item.icon]"></div>
              <div class="text">{{ item.name }}</div>
          </div>
      </template>
        </div>
      <div class="menu-sub-list">
          <div
            v-for="sub in currentMenu.children"
            :key="sub.path"
            :class="['menu-item-sub', currentPath == sub.path ? 'active' : '']"
            @click="jump(sub)"
          >
            <span
            v-if="sub.icon"
            :class="['iconfont', 'icon-' + sub.icon]"
            ></span>
            <span class="text">{{ sub.name }}</span>
        </div>
        <div v-if="currentMenu && currentMenu.tips" class="tips">
          {{ currentMenu.tips }}
        </div>
      </div>
  </div>
</template>

<script setup>
import { ref, watch, computed } from "vue";
import { useRouter, useRoute } from "vue-router";
import { useUserInfoStore } from "@/stores/userInfoStore";

defineProps({
  mobileMenuOpen: Boolean
});
const emit = defineEmits(['update:mobileMenuOpen']);

const router = useRouter();
const route = useRoute();
const userInfoStore = useUserInfoStore();

//获取用户信息
const userInfo = computed(() => userInfoStore.userInfo);

const menus = [
    {
        icon: "cloude",
        name: "首页",
        menuCode: "main",
        path: "/main/all",
        allShow: true,
        children: [
            {
                icon: "all",
                name: "全部",
                category: "all",
                path: "/main/all",
            },
            {
                icon: "video",
                name: "视频",
                category: "video",
                path: "/main/video",
            },
            {
                icon: "music",
                name: "音频",
                category: "music",
                path: "/main/music",
            },
            {
                icon: "image",
                name: "图片",
                category: "image",
                path: "/main/image",
            },
            {
                icon: "doc",
                name: "文档",
                category: "doc",
                path: "/main/doc",
            },
            {
                icon: "more",
                name: "其他",
                category: "others",
                path: "/main/others",
            },
        ],
    },
    {
        path: "/myshare",
        icon: "share",
        name: "分享",
        menuCode: "share",
        allShow: true,
        children: [
            {
                name: "分享记录",
                path: "/myshare",
            },
        ],
    },
    {
        path: "/recycle",
        icon: "del",
        name: "回收站",
        menuCode: "recycle",
        tips: "回收站为你保存10天内删除的文件",
        allShow: true,
        children: [
            {
                name: "删除的文件",
                path: "/recycle",
            },
        ],
    },
    {
        path: "/settings/fileList",
        icon: "settings",
        name: "设置",
        menuCode: "settings",
        allShow: false,
        children: [
            {
                name: "用户文件",
                path: "/settings/fileList",
            },
            {
                name: "用户管理",
                path: "/settings/userList",
            },
            {
                name: "系统设置",
                path: "/settings/sysSetting",
            },
        ],
    },
];

const currentMenu = ref({});
const currentPath = ref();

const jump = (data) => {
    if (!data.path) {
        return;
    }
    if (data.menuCode && data.menuCode == currentMenu.value.menuCode) {
        emit('update:mobileMenuOpen', false);
        return;
    }
    router.push(data.path);
    emit('update:mobileMenuOpen', false);
};

const setMenu = (menuCode, path) => {
    const menu = menus.find((item) => {
        return item.menuCode === menuCode;
    });
    currentMenu.value = menu;
    currentPath.value = path;
};

watch (
    () => route,
    (newVal) => {
        if (newVal.meta.menuCode) {
            setMenu(newVal.meta.menuCode, newVal.path);
        }
        emit('update:mobileMenuOpen', false);
    },
    { immediate: true, deep: true }
);

</script>

<style lang="scss" scoped>
.left-sider {
    width: 260px;
    height: 100%;
    z-index: 900;
    background: #fff;
    border-right: 1px solid var(--border-color);
    box-shadow: 2px 0 8px rgba(0, 0, 0, 0.02);
    display: flex;
    overflow: hidden;
    transition: width 0.3s ease;

    .menu-list {
        width: 72px;
        height: 100%;
        padding: 20px 0;
        border-right: 1px solid var(--border-color);
        background: #f8fafc;

        .menu-item {
            text-align: center;
            font-size: 12px;
            font-weight: 500;
            padding: 12px 0;
            margin: 0 6px 4px;
            border-radius: 10px;
            cursor: pointer;
            color: var(--text-secondary);
            transition: all 0.2s ease-out;
            position: relative;
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            gap: 4px;

            .iconfont {
                font-size: 22px;
                line-height: 1;
                transition: all 0.2s ease-out;
            }

            .text {
                line-height: 1;
            }

            &:hover {
                color: var(--primary);
                background: rgba(6, 167, 255, 0.08);
            }
        }

        .active {
            color: var(--primary);
            background: rgba(6, 167, 255, 0.12);

            .iconfont {
                color: var(--primary);
            }

            &::before {
                content: "";
                position: absolute;
                left: -8px;
                top: 20%;
                width: 3px;
                height: 60%;
                border-radius: 2px;
                background: var(--primary);
            }
        }
    }

    .menu-sub-list {
        flex: 1;
        height: 100%;
        padding: 16px 16px 12px;
        position: relative;
        background: #fff;

        .menu-item-sub {
            height: 40px;
            padding: 0 12px;
            display: flex;
            align-items: center;
            gap: 10px;
            border-radius: 10px;
            margin-bottom: 4px;
            cursor: pointer;
            color: var(--text-main);
            transition: all 0.2s ease-out;

            .iconfont {
                font-size: 16px;
                width: 20px;
                text-align: center;
                flex-shrink: 0;
                color: var(--text-secondary);
                transition: color 0.2s ease-out;
            }

            .text {
                font-size: 13px;
                font-weight: 500;
            }

            &:hover {
                background: rgba(6, 167, 255, 0.06);
            }
        }

        .active {
            background: rgba(6, 167, 255, 0.1);
            color: var(--primary);
            font-weight: 600;
            border-left: 2px solid var(--primary);

            .iconfont {
                color: var(--primary);
            }
        }

        .tips {
            margin-top: 8px;
            padding: 0 8px;
            font-size: 12px;
            line-height: 1.45;
            color: var(--text-light);
        }
    }
}

@media screen and (max-width: 768px) {
    .left-sider {
        position: fixed;
        top: 56px;
        left: 0;
        bottom: 0;
        width: 260px;
        z-index: 320;
        border-radius: 0;
        border-right: 1px solid var(--border-color);
        transform: translateX(-100%);
        transition: transform 0.24s ease;

        &.open {
            transform: translateX(0);
        }
    }
}
</style>
