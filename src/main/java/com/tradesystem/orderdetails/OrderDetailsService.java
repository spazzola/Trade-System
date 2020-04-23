package com.tradesystem.orderdetails;

import com.tradesystem.buyer.Buyer;
import com.tradesystem.invoice.Invoice;
import com.tradesystem.invoice.InvoiceDao;
import com.tradesystem.ordercomment.OrderComment;
import com.tradesystem.ordercomment.OrderCommentDao;
import com.tradesystem.ordercomment.OrderCommentService;
import com.tradesystem.price.PriceDao;
import com.tradesystem.supplier.Supplier;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;


@Service
public class OrderDetailsService {


    private PriceDao priceDao;
    private InvoiceDao invoiceDao;
    private OrderDetailsDao orderDetailsDao;
    private OrderCommentService orderCommentService;
    private OrderCommentDao orderCommentDao;


    public OrderDetailsService(PriceDao priceDao, InvoiceDao invoiceDao, OrderDetailsDao orderDetailsDao,
                               OrderCommentService orderCommentService, OrderCommentDao orderCommentDao) {
        this.priceDao = priceDao;
        this.invoiceDao = invoiceDao;
        this.orderDetailsDao = orderDetailsDao;
        this.orderCommentService = orderCommentService;
        this.orderCommentDao = orderCommentDao;
    }

    @Transactional
    public void calculateOrderDetail(OrderDetails orderDetails) {
        BigDecimal buyerSum = calculateBuyerOrder(orderDetails);
        BigDecimal supplierSum = calculateSupplierOrder(orderDetails);

        orderDetails.setBuyerSum(buyerSum);
        orderDetails.setSupplierSum(supplierSum);
        orderDetailsDao.save(orderDetails);

        payForBuyerOrder2(orderDetails, buyerSum);
        payForSupplierOrder2(orderDetails, supplierSum);

    }

    private BigDecimal calculateBuyerOrder(OrderDetails orderDetails) {
        Long buyerId = orderDetails.getOrder().getBuyer().getId();
        Long productId = orderDetails.getProduct().getId();

        BigDecimal quantity = orderDetails.getQuantity();

        BigDecimal price;

        if (orderDetails.getTypedPrice().doubleValue() > 0) {
            price = orderDetails.getTypedPrice();
        } else {
            price = priceDao.getBuyerPrice(buyerId, productId);
        }

        if (price != null) {
            return quantity.multiply(price).setScale(2, RoundingMode.HALF_UP);
        } else {
            throw new RuntimeException("Kupiec nie ma ustawionej ceny dla tego produktu");
        }

    }

    private BigDecimal calculateSupplierOrder(OrderDetails orderDetails) {
        Long supplierId = orderDetails.getOrder().getSupplier().getId();
        Long productId = orderDetails.getProduct().getId();

        BigDecimal quantity = orderDetails.getQuantity();

        BigDecimal price = priceDao.getSupplierPrice(supplierId, productId);
        if (price != null) {
            return quantity.multiply(price).setScale(2, RoundingMode.HALF_UP);
        } else {
            throw new RuntimeException("Dostawca nie ma ustawionej ceny dla tego produktu");
        }

    }

    private void payForSupplierOrder2(OrderDetails orderDetails, BigDecimal amount) {
        Long supplierId = orderDetails.getOrder().getSupplier().getId();
        List<Invoice> invoices = invoiceDao.getSupplierNotUsedInvoices(supplierId);

        payForSupplierOrder(orderDetails, amount, invoices);
    }

    private void payForBuyerOrder2(OrderDetails orderDetails, BigDecimal amount) {
        Long buyerId = orderDetails.getOrder().getBuyer().getId();
        List<Invoice> invoices = invoiceDao.getBuyerNotUsedInvoices(buyerId);

        //invoices.sort(Comparator.comparing(Invoice::getId));

        payForBuyerOrder(orderDetails, amount, invoices);
    }


    private void payForSupplierOrder(OrderDetails orderDetails, BigDecimal amount, List<Invoice> invoices) {
        BigDecimal amountToPay = amount;
        amountToPay = amountToPay.setScale(2, RoundingMode.HALF_UP);
        List<String> invoiceNumbers = new ArrayList<>();
        int countedInvoices = 0;
        OrderComment orderComment;

        for (Invoice invoice : invoices) {
            BigDecimal invoiceValue = invoice.getAmountToUse();

            countedInvoices++;

            if (invoiceValue.subtract(amountToPay).compareTo(BigDecimal.ZERO) > 0) {
                invoice.setAmountToUse(invoiceValue.subtract(amountToPay));
                amount = BigDecimal.valueOf(0.0);

                saveInvoice(invoice, false);

                invoiceNumbers.add(invoice.getInvoiceNumber());

                if (countedInvoices > 1) {
                    String previousComment = orderDetails.getOrderComment().getSystemComment();
                    orderComment = orderDetails.getOrderComment();
                    orderComment.setSystemComment(previousComment + ", " + amountToPay + " z FV nr " + invoiceNumbers.get(countedInvoices - 1));
                    orderDetails.setOrderComment(orderComment);
                    orderCommentDao.save(orderComment);
                } else {
                    orderCommentService.addSupplierComment(orderDetails, amountToPay, invoice);
                }
                orderDetailsDao.save(orderDetails);
                break;

            } else if (invoiceValue.subtract(amountToPay).compareTo(BigDecimal.ZERO) < 0) {
                invoice.setAmountToUse(BigDecimal.valueOf(0.0));
                amount = amount.subtract(invoiceValue);
                amountToPay = amount;

                orderCommentService.addSupplierComment(orderDetails, invoiceValue, invoice);

                orderDetailsDao.save(orderDetails);
                saveInvoice(invoice, true);
                invoiceNumbers.add(invoice.getInvoiceNumber());

            } else if (invoiceValue.subtract(amountToPay).compareTo(BigDecimal.ZERO) == 0) {

                invoice.setAmountToUse(BigDecimal.valueOf(0.0));
                saveInvoice(invoice, true);

                orderCommentService.addSupplierComment(orderDetails, invoiceValue, invoice);
                invoiceNumbers.add(invoice.getInvoiceNumber());
                amount = BigDecimal.valueOf(0.0);
                break;

            } else if (amount.compareTo(BigDecimal.ZERO) == 0) {
                saveInvoice(invoice, true);
                invoiceNumbers.add(invoice.getInvoiceNumber());
                break;

            } else {
                saveInvoice(invoice, false);
                break;
            }
        }

        if (amount.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal negativeValue = amount.multiply(BigDecimal.valueOf(-1));
            createSupplierNegativeInvoice(negativeValue, orderDetails);

            Supplier supplier = orderDetails.getOrder().getSupplier();
            Invoice negativeInvoice = invoiceDao.getSupplierNegativeInvoice(supplier.getId())
                    .orElseThrow(RuntimeException::new);
            //Invoice negativeInvoice = optionalInvoice.get();
            orderCommentService.addLackAmountComment(orderDetails, negativeValue, negativeInvoice);
            orderDetailsDao.save(orderDetails);
        }

    }

    private void payForBuyerOrder(OrderDetails orderDetails, BigDecimal amount, List<Invoice> invoices) {
        BigDecimal amountToPay = orderDetails.getBuyerSum();
        amountToPay = amountToPay.setScale(2, RoundingMode.HALF_UP);
        List<String> invoiceNumbers = new ArrayList<>();
        int countedInvoices = 0;
        OrderComment orderComment;

        for (Invoice invoice : invoices) {
            BigDecimal invoiceValue = invoice.getAmountToUse();
            countedInvoices++;

            if (invoiceValue.subtract(amountToPay).compareTo(BigDecimal.ZERO) > 0) {

                invoice.setAmountToUse(invoiceValue.subtract(amountToPay));
                amount = BigDecimal.valueOf(0.0);

                saveInvoice(invoice, false);

                invoiceNumbers.add(invoice.getInvoiceNumber());

                if (countedInvoices > 1) {
                    String previousComment = orderDetails.getOrderComment().getSystemComment();

                    orderComment = orderDetails.getOrderComment();
                    orderComment.setSystemComment(previousComment + ", " + amountToPay + " z FV nr " + invoiceNumbers.get(countedInvoices - 1));
                    orderDetails.setOrderComment(orderComment);
                    orderCommentDao.save(orderComment);
                } else {
                    orderCommentService.addBuyerComment(orderDetails, amountToPay, invoice);
                }
                orderDetailsDao.save(orderDetails);
                break;

            } else if (invoiceValue.subtract(amountToPay).compareTo(BigDecimal.ZERO) < 0) {
                invoice.setAmountToUse(BigDecimal.valueOf(0.0));
                amount = amount.subtract(invoiceValue);
                amountToPay = amount;

                orderCommentService.addBuyerComment(orderDetails, invoiceValue, invoice);

                orderDetailsDao.save(orderDetails);
                saveInvoice(invoice, true);
                invoiceNumbers.add(invoice.getInvoiceNumber());

            } else if (invoiceValue.subtract(amountToPay).compareTo(BigDecimal.ZERO) == 0) {

                invoice.setAmountToUse(BigDecimal.valueOf(0.0));
                saveInvoice(invoice, true);

                orderCommentService.addBuyerComment(orderDetails, invoiceValue, invoice);

                invoiceNumbers.add(invoice.getInvoiceNumber());
                amount = BigDecimal.valueOf(0.0);
                break;

            } else if (amount.compareTo(BigDecimal.ZERO) == 0) {
                saveInvoice(invoice, true);
                invoiceNumbers.add(invoice.getInvoiceNumber());
                break;

            } else {
                saveInvoice(invoice, false);
                break;
            }
        }

        if (amount.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal negativeValue = amount.multiply(BigDecimal.valueOf(-1));
            createBuyerNegativeInvoice(negativeValue, orderDetails);

            Buyer buyer = orderDetails.getOrder().getBuyer();
            Invoice negativeInvoice = invoiceDao.getBuyerNegativeInvoice(buyer.getId())
                    .orElseThrow(RuntimeException::new);
            //Invoice negativeInvoice = optionalInvoice.get();
            orderCommentService.addLackAmountComment(orderDetails, negativeValue, negativeInvoice);

            orderDetailsDao.save(orderDetails);
        }

    }

    private void createBuyerNegativeInvoice(BigDecimal amount, OrderDetails orderDetails) {
        Buyer buyer = orderDetails.getOrder().getBuyer();
        Optional<Invoice> negativeInvoice = invoiceDao.getBuyerNegativeInvoice(buyer.getId());

        if (negativeInvoice.isPresent()) {
            Invoice invoice = negativeInvoice.get();
            BigDecimal invoiceValue = invoice.getValue();
            BigDecimal invoiceAmount = invoice.getAmountToUse();
            BigDecimal newInvoiceAmount = invoiceAmount.add(amount);
            invoice.setValue(invoiceValue.add(amount));
            invoice.setAmountToUse(newInvoiceAmount);
            invoiceDao.save(invoice);
        } else {
            Invoice invoice = new Invoice();
            invoice.setInvoiceNumber("Negatywna");
            invoice.setAmountToUse(amount);
            invoice.setValue(amount);
            invoice.setDate(LocalDate.now());
            invoice.setBuyer(buyer);
            invoiceDao.save(invoice);
        }

    }

    private void createSupplierNegativeInvoice(BigDecimal amount, OrderDetails orderDetails) {
        Supplier supplier = orderDetails.getOrder().getSupplier();
        Optional<Invoice> negativeInvoice = invoiceDao.getSupplierNegativeInvoice(supplier.getId());

        if (negativeInvoice.isPresent()) {
            Invoice invoice = negativeInvoice.get();
            BigDecimal invoiceAmount = invoice.getAmountToUse();
            BigDecimal newInvoiceAmount = invoiceAmount.add(amount);

            invoice.setAmountToUse(newInvoiceAmount);
            invoiceDao.save(invoice);
        } else {
            Invoice invoice = new Invoice();
            invoice.setInvoiceNumber("Negatywna");
            invoice.setAmountToUse(amount);
            invoice.setValue(amount);
            invoice.setDate(LocalDate.now());
            invoice.setSupplier(supplier);
            invoiceDao.save(invoice);
        }
    }

    private void saveInvoice(Invoice invoice, boolean isUsed) {
        invoice.setUsed(isUsed);
        invoiceDao.save(invoice);
    }
}
