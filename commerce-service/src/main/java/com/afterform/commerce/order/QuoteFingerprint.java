package com.afterform.commerce.order;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Deterministic request digest to reject reusing a key with different trusted cart values. */
final class QuoteFingerprint {
    private QuoteFingerprint() {}
    static String forQuote(CheckoutQuote quote) {
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            append(digest,quote.currency());
            append(digest,Integer.toString(quote.lines().size()));
            for(QuoteLine line:quote.lines()) {
                append(digest,line.variantId().toString());
                append(digest,line.sku());
                append(digest,line.productName());
                append(digest,Integer.toString(line.quantity()));
                append(digest,line.unitPrice().stripTrailingZeros().toPlainString());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch(NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable",ex);
        }
    }
    private static void append(MessageDigest digest,String s) {
        byte[] bytes=s.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(4).putInt(bytes.length).array());
        digest.update(bytes);
    }
}
