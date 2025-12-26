package com.seowon.coding.service;

import com.seowon.coding.domain.model.Product;
import com.seowon.coding.domain.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductService {
    
    private final ProductRepository productRepository;
    
    @Transactional(readOnly = true)
    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }
    
    @Transactional(readOnly = true)
    public Optional<Product> getProductById(Long id) {
        return productRepository.findById(id);
    }
    
    public Product createProduct(Product product) {
        return productRepository.save(product);
    }
    
    public Product updateProduct(Long id, Product product) {
        if (!productRepository.existsById(id)) {
            throw new RuntimeException("Product not found with id: " + id);
        }
        product.setId(id);
        return productRepository.save(product);
    }

    public void applyBulkPriceChangeNullCheck(List<Long> productIds){
        if (productIds == null || productIds.isEmpty()) {
            throw new IllegalArgumentException("empty productIds");
        }
    }

    public void deleteProduct(Long id) {
        if (!productRepository.existsById(id)) {
            throw new RuntimeException("Product not found with id: " + id);
        }
        productRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<Product> findProductsByCategory(String category) {
        // TODO #1: 구현 항목
        // Repository를 사용하여 category 로 찾을 제품목록 제공
        List<Product> foundProduct = productRepository.findByCategory(category);
        if(foundProduct.isEmpty()){
            throw new RuntimeException("Product not found with Category: " + category);
        }
        return foundProduct;
    }

    /**
     * TODO #6 (리펙토링): 대량 가격 변경 로직을 도메인 객체 안으로 리팩토링하세요.
     */
    public void applyBulkPriceChange(List<Long> productIds, Long percentage, boolean includeTax) {
        applyBulkPriceChangeNullCheck(productIds);
        // 잘못된 구현 예시: double 사용, 루프 내 개별 조회/저장, 하드코딩 세금/반올림 규칙
        List<Product> products = new ArrayList<>();
        for (Long id : productIds) {
            Product p = productRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + id));

            Long base = (long) (p.getPrice() == null ? 0.0 : p.getPrice().doubleValue());
            Long changed = (long) (base + (base * (percentage / 100.0))); // 부동소수점 오류 가능
            if (includeTax) {
                changed = (long) (changed * 1.1); // 하드코딩 VAT 10%, 지역/카테고리별 규칙 미반영
            }
            // 임의 반올림: 일관되지 않은 스케일/반올림 모드
            BigDecimal newPrice = BigDecimal.valueOf(changed).setScale(2, RoundingMode.HALF_UP);
            p.setPrice(newPrice);
            products.add(p);
        }
        productRepository.saveAll(products); // 루프마다 저장 (비효율적)
    }
}
