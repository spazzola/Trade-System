package com.tradesystem.payment;

import com.tradesystem.invoice.Invoice;
import com.tradesystem.orderdetails.OrderDetails;
import lombok.*;

import javax.persistence.*;

@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "buyer_invoice_fk")
    private Invoice buyerInvoice;

    @ManyToOne
    @JoinColumn(name = "supplier_invoice_fk")
    private Invoice supplierInvoice;

    @ManyToOne
    @JoinColumn(name = "order_details_fk")
    private OrderDetails orderDetails;
}
