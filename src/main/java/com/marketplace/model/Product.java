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
    private Integer version;
    private String supplierEmail;

    /**
     * Default constructor for Jackson deserialization.
     */
    public Product() {}

    /**
     * Constructs a new Product with the specified details.
     *
     * @param id            The unique identifier of the product.
     * @param name          The name of the product.
     * @param price         The price of the product.
     * @param category      The category the product belongs to.
     * @param version       The version of the product for optimistic locking.
     * @param supplierEmail The sensitive email of the supplier.
     */
    public Product(String id, String name, double price, String category, Integer version, String supplierEmail) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.category = category;
        this.version = version;
        this.supplierEmail = supplierEmail;
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

    /**
     * Returns the product version.
     *
     * @return The product version.
     */
    @JsonProperty("version")
    public Integer getVersion() { return version; }

    /**
     * Sets the product version.
     *
     * @param version The product version.
     */
    public void setVersion(Integer version) { this.version = version; }

    /**
     * Returns the supplier email.
     *
     * @return The supplier email.
     */
    @JsonProperty("supplierEmail")
    public String getSupplierEmail() { return supplierEmail; }

    /**
     * Sets the supplier email.
     *
     * @param supplierEmail The supplier email.
     */
    public void setSupplierEmail(String supplierEmail) { this.supplierEmail = supplierEmail; }
}