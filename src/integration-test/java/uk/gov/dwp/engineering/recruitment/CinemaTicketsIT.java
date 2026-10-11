package uk.gov.dwp.engineering.recruitment;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.dwp.engineering.recruitment.CinemaConstants.EMPTY_REQUEST;
import static uk.gov.dwp.engineering.recruitment.CinemaConstants.INVALID_ACCOUNT;
import static uk.gov.dwp.engineering.recruitment.CinemaConstants.INVALID_TICKET_TYPE;
import static uk.gov.dwp.engineering.recruitment.CinemaConstants.MAXIMUM_TICKET_LIMIT;
import static uk.gov.dwp.engineering.recruitment.CinemaConstants.MINIMUM_TICKET;
import static uk.gov.dwp.engineering.recruitment.CinemaConstants.MORE_INFANT_THAN_ADULT_MESSAGE;
import static uk.gov.dwp.engineering.recruitment.CinemaConstants.NO_ADULT_MESSAGE;
import static uk.gov.dwp.engineering.recruitment.CinemaConstants.TICKET_COUNT_NEGATIVE;

import java.math.BigDecimal;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import uk.gov.dwp.engineering.recruitment.thirdparty.PaymentService;
import uk.gov.dwp.engineering.recruitment.thirdparty.SeatReservationService;

@SpringBootTest
@AutoConfigureMockMvc
public class CinemaTicketsIT {
    private static final String BOOKINGS_URL = "/cinema/bookings";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;

    @MockitoBean
    private SeatReservationService seatReservationService;

    static Stream<Arguments> validBookings() {
        return Stream.of(
                Arguments.of("adults and a child", """
            [{"type": "ADULT", "ticketCount": 2}, {"type": "CHILD", "ticketCount": 1}]
            """, 3, "69.48"),
                Arguments.of("one infant per adult", """
            [{"type": "ADULT", "ticketCount": 2}, {"type": "INFANT", "ticketCount": 2}]
            """, 2, "51.98"),
                Arguments.of("exactly 25 tickets including infants", """
            [{"type": "ADULT", "ticketCount": 10}, {"type": "CHILD", "ticketCount": 10},
             {"type": "INFANT", "ticketCount": 5}]
            """, 20, "434.90"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("validBookings")
    void givenValidBooking_whenPostBooking_thenPaysReservesAndConfirms(final String description,
                                                                       final String ticketRequests, final int expectedSeats, final String expectedCost)
            throws Exception {
        postBooking("{\"accountId\": 1, \"ticketRequests\": " + ticketRequests + "}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId").value(1))
                .andExpect(jsonPath("$.seatCount").value(expectedSeats))
                .andExpect(jsonPath("$.totalCost").value(Double.parseDouble(expectedCost)));

        final InOrder inOrder = inOrder(paymentService, seatReservationService);
        inOrder.verify(paymentService).debitAccount(eq(1L),
                argThat(amount -> amount.compareTo(new BigDecimal(expectedCost)) == 0));
        inOrder.verify(seatReservationService).reserveSeats(1L, (long) expectedSeats);
    }

    static Stream<Arguments> rejectedBookings() {
        return Stream.of(
                Arguments.of("account id missing", """
            {"ticketRequests": [{"type": "ADULT", "ticketCount": 1}]}
            """, INVALID_ACCOUNT),
                Arguments.of("account id zero", """
            {"accountId": 0, "ticketRequests": [{"type": "ADULT", "ticketCount": 1}]}
            """, INVALID_ACCOUNT),
                Arguments.of("ticket requests missing", """
            {"accountId": 1}
            """, EMPTY_REQUEST),
                Arguments.of("empty ticket requests", """
            {"accountId": 1, "ticketRequests": []}
            """, EMPTY_REQUEST),
                Arguments.of("null ticket request", """
            {"accountId": 1, "ticketRequests": [null]}
            """, INVALID_TICKET_TYPE),
                Arguments.of("ticket type missing", """
            {"accountId": 1, "ticketRequests": [{"ticketCount": 1}]}
            """, INVALID_TICKET_TYPE),
                Arguments.of("negative ticket count", """
            {"accountId": 1, "ticketRequests": [{"type": "ADULT", "ticketCount": 2},
             {"type": "CHILD", "ticketCount": -1}]}
            """, TICKET_COUNT_NEGATIVE),
                Arguments.of("no tickets in total", """
            {"accountId": 1, "ticketRequests": [{"type": "ADULT", "ticketCount": 0}]}
            """, MINIMUM_TICKET),
                Arguments.of("26 tickets", """
            {"accountId": 1, "ticketRequests": [{"type": "ADULT", "ticketCount": 26}]}
            """, MAXIMUM_TICKET_LIMIT),
                Arguments.of("infants count towards the 25", """
            {"accountId": 1, "ticketRequests": [{"type": "ADULT", "ticketCount": 13},
             {"type": "INFANT", "ticketCount": 13}]}
            """, MAXIMUM_TICKET_LIMIT),
                Arguments.of("counts that would overflow an int", """
            {"accountId": 1, "ticketRequests": [{"type": "ADULT", "ticketCount": 1},
             {"type": "CHILD", "ticketCount": 2147483647}]}
            """, MAXIMUM_TICKET_LIMIT),
                Arguments.of("child without adult", """
            {"accountId": 1, "ticketRequests": [{"type": "CHILD", "ticketCount": 1}]}
            """, NO_ADULT_MESSAGE),
                Arguments.of("infant without adult", """
            {"accountId": 1, "ticketRequests": [{"type": "INFANT", "ticketCount": 1}]}
            """, NO_ADULT_MESSAGE),
                Arguments.of("more infants than adults", """
            {"accountId": 1, "ticketRequests": [{"type": "ADULT", "ticketCount": 1},
             {"type": "INFANT", "ticketCount": 2}]}
            """, MORE_INFANT_THAN_ADULT_MESSAGE));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rejectedBookings")
    void givenInvalidBooking_whenPostBooking_thenBadRequestAndNothingPaidOrReserved(
            final String description, final String body, final String expectedDetail)
            throws Exception {
        postBooking(body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Invalid booking"))
                .andExpect(jsonPath("$.detail").value(expectedDetail));

        verifyNoInteractions(paymentService, seatReservationService);
    }

    private ResultActions postBooking(final String body) throws Exception {
        return mockMvc.perform(post(BOOKINGS_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
