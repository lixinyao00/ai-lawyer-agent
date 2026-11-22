<template>
  <div class="app-container lawyer-surface">
    <el-card shadow="never" class="knowledge-placeholder-card">
      <el-empty description="法律知识库管理功能建设中（M2 即将上线）">
        <p class="knowledge-placeholder-desc">
          M2 里程碑将提供法律法规文档管理、条文自动切片编辑、批量法规导入与 RAG 检索调试能力。
        </p>
      </el-empty>
    </el-card>
  </div>
</template>

<script setup name="LawyerKnowledge">
</script>

<style scoped>
.knowledge-placeholder-card {
  border-radius: 12px;
}

.knowledge-placeholder-desc {
  margin: 0;
  color: var(--law-muted);
  font-size: 13px;
  line-height: 1.6;
}
</style>
