<template>
  <section class="workspace">
    <header class="page-header">
      <div>
        <h2>Reports</h2>
        <p>Review offering summaries, official giving extracts, and budget performance.</p>
      </div>
    </header>

    <section class="panel">
      <div class="toolbar reports-toolbar" role="tablist" aria-label="Reports">
        <button
          v-for="report in visibleReportTabs"
          :key="report.id"
          type="button"
          :class="{ secondary: report.id !== activeVisibleReportId }"
          @click="activeReportId = report.id"
        >
          {{ report.label }}
        </button>
      </div>

      <div class="report-placeholder">
        <h3>{{ activeReport.title }}</h3>
        <p>{{ activeReport.description }}</p>
        <p class="placeholder-note">Filters, results, and exports arrive in Task 5.</p>
      </div>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { authState, type Role } from '../auth/authStore';

interface ReportTab {
  id: 'weekly-offerings' | 'member-offerings' | 'tax-return' | 'financial-budget';
  label: string;
  title: string;
  description: string;
}

const reportTabs: ReportTab[] = [
  {
    id: 'weekly-offerings',
    label: 'Weekly offerings',
    title: 'Weekly Offering Status',
    description: 'Track offering totals by Sunday, fund, giver type, and payment method.',
  },
  {
    id: 'member-offerings',
    label: 'Member offerings',
    title: 'Member Offering Summary',
    description: 'Review giving totals by member and fund category across a selected date range.',
  },
  {
    id: 'tax-return',
    label: 'Official tax',
    title: 'Official Tax Report',
    description: 'Prepare the annual giving extract used for official member tax receipts.',
  },
  {
    id: 'financial-budget',
    label: 'Budget performance',
    title: 'Financial Budget Report',
    description: 'Compare budget, actuals, and variance for the selected fiscal year.',
  },
];

const activeReportId = ref<ReportTab['id']>('weekly-offerings');
const officialTaxRoles: Role[] = ['ADMIN', 'TREASURER'];

const visibleReportTabs = computed(() =>
  reportTabs.filter((report) => report.id !== 'tax-return' || hasOfficialTaxAccess()),
);

const activeVisibleReportId = computed<ReportTab['id']>(() => {
  return visibleReportTabs.value.some((report) => report.id === activeReportId.value)
    ? activeReportId.value
    : visibleReportTabs.value[0]?.id ?? 'weekly-offerings';
});

const activeReport = computed(
  () => visibleReportTabs.value.find((report) => report.id === activeVisibleReportId.value) ?? visibleReportTabs.value[0],
);

function hasOfficialTaxAccess() {
  const roles = authState.currentUser?.roles ?? [];
  return roles.some((role) => officialTaxRoles.includes(role));
}
</script>
