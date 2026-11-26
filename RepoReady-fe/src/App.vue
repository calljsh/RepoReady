<script setup>
import { computed, ref } from 'vue'
import { ArrowRight, CircleCheck, Link, Search, Warning } from '@element-plus/icons-vue'

const repositoryUrl = ref('')
const loading = ref(false)
const error = ref('')
const report = ref(null)
const severity = ref('ALL')
const categories = [
  { key: 'documentation', label: 'README', max: 25 },
  { key: 'dependencies', label: '依赖清单', max: 20 },
  { key: 'startup', label: '启动入口', max: 20 },
  { key: 'portability', label: '可移植性', max: 15 },
  { key: 'security', label: '敏感信息', max: 20 },
]
const filteredRisks = computed(() => (report.value?.risks || []).filter(item => severity.value === 'ALL' || item.severity === severity.value))
const highCount = computed(() => report.value?.risks?.filter(item => item.severity === 'HIGH').length || 0)
const mediumCount = computed(() => report.value?.risks?.filter(item => item.severity === 'MEDIUM').length || 0)
const scoreColor = computed(() => report.value?.score >= 80 ? '#368670' : report.value?.score >= 60 ? '#bd8038' : '#bd5850')
const scoreLabel = computed(() => report.value?.score >= 80 ? '接入准备较充分' : report.value?.score >= 60 ? '建议先处理主要风险' : '建议整改后再接入')

function validUrl(value) {
  try {
    const url = new URL(value)
    return url.protocol === 'https:' && url.hostname === 'github.com' && !url.port && !url.username && !url.password && !url.search && !url.hash
      && /^\/[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+(?:\.git)?\/?$/.test(url.pathname)
  } catch { return false }
}

async function scan() {
  const url = repositoryUrl.value.trim()
  error.value = ''
  if (!validUrl(url)) {
    error.value = '请输入公开 GitHub 仓库链接，例如 https://github.com/owner/repo'
    return
  }
  loading.value = true
  report.value = null
  severity.value = 'ALL'
  try {
    const response = await fetch('/api/reports/scan', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ repositoryUrl: url }) })
    const data = await response.json().catch(() => ({}))
    if (!response.ok) throw new Error(data.message || '扫描失败，请稍后重试')
    report.value = data
  } catch (cause) {
    error.value = cause instanceof TypeError ? '无法连接扫描服务，请确认后端已启动' : cause.message
  } finally { loading.value = false }
}
function formatTime(value) {
  return value ? new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) : '—'
}
</script>

<template>
  <div class="app-shell">
    <header class="topbar">
      <div class="brand"><span class="brand-mark"><el-icon><Search /></el-icon></span>RepoReady</div>
      <span class="topbar-caption">Git 仓库接入体检</span><span class="topbar-status"><i></i>规则扫描</span>
    </header>
    <main class="workspace">
      <section class="intro"><p class="eyebrow">REPOSITORY ASSESSMENT</p><h1>接入前，先看清仓库状态。</h1>
        <p>输入公开 GitHub 仓库地址，获取基于当前 commit 的就绪度评分与风险位置。</p></section>
      <section class="scan-panel glass" aria-labelledby="scan-title">
        <div class="section-head"><div><span class="eyebrow">NEW SCAN</span><h2 id="scan-title">扫描仓库</h2></div><span class="aside">仅支持公开 GitHub 仓库</span></div>
        <form class="scan-form" @submit.prevent="scan">
          <el-input v-model="repositoryUrl" size="large" :prefix-icon="Link" clearable :disabled="loading" placeholder="https://github.com/owner/repository" aria-label="GitHub 仓库链接" />
          <el-button type="primary" size="large" native-type="submit" :loading="loading"><el-icon v-if="!loading"><ArrowRight /></el-icon>{{ loading ? '正在扫描' : '开始体检' }}</el-button>
        </form>
        <el-alert v-if="error" class="notice" :title="error" type="error" :closable="false" show-icon />
        <p class="hint">扫描只读取仓库文件，不执行代码。报告可能因文件大小或 GitHub 限流而不完整。</p>
      </section>

      <section v-if="loading" class="loading-state" aria-live="polite"><div class="loading-icon"><el-icon :size="28"><Search /></el-icon></div><h2>正在分析仓库</h2><p>获取固定 commit、读取文件并检查接入风险…</p></section>
      <template v-else-if="report">
        <div class="report-title"><div><span class="eyebrow">ASSESSMENT REPORT</span><h2>{{ report.repository }}</h2></div><el-button plain @click="scan">重新扫描</el-button></div>
        <el-alert v-if="report.partialScan" class="partial-alert" title="部分扫描：未读取全部文件，未扫描部分不能视为无风险。" type="warning" :closable="false" show-icon />
        <section class="overview">
          <div class="score-panel glass"><div class="score-ring" :style="{ '--score': `${report.score}%`, '--score-color': scoreColor }"><div><strong>{{ report.score }}</strong><span>/ 100</span></div></div><div><span class="eyebrow">READINESS SCORE</span><h3>{{ scoreLabel }}</h3><p>分数基于仓库结构与已扫描文件的规则命中计算。</p></div></div>
          <div class="summary-panel glass"><span class="eyebrow">AT A GLANCE</span><div class="summary-grid"><div><strong>{{ report.risks?.length || 0 }}</strong><span>风险项</span></div><div><strong class="danger-number">{{ highCount }}</strong><span>高风险</span></div><div><strong>{{ report.scannedFiles }}</strong><span>已读取文件</span></div></div><div class="coverage-label"><span>扫描覆盖</span><strong>{{ report.scannedFiles }} / {{ report.totalFiles }}</strong></div><el-progress :percentage="report.totalFiles ? Math.round(report.scannedFiles / report.totalFiles * 100) : 0" :show-text="false" :stroke-width="7" color="#438a79" /></div>
        </section>
        <section class="report-section"><div class="section-head"><div><span class="eyebrow">CATEGORY BREAKDOWN</span><h2>分项得分</h2></div></div><div class="category-grid"><div v-for="item in categories" :key="item.key" class="category glass"><div><span>{{ item.label }}</span><strong>{{ report.categoryScores?.[item.key] ?? 0 }} <small>/ {{ item.max }}</small></strong></div><el-progress :percentage="Math.round((report.categoryScores?.[item.key] ?? 0) / item.max * 100)" :show-text="false" :stroke-width="6" :color="(report.categoryScores?.[item.key] ?? 0) === item.max ? '#438a79' : '#c68244'" /></div></div></section>
        <section class="report-section"><div class="section-head risk-head"><div><span class="eyebrow">FINDINGS</span><h2>主要风险 <small>{{ report.risks?.length || 0 }}</small></h2></div><el-radio-group v-model="severity" size="small" aria-label="风险等级筛选"><el-radio-button value="ALL">全部</el-radio-button><el-radio-button value="HIGH">高风险 {{ highCount }}</el-radio-button><el-radio-button value="MEDIUM">中风险 {{ mediumCount }}</el-radio-button></el-radio-group></div>
          <div v-if="filteredRisks.length" class="risk-list"><article v-for="(risk, index) in filteredRisks" :key="`${risk.file}-${risk.line}-${index}`" class="risk glass"><div class="risk-icon" :class="risk.severity.toLowerCase()"><el-icon><Warning /></el-icon></div><div><div class="risk-name"><h3>{{ risk.title }}</h3><el-tag :type="risk.severity === 'HIGH' ? 'danger' : 'warning'" size="small">{{ risk.severity === 'HIGH' ? '高风险' : '中风险' }}</el-tag></div><p class="file-location">{{ risk.file }}<span v-if="risk.line"> : {{ risk.line }}</span></p><p class="recommendation">{{ risk.recommendation }}</p></div></article></div>
          <div v-else class="empty-risk glass"><el-icon :size="22"><CircleCheck /></el-icon>{{ severity === 'ALL' ? '已扫描范围内未发现规则风险' : '该等级暂无风险项' }}</div>
        </section>
        <section class="report-meta glass"><div><span>默认分支</span><strong>{{ report.branch }}</strong></div><div><span>Commit</span><code :title="report.commit">{{ report.commit }}</code></div><div><span>扫描时间</span><strong>{{ formatTime(report.scannedAt) }}</strong></div><div><span>规则版本</span><strong>v{{ report.ruleVersion }}</strong></div></section>
      </template>
      <section v-else class="initial"><div class="initial-label">检查范围</div><div class="scope-grid"><div><span>01</span><h3>项目说明</h3><p>README、依赖和启动入口</p></div><div><span>02</span><h3>跨平台风险</h3><p>Windows 脚本与本机路径</p></div><div><span>03</span><h3>敏感信息</h3><p>疑似凭据、私钥及 .env</p></div></div></section>
    </main><footer>RepoReady <span>基于规则的接入评估 · 结果仅供辅助判断</span></footer>
  </div>
</template>
