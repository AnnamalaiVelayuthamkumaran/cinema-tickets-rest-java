package uk.gov.dwp.engineering.recruitment;

import org.springframework.stereotype.Service;
import uk.gov.dwp.engineering.recruitment.domain.BookingConfirmation;
import uk.gov.dwp.engineering.recruitment.domain.TicketRequest;
import uk.gov.dwp.engineering.recruitment.exception.InvalidBookingException;
import uk.gov.dwp.engineering.recruitment.thirdparty.PaymentService;
import uk.gov.dwp.engineering.recruitment.thirdparty.SeatReservationService;

import java.math.BigDecimal;

import static uk.gov.dwp.engineering.recruitment.CinemaConstants.*;

@Service
public class CinemaTicketsServiceImpl implements CinemaTicketsService {

    private final PaymentService paymentService;

    private final SeatReservationService seatReservationService;

    private static final BigDecimal ADULT_PRICE = new BigDecimal("25.99");
    private static final BigDecimal CHILD_PRICE = new BigDecimal("17.50");
    private static final BigDecimal INFANT_PRICE = new BigDecimal("0.00");

    public CinemaTicketsServiceImpl(PaymentService paymentService,
                                    SeatReservationService seatReservationService) {
        this.paymentService = paymentService;
        this.seatReservationService = seatReservationService;
    }

    @Override
    public BookingConfirmation purchaseTickets(final Long accountId,
                                               final TicketRequest... ticketRequests)
            throws InvalidBookingException {
        validateAccount(accountId);
        TicketCounts ticketCounts = validateAndCountTicketRequest(ticketRequests);
        validateBusinessRules(ticketCounts);
        BigDecimal ticketCost = calculateTotalCost(ticketCounts);
        paymentService.debitAccount(accountId, ticketCost);
        seatReservationService.reserveSeats(accountId, ticketCounts.seats());
        return new BookingConfirmation(accountId, ticketCounts.seats(), ticketCost);
    }

    private void validateAccount(final Long accountId) {
        if (accountId == null || accountId <= 0) {
            throw new InvalidBookingException(INVALID_ACCOUNT);
        }
    }

    private TicketCounts validateAndCountTicketRequest(final TicketRequest... ticketRequests) {
        if (ticketRequests == null || ticketRequests.length == 0) {
            throw new InvalidBookingException(EMPTY_REQUEST);
        }
        long adult = 0;
        long child = 0;
        long infant = 0;
        for (final TicketRequest ticket : ticketRequests) {
            if (ticket == null || ticket.type() == null) {
                throw new InvalidBookingException(INVALID_TICKET_TYPE);
            }
            if (ticket.ticketCount() < 0) {
                throw new InvalidBookingException(TICKET_COUNT_NEGATIVE);
            }
            switch (ticket.type()) {
                case ADULT -> adult += ticket.ticketCount();
                case CHILD -> child += ticket.ticketCount();
                case INFANT -> infant += ticket.ticketCount();
            }
        }
        return new TicketCounts(adult, child, infant);
    }

    private void validateBusinessRules(TicketCounts ticketCounts) {
        if (ticketCounts.total() < 1) {
            throw new InvalidBookingException(MINIMUM_TICKET);
        }
        if (ticketCounts.adults() < 1) {
            throw new InvalidBookingException(NO_ADULT_MESSAGE);
        }
        if (ticketCounts.infants > ticketCounts.adults()) {
            throw new InvalidBookingException(MORE_INFANT_THAN_ADULT_MESSAGE);
        }
        if (ticketCounts.total() > MAX_TICKETS_PER_PURCHASE) {
            throw new InvalidBookingException(MAXIMUM_TICKET_LIMIT);
        }
    }

    private BigDecimal calculateTotalCost(TicketCounts ticketCounts) {
        BigDecimal price = new BigDecimal("0.00");
        price = price.add(BigDecimal.valueOf(ticketCounts.adults).multiply(ADULT_PRICE));
        price = price.add(BigDecimal.valueOf(ticketCounts.children).multiply(CHILD_PRICE));
        price = price.add(BigDecimal.valueOf(ticketCounts.infants).multiply(INFANT_PRICE));
        return price;
    }

    private record TicketCounts(long adults, long children, long infants) {

        long total() {
            return adults + children + infants;
        }

        /**
         * Infants sit on an adult's lap, so they need no seat.
         */
        long seats() {
            return adults + children;
        }
    }


}
