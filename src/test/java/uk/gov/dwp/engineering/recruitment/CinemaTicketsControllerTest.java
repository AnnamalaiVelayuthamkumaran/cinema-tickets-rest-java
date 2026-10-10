package uk.gov.dwp.engineering.recruitment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.dwp.engineering.recruitment.domain.Booking;
import uk.gov.dwp.engineering.recruitment.domain.BookingConfirmation;
import uk.gov.dwp.engineering.recruitment.domain.TicketRequest;
import uk.gov.dwp.engineering.recruitment.domain.TicketType;
import uk.gov.dwp.engineering.recruitment.exception.InvalidBookingException;

import java.math.BigDecimal;

import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.dwp.engineering.recruitment.CinemaConstants.MORE_INFANT_THAN_ADULT_MESSAGE;
import static uk.gov.dwp.engineering.recruitment.CinemaConstants.NO_ADULT_MESSAGE;

import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;


@WebMvcTest(CinemaTicketsController.class)
class CinemaTicketsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CinemaTicketsService cinemaTicketsService;

    private static final String BOOKINGS_URL = "/cinema/bookings";


    @Test
    void makeBooking_ValidRequest() throws Exception {
        when(cinemaTicketsService.purchaseTickets(122L, new TicketRequest(TicketType.ADULT, 2)))
                .thenReturn(new BookingConfirmation(122L, 2L, new BigDecimal("51.98")));

        mockMvc.perform(post(BOOKINGS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountId": 122, "ticketRequests": [{"type": "ADULT", "ticketCount": 2}]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId").value(122))
                .andExpect(jsonPath("$.seatCount").value(2))
                .andExpect(jsonPath("$.totalCost").value(51.98));

    }

    @Test
    void rejectsBooking_whenAdultMissing() throws Exception {
        when(cinemaTicketsService.purchaseTickets(213L, new TicketRequest(TicketType.CHILD, 1)))
                .thenThrow(new InvalidBookingException(NO_ADULT_MESSAGE));

        mockMvc.perform(post(BOOKINGS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountId": 213, "ticketRequests": [{"type": "CHILD", "ticketCount": 1}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Invalid booking"))
                .andExpect(jsonPath("$.detail").value(NO_ADULT_MESSAGE));
    }

    @Test
    void rejectsBooking_whenInfantMoreThanAdult() throws Exception {
        when(cinemaTicketsService.purchaseTickets(122L, new TicketRequest(TicketType.ADULT, 1),
                new TicketRequest(TicketType.INFANT, 2)))
                .thenThrow(new InvalidBookingException(MORE_INFANT_THAN_ADULT_MESSAGE));

        mockMvc.perform(post(BOOKINGS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountId": 122, "ticketRequests": [{"type": "ADULT", "ticketCount": 1},
                                {"type": "INFANT", "ticketCount": 2}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Invalid booking"))
                .andExpect(jsonPath("$.detail").value(MORE_INFANT_THAN_ADULT_MESSAGE));
    }

}
