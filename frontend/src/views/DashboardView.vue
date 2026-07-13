<template>
  <section class="workspace dashboard-page">
    <section class="dashboard-hero">
      <div class="banner-panel" :class="{ 'empty-banner': !churchInfo?.bannerPath }">
        <img v-if="churchInfo?.bannerPath" :src="churchInfo.bannerPath" alt="" />
        <div class="banner-copy">
          <h2>Faith, Hope, Love</h2>
          <p>Serving our community together</p>
        </div>
      </div>

      <aside class="church-info-panel">
        <div>
          <h2>{{ churchName }}</h2>
          <p>{{ churchAddress }}</p>
          <p>{{ churchContactInfo }}</p>
          <p>{{ treasurerLabel }}</p>
        </div>
        <div class="user-role">
          <span>{{ userInitials }}</span>
          <strong>{{ currentRoleLabel }}</strong>
        </div>
      </aside>
    </section>

    <p v-if="dashboardError" class="error dashboard-error">{{ dashboardError }}</p>

    <template v-if="dashboard">
      <section class="dashboard-summary-grid" aria-label="Fiscal snapshot">
        <article class="summary-card members-card">
          <div class="summary-icon"><Users :size="30" aria-hidden="true" /></div>
          <div class="summary-content">
            <span>Active Members</span>
            <strong>{{ dashboard.activeMemberCount }}</strong>
            <small>New this month: {{ dashboard.newMemberCount }}</small>
          </div>
        </article>

        <article class="summary-card offering-card">
          <div class="summary-icon"><HandHeart :size="30" aria-hidden="true" /></div>
          <div class="summary-content">
            <span>YTD Offering vs Budget</span>
            <strong>{{ formatPercentage(dashboard.ytdOfferingPercentage) }}</strong>
            <small>{{ formatMoney(dashboard.ytdOfferingActual) }} / {{ formatMoney(dashboard.ytdOfferingBudget) }}</small>
            <div v-if="dashboard.ytdOfferingPercentage !== null" class="progress-track" aria-hidden="true">
              <span :style="{ width: progressWidth(dashboard.ytdOfferingPercentage) }"></span>
            </div>
          </div>
        </article>

        <article class="summary-card expense-card">
          <div class="summary-icon"><ChartNoAxesCombined :size="30" aria-hidden="true" /></div>
          <div class="summary-content">
            <span>YTD Expense vs Budget</span>
            <strong>{{ formatPercentage(dashboard.ytdExpensePercentage) }}</strong>
            <small>{{ formatMoney(dashboard.ytdExpenseActual) }} / {{ formatMoney(dashboard.ytdExpenseBudget) }}</small>
            <div v-if="dashboard.ytdExpensePercentage !== null" class="progress-track" aria-hidden="true">
              <span :style="{ width: progressWidth(dashboard.ytdExpensePercentage) }"></span>
            </div>
          </div>
        </article>

        <article class="summary-card cheque-card">
          <div class="summary-icon"><ReceiptText :size="30" aria-hidden="true" /></div>
          <div class="summary-content">
            <span>Pending Cheques</span>
            <strong>{{ dashboard.pendingChequeCount }}</strong>
            <small>Total: {{ formatMoney(dashboard.pendingChequeTotal) }}</small>
          </div>
        </article>
      </section>

      <section class="dashboard-band offering-overview">
        <div class="section-heading">
          <div>
            <h3>Offering Overview</h3>
            <p>Current giving totals from active offering records.</p>
          </div>
        </div>
        <div class="overview-grid">
          <div class="overview-metric">
            <span>This Week</span>
            <strong>{{ formatMoney(dashboard.weekOfferingTotal) }}</strong>
            <small>Current Sunday week</small>
          </div>
          <div class="overview-metric">
            <span>Month to Date</span>
            <strong>{{ formatMoney(dashboard.monthOfferingTotal) }}</strong>
            <small>Current calendar month</small>
          </div>
          <div class="overview-metric">
            <span>Year to Date</span>
            <strong>{{ formatMoney(dashboard.yearOfferingTotal) }}</strong>
            <small>Current calendar year</small>
          </div>
        </div>
      </section>

      <section class="panel trend-panel">
        <div class="section-heading">
          <div>
            <h3>Offering Trend</h3>
            <p>Last 12 Sundays, including Sundays with no recorded offerings.</p>
          </div>
          <span class="fiscal-period">Fiscal year {{ dashboard.fiscalYearStart }} to {{ dashboard.fiscalYearEnd }}</span>
        </div>
        <div class="trend-chart">
          <Bar :data="chartData" :options="chartOptions" />
        </div>
      </section>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { Bar } from 'vue-chartjs';
import { BarElement, CategoryScale, Chart as ChartJS, LinearScale, Tooltip, type ChartData, type ChartOptions } from 'chart.js';
import { ChartNoAxesCombined, HandHeart, ReceiptText, Users } from '@lucide/vue';
import { getChurchInformation, type ChurchInformation } from '../api/churchInformation';
import { getDashboard, type DashboardResponse } from '../api/dashboard';
import { authState } from '../auth/authStore';

ChartJS.register(CategoryScale, LinearScale, BarElement, Tooltip);

const dashboard = ref<DashboardResponse | null>(null);
const dashboardError = ref('');
const churchInfo = ref<ChurchInformation | null>(null);

const chartData = computed<ChartData<'bar'>>(() => ({
  labels: dashboard.value?.offeringTrend.map((point) => point.sunday) ?? [],
  datasets: [{
    label: 'Offering',
    data: dashboard.value?.offeringTrend.map((point) => point.amount) ?? [],
    backgroundColor: '#d99a13',
    borderRadius: 3,
    maxBarThickness: 42,
  }],
}));

const chartOptions: ChartOptions<'bar'> = {
  responsive: true,
  maintainAspectRatio: false,
  plugins: {
    legend: { display: false },
    tooltip: {
      callbacks: {
        label: (context) => formatMoney(Number(context.raw)),
      },
    },
  },
  scales: {
    x: { grid: { display: false } },
    y: { beginAtZero: true },
  },
};

const churchName = computed(() => churchInfo.value?.name || 'Church Operations');
const churchAddress = computed(() => churchInfo.value?.address || 'Address not configured');
const churchContactInfo = computed(() => churchInfo.value?.contactInfo || 'Contact info not configured');
const treasurerLabel = computed(() => `Treasurer: ${churchInfo.value?.treasurerName || 'Not configured'}`);
const currentRoleLabel = computed(() => authState.currentUser?.roles[0] ?? 'User');
const userInitials = computed(() => {
  const source = authState.currentUser?.displayName || authState.currentUser?.primaryEmail || 'User';
  return source
    .split(/[\s@.]+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0].toUpperCase())
    .join('');
});

onMounted(() => {
  void loadChurchInformation();
  void loadDashboard();
});

async function loadChurchInformation() {
  try {
    churchInfo.value = await getChurchInformation();
  } catch {
    churchInfo.value = null;
  }
}

async function loadDashboard() {
  dashboardError.value = '';
  try {
    dashboard.value = await getDashboard();
  } catch (error) {
    dashboardError.value = error instanceof Error ? error.message : 'Dashboard unavailable.';
  }
}

function formatMoney(value: number) {
  return new Intl.NumberFormat('en-CA', {
    style: 'currency',
    currency: 'CAD',
  }).format(value);
}

function formatPercentage(value: number | null) {
  return value === null ? 'Not budgeted' : `${value.toFixed(2)}%`;
}

function progressWidth(value: number | null) {
  return `${Math.min(Math.max(value ?? 0, 0), 100)}%`;
}
</script>

<style scoped>
.dashboard-page {
  gap: 18px;
}

.dashboard-hero {
  display: grid;
  grid-template-columns: minmax(234px, 0.625fr) minmax(320px, 1fr);
  gap: 0;
  overflow: hidden;
  border: 1px solid #d8dee6;
  border-radius: 8px;
  background: white;
}

.banner-panel {
  position: relative;
  align-self: start;
  overflow: hidden;
  background: #123047;
}

.banner-panel img {
  width: 100%;
  height: auto;
  display: block;
}

.banner-panel::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, rgba(18, 48, 71, 0.72), rgba(18, 48, 71, 0.12));
}

.empty-banner {
  aspect-ratio: 977 / 240;
}

.banner-copy {
  position: absolute;
  left: 28px;
  bottom: 28px;
  z-index: 1;
  color: white;
}

.banner-copy h2 {
  margin: 0;
  font-size: 2rem;
}

.banner-copy p {
  margin: 8px 0 0;
}

.church-info-panel {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  padding: 16px 24px;
  background: white;
}

.church-info-panel h2 {
  margin: 0 0 8px;
}

.church-info-panel p {
  margin: 6px 0;
  color: #344054;
}

.user-role {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  white-space: nowrap;
}

.user-role span {
  width: 42px;
  height: 42px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: #0b6fd3;
  color: white;
  font-weight: 700;
}

.user-role strong {
  padding-top: 10px;
  font-size: 0.9rem;
}

.dashboard-error {
  margin: 0;
}

.dashboard-summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.summary-card {
  min-height: 154px;
  display: grid;
  grid-template-columns: 58px minmax(0, 1fr);
  gap: 14px;
  align-items: start;
  border: 1px solid #d8dee6;
  border-radius: 8px;
  padding: 18px;
  background: white;
}

.members-card { border-color: #a9d7ae; }
.offering-card { border-color: #e8c36d; }
.expense-card { border-color: #9fc5ed; }
.cheque-card { border-color: #edb28f; }

.summary-icon {
  width: 54px;
  height: 54px;
  display: grid;
  place-items: center;
  border-radius: 50%;
}

.members-card .summary-icon { background: #e2f3e2; color: #237238; }
.offering-card .summary-icon { background: #fff0c9; color: #a76d00; }
.expense-card .summary-icon { background: #e4f1fc; color: #1768b0; }
.cheque-card .summary-icon { background: #fde9dd; color: #b8541c; }

.summary-content {
  min-width: 0;
  display: grid;
  gap: 6px;
}

.summary-content > span {
  color: #344054;
  font-size: 0.92rem;
}

.summary-content strong {
  color: #182230;
  font-size: 1.65rem;
  line-height: 1.15;
}

.summary-content small {
  color: #667085;
  line-height: 1.35;
  overflow-wrap: anywhere;
}

.progress-track {
  height: 7px;
  margin-top: 4px;
  overflow: hidden;
  border-radius: 4px;
  background: #e5e9ef;
}

.progress-track span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: #2274bd;
}

.offering-card .progress-track span {
  background: #d99a13;
}

.dashboard-band {
  padding: 18px 4px 20px;
  border-top: 1px solid #d8dee6;
  border-bottom: 1px solid #d8dee6;
}

.section-heading {
  display: flex;
  align-items: start;
  justify-content: space-between;
  gap: 18px;
}

.section-heading h3,
.section-heading p {
  margin: 0;
}

.section-heading p {
  margin-top: 6px;
  color: #667085;
}

.overview-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin-top: 18px;
}

.overview-metric {
  min-width: 0;
  display: grid;
  gap: 6px;
  padding: 4px 20px;
  border-left: 1px solid #d8dee6;
}

.overview-metric:first-child {
  border-left: 0;
}

.overview-metric span,
.overview-metric small,
.fiscal-period {
  color: #667085;
}

.overview-metric strong {
  font-size: 1.5rem;
}

.trend-panel {
  display: grid;
  gap: 18px;
}

.fiscal-period {
  font-size: 0.85rem;
  text-align: right;
}

.trend-chart {
  height: 290px;
  min-width: 0;
}

@media (max-width: 1180px) {
  .dashboard-summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 900px) {
  .dashboard-hero {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 680px) {
  .dashboard-summary-grid,
  .overview-grid {
    grid-template-columns: 1fr;
  }

  .summary-card {
    min-height: 132px;
  }

  .overview-metric,
  .overview-metric:first-child {
    padding: 12px 4px;
    border-left: 0;
    border-top: 1px solid #d8dee6;
  }

  .overview-metric:first-child {
    border-top: 0;
  }

  .church-info-panel,
  .section-heading {
    display: grid;
  }

  .fiscal-period {
    text-align: left;
  }
}
</style>
