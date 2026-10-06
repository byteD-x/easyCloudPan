<template>
    <div>
        <div class="top">
            <el-button
              type="primary"
              :disabled="selectIdList.length == 0"
              @click="cancelShareBatch"
            >
              <span class="iconfont icon-cancel"></span>
              取消分享</el-button
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
                  onSwipeLeft: () => cancelShare(row),
                  onSwipeRight: () => copy(row),
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
                    <span class="iconfont icon-link" @click="copy(row)"
                      >复制链接</span
                    >
                    <span class="iconfont icon-cancel" @click="cancelShare(row)"
                      >取消分享</span
                    >
                </template>
              </span>
              </div>
            </template>
            <template #expireTime="{ row }">
              {{ row.validType == 3 ? "永久" : row.expireTime }}
            </template>
          </Table>
        </div>
    </div>
</template>

<script setup>
import useClipboard from "vue-clipboard3";
const { toClipboard } = useClipboard();

import { ref, getCurrentInstance } from "vue";
import * as shareService from "@/services/shareService";
const { proxy } = getCurrentInstance();


const columns = [
    {
        label: "文件名",
        prop: "fileName",
        scopedSlots: "fileName",
    },
    {
        label: "分享时间",
        prop: "shareTime",
        width: 200,
    },
    {
        label: "失效时间",
        prop: "expireTime",
        scopedSlots: "expireTime",
        width: 200,
    },
    {
        label: "浏览次数",
        prop: "showCount",
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
    const result = await shareService.loadShareList(params);
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
        selectIdList.value.push(item.shareId);
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
// 复制链接
const shareUrl = ref(document.location.origin + "/share/");
const copy = async (data) => {
    await toClipboard(
      `链接:${shareUrl.value}${data.shareId} 提取码:${data.code}`
    );
    proxy.Message.success("复制成功");
};

// 取消分享
const cancelShareIdList = ref([]);
const cancelShareBatch = () => {
    if (selectIdList.value.length == 0) {
        return;
    }
    cancelShareIdList.value = selectIdList.value;
    cancelShareDone();
};
const cancelShare = (row) => {
    cancelShareIdList.value = [row.shareId];
    cancelShareDone();
};

const cancelShareDone = async () => {
    proxy.Confirm(`你确定要取消分享吗?`, async () => {
        // 先行更新（Optimistic UI）
        const ids = cancelShareIdList.value;
        const backupList = [...tableData.value.list];
        tableData.value.list = tableData.value.list.filter(item => !ids.includes(item.shareId));

        const result = await shareService.cancelShare(cancelShareIdList.value.join(","));
        if (!result) {
            tableData.value.list = backupList; // 回滚
            return;
        }
        proxy.Message.success("取消分享成功");
        selectIdList.value = [];
        loadDataList(); // Safe silent reload
    });
};
</script>

<style lang="scss" scoped>
@import "@/assets/file.list.scss";
.file-list { 
    margin-top: 10px;
    .file-item {
        .file-name {
            span {
                &:hover {
                    color: var(--primary-dark);
                }
            }
        }
        .op {
            width: 170px;
        }
    }
}
</style>
