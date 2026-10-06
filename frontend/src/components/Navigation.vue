<template>
    <div class="top-navigation">
        <template v-if="folderList.length > 0">
            <span class="back link" @click="backParent">返回上一级</span>
            <el-divider direction="vertical"></el-divider>
        </template>
        <span v-if="folderList.length == 0" class="all-file">全部文件</span>
        <span
          v-if="folderList.length > 0"
          class="link"
          @click="setCurrentFolder(-1)"
        >
          全部文件
        </span>
        
        <!-- 短路径：直接显示 -->
        <template v-if="folderList.length <= 4">
          <template v-for="(item, index) in folderList" :key="item.fileId || index">
              <span class="iconfont icon-right"></span>
              <span
                v-if="index < folderList.length - 1"
                class="link"
                @click="setCurrentFolder(index)"
                >{{ item.fileName }}</span
              >
              <span v-if="index == folderList.length - 1" class="text">{{
                item.fileName
              }}</span>
          </template>
        </template>
        
        <!-- 长路径：折叠中间 -->
        <template v-else>
          <span class="iconfont icon-right"></span>
          <el-dropdown trigger="click" @command="setCurrentFolder">
            <span class="link collapsed">
              <span class="iconfont icon-more"></span>
              <span class="collapsed-count">{{ folderList.length - 1 }}级</span>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item
                  v-for="(item, index) in folderList.slice(0, -1)"
                  :key="item.fileId"
                  :command="index"
                >
                  {{ item.fileName }}
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <span class="iconfont icon-right"></span>
          <span class="text">{{ folderList[folderList.length - 1].fileName }}</span>
        </template>
    </div>
</template>

<script setup>
import { ref, watch } from "vue";
import { getFileFolderInfo, getShareFolderInfo, getAdminFolderInfo } from "@/services";
import { useRouter, useRoute } from "vue-router";
const router = useRouter();
const route = useRoute();

const props = defineProps({
    watchPath: {
        type: Boolean,
        default: true,
    },
    shareId: {
        type: String,
    },
    adminShow: {
        type: Boolean,
        default: false,
    },
});

// 分类
const category = ref();
// 目录集合
const folderList = ref([]);
// 当前目录
const currentFolder = ref({fileId: "0"});

const init = () => {
    folderList.value = [];
    currentFolder.value = {fileId: "0"};
    doCallback();
};

const openFolder = (data) => {
    const { fileId, fileName } = data;
    const folder = {
        fileName: fileName,
        fileId: fileId,
    };
    folderList.value.push(folder);
    currentFolder.value = folder;
    setPath();
};
defineExpose({ openFolder });

// 返回上一级
const backParent = () => {
    let currentIndex = null;
    for (let i = 0; i < folderList.value.length; i++) {
        if (folderList.value[i].fileId == currentFolder.value.fileId) {
            currentIndex = i;
            break;
        }
    }
    setCurrentFolder(currentIndex - 1);
};
// 点击导航  设置当前目录
const setCurrentFolder = (index) => {
    if (index == -1) {
      // 返回全部
      currentFolder.value = { fileId: "0" };
      folderList.value = [];
    } else {
        currentFolder.value = folderList.value[index];
        folderList.value.splice(index + 1, folderList.value.length);
    }
    setPath();
};

const setPath = () => {
    if (!props.watchPath) {
        // TODO 设置不监听路由回调方法
        doCallback();
        return;
    }
    const pathArray = [];
    folderList.value.forEach(item => {
        pathArray.push(item.fileId);
    })
    router.push({
        path: route.path,
        query: pathArray.length == 0 ? "" : { path: pathArray.join("/") },
    });
};

// 获取当前路径的目录
const getNavigationFolder = async (path) => {
    let result = null;
    if (props.shareId) {
        result = await getShareFolderInfo(props.shareId, path);
    } else if (props.adminShow) {
        result = await getAdminFolderInfo(path);
    } else {
        result = await getFileFolderInfo(path);
    }
    if (!result) {
        return;
    }
    folderList.value = result;
};

const emit = defineEmits(["navChange"]);
const doCallback = () => {
    emit("navChange", {
        categoryId: category.value,
        curFolder: currentFolder.value,
    });
};

watch(
    () => [route.query.path, route.params.category, route.path],
    (newValues, _oldValues) => {
        if (!props.watchPath) {
            return;
        }
        const [path, categoryId, routePath] = newValues;
        
        if (routePath.indexOf("/main") === -1 &&
            routePath.indexOf("/settings/fileList") === -1 &&
            routePath.indexOf("/share") === -1
        ) {
            return;
        }
        category.value = categoryId;
        if (path == undefined) {
            init();
        } else {
            getNavigationFolder(path);
            const pathArray = path.split("/");
            currentFolder.value = {
                fileId: pathArray[pathArray.length - 1],
            };
            doCallback();
        }
    },
    { immediate: true, deep: false }
);
</script>

<style lang="scss" scoped>
.top-navigation {
    margin-top: 10px;
    padding: 8px 10px;
    border-radius: 12px;
    border: 1px solid rgba(189, 208, 202, 0.72);
    background: rgba(255, 255, 255, 0.72);
    font-size: 13px;
    display: flex;
    align-items: center;
    line-height: 1.6;
    min-height: 40px;
    flex-wrap: wrap;
    color: var(--text-secondary);
    transition: border-color 0.2s ease-out;
    
    .all-file {
        font-weight: 700;
        letter-spacing: 0.02em;
        color: var(--text-main);
    }
    
    .link {
        color: var(--primary);
        cursor: pointer;
        transition: all 0.15s ease-out;
        padding: 2px 6px;
        border-radius: 6px;
        
        &:hover {
            color: var(--primary-dark);
            background: rgba(6, 167, 255, 0.08);
        }
    }
    
    .icon-right {
        color: var(--text-light);
        padding: 0 5px;
        font-size: 11px;
        opacity: 0.4;
    }
    
    .text {
        color: var(--text-secondary);
        font-weight: 600;
        cursor: default;
    }
    
    .collapsed {
        display: inline-flex;
        align-items: center;
        gap: 4px;
        padding: 2px 8px;
        border-radius: 6px;
        background: rgba(31, 79, 104, 0.08);
        
        .icon-more {
            font-size: 12px;
        }
        
        .collapsed-count {
            font-size: 11px;
            opacity: 0.8;
        }
        
        &:hover {
            background: rgba(31, 79, 104, 0.14);
        }
    }
}
</style>
