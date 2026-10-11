package uk.gov.dwp.engineering.recruitment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.dwp.engineering.recruitment.domain.BookingConfirmation;
import uk.gov.dwp.engineering.recruitment.domain.TicketRequest;
import uk.gov.dwp.engineering.recruitment.domain.TicketType;
import uk.gov.dwp.engineering.recruitment.exception.InvalidBookingException;
import uk.gov.dwp.engineering.recruitment.thirdparty.PaymentService;
import uk.gov.dwp.engineering.recruitment.thirdparty.SeatReservationService;

import java.math.BigDecimal;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static uk.gov.dwp.engineering.recruitment.CinemaConstants.*;

@ExtendWith(MockitoExtension.class)
class CinemaTicketsServiceImplTest {

    private static final Long ACCOUNT_ID = 123L;
    @Mock
    private PaymentService paymentService;
    @Mock
    private SeatReservationService seatReservationService;
    private CinemaTicketsService cinemaTicketsService;

    static Stream<Arguments> validPurchases() {
        return Stream.of(
                Arguments.of("one adult", tickets(ticket(TicketType.ADULT, 1)), "25.99", 1L),
                Arguments.of("adult and child", tickets(ticket(TicketType.ADULT, 1), ticket(TicketType.CHILD, 1)), "43.49", 2L),
                Arguments.of("adults, children and an infant",
                        tickets(ticket(TicketType.ADULT, 2), ticket(TicketType.CHILD, 2), ticket(TicketType.INFANT, 1)), "86.98", 4L),
                Arguments.of("repeated ticket types are added together",
                        tickets(ticket(TicketType.ADULT, 1), ticket(TicketType.ADULT, 1), ticket(TicketType.CHILD, 1), ticket(TicketType.INFANT, 1)),
                        "69.48", 3L),
                Arguments.of("one infant per adult is allowed",
                        tickets(ticket(TicketType.ADULT, 2), ticket(TicketType.INFANT, 2)), "51.98", 2L),
                Arguments.of("exactly 25 tickets including infants",
                        tickets(ticket(TicketType.ADULT, 10), ticket(TicketType.CHILD, 10), ticket(TicketType.INFANT, 5)), "434.90", 20L),
                Arguments.of("exactly 25 adult tickets", tickets(ticket(TicketType.ADULT, 25)), "649.75", 25L),
                Arguments.of("a zero-count line is ignored",
                        tickets(ticket(TicketType.ADULT, 2), ticket(TicketType.INFANT, 0)), "51.98", 2L));
    }

    static Stream<Arguments> invalidPurchases() {
        return Stream.of(
                Arguments.of("null account id", null, tickets(ticket(TicketType.ADULT, 1)), INVALID_ACCOUNT),
                Arguments.of("zero account id", 0L, tickets(ticket(TicketType.ADULT, 1)), INVALID_ACCOUNT),
                Arguments.of("negative account id", -1L, tickets(ticket(TicketType.ADULT, 1)), INVALID_ACCOUNT),
                Arguments.of("null ticket request array", ACCOUNT_ID, null, EMPTY_REQUEST),
                Arguments.of("no ticket requests", ACCOUNT_ID, tickets(), EMPTY_REQUEST),
                Arguments.of("null ticket request", ACCOUNT_ID, tickets(ticket(TicketType.ADULT, 1), null), INVALID_TICKET_TYPE),
                Arguments.of("null ticket type", ACCOUNT_ID, tickets(ticket(null, 1)), INVALID_TICKET_TYPE),
                Arguments.of("negative ticket count", ACCOUNT_ID,
                        tickets(ticket(TicketType.ADULT, 2), ticket(TicketType.CHILD, -1)), TICKET_COUNT_NEGATIVE),
                Arguments.of("only zero-count lines", ACCOUNT_ID, tickets(ticket(TicketType.ADULT, 0)), MINIMUM_TICKET),
                Arguments.of("26 tickets in one line", ACCOUNT_ID, tickets(ticket(TicketType.ADULT, 26)),
                        MAXIMUM_TICKET_LIMIT),
                Arguments.of("26 tickets across lines", ACCOUNT_ID,
                        tickets(ticket(TicketType.ADULT, 10), ticket(TicketType.CHILD, 10), ticket(TicketType.INFANT, 6)), MAXIMUM_TICKET_LIMIT),
                Arguments.of("infants count towards the 25", ACCOUNT_ID,
                        tickets(ticket(TicketType.ADULT, 13), ticket(TicketType.INFANT, 13)), MAXIMUM_TICKET_LIMIT),
                Arguments.of("counts that would overflow an int", ACCOUNT_ID,
                        tickets(ticket(TicketType.ADULT, 1), ticket(TicketType.CHILD, Integer.MAX_VALUE)), MAXIMUM_TICKET_LIMIT),
                Arguments.of("child without adult", ACCOUNT_ID, tickets(ticket(TicketType.CHILD, 1)), NO_ADULT_MESSAGE),
                Arguments.of("infant without adult", ACCOUNT_ID, tickets(ticket(TicketType.INFANT, 1)), NO_ADULT_MESSAGE),
                Arguments.of("child and infant without adult", ACCOUNT_ID,
                        tickets(ticket(TicketType.CHILD, 2), ticket(TicketType.INFANT, 1)), NO_ADULT_MESSAGE),
                Arguments.of("more infants than adults", ACCOUNT_ID,
                        tickets(ticket(TicketType.ADULT, 1), ticket(TicketType.INFANT, 2)), MORE_INFANT_THAN_ADULT_MESSAGE));
    }

    private static TicketRequest ticket(final TicketType type, final int count) {
        return new TicketRequest(type, count);
    }

    private static TicketRequest[] tickets(final TicketRequest... requests) {
        return requests;
    }

    @BeforeEach
    void setUp() {
        cinemaTicketsService = new CinemaTicketsServiceImpl(
                paymentService, seatReservationService);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("validPurchases")
    void givenValidPurchase_whenPurchaseTickets_thenPaysThenReservesAndConfirms(
            final String description, final TicketRequest[] requests, final String expectedCost,
            final long expectedSeats) {

        final BookingConfirmation confirmation =
                cinemaTicketsService.purchaseTickets(ACCOUNT_ID, requests);

        assertThat(confirmation.accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(confirmation.seatCount()).isEqualTo(expectedSeats);
        assertThat(confirmation.totalCost()).isEqualByComparingTo(expectedCost);

        final ArgumentCaptor<BigDecimal> amountPaid = ArgumentCaptor.forClass(BigDecimal.class);
        final InOrder inOrder = inOrder(paymentService, seatReservationService);
        inOrder.verify(paymentService).debitAccount(eq(ACCOUNT_ID), amountPaid.capture());
        inOrder.verify(seatReservationService).reserveSeats(ACCOUNT_ID, expectedSeats);
        verifyNoMoreInteractions(paymentService, seatReservationService);

        assertThat(amountPaid.getValue()).isEqualByComparingTo(expectedCost);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidPurchases")
    void givenInvalidPurchase_whenPurchaseTickets_thenRejectedAndNothingPaidOrReserved(
            final String description, final Long accountId, final TicketRequest[] requests,
            final String expectedMessage) {

        assertThatThrownBy(() -> cinemaTicketsService.purchaseTickets(accountId, requests))
                .isInstanceOf(InvalidBookingException.class)
                .hasMessage(expectedMessage);

        verifyNoInteractions(paymentService, seatReservationService);
    }

}
