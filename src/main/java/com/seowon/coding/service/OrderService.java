package com.seowon.coding.service;

import com.seowon.coding.domain.model.Order;
import com.seowon.coding.domain.model.OrderItem;
import com.seowon.coding.domain.model.ProcessingStatus;
import com.seowon.coding.domain.model.Product;
import com.seowon.coding.domain.repository.OrderRepository;
import com.seowon.coding.domain.repository.ProcessingStatusRepository;
import com.seowon.coding.domain.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Transactional
public class OrderService {
    
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final ProcessingStatusRepository processingStatusRepository;

    @Transactional(readOnly = true)
    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }
    
    @Transactional(readOnly = true)
    public Optional<Order> getOrderById(Long id) {
        return orderRepository.findById(id);
    }
    

    public Order updateOrder(Long id, Order order) {
        if (!orderRepository.existsById(id)) {
            throw new RuntimeException("Order not found with id: " + id);
        }
        order.setId(id);
        return orderRepository.save(order);
    }
    
    public void deleteOrder(Long id) {
        if (!orderRepository.existsById(id)) {
            throw new RuntimeException("Order not found with id: " + id);
        }
        orderRepository.deleteById(id);
    }

    public void checkCustomerNameAndEmail(String customerName, String customerEmail) {
        if (customerName == null || customerEmail == null) {
            throw new IllegalArgumentException("customer info required");
        }
    }

    public void orderCheck(List<OrderProduct> orderProducts) {
        if (orderProducts == null || orderProducts.isEmpty()) {
            throw new IllegalArgumentException("orderReqs invalid");
        }
    }

    public Product checkProductsQuantity(OrderProduct orderProducts) {
        int qty = orderProducts.getQuantity();
        Long productId = orderProducts.getProductId();

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));
        if (qty <= 0) {
            throw new IllegalArgumentException("quantity must be positive: " + qty);
        }
//        찾기product에 있는 수량과 입력받은 수량 비교 후 현재 보유보다 큰 입력값일 경우 throw
        if (product.getStockQuantity() < qty) {
            throw new IllegalStateException("insufficient stock for product " + productId);
        }
        return product;
    }

    public Order placeOrder(String customerName, String customerEmail, List<Long> productIds, List<Integer> quantities) {
        // TODO #3: 구현 항목
        Order order = new Order();
        order.setCustomerName(customerName);
        order.setCustomerEmail(customerEmail);

        List<Product> products = productIds.stream().map(productId -> {
            return productRepository.findById(productId).orElseThrow(() -> new RuntimeException("Product not found with id: " + productId));
        }).toList();
//        OrderItem orderItem = new OrderItem(products);
        // * 주어진 고객 정보로 새 Order를 생성
        // * 지정된 Product를 주문에 추가
        // * order 의 상태를 PENDING 으로 변경
        order.setStatus(Order.OrderStatus.PENDING);
        // * orderDate 를 현재시간으로 설정
        order.setOrderDate(LocalDateTime.now());
        // * order 를 저장
        orderRepository.save(order);
        // * 각 Product 의 재고를 수정
        productIds.forEach(id -> {
            Product product = productRepository.findById(id).orElseThrow(() -> new RuntimeException("Product not found with id: " + id));
            quantities.forEach(quantity -> {
                product.decreaseStock(quantity);
                productRepository.save(product);
            });
        });
        // * placeOrder 메소드의 시그니처는 변경하지 않은 채 구현하세요.
        return null;
    }

    /**
     * TODO #4 (리펙토링): Service 에 몰린 도메인 로직을 도메인 객체 안으로 이동
     * - Repository 조회는 도메인 객체 밖에서 해결하여 의존 차단 합니다.
     * - #3 에서 추가한 도메인 메소드가 있을 경우 사용해도 됩니다.
     */
    public Order checkoutOrder(String customerName,
                               String customerEmail,
                               List<OrderProduct> orderProducts,
                               String couponCode) {
//        빈값 들어왔을 때 throw
        checkCustomerNameAndEmail(customerName, customerEmail);
        orderCheck(orderProducts);

        Order order = Order.builder()
                .customerName(customerName)
                .customerEmail(customerEmail)
                .status(Order.OrderStatus.PENDING)
                .orderDate(LocalDateTime.now())
                .items(new ArrayList<>())
                .totalAmount(BigDecimal.ZERO)
                .build();


        BigDecimal subtotal = BigDecimal.ZERO;
//       args == orderProducts + Order
//        id + product + qty 3개 받아서 처리
//        주문상품 리스트를 받아서 수량, 아이디 꺼내고 +
        orderProducts.get(orderProducts.size()).getProductId();
        for (OrderProduct req : orderProducts) {
            int qty = req.getQuantity();
            Product product = checkProductsQuantity(req);

            OrderItem item = OrderItem.builder()
                    .order(order)
                    .product(product)
                    .quantity(qty)
                    .price(product.getPrice())
                    .build();
            order.getItems().add(item);

//         현재 상품의 수량 - 입력받은 수량
            product.decreaseStock(qty);
            subtotal = subtotal.add(product.getPrice().multiply(BigDecimal.valueOf(qty)));
        }

        BigDecimal shipping = subtotal.compareTo(new BigDecimal("100.00")) >= 0 ? BigDecimal.ZERO : new BigDecimal("5.00");
        BigDecimal discount = (couponCode != null && couponCode.startsWith("SALE")) ? new BigDecimal("10.00") : BigDecimal.ZERO;

        order.setTotalAmount(subtotal.add(shipping).subtract(discount));
        order.setStatus(Order.OrderStatus.PROCESSING);
        return orderRepository.save(order);
    }

    /**
     * TODO #5: 코드 리뷰 - 장시간 작업과 진행률 저장의 트랜잭션 분리
     * - 시나리오: 일괄 배송 처리 중 진행률을 저장하여 다른 사용자가 조회 가능해야 함.
     * - 리뷰 포인트: proxy 및 transaction 분리, 예외 전파/롤백 범위, 가독성 등
     * - 상식적인 수준에서 요구사항(기획)을 가정하며 최대한 상세히 작성하세요.
     */
    @Transactional
    public void bulkShipOrdersParent(String jobId, List<Long> orderIds) {

//        orElseThrow로 예외 처리 해서 데이터 가져와야 예외처리를 할 수 있을 것 같고,
//        ProcessingStatus 부분을 find 하려는 건지 save하려는 건지 책이 분리 부탁드립니다.
        ProcessingStatus ps = processingStatusRepository.findByJobId(jobId)
                .orElseGet(() -> processingStatusRepository.save(ProcessingStatus.builder().jobId(jobId).build()));

//        jobId, orderIds 가 null값일 경우 최상단 if문으로 선제 return 처리가 더 좋을 것 같습니다.
        ps.markRunning(orderIds == null ? 0 : orderIds.size());
        processingStatusRepository.save(ps);

        int processed = 0;
        for (Long orderId : (orderIds == null ? List.<Long>of() : orderIds)) {
            try {
                // 오래 걸리는 작업 이라는 가정 시뮬레이션 (예: 외부 시스템 연동, 대용량 계산 등)
                orderRepository.findById(orderId).ifPresent(o -> o.setStatus(Order.OrderStatus.PROCESSING));
                // 중간 진행률 저장
                this.updateProgressRequiresNew(jobId, ++processed, orderIds.size());
            } catch (Exception e) {
            }
        }
        ps = processingStatusRepository.findByJobId(jobId).orElse(ps);
        ps.markCompleted();
        processingStatusRepository.save(ps);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateProgressRequiresNew(String jobId, int processed, int total) {
        ProcessingStatus ps = processingStatusRepository.findByJobId(jobId)
                .orElseGet(() -> ProcessingStatus.builder().jobId(jobId).build());
        ps.updateProgress(processed, total);
        processingStatusRepository.save(ps);
    }

}