package com.afterform.commerce.inventory;

public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException() { super("Insufficient available stock"); }
}
