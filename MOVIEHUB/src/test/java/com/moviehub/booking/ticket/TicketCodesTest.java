package com.moviehub.booking.ticket;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TicketCodesTest {

    private final TicketCodes codes = new TicketCodes("test-ticket-secret", "");

    @Test
    void issuedCodesVerify() {
        String code = codes.issue("MH-ABC234");
        assertThat(code).startsWith("MH1.MH-ABC234.");
        assertThat(codes.verify(code)).contains("MH-ABC234");
        assertThat(codes.verify("  " + code + "\n")).contains("MH-ABC234");
    }

    @Test
    void rejectsForgedOrMalformedCodes() {
        String code = codes.issue("MH-ABC234");
        String signature = code.substring(code.lastIndexOf('.') + 1);
        assertThat(codes.verify("MH1.MH-ZZZ999." + signature)).isEmpty();
        assertThat(codes.verify(code.substring(0, code.length() - 1) + "A")).isEmpty();
        assertThat(codes.verify(new TicketCodes("another-secret", "").issue("MH-ABC234"))).isEmpty();
        assertThat(codes.verify("MH-ABC234")).isEmpty();
        assertThat(codes.verify("")).isEmpty();
        assertThat(codes.verify(null)).isEmpty();
    }

    @Test
    void qrCodeIsAPng() {
        byte[] png = new QrCodes().png(codes.issue("MH-ABC234"), 200);
        assertThat(png).startsWith(0x89, 'P', 'N', 'G');
    }
}
