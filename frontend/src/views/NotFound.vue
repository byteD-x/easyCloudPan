<template>
  <div class="not-found">
    <div class="card">
      <div class="brand">
        <span class="iconfont icon-pan"></span>
        <span class="name">易云盘</span>
      </div>
      <div class="code">404</div>
      <div class="title">页面不存在</div>
      <div class="desc">你访问的地址可能已更改、已删除，或暂时不可用。</div>
      <div class="actions">
        <el-button type="primary" @click="goHome">返回首页</el-button>
        <el-button @click="goBack">返回上一页</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from "vue-router";

const router = useRouter();

const goHome = () => {
  router.push("/");
};

const goBack = () => {
  router.back();
};
</script>

<style lang="scss" scoped>
.not-found {
  min-height: 100vh;
  padding: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  background:
    radial-gradient(900px 380px at 20% 10%, rgba(47, 109, 140, 0.16), rgba(47, 109, 140, 0) 60%),
    linear-gradient(180deg, #f5f8fa 0%, #eef3f6 100%);
}

.card {
  width: min(560px, 100%);
  background: rgba(255, 255, 255, 0.86);
  border: 1px solid rgba(194, 204, 220, 0.82);
  border-radius: 24px;
  box-shadow: var(--shadow-lg);
  padding: 28px;
  backdrop-filter: blur(12px);
}

.brand {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text-main);

  .icon-pan {
    width: 34px;
    height: 34px;
    border-radius: 10px;
    font-size: 22px;
    color: #fff;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    background: var(--primary);
  }

  .name {
    font-size: 15px;
    font-weight: 700;
    letter-spacing: 0.05em;
    font-family: var(--font-heading);
  }
}

.code {
  margin-top: 18px;
  font-size: 66px;
  line-height: 1;
  font-weight: 800;
  letter-spacing: 0.08em;
  color: var(--primary-dark);
  font-family: var(--font-heading);
}

.title {
  margin-top: 10px;
  font-size: 18px;
  font-weight: 700;
  color: var(--text-main);
}

.desc {
  margin-top: 8px;
  color: var(--text-secondary);
  font-size: 14px;
}

.actions {
  margin-top: 18px;
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}
</style>

