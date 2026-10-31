<script setup lang="ts">
import { useAppStore } from '@/stores/app'

const app = useAppStore()
</script>

<template>
  <main class="home">
    <el-card header="前端脚手架已就绪">
      <p>Element Plus 与 Pinia 均已接入，开发代理指向后端 8080 端口。</p>
      <el-button type="primary" @click="app.visits++">点我加一</el-button>
      <el-tag class="visits">点击次数：{{ app.visits }}</el-tag>
    </el-card>
  </main>
</template>

<style scoped>
.home {
  max-width: 640px;
  margin: 4rem auto;
  padding: 0 1rem;
}

.visits {
  margin-left: 0.75rem;
}
</style>
