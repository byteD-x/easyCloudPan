<template>
  <div class="loading-state-container">
    <div v-if="state === 'loading'" class="state-loading">
      <div class="loading-spinner"></div>
      <p class="loading-text">{{ loadingText || "加载中..." }}</p>
    </div>

    <div v-else-if="state === 'empty'" class="state-empty">
      <p class="empty-text">{{ emptyText || "暂无数据" }}</p>
      <slot name="empty-action"></slot>
    </div>

    <div v-else-if="state === 'error'" class="state-error">
      <p class="error-text">{{ errorText || "加载失败" }}</p>
      <button v-if="showRetry" class="retry-button" @click="handleRetry">
        重试
      </button>
      <slot name="error-action"></slot>
    </div>

    <div v-else-if="state === 'success'" class="state-success">
      <slot></slot>
    </div>
  </div>
</template>

<script setup>
import { defineEmits, defineProps } from "vue";

defineProps({
  state: {
    type: String,
    required: true,
    validator: value => ["loading", "empty", "error", "success"].includes(value),
  },
  loadingText: {
    type: String,
    default: "加载中...",
  },
  emptyText: {
    type: String,
    default: "暂无数据",
  },
  errorText: {
    type: String,
    default: "加载失败，请重试",
  },
  showRetry: {
    type: Boolean,
    default: true,
  },
});

const emit = defineEmits(["retry"]);

const handleRetry = () => {
  emit("retry");
};
</script>

<style lang="scss" scoped>
.loading-state-container {
  width: 100%;
  min-height: 220px;
  display: flex;
  align-items: center;
  justify-content: center;

  .state-loading,
  .state-empty,
  .state-error {
    text-align: center;
    padding: 32px 20px;
  }

  .loading-spinner {
    width: 44px;
    height: 44px;
    margin: 0 auto 14px;
    border-radius: 50%;
    border: 3px solid rgba(31, 79, 104, 0.2);
    border-top-color: var(--primary);
    animation: spin 1s linear infinite;
  }

  .loading-text,
  .empty-text {
    color: var(--text-secondary);
    font-size: 13px;
    letter-spacing: 0.03em;
  }

  .error-text {
    color: var(--danger);
    font-size: 13px;
    margin-bottom: 12px;
  }

  .retry-button {
    padding: 8px 18px;
    border-radius: var(--btn-radius-pill);
    border: 1px solid var(--btn-primary-bg);
    background: var(--btn-primary-bg);
    color: var(--btn-primary-text);
    font-weight: 600;
    cursor: pointer;
    transition: var(--transition-fast);

    &:hover {
      background: var(--btn-primary-hover-bg);
      border-color: var(--btn-primary-hover-bg);
      box-shadow: 0 8px 14px rgba(31, 79, 104, 0.18);
    }
  }

  .state-success {
    width: 100%;
  }
}

@keyframes spin {
  from {
    transform: rotate(0);
  }
  to {
    transform: rotate(360deg);
  }
}
</style>

