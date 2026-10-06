<template>
  <div class="share">
    <div class="header">
      <div class="header-content">
        <div class="logo" @click="jump">
          <span class="iconfont icon-pan"></span>
          <span class="name">易云盘</span>
        </div>
      </div>
    </div>
  </div>
  <div class="share-body">
    <template v-if="Object.keys(shareInfo).length == 0">
      <div v-loading="Object.keys(shareInfo).length == 0" class="loading"></div>
    </template>
    <template v-else>
      <div class="share-panel">
        <div class="share-user-info">
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
              <span class="share-time">分享于：{{ shareInfo.shareTime }}</span>
            </div>
            <div class="file-name">分享文件：{{ shareInfo.fileName }}</div>
          </div>
        </div>
        <div class="share-op-btn">
            <el-button
              v-if="shareInfo.currentUser"
              type="primary"
              @click="cancelShare"
            >
              <span class="iconfont icon-cancel"></span>
              取消分享</el-button
            >
            <el-button
              v-else
              type="primary"
              :disabled="selectIdList.length == 0"
              @click="save2MyPan"
            >
              <span class="iconfont icon-import"></span>
              保存到我的网盘</el-button
            >
        </div>
      </div>
      <Navigation
        ref="navigationRef"
        :share-id="shareId"
        @nav-change="navChange"
      ></Navigation>
      <div class="file-list">
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
                onSwipeLeft: () => {
                  if (row.folderType == 0) {
                    download(row);
                  }
                },
                onSwipeRight: () => {
                  if (!shareInfo.currentUser && row.folderType == 0) {
                    save2MyPanSingle(row);
                  }
                },
              }"
              :class="['file-item', row.showOp ? 'show-op' : '']"
              @mouseenter="showOp(row)"
              @mouseleave="cancelShowOp(row)"
            >
            <template
              v-if="
                (row.fileType == 3 || row.fileType == 1) && row.status == 2
              "
            >
              <Icon :cover="row.fileCover" :width="32" :share-id="shareId"></Icon>
            </template>
              <template v-else>
                <Icon
                  v-if="row.folderType == 0"
                  :file-type="row.fileType"
                ></Icon>
                <Icon v-if="row.folderType == 1" :file-type="0"></Icon>
              </template>
              <span class="file-name" :title="row.fileName">
                <span @click="preview(row)">{{ row.fileName }}</span>
              </span>
              <span class="op">
                <span
                  v-if="row.folderType == 0"
                  class="iconfont icon-download"
                  @click="download(row)"
                  >下载</span
                >
                <span
                  v-if="row.showOp && !shareInfo.currentUser"
                  class="iconfont icon-import"
                  @click="save2MyPanSingle(row)"
                  >保存到我的网盘</span
                >
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
    </template>
    <!-- 目录选择 -->
    <FolderSelect
      ref="folderSelectRef"
      @folder-select="save2MyPanDone"
    ></FolderSelect>
    <Preview ref="previewRef"></Preview>
  </div>
</template>

<script setup>
import { ref, getCurrentInstance } from "vue";
import { useRouter, useRoute } from "vue-router";
import { useUserInfoStore } from "@/stores/userInfoStore";
import * as shareService from "@/services/shareService";

const { proxy } = getCurrentInstance();
const router = useRouter();
const route = useRoute();
const userInfoStore = useUserInfoStore();


const shareId = route.params.shareId;
const shareInfo = ref({});
const getShareInfo = async () => {
    const result = await shareService.getShareLoginInfo(shareId);
    if (!result) {
        return;
    }
    if (result == null) {
        router.push(`/shareCheck/${shareId}`);
        return;
    }
    shareInfo.value = result;
};
getShareInfo();

const columns = [
    {
        label: "文件名",
        prop: "fileName",
        scopedSlots: "fileName",
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
const tableData = ref({});
const tableOptions = {
    extHeight: 80,
    selectType: "checkbox",
};

const loadDataList = async () => {
    const params = {
        pageNo: tableData.value.pageNo,
        pageSize: tableData.value.pageSize,
        shareId: shareId,
        filePid: currentFolder.value.fileId,
    };
    const result = await shareService.loadFileList(params);
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

const selectIdList = ref([]);
const rowSelected = (rows) => {
    selectIdList.value = [];
    rows.forEach((item) => {
        selectIdList.value.push(item.fileId);
    });
};

const currentFolder = ref({ fileId: "0" });
const navChange = (data) => {
    const { curFolder } = data;
    currentFolder.value = curFolder;
    loadDataList();
};

// 预览, 查看
const previewRef = ref();
const navigationRef = ref();

const preview = (data) => {
    if (data.folderType == 1) {
        navigationRef.value.openFolder(data);
        return;
    }
    data.shareId = shareId;
    previewRef.value.showPreview(data, 2);
};

// 下载文件
const download = async (row) => {
  const result = await shareService.createDownloadUrl(shareId, row.fileId);
  if (!result) {
      return;
  }
  window.location.href = shareService.getDownloadUrl(result);
};

// 保存到我的网盘
const folderSelectRef = ref();
const save2MyPanFileIdArray = ref([]);
const save2MyPan = () => {
    if (selectIdList.value.length == 0) {
        return;
    }
    if (!userInfoStore.userInfo) {
        router.push({
            path: "/login",
            query: {
                redirectUrl: route.fullPath,
            },
        });
        return;
    }
    save2MyPanFileIdArray.value = selectIdList.value;
    folderSelectRef.value.showFolderDialog({});
};

const save2MyPanSingle = (row) => {
    if (!userInfoStore.userInfo) {
        router.push({
            path: "/login",
            query: {
                redirectUrl: route.fullPath,
            },
        });
        return;
    }
    save2MyPanFileIdArray.value = [row.fileId];
    folderSelectRef.value.showFolderDialog({});
};

const save2MyPanDone = async (folderId) => {
    const result = await shareService.saveShare({
        shareId: shareId,
        shareFileIds: save2MyPanFileIdArray.value.join(","),
        myFolderId: folderId,
    });
    if (!result) {
        return;
    }
    loadDataList();
    proxy.Message.success("保存成功");
    folderSelectRef.value.close();
};

// 取消分享
const cancelShare = () => {
    proxy.Confirm(`你确定要取消分享吗?`, async () => {
        const result = await shareService.cancelShare(shareId);
        if (!result) {
            return;
        }
        proxy.Message.success("取消分享成功");
        router.push("/");
    });
};

const jump = () => {
    router.push("/");
};
</script>

<style lang="scss" scoped>
@import "@/assets/file.list.scss";
.share {
    min-height: 100vh;
    background:
      radial-gradient(620px 420px at 2% -2%, rgba(43, 137, 169, 0.16), rgba(43, 137, 169, 0)),
      radial-gradient(620px 420px at 98% 100%, rgba(58, 115, 145, 0.12), rgba(58, 115, 145, 0)),
      linear-gradient(180deg, #f7f8fb 0%, #f1f3f8 100%);
}

.header {
    width: 100%;
    position: fixed;
    height: 62px;
    z-index: 12;
    border-bottom: 1px solid rgba(194, 204, 220, 0.6);
    background: rgba(255, 255, 255, 0.86);
    backdrop-filter: blur(10px);

    .header-content {
        width: min(1100px, calc(100% - 32px));
        margin: 0 auto;
        color: var(--text-main);
        line-height: 62px;

        .logo {
            display: flex;
            align-items: center;
            cursor: pointer;
            user-select: none;

            .icon-pan {
                width: 40px;
                height: 40px;
                border-radius: 12px;
                font-size: 24px;
                display: flex;
                align-items: center;
                justify-content: center;
                color: #fff;
                background: var(--primary);
                box-shadow: 0 12px 22px rgba(31, 79, 104, 0.24);
            }

            .name {
                margin-left: 8px;
                font-size: 20px;
                letter-spacing: 0.06em;
                font-family: var(--font-heading);
                font-weight: 700;
            }
        }
    }
}

.share-body {
    width: min(1100px, calc(100% - 32px));
    margin: 0 auto;
    padding-top: 80px;

    .loading {
        height: calc(100vh / 2);
        width: 100%;
    }

    .share-panel {
        margin-top: 16px;
        display: flex;
        align-items: flex-start;
        justify-content: space-between;
        gap: 12px;
        padding: 16px;
        border: 1px solid rgba(194, 204, 220, 0.8);
        border-radius: 20px;
        background: rgba(255, 255, 255, 0.8);
        box-shadow: var(--shadow-sm);
        backdrop-filter: blur(8px);

        .share-user-info {
            flex: 1;
            display: flex;
            align-items: center;

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
                        color: var(--text-main);
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

        .share-op-btn {
            display: flex;
            align-items: center;
            justify-content: flex-end;
            gap: 10px;
            flex-shrink: 0;
        }
    }
}

.file-list {
    margin-top: 10px;

    .file-item {
        .op {
            width: 170px;
        }
    }
}

@media screen and (max-width: 768px) {
    .share-body {
        padding-top: 70px;
    }
    .share-body .share-panel {
        flex-direction: column;
        align-items: stretch;
    }
    .share-body .share-panel .share-user-info .share-info .user-info .share-time {
        margin-left: 0;
        width: 100%;
        margin-top: 4px;
    }
}
</style>

