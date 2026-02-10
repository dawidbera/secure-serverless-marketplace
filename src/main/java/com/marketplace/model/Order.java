package com.marketplace.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents an order in the marketplace.
 */
public class Order {
    private String orderId;
    private String productId;
    private String userId;
    private int quantity;
    private long timestamp;

    /**
     * Default constructor for Jackson deserialization.
     */
    public Order() {}

    /**
     * Constructs a new Order.
     *
     * @param orderId   The unique identifier for the order.
     * @param productId The ID of the product being ordered.
     * @param userId    The ID of the user placing the order.
     * @param quantity  The quantity of the product ordered.
     * @param timestamp The time the order was placed.
     */
    public Order(String orderId, String productId, String userId, int quantity, long timestamp) {
        this.orderId = orderId;
        this.productId = productId;
        this.userId = userId;
        this.quantity = quantity;
        this.timestamp = timestamp;
    }

    @JsonProperty("orderId")
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    @JsonProperty("productId")
    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    @JsonProperty("userId")
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    @JsonProperty("quantity")
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    @JsonProperty("timestamp")
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
