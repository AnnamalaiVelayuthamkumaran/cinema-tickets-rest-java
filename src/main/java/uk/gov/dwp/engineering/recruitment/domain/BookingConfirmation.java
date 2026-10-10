package uk.gov.dwp.engineering.recruitment.domain;

import java.math.BigDecimal;

public record BookingConfirmation(Long accountId, Long seatCount, BigDecimal totalCost) {
    public BookingConfirmation(final Long accountId) {
        this(accountId, null, null);
    }
}
