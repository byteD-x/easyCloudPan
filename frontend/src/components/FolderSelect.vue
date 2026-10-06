<template>
  <div>
    <Dialog
      :show="dialogConfig.show"
      :title="dialogConfig.title"
      :buttons="dialogConfig.buttons"
      width="600px"
      :show-cancel="false"
      @close="dialogConfig.show = false"
    >
      <!-- 搜索栏 -->
      <div class="search-bar">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索目录..."
          clearable
          size="small"
          @input="handleSearch"
        >
          <template #prefix>
            <span class="iconfont icon-search"></span>
          </template>
        </el-input>
        <el-dropdown v-if="recentFolders.length > 0" trigger="click" @command="selectRecentFolder">
          <el-button text size="small">
            <span class="iconfont icon-time"></span>
            最近
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item
                v-for="folder in recentFolders"
                :key="folder.fileId"
                :command="folder"
              >
                <Icon :file-type="0" :width="16"></Icon>
                <span class="recent-name">{{ folder.fileName }}</span>
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
      
      <!-- 导航面板 -->
      <div class="navigation-panel">
        <Navigation
          ref="navigationRef"
          :watch-path="false"
          @nav-change="navChange"
        ></Navigation>
      </div>
      
      <!-- 目录树容器 -->
      <div class="folder-tree-container">
        <!-- 根目录选项 -->
        <div
          :class="['tree-node', 'root-node', { selected: filePid === '0' }]"
          @click="selectRootFolder"
        >
          <span class="iconfont icon-home"></span>
          <span class="node-name">全部文件</span>
          <span v-if="filePid === '0'" class="iconfont icon-check check-icon"></span>
        </div>
        
        <!-- 搜索结果 -->
        <div v-if="searchKeyword && filteredFolders.length > 0" class="search-results">
          <div
            v-for="item in filteredFolders"
            :key="item.fileId"
            :class="['tree-node', { selected: filePid === item.fileId }]"
            @click="selectSearchResult(item)"
          >
            <Icon :file-type="0" :width="20"></Icon>
            <span class="node-name">{{ item.fileName }}</span>
            <span v-if="filePid === item.fileId" class="iconfont icon-check check-icon"></span>
          </div>
        </div>
        
        <!-- 平铺目录列表 -->
        <div v-else-if="folderList.length > 0" class="folder-list-tree">
          <div
            v-for="item in folderList"
            :key="item.fileId"
            :class="['tree-node', { selected: filePid === item.fileId }]"
            @click="selectFolderNode(item)"
          >
            <Icon :file-type="0" :width="20"></Icon>
            <span class="node-name">{{ item.fileName }}</span>
            <span v-if="item.fileId === currentFolder.fileId" class="current-badge">当前</span>
            <span v-if="filePid === item.fileId" class="iconfont icon-check check-icon"></span>
          </div>
        </div>
        
        <!-- 空状态 -->
        <div v-else class="empty-state">
          <span class="iconfont icon-folder"></span>
          <p>当前目录下没有子文件夹</p>
        </div>
      </div>
      
      <!-- 当前选择提示 -->
      <div class="selection-info">
        <span class="label">移动到：</span>
        <span class="path">{{ currentPath }}</span>
      </div>
    </Dialog>
  </div>
</template>

<script setup>
import { ref, computed } from "vue";
import { loadAllFolder as loadAllFolderService } from "@/services";

const dialogConfig = ref({
    show: false,
    title: "移动到",
    buttons: [
        {
            type: "primary",
            text: "移动到此文件夹",
            click: () => {
                folderSelect();
            },
        },
    ],
});

// 父级ID
const filePid = ref("0");
const currentFileIds = ref(undefined);
const folderList = ref([]);
const searchKeyword = ref("");
const filteredFolders = ref([]);
const recentFolders = ref([]);

// 当前文件夹
const currentFolder = ref({ fileId: "0", fileName: "全部文件" });

// 当前路径
const currentPath = computed(() => {
  if (filePid.value === "0") return "全部文件";
  const folder = folderList.value.find(f => f.fileId === filePid.value);
  return folder ? folder.fileName : "未知目录";
});

// 加载所有文件夹
const loadAllFolder = async () => {
    const result = await loadAllFolderService({
        filePid: "0",
        currentFileIds: currentFileIds.value,
    });
    if (!result) {
        return;
    }
    folderList.value = result;
};

const close = () => {
    dialogConfig.value.show = false;
    searchKeyword.value = "";
    filteredFolders.value = [];
};

const showFolderDialog = (currentFileIdsParam) => {
    dialogConfig.value.show = true;
    currentFileIds.value = typeof currentFileIdsParam === "string" ? currentFileIdsParam : undefined;
    filePid.value = "0";
    currentFolder.value = { fileId: "0", fileName: "全部文件" };
    searchKeyword.value = "";
    loadAllFolder();
    loadRecentFolders();
};

defineExpose({
    showFolderDialog,
    close,
});

// 搜索
const handleSearch = () => {
  if (!searchKeyword.value) {
    filteredFolders.value = [];
    return;
  }
  const keyword = searchKeyword.value.toLowerCase();
  filteredFolders.value = folderList.value.filter(item => 
    item.fileName.toLowerCase().includes(keyword)
  );
};

// 选择搜索结果
const selectSearchResult = (item) => {
  filePid.value = item.fileId;
  currentFolder.value = item;
};

// 选择根目录
const selectRootFolder = () => {
  filePid.value = "0";
  currentFolder.value = { fileId: "0", fileName: "全部文件" };
};

// 选择目录节点
const selectFolderNode = (item) => {
  filePid.value = item.fileId;
  currentFolder.value = item;
};

// 最近使用的目录
const RECENT_KEY = "recent_move_folders";
const loadRecentFolders = () => {
  try {
    const data = localStorage.getItem(RECENT_KEY);
    recentFolders.value = data ? JSON.parse(data) : [];
  } catch {
    recentFolders.value = [];
  }
};

const saveRecentFolder = (folder) => {
  const list = recentFolders.value.filter(f => f.fileId !== folder.fileId);
  list.unshift(folder);
  recentFolders.value = list.slice(0, 5);
  localStorage.setItem(RECENT_KEY, JSON.stringify(recentFolders.value));
};

const selectRecentFolder = (folder) => {
  filePid.value = folder.fileId;
  currentFolder.value = folder;
};

// 确定选择目录
const emit = defineEmits(["folderSelect"]);
const folderSelect = () => {
    // 保存到最近使用
    if (currentFolder.value.fileId !== "0") {
      saveRecentFolder(currentFolder.value);
    }
    emit("folderSelect", filePid.value);
};

// 导航改变回调
const navigationRef = ref();
const navChange = (data) => {
    const { curFolder } = data;
    currentFolder.value = curFolder;
    filePid.value = curFolder.fileId;
    loadAllFolder();
};
</script>

<style lang="scss" scoped>
.search-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;

  .el-input {
    flex: 1;
  }

  .el-button {
    white-space: nowrap;
  }
}

.navigation-panel {
    padding: 2px 8px 0;
    border-radius: 12px;
    background: var(--btn-secondary-bg);
    border: 1px solid var(--border-color);
    margin-bottom: 10px;
}

.folder-tree-container {
    border-radius: 12px;
    border: 1px solid var(--border-color);
    background: rgba(255, 255, 255, 0.6);
    max-height: calc(100vh - 280px);
    min-height: 180px;
    overflow: auto;
    padding: 4px;
}

.tree-node {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 8px;
  cursor: pointer;
  transition: var(--transition-fast);

  &:hover {
    background: rgba(31, 79, 104, 0.08);
  }

  &.selected {
    background: rgba(31, 79, 104, 0.14);
    
    .node-name {
      font-weight: 600;
      color: var(--primary-dark);
    }
  }

  .node-name {
    flex: 1;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .check-icon {
    color: var(--primary);
    font-size: 14px;
  }

  .current-badge {
    font-size: 10px;
    padding: 1px 6px;
    border-radius: 4px;
    background: rgba(154, 118, 83, 0.16);
    color: #9a7653;
  }
}

.root-node {
  .icon-home {
    color: var(--primary);
    font-size: 16px;
  }
}

.search-results {
  .tree-node {
    padding-left: 16px;
  }
}

.folder-list-tree {
  .tree-node {
    padding-left: 12px;
  }
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
  color: var(--text-light);

  .iconfont {
    font-size: 48px;
    margin-bottom: 12px;
    opacity: 0.5;
  }

  p {
    margin: 0;
    font-size: 13px;
  }
}

.selection-info {
  margin-top: 12px;
  padding: 8px 12px;
  border-radius: 8px;
  background: rgba(31, 79, 104, 0.06);
  font-size: 13px;

  .label {
    color: var(--text-light);
  }

  .path {
    color: var(--primary-dark);
    font-weight: 600;
  }
}

.recent-name {
  margin-left: 8px;
}

:deep(.el-dropdown-menu__item) {
  display: flex;
  align-items: center;
}
</style>
