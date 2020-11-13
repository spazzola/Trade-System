package com.tradesystem.report;

import com.tradesystem.cost.Cost;
import com.tradesystem.cost.CostDao;
import com.tradesystem.invoice.Invoice;
import com.tradesystem.invoice.InvoiceDao;
import com.tradesystem.order.Order;
import com.tradesystem.order.OrderDao;
import com.tradesystem.orderdetails.OrderDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

@Service
public class ReportYearService {

    private CostDao costDao;
    private OrderDao orderDao;
    private ReportDao reportDao;
    private InvoiceDao invoiceDao;
    private ReportService reportService;


    public ReportYearService(InvoiceDao invoiceDao, OrderDao orderDao,
                             CostDao costDao, ReportDao reportDao, ReportService reportService) {
        this.invoiceDao = invoiceDao;
        this.orderDao = orderDao;
        this.costDao = costDao;
        this.reportDao = reportDao;
        this.reportService = reportService;
    }

    @Transactional
    public Report generateYearReport(int year) {
        BigDecimal sumCosts = calculateCosts(year);
        BigDecimal soldValue = sumYearSoldValue(year);
        BigDecimal boughtValue = sumYearBoughtValue(year);
        BigDecimal soldQuantity = sumMonthlySoldQuantity(year);
        BigDecimal averageSold = calculateAverageSold(year, soldQuantity);
        BigDecimal averagePurchase = calculateAveragePurchase(year, soldQuantity);
        BigDecimal averageEarningsPerM3 = averageSold.subtract(averagePurchase);
        BigDecimal income = soldValue.subtract(boughtValue);
        BigDecimal buyersNotPaidInvoices = calculateBuyersNotPaidInvoices(year);

        String reportType = String.valueOf(year);

        Report report = Report.builder()
                .soldValue(soldValue)
                .boughtValue(boughtValue)
                .soldQuantity(soldQuantity)
                .averageEarningsPerM3(averageEarningsPerM3)
                .income(income)
                .buyersNotPaidInvoices(buyersNotPaidInvoices)
                .sumCosts(sumCosts)
                .type(reportType)
                .build();

        if (reportService.checkIfReportExist(reportType)) {
            reportDao.save(report);
        }
        else {
        Report previousReport = reportDao.findByType(report.getType());
            previousReport.setSoldValue(soldValue);
            previousReport.setBoughtValue(boughtValue);
            previousReport.setSoldQuantity(soldQuantity);
            previousReport.setAverageEarningsPerM3(averageEarningsPerM3);
            previousReport.setIncome(income);
            previousReport.setSumCosts(sumCosts);
            previousReport.setBuyersNotPaidInvoices(buyersNotPaidInvoices);
            previousReport.setType(reportType);

        reportDao.save(previousReport);
    }
        return report;
    }

    private BigDecimal calculateCosts(int year) {
        List<Cost> costs = costDao.getYearCosts(year);
        BigDecimal sumCosts = BigDecimal.valueOf(0);

        for (Cost cost : costs) {
            sumCosts = sumCosts.add(cost.getValue().multiply(BigDecimal.valueOf(-1)));
        }
        return sumCosts;
    }

    private BigDecimal calculateBuyersNotUsedAmount(int year) {
        Optional<List<Invoice>> notUsedInvoices = invoiceDao.getBuyersYearNotUsedPositivesInvoices(year);
        BigDecimal notUsedValue = BigDecimal.valueOf(0);

        if (notUsedInvoices.isPresent()) {
            for (Invoice invoice : notUsedInvoices.get()) {
                notUsedValue = notUsedValue.add(invoice.getAmountToUse());
            }
        }
        return notUsedValue;
    }

    private BigDecimal calculateSuppliersNotUsedValue(int year) {
        Optional<List<Invoice>> suppliersNotUsedInvoices = invoiceDao.getSuppliersYearNotUsedInvoices(year);
        BigDecimal notUsedValue = BigDecimal.valueOf(0);

        if (suppliersNotUsedInvoices.isPresent()) {
            for (Invoice invoice : suppliersNotUsedInvoices.get()) {
                notUsedValue = notUsedValue.add(invoice.getAmountToUse());
            }
        }
        return notUsedValue;
    }

    private BigDecimal sumYearSoldValue(int year) {
        Set<Order> orders = orderDao.getYearOrders(year);
        BigDecimal sum = BigDecimal.valueOf(0);

        for (Order order : orders) {
            List<OrderDetails> orderDetails = order.getOrderDetails();

            for (OrderDetails orderDetail : orderDetails) {
                sum = sum.add(orderDetail.getBuyerSum());
            }
        }
        return sum;
    }

    private BigDecimal sumYearBoughtValue(int year) {
        Set<Order> orders = orderDao.getYearOrders(year);
        BigDecimal sum = BigDecimal.valueOf(0);

        for (Order order : orders) {
            List<OrderDetails> orderDetails = order.getOrderDetails();

            for (OrderDetails orderDetail : orderDetails) {
                sum = sum.add(orderDetail.getSupplierSum());
            }
        }
        return sum;
    }

    private BigDecimal calculateAverageSold(int year, BigDecimal quantity) {
        Set<Order> orders = orderDao.getYearOrders(year);
        BigDecimal sum = BigDecimal.valueOf(0);

        for (Order order : orders) {
            List<OrderDetails> orderDetails = order.getOrderDetails();

            for (OrderDetails orderDetail : orderDetails) {
                sum = sum.add(orderDetail.getBuyerSum());
            }
        }
        return sum.divide(quantity, RoundingMode.HALF_EVEN);
    }

    private BigDecimal sumYearIncomes(int year) {
        List<Invoice> invoices = invoiceDao.getBuyersYearIncomedInvoices(year);
        BigDecimal incomeValue = BigDecimal.valueOf(0);

        for (Invoice invoice : invoices) {
            incomeValue = incomeValue.add(invoice.getValue());
        }
        return incomeValue;
    }

    private BigDecimal calculateAveragePurchase(int year, BigDecimal soldedQuantity) {
        Set<Order> orders = orderDao.getYearOrders(year);
        BigDecimal sum = BigDecimal.valueOf(0);

        for (Order order : orders) {
            List<OrderDetails> orderDetails = order.getOrderDetails();

            for (OrderDetails orderDetail : orderDetails) {
                sum = sum.add(orderDetail.getSupplierSum());
            }
        }
        return sum.divide(soldedQuantity, RoundingMode.HALF_EVEN);
    }

    private BigDecimal sumMonthlySoldQuantity(int year) {
        Set<Order> orders = orderDao.getYearOrders(year);
        BigDecimal totalQuantity = BigDecimal.valueOf(0);

        for (Order order : orders) {
            List<OrderDetails> orderDetails = order.getOrderDetails();

            for (OrderDetails orderDetail : orderDetails) {
                totalQuantity = totalQuantity.add(orderDetail.getQuantity());
            }
        }
        return totalQuantity;
    }

    private BigDecimal calculateSuppliersUsedAmount(int year) {
        List<Invoice> invoices = invoiceDao.getSuppliersYearInvoices(year);

        BigDecimal generalAmountToUse = BigDecimal.valueOf(0);
        for (Invoice invoice : invoices) {
            generalAmountToUse = generalAmountToUse.add(invoice.getValue());
        }

        BigDecimal amountToUse = BigDecimal.valueOf(0);
        for (Invoice invoice : invoices) {
            amountToUse = amountToUse.add(invoice.getAmountToUse());
        }

        return generalAmountToUse.subtract(amountToUse);
    }

    private BigDecimal calculateProfits(int year, BigDecimal sumCosts, BigDecimal boughtValue) {
        BigDecimal paidOrders = calculateBuyersUsedAmount(year);

        BigDecimal income = paidOrders.subtract(boughtValue);
        BigDecimal profits = income.add(sumCosts);

        return profits;
    }

    private BigDecimal calculateBuyersUsedAmount(int year) {
        List<Invoice> notUsedInvoices = invoiceDao.getBuyersYearIncomedInvoices(year);
        BigDecimal generalValue = BigDecimal.valueOf(0);
        BigDecimal notUsedAmount = BigDecimal.valueOf(0);

        for (Invoice invoice : notUsedInvoices) {
            generalValue = generalValue.add(invoice.getValue());
            notUsedAmount = notUsedAmount.add(invoice.getAmountToUse());
        }

        return generalValue.subtract(notUsedAmount);
    }

    private BigDecimal calculateBuyersNotPaidInvoices(int year) {
        BigDecimal result = new BigDecimal(0);

        List<Invoice> notPaidInvoicesNotCreatedToOrder = invoiceDao.getBuyersYearNotPaidInvoicesNotCreatedToOrder(year)
                .orElseThrow(NoSuchElementException::new);

        for (Invoice invoice : notPaidInvoicesNotCreatedToOrder) {
            result = result.add(invoice.getAmountToUse());
        }

        List<Invoice> notPaidInvoicesCreatedToOrder = invoiceDao.getBuyersYearNotPaidInvoicesCreatedToOrder(year)
                .orElseThrow(NoSuchElementException::new);

        for (Invoice invoice : notPaidInvoicesCreatedToOrder) {
            result = result.add(invoice.getValue());
        }

        return result;
    }

}
