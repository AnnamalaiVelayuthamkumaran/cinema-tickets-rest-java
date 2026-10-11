# Cinema Tickets

A working implementation of `CinemaTicketsServiceImpl`, with error handling and tests for the
`CinemaTicketsController` booking endpoint.

## Running

Requires Java 21 and Maven.

```shell
mvn verify
```

This runs the unit tests (`src/test/java`) and the end-to-end tests
(`src/integration-test/java`, classes ending in `IT`). `mvn test` runs the unit tests only.

## Approach

`CinemaTicketsServiceImpl.purchaseTickets` does four things, in this order:

1. **Validates the account.** The account ID must be present and greater than zero.
2. **Counts the tickets by type.** Repeated ticket types are added together. Requests that are
   structurally invalid are rejected here: no requests, a missing request or ticket type, or a
   negative count. Counts are held as `long`, so very large counts cannot overflow and get past
   the 25-ticket limit.
3. **Applies the business rules** to the counted tickets (see below).
4. **Pays, then reserves.** The total cost is worked out from the counts, the account is debited
   through `PaymentService`, then seats are reserved through `SeatReservationService`. The
   service returns a `BookingConfirmation` with the account, seat count and total cost.

Nothing is sent to either external service unless every check passes.

Prices are held as `BigDecimal`, because £25.99 and £17.50 cannot be stored exactly as a
`double`. Error messages live in `CinemaConstants`, so the service and the tests use the same
text.

Any `InvalidBookingException` is turned into a `400 Bad Request`.

## Business rules

Prices: ADULT £25.99, CHILD £17.50, INFANT £0. Infants are not given a seat.

Rules are checked in this order. When a request breaks more than one rule, the first one in
this table is reported.

| Rule                                               | Example of a rejected request |
| -------------------------------------------------- | ----------------------------- |
| The account ID must be greater than zero           | missing, `0`, `-1`            |
| At least one ticket request must be supplied       | no `ticketRequests`, or `[]`  |
| Every ticket request must have a ticket type       | `[null]`, or a missing `type` |
| Ticket counts cannot be negative                   | `CHILD x -1`                  |
| At least one ticket must be purchased              | only `ADULT x 0`              |
| CHILD and INFANT tickets need an ADULT ticket      | `CHILD x 1` on its own        |
| Each infant sits on an adult's lap (one per adult) | `ADULT x 1` with `INFANT x 2` |
| No more than 25 tickets per purchase               | `ADULT x 26`                  |

## Assumptions and decisions

- **Infants count towards the 25-ticket limit.** They are issued a ticket, even though it is
  free, and the rule limits tickets rather than seats. So `ADULT x 13` with `INFANT x 13` is 26
  tickets and is rejected, even though it needs only 13 seats.
- **One infant per adult.** Infants sit on an adult's lap and an adult has one lap, so a
  purchase with more infants than adults is rejected.
- **The limit applies to the whole purchase.** Repeated ticket types are added together first,
  so `ADULT x 20` plus `ADULT x 10` is 30 tickets and is rejected.
- **A ticket request with a count of zero is valid but ignored.** The template's own
  `TicketRequestTest` treats `INFANT x 0` as valid, and the API contract allows a minimum of 0.
  A purchase with no tickets at all is rejected.
- **The brief gives no order for the rules**, so I chose the order in the table above and
  documented it, so the error a client receives is predictable.
- **Pay first, then reserve, with no rollback.** The brief states that both providers always
  succeed once called, so their responses are not inspected and no compensation is attempted.
- **The two external calls are made one after the other, not in parallel.** One takes money and
  the other allocates seats, so the order matters, and with providers that always succeed there
  is no latency problem to solve.
- **Ticket types are case-sensitive.** `ADULT` is accepted, `adult` is not, matching the enum
  in the API contract.

## Differences between the template and `cinema-tickets.yaml`

The OpenAPI contract in `src/main/resources/cinema-tickets.yaml` and the template code disagree
in places. This is how I handled each one.

1. **Error status.** The contract says a rejected booking returns `400`, but
   `RestExceptionHandler` returned `501 Not Implemented`. I changed it to `400` and added a
   title, so the response is a complete Problem Detail.
2. **Response fields.** The contract's `BookingConfirmation` has `accountId`, `seatCount` and
   `totalCost`, but the record only had `accountId`. The brief allows `BookingConfirmation` to
   be extended in a non-breaking way, so I added the two fields and kept a public one-argument
   constructor. The existing `BookingConfirmationTest` passes unchanged, which shows the change
   is non-breaking. That constructor leaves the new fields `null`; the service always uses the
   full constructor, so API responses always include all three fields.
3. **Request field names.** The contract names the request fields `tickets` and `count`, but
   the controller reads the request into the domain `Booking` and `TicketRequest` records,
   whose fields are `ticketRequests` and `ticketCount`. The `domain` package cannot be changed,
   and I assumed the provided code is correct, so I kept the controller as supplied and the API
   accepts the domain field names:

   ```json
   {
     "accountId": 1,
     "ticketRequests": [{ "type": "ADULT", "ticketCount": 2 }]
   }
   ```

   A client following the contract as written would receive `400` ("At least one ticket
   request is required"). In a real team I would raise this before release. The fix would be
   request classes that match the contract, mapped to the domain types in the controller.

## Constraints respected

- The `CinemaTicketsService` interface is unchanged.
- Nothing in the `domain` package has changed apart from the non-breaking extension of
  `BookingConfirmation`.

## Testing

| Test class                     | What it covers                                                   |
| ------------------------------ | ---------------------------------------------------------------- |
| `CinemaTicketsServiceImplTest` | Every accepted and rejected case, with mocked external services  |
| `CinemaTicketsControllerTest`  | The web layer: 201 with the confirmation, 400 with Problem Detail |
| `BookingConfirmationTest`      | The original and extended constructors                           |
| `CinemaTicketsIT`              | Every business rule end to end through the API                   |

Accepted cases check the exact amount paid, the exact number of seats reserved, and that payment
happens before reservation. Rejected cases check the exact error message and that neither
external service was called.

## What I would do next in a real system

- Align the request field names with the API contract, or update the contract.
- Check the responses from the payment and reservation services, and refund or release seats
  if one step fails after the other has succeeded.
- Add structured logging and metrics for rejected bookings.