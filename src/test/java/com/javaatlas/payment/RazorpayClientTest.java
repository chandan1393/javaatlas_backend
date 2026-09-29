package com.javaatlas.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RazorpayClientTest {

    @Test
    void computesCheckoutSignatureLikeRazorpay() {
        String signature = RazorpayClient.hmacSha256Hex("test_secret", "order_9A33XWu170gUtm|pay_29QQoUBi66xm2f");
        assertEquals("a982c20f48234e966ccc8d903bff75730b34341007236ad8c8a9d7c0ae5848c5", signature);
    }

    @Test
    void acceptsMatchingSignatureOnly() {
        String expected = RazorpayClient.hmacSha256Hex("whsec", "{\"event\":\"payment.captured\"}");
        assertEquals("4673dd707ef4c41b987cb7fefe1583142dc702388c93145b7814b9ad3d3c183e", expected);
        assertTrue(RazorpayClient.matches(expected, expected));
        assertFalse(RazorpayClient.matches(expected, "deadbeef"));
        assertFalse(RazorpayClient.matches(expected, null));
    }
}
