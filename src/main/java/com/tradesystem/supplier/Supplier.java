package com.tradesystem.supplier;

import java.math.BigDecimal;
import java.util.List;

import com.tradesystem.invoice.Invoice;
import com.tradesystem.order.Order;
import com.tradesystem.price.Price;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;

@Entity
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "suppliers")
public class Supplier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "supplier_id")
    private Long id;

    private String name;

    private BigDecimal currentBalance;

    @OneToMany(mappedBy = "supplier", fetch = FetchType.EAGER)
    private List<Price> prices;

    @OneToMany(mappedBy = "supplier")
    private List<Invoice> invoices;

    @OneToMany(mappedBy = "supplier")
    private List<Order> orders;
}