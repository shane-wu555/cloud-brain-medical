<template>
  <router-view />
</template>

<style>
:root {
  font-family: Inter, "PingFang SC", "HarmonyOS Sans SC", "MiSans", "Noto Sans CJK SC", "Source Han Sans SC", system-ui, -apple-system, BlinkMacSystemFont, "Microsoft YaHei", sans-serif;
  color: #1f2937;
  background: #f5f7fb;
}

* {
  box-sizing: border-box;
}

body {
  margin: 0;
}

.page {
  min-height: 100vh;
  padding: 24px;
}

.workspace {
  max-width: 1280px;
  margin: 0 auto;
}

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
}

.topbar h1,
.login-panel h1 {
  margin: 0;
  font-size: 28px;
  letter-spacing: 0;
}

.muted {
  color: #64748b;
}

.eyebrow {
  margin: 0 0 8px;
  color: #0f766e;
  font-weight: 700;
}

.grid {
  display: grid;
  grid-template-columns: repeat(12, 1fr);
  gap: 16px;
}

.panel {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 18px;
}

.span-4 {
  grid-column: span 4;
}

.span-3 {
  grid-column: span 3;
}

.span-5 {
  grid-column: span 5;
}

.span-6 {
  grid-column: span 6;
}

.span-7 {
  grid-column: span 7;
}

.span-8 {
  grid-column: span 8;
}

.span-12 {
  grid-column: span 12;
}

.full {
  width: 100%;
}

.login-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 24px;
  background: linear-gradient(135deg, #eef7f4, #f7f8fb 44%, #eef2ff);
}

.login-panel {
  width: min(440px, 100%);
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 28px;
  box-shadow: 0 18px 48px rgb(15 23 42 / 10%);
}

.stat {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.stat strong {
  font-size: 30px;
}

@media (max-width: 900px) {
  .span-3,
  .span-4,
  .span-5,
  .span-6,
  .span-7,
  .span-8 {
    grid-column: span 12;
  }

  .topbar {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
