<template>
  <div>
    <div class="top">
      <el-button
        type="success"
        :disabled="selectIdList.length == 0"
        @click="revertBatch"
      >
        <span class="iconfont icon-revert"></span>
        还原</el-button
      >
      <el-button
        type="danger"
        :disabled="selectIdList.length == 0"
        @click="delBatch"
      >
        <span class="iconfont icon-del"></span>
        批量删除</el-button
      >
    </div>
    <div class="file-list">
      <Table
        ref="dataTableRef"
        :columns="columns"
        :data-source="tableData"
        :fetch="loadDataList"
        :init-fetch="true"
        :options="tableOptions"
        :skeleton="true"
        @row-selected="rowSelected"
      >
        <template #fileName="{ row }">
          <div
            v-touch="{
              onLongPress: () => showOp(row),
              onSwipeLeft: () => delFile(row),
              onSwipeRight: () => revert(row),
            }"
            :class="['file-item', row.showOp ? 'show-op' : '']"
            @mouseenter="showOp(row)"
            @mouseleave="cancelShowOp(row)"
          >
            <template
              v-if="
                (row.fileType == 3 || row.fileType == 1) && row.status !== 0
              "
              >
              <Icon :cover="row.fileCover"></Icon>
            </template>
              <template v-else>
                <Icon v-if="row.folderType == 0" :file-type="row.fileType"></Icon>
                <Icon v-if="row.folderType == 1" :file-type="0"></Icon>
              </template>
                <span class="file-name" :title="row.fileName">{{
                  row.fileName
                }}</span>
            <span class="op">
              <template v-if="row.showOp">
                <span class="iconfont icon-link" @click="revert(row)"
                  >还原</span
                >
                <span class="iconfont icon-cancel" @click="delFile(row)"
                  >删除</span
                >
              </template>
            </span>
          </div>
        </template>
        <template #fileSize="{ row }">
          <span v-if="row.fileSize">
            {{ proxy.Utils.size2Str(row.fileSize) }}
          </span>
        </template>
      </Table>
    </div>
  </div>
</template>

<script setup>
import { ref, getCurrentInstance } from "vue";
import * as recycleService from "@/services/recycleService";
const { proxy } = getCurrentInstance();


const columns = [
    {
        label: "文件名",
        prop: "fileName",
        scopedSlots: "fileName",
    },
    {
        label: "删除时间",
        prop: "recoveryTime",
        width: 200,
    },
    {
        label: "大小",
        prop: "fileSize",
        scopedSlots: "fileSize",
        width: 200,
    },
];
const tableData = ref({});
const tableOptions = {
    extHeight: 20,
    selectType: "checkbox",
};

const loadDataList = async () => {
    const params = {
        pageNo: tableData.value.pageNo,
        pageSize: tableData.value.pageSize,
    };
    const result = await recycleService.loadRecycleList(params);
    if (!result) {
        return;
    }
    tableData.value = result;
};

// 多选 批量选择
const selectIdList = ref([]);
const rowSelected = (rows) => {
    selectIdList.value = [];
    rows.forEach((item) => {
        selectIdList.value.push(item.fileId);
    });
};

const showOp = (row) => {
    const nextShow = !row.showOp;
    tableData.value.list.forEach((item) => {
        item.showOp = false;
    });
    row.showOp = nextShow;
};

const cancelShowOp = (row) => {
    row.showOp = false;
};

// 恢复
const revert = (row) => {
    proxy.Confirm(`你确定要还原【${row.fileName}】吗?`, async () => {
        // 先行更新（Optimistic UI）
        const index = tableData.value.list.findIndex(item => item.fileId === row.fileId);
        if (index !== -1) {
            tableData.value.list.splice(index, 1);
        }

        const result = await recycleService.recoverFile(row.fileId);
        if (!result) {
            loadDataList(); // 失败时回滚
            return;
        }
    });
};

const revertBatch = () => {
    if (selectIdList.value.length == 0) return;
    proxy.Confirm(`你确定要还原这些文件吗?`, async () => {
        // 先行更新（Optimistic UI）
        const ids = selectIdList.value;
        const backupList = [...tableData.value.list];
        tableData.value.list = tableData.value.list.filter(item => !ids.includes(item.fileId));

        const result = await recycleService.recoverFile(selectIdList.value.join(","));
        if (!result) {
            tableData.value.list = backupList; // 回滚
            return;
        }
        selectIdList.value = [];
        loadDataList();
    });
};

// 删除文件
const emit = defineEmits(["reload"]);
const delFile = (row) => {
    proxy.Confirm(`你确定要删除【${row.fileName}】吗? 删除后无法恢复`, async () => {
        // 先行更新（Optimistic UI）
        const index = tableData.value.list.findIndex(item => item.fileId === row.fileId);
        if (index !== -1) {
            tableData.value.list.splice(index, 1);
        }

        const result = await recycleService.delFile(row.fileId);
        if (!result) {
            loadDataList(); // 失败时回滚
            return;
        }
        emit("reload");
    });
};

const delBatch = () => {
    if (selectIdList.value.length == 0) return;
    proxy.Confirm(`你确定要删除这些文件吗? 删除后无法恢复`, async () => {
        // 先行更新（Optimistic UI）
        const ids = selectIdList.value;
        const backupList = [...tableData.value.list];
        tableData.value.list = tableData.value.list.filter(item => !ids.includes(item.fileId));

        const result = await recycleService.delFile(selectIdList.value.join(","));
        if (!result) {
            tableData.value.list = backupList; // Revert
            return;
        }
        selectIdList.value = [];
        emit("reload");
        loadDataList();
    });
};
</script>

<style lang="scss" scoped>
@import "@/assets/file.list.scss";
.file-list {
    margin-top: 10px;

    .file-item {
        .op {
            width: 120px;
        }
    }
}

.top {
    .el-button {
        min-width: 112px;
        letter-spacing: 0.03em;
    }
}
</style>
