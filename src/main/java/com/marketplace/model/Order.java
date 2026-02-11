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

    /**
     * Returns the unique identifier for the order.
     *
     * @return The order ID.
     */
    @JsonProperty("orderId")
    public String getOrderId() { return orderId; }

    /**
     * Sets the unique identifier for the order.
     *
     * @param orderId The order ID to set.
     */
    public void setOrderId(String orderId) { this.orderId = orderId; }

    /**
     * Returns the ID of the product being ordered.
     *
     * @return The product ID.
     */
    @JsonProperty("productId")
    public String getProductId() { return productId; }

    /**
     * Sets the ID of the product being ordered.
     *
     * @param productId The product ID to set.
     */
    public void setProductId(String productId) { this.productId = productId; }

    /**
     * Returns the ID of the user who placed the order.
     *
     * @return The user ID.
     */
    @JsonProperty("userId")
    public String getUserId() { return userId; }

    /**
     * Sets the ID of the user who placed the order.
     *
     * @param userId The user ID to set.
     */
    public void setUserId(String userId) { this.userId = userId; }

    /**
     * Returns the quantity of the product ordered.
     *
     * @return The quantity.
     */
    @JsonProperty("quantity")
    public int getQuantity() { return quantity; }

    /**
     * Sets the quantity of the product ordered.
     *
     * @param quantity The quantity to set.
     */
    public void setQuantity(int quantity) { this.quantity = quantity; }

    /**
     * Returns the timestamp when the order was placed.
     *
     * @return The timestamp.
     */
    @JsonProperty("timestamp")
    public long getTimestamp() { return timestamp; }

    /**
     * Sets the timestamp when the order was placed.
     *
     * @param timestamp The timestamp to set.
     */
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
