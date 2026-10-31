<script setup lang="ts">
import { onMounted, ref } from 'vue'

const count = ref(0)
const failed = ref(false)

/**
 * 请求后端计数接口
 */
async function request(url: string, method = 'GET') {
  try {
    const res = await fetch(url, { method })
    if (!res.ok) throw new Error(String(res.status))
    count.value = (await res.json()) as number
    failed.value = false
  } catch {
    failed.value = true
  }
}

onMounted(() => request('/api/counter'))
</script>

<template>
  <main class="home">
    <el-card header="后端计数器">
      <el-button type="primary" @click="request('/api/counter/increment', 'POST')">
        点我加一
      </el-button>
      <el-tag class="count">当前值：{{ count }}</el-tag>
      <p v-if="failed" class="error">后端请求失败，请确认服务已启动。</p>
    </el-card>
  </main>
</template>

<style scoped>
.home {
  max-width: 640px;
  margin: 4rem auto;
  padding: 0 1rem;
}

.count {
  margin-left: 0.75rem;
}

.error {
  margin-top: 1rem;
  color: var(--el-color-danger);
}
</style>
