<template>
  <span class="lawyer-mark" aria-hidden="true"><i></i><i></i><i></i><i></i></span>
</template>

<style scoped>
.lawyer-mark {
  display: inline-grid;
  grid-template-columns: repeat(2, 1fr);
  grid-template-rows: repeat(2, 1fr);
  gap: 8%;
  flex: none;
  width: var(--mark-size, 34px);
  height: var(--mark-size, 34px);
  padding: calc(var(--mark-size, 34px) * .22);
  box-sizing: border-box;
  border-radius: 28%;
  background: var(--mark-bg, var(--law-accent));
  color: var(--mark-ink, var(--law-on-accent));
}

i {
  display: block;
  background: currentColor;
  border-radius: 62% 62% 20% 62%;
}

i:nth-child(2) { transform: rotate(90deg); opacity: .67; }
i:nth-child(3) { transform: rotate(-90deg); opacity: .79; }
i:nth-child(4) { transform: rotate(180deg); }
</style>
