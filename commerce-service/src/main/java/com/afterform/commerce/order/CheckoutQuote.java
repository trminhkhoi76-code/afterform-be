package com.afterform.commerce.order;

import java.util.List;

public record CheckoutQuote(String currency, List<QuoteLine> lines) {
    public CheckoutQuote {
        if(currency == null || !currency.matches("^[A-Z]{3}$"))
            throw new IllegalArgumentException("Invalid currency");
        if(lines == null || lines.isEmpty() || lines.size() > 100)
            throw new IllegalArgumentException("Invalid line count");
        if(lines.stream().anyMatch(java.util.Objects::isNull))
            throw new IllegalArgumentException("Missing line");
        lines = List.copyOf(lines);
    }
}
