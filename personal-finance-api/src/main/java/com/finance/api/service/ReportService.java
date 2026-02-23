package com.finance.api.service;

import com.finance.api.dto.MonthlyReportDto;
import com.finance.api.entity.CategoryType;
import com.finance.api.repository.TransactionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class ReportService {

    private final TransactionRepository transactionRepository;

    public ReportService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public MonthlyReportDto getMonthlyReport(int month, int year) {
        BigDecimal totalIncome = transactionRepository.sumByTypeAndMonthAndYear(CategoryType.INCOME, month, year);
        BigDecimal totalExpense = transactionRepository.sumByTypeAndMonthAndYear(CategoryType.EXPENSE, month, year);

        if (totalIncome == null) totalIncome = BigDecimal.ZERO;
        if (totalExpense == null) totalExpense = BigDecimal.ZERO;

        MonthlyReportDto report = new MonthlyReportDto();
        report.setMonth(month);
        report.setYear(year);
        report.setTotalIncome(totalIncome);
        report.setTotalExpense(totalExpense);
        report.setNetSavings(totalIncome.subtract(totalExpense));
        return report;
    }
}
