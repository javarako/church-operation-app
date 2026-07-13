package com.church.operation.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DashboardTrendPoint(LocalDate sunday, BigDecimal amount) {
}
