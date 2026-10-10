# Cinema Tickets Code Test

## Objective

To assess your ability to build a simple API.

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
