package uk.gov.dwp.engineering.recruitment;

import java.math.BigDecimal;

public final class CinemaConstants {
    public static final String NO_ADULT_MESSAGE = "CHILD and INFANT tickets cannot be purchased without an ADULT ticket";
    public static final String MORE_INFANT_THAN_ADULT_MESSAGE = "Each INFANT must sit on an ADULT's lap, so infants cannot outnumber adults";
    public static final String INVALID_ACCOUNT = "Account ID must be greater than zero";
    public static final String MAXIMUM_TICKET_LIMIT = "A maximum of 25 tickets can be purchased at a time";
    public static final String MINIMUM_TICKET = "At least one ticket must be purchased";
    public static final String TICKET_COUNT_NEGATIVE = "Ticket count cannot be negative";
    public static final String INVALID_TICKET_TYPE = "Each ticket request must have a ticket type";
    public static final String EMPTY_REQUEST = "At least one ticket request is required";
    public static final int MAX_TICKETS_PER_PURCHASE = 25;
}
