<template>
    <div class="file-list-panel">
        <div class="top">
          <div class="top-op">
              <div class="search-panel">
                <!-- 搜索文件 -->
                <el-input
                  v-model="fileNameFuzzy"
                  clearable
                  placeholder="请输入文件名搜索"
                  @keyup.enter="search"
                >
                    <template #suffix>
                        <i class="iconfont icon-search" @click="search"></i>
                    </template>
                </el-input>
              </div>
              <div class="iconfont icon-refresh" @click="loadDataList"></div>
              <el-button
                type="danger"
                :style="{ 'margin-left': '10px' }"
                :disabled="selectFileIdList.length == 0"
                @click="delFileBatch"
              >
                <span class="iconfont icon-del"></span>
                批量删除
            </el-button>
          </div>
          <!-- 导航 -->
          <Navigation ref="navigationRef" @nav-change="navChange"></Navigation>
      </div>
      <div v-if="tableData.list && tableData.list.length > 0" class="file-list">
        <Table
          ref="dataTableRef"
          :columns="columns"
          :data-source="tableData"
          :fetch="loadDataList"
          :init-fetch="false"
          :options="tableOptions"
          :skeleton="true"
          @row-selected="rowSelected"
        >
        <template #fileName="{ row }">
            <div
              v-touch="{
                onLongPress: () => showOp(row),
                onSwipeLeft: () => delFile(row),
              }"
              :class="['file-item', row.showOp ? 'show-op' : '']"
              @mouseenter="showOp(row)"
              @mouseleave="cancelShowOp(row)"
            >
              <template
                v-if="(row.fileType == 3 || row.fileType == 1) && row.status == 2"
              >
                <Icon :cover="row.fileCover" :width="32" :admin-user-id="row.userId"></Icon>
              </template>
              <template v-else>
                <Icon v-if="row.folderType == 0" :file-type="row.fileType"></Icon>
                <Icon v-if="row.folderType == 1" :file-type="0"></Icon>
              </template>
              <span v-if="!row.showEdit" class="file-name" :title="row.fileName">
                <span @click="preview(row)">{{ row.fileName }}</span>
                <span v-if="row.status == 0" class="transfer-status">转码中</span>
                <span v-if="row.status == 1" class="transfer-status transfer-fail"
                  >转码失败</span
                >
              </span>
              <span class="op">
                <template v-if="row.showOp && row.fileId && row.status == 2">
                    <span
                      v-if="row.folderType == 0"
                      class="iconfont icon-download"
                      @click="download(row)"
                      >下载</span
                    >
                    <span class="iconfont icon-del" @click="delFile(row)"
                      >删除</span
                    >
                </template>
              </span>
            </div>
        </template>
        <template #fileSize="{ row }">
            <span v-if="row.fileSize">{{
              proxy.Utils.size2Str(row.fileSize)
            }}</span>
        </template>
        </Table>
      </div>
      <!-- 预览 -->
      <Preview ref="previewRef"></Preview>
    </div>
</template>

<script setup>
import { ref, getCurrentInstance } from "vue";
import * as adminService from "@/services/adminService";
const { proxy } = getCurrentInstance();


const columns = [
    {
        label: "文件名",
        prop: "fileName",
        scopedSlots: "fileName",
    },
    {
        label: "发布人",
        prop: "nickName",
        width: 250,
    },
    {
        label: "修改时间",
        prop: "lastUpdateTime",
        width: 200,
        className: "hidden-mobile",
    },
    {
        label: "大小",
        prop: "fileSize",
        scopedSlots: "fileSize",
        width: 200,
        className: "hidden-mobile",
    },
];
// 搜索
const search = () => {
  showLoading.value = true;
  loadDataList();
};

const tableData = ref({
  list: [],
  pageNo: 1,
  pageSize: 15,
  totalCount: 0,
  pageTotal: 0,
});
const tableOptions = ref({
    extHeight: 50,
    selectType: "checkbox",
    tableHeight: "calc(100% - 50px)",
});

// 多选
const selectFileIdList = ref([]);
const rowSelected = (rows) => {
  selectFileIdList.value = [];
  rows.forEach((item) => {
    selectFileIdList.value.push(item.userId + "_" + item.fileId);
  });
};

const fileNameFuzzy = ref();
const showLoading = ref(true);
const loadDataList = async () => {
    const params = {
        pageNo: tableData.value.pageNo || 1,
        pageSize: tableData.value.pageSize || 15,
        fileNameFuzzy: fileNameFuzzy.value,
        filePid: currentFolder.value.fileId,
    };
    const result = await adminService.loadFileList(params);
    if (!result) {
        return;
    }
    tableData.value = result;
};

// 展示操作按钮
const showOp = (row) => {
  const nextShow = !row.showOp;
  tableData.value.list.forEach((element) => {
    element.showOp = false;
  });
  row.showOp = nextShow;
};

const cancelShowOp = (row) => {
    row.showOp = false;
};

// 预览
const navigationRef = ref();
const previewRef = ref();
const preview = (data) => {
  // 目录
  if (data.folderType == 1) {
    navigationRef.value.openFolder(data);
    return;
  }
  // 文件
  if (data.status != 2) {
    proxy.Message.warning("文件未完成转码，无法预览");
    return;
  }
  previewRef.value.showPreview(data, 1);
};

const currentFolder = ref({ fileId: "0" });
const navChange = (data) => {
  const { curFolder } = data;
  currentFolder.value = curFolder;
  showLoading.value = true;
  loadDataList();
};

// 删除
const delFile = (row) => {
  proxy.Confirm(
    `你确定要删除【${row.fileName}】吗? 删除后不可还原`,
    async () => {
      // 先行更新（Optimistic UI）
      const index = tableData.value.list.findIndex(item => item.fileId === row.fileId && item.userId === row.userId);
      if (index !== -1) {
          tableData.value.list.splice(index, 1);
      }

      const result = await adminService.delFile(row.userId + "_" + row.fileId);
      if (!result) {
        loadDataList(); // 失败时回滚
        return;
      }
    }
  );
};

const delFileBatch = () => {
  if (selectFileIdList.value.length == 0) {
    return;
  }
  proxy.Confirm(
    `你确定要删除这些文件吗? 删除后不可还原`,
    async () => {
      // 先行更新（Optimistic UI）
      const ids = selectFileIdList.value;
      const backupList = [...tableData.value.list];
      tableData.value.list = tableData.value.list.filter(item => !ids.includes(item.userId + "_" + item.fileId));

      const result = await adminService.delFile(selectFileIdList.value.join(","));
      if (!result) {
        tableData.value.list = backupList; // Revert
        return;
      }
      
      selectFileIdList.value = [];
      loadDataList(); // Safe silent reload
    }
  );
};

// 下载文件
const download = async (row) => {
  const result = await adminService.createDownloadUrl(row.userId, row.fileId);
  if (!result) {
      return;
  }
  window.location.href = adminService.getDownloadUrl(result);
};
</script>

<style lang="scss" scoped>
@import "@/assets/file.list.scss";
.file-list-panel {
    height: 100%;
    min-height: 0;
    display: flex;
    flex-direction: column;
}
.search-panel {
    margin-left: 0px !important;
}
.file-list {
    margin-top: 10px;
    flex: 1;
    min-height: 0;
    overflow: hidden;
    .file-item {
        position: relative;
        .op {
            width: 120px;
        }
    }
}

@media screen and (max-width: 768px) {
  .file-list .file-item {
      .op {
          display: flex !important;
          position: absolute;
          right: 0;
          top: 0;
          bottom: 0;
          background: rgba(255,255,255,0.9);
          align-items: center;
          justify-content: flex-end;
          padding-right: 10px;
          z-index: 10;
      }
  }
}

@media screen and (max-width: 768px) {
  .top {
    flex-direction: column;
    
    .top-op {
      flex-wrap: wrap;
      gap: 5px;
      
      .search-panel {
        width: 100%;
        margin-left: 0 !important;
      }
      
      .el-button {
        margin-left: 0 !important;
        margin-top: 5px;
      }
    }
  }
}
</style>
