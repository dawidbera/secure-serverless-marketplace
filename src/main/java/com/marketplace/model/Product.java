package com.marketplace.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a product in the marketplace.
 */
public class Product {
    private String id;
    private String name;
    private double price;
    private String category;

    /**
     * Default constructor for Jackson deserialization.
     */
    public Product() {}

    /**
     * Constructs a new Product with the specified details.
     *
     * @param id       The unique identifier of the product.
     * @param name     The name of the product.
     * @param price    The price of the product.
     * @param category The category the product belongs to.
     */
    public Product(String id, String name, double price, String category) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.category = category;
    }

    /**
     * Returns the product ID.
     *
     * @return The product ID.
     */
    @JsonProperty("id")
    public String getId() { return id; }

    /**
     * Sets the product ID.
     *
     * @param id The product ID.
     */
    public void setId(String id) { this.id = id; }

    /**
     * Returns the product name.
     *
     * @return The product name.
     */
    @JsonProperty("name")
    public String getName() { return name; }

    /**
     * Sets the product name.
     *
     * @param name The product name.
     */
    public void setName(String name) { this.name = name; }

    /**
     * Returns the product price.
     *
     * @return The product price.
     */
    @JsonProperty("price")
    public double getPrice() { return price; }

    /**
     * Sets the product price.
     *
     * @param price The product price.
     */
    public void setPrice(double price) { this.price = price; }

    /**
     * Returns the product category.
     *
     * @return The product category.
     */
    @JsonProperty("category")
    public String getCategory() { return category; }

    /**
     * Sets the product category.
     *
     * @param category The product category.
     */
    public void setCategory(String category) { this.category = category; }
}