package com.seowon.coding.domain.dto;

import com.seowon.coding.domain.model.Product;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class OrderRequestDTO {
    private String customerName;
    private String customerEmail;
    private List<Product> products;
}
