<template>
  <Transition name="slide-down">
    <div v-if="visible && selectedCount > 0" class="batch-toolbar">
      <div class="batch-info">
        <el-checkbox
          :model-value="isAllSelected"
          :indeterminate="isPartialSelected"
          @change="handleSelectAll"
        />
        <span class="selected-count">已选 <b>{{ selectedCount }}</b> 项</span>
        <el-button text size="small" @click="handleSelectAll(true)">全选</el-button>
        <el-button text size="small" @click="handleInvertSelect">反选</el-button>
        <span class="shortcut-hint">Ctrl+A 全选</span>
      </div>
      <div class="batch-actions">
        <el-button size="small" @click="$emit('move')">
          <span class="iconfont icon-move"></span>
          移动
        </el-button>
        <el-button size="small" type="danger" @click="$emit('delete')">
          <span class="iconfont icon-del"></span>
          删除
        </el-button>
        <el-button size="small" @click="$emit('share')">
          <span class="iconfont icon-share"></span>
          分享
        </el-button>
        <el-button size="small" @click="$emit('download')">
          <span class="iconfont icon-download"></span>
          下载
        </el-button>
        <el-button text size="small" @click="$emit('clear')">
          取消选择
        </el-button>
      </div>
    </div>
  </Transition>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted } from 'vue'
import type { CheckboxValueType } from 'element-plus'

const props = defineProps<{
  selectedCount: number
  totalCount: number
  isAllSelected: boolean
  isPartialSelected: boolean
}>()

const emit = defineEmits<{
  (e: 'select-all', value: boolean): void
  (e: 'invert-select'): void
  (e: 'move'): void
  (e: 'delete'): void
  (e: 'share'): void
  (e: 'download'): void
  (e: 'clear'): void
}>()

const visible = computed(() => props.selectedCount > 0)

const handleSelectAll = (value: CheckboxValueType) => {
  emit('select-all', !!value)
}

const handleInvertSelect = () => {
  emit('invert-select')
}

// 键盘快捷键
const handleKeydown = (event: KeyboardEvent) => {
  if (event.ctrlKey || event.metaKey) {
    switch (event.key.toLowerCase()) {
      case 'a':
        event.preventDefault()
        if (event.shiftKey) {
          // Ctrl+Shift+A: 反选
          handleInvertSelect()
        } else {
          // Ctrl+A: 全选
          handleSelectAll(true)
        }
        break
    }
  }
}

onMounted(() => {
  window.addEventListener('keydown', handleKeydown)
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeydown)
})
</script>

<style lang="scss" scoped>
.batch-toolbar {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
  margin-bottom: 8px;
  border-radius: 12px;
  background: rgba(31, 79, 104, 0.95);
  backdrop-filter: blur(12px);
  box-shadow: var(--shadow-md);
  color: #fff;
}

.batch-info {
  display: flex;
  align-items: center;
  gap: 10px;

  :deep(.el-checkbox__inner) {
    background: rgba(255, 255, 255, 0.2);
    border-color: rgba(255, 255, 255, 0.4);
  }

  :deep(.el-checkbox__input.is-checked .el-checkbox__inner) {
    background: var(--primary-light);
    border-color: var(--primary-light);
  }

  :deep(.el-checkbox__input.is-indeterminate .el-checkbox__inner) {
    background: var(--primary-soft);
    border-color: var(--primary-soft);
  }

  .selected-count {
    font-size: 14px;

    b {
      font-size: 16px;
      font-weight: 700;
      color: #7bdfc8;
    }
  }

  .el-button {
    color: rgba(255, 255, 255, 0.85);

    &:hover {
      color: #fff;
      background: rgba(255, 255, 255, 0.15);
    }
  }

  .shortcut-hint {
    margin-left: 8px;
    padding: 2px 8px;
    font-size: 11px;
    color: rgba(255, 255, 255, 0.5);
    background: rgba(255, 255, 255, 0.1);
    border-radius: 4px;
  }
}

.batch-actions {
  display: flex;
  align-items: center;
  gap: 6px;

  .el-button {
    --el-button-bg-color: rgba(255, 255, 255, 0.12);
    --el-button-border-color: rgba(255, 255, 255, 0.2);
    --el-button-text-color: #fff;
    --el-button-hover-bg-color: rgba(255, 255, 255, 0.2);
    --el-button-hover-border-color: rgba(255, 255, 255, 0.3);

    &.el-button--danger {
      --el-button-bg-color: rgba(178, 81, 81, 0.85);
      --el-button-border-color: rgba(178, 81, 81, 0.85);
    }

    .iconfont::before {
      margin-right: 4px;
    }
  }
}

// 滑入动画
.slide-down-enter-active,
.slide-down-leave-active {
  transition: all 0.25s cubic-bezier(0.22, 1, 0.36, 1);
}

.slide-down-enter-from,
.slide-down-leave-to {
  transform: translateY(-100%);
  opacity: 0;
}

// 移动端适配
@media screen and (max-width: 768px) {
  .batch-toolbar {
    flex-direction: column;
    gap: 8px;
    padding: 8px 10px;
  }

  .batch-info {
    width: 100%;
    flex-wrap: wrap;
    gap: 6px;

    .shortcut-hint {
      display: none;
    }
  }

  .batch-actions {
    width: 100%;
    justify-content: flex-end;
    flex-wrap: wrap;
    gap: 4px;

    .el-button {
      flex: 0 0 auto;
      min-width: auto;
      padding: 6px 10px;
    }
  }
}
</style>
