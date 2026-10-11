package uk.gov.dwp.engineering.recruitment.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

class BookingConfirmationTest {

  @Test
  void givenBookingConfirmation_whenGetAccount_thenExpectedValueReturned() {
    final BookingConfirmation bookingConfirmation = new BookingConfirmation(99L);
    assertEquals(99L, bookingConfirmation.accountId());
  }

  @Test
  void givenBookingConfirmation_whenGetBookingConfirmation_thenExpectedValueReturned() {
    final BookingConfirmation bookingConfirmation = new BookingConfirmation(99L, 2L, new BigDecimal("43.49"));
    assertEquals(99L, bookingConfirmation.accountId());
    assertEquals(2L, bookingConfirmation.seatCount());
    assertEquals(new BigDecimal("43.49"), bookingConfirmation.totalCost());
  }
}
