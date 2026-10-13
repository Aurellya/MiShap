# MiShap Design Note

<img src="./MiShap.png" width="150" alt="MiShap Icon">

## Goal

MiShap checks a microservice workflow for failure-related design bugs before any code is written. The developer describes services, messages, failures, and rules in a DSL. MiShap explores all reachable states within bounds, reports a **mishap report** (a counterexample trace) when a rule is violated, and generates a Spring Boot project from the same model.

## Scenario: Order checkout with payment timeout

### Services

| Service | States | Variables |
|---|---|---|
| **Order** | NEW, AWAITING_PAYMENT, PAID, SHIPPED, CANCELLED | none |
| **Payment** | IDLE, CHARGED, REFUNDED, VOIDED (v3 only) | `charges` (int, starts at 0) |
| **Inventory** | FREE, RESERVED | none |

### Messages

| Message | From | To | Meaning |
|---|---|---|---|
| Start | init | Order | Begins checkout |
| Charge | Order | Payment | Request payment |
| PaymentOk | Payment | Order | Payment confirmed |
| Refund | Order | Payment | Compensate a charge |
| Reserve | Order | Inventory | Reserve stock |
| Reserved | Inventory | Order | Stock reserved |

### Happy path

1. `Start`: Order goes to AWAITING_PAYMENT and sends `Payment.Charge`.
2. Payment goes to CHARGED (`charges = 1`) and replies `PaymentOk`.
3. Order goes to PAID and sends `Inventory.Reserve`.
4. Inventory goes to RESERVED and replies `Reserved`; Order goes to SHIPPED.

## Execution semantics

These rules must be identical in the explorer and the code generator.

- **Messages in flight:** a multiset of (target service, message name). Delivery order is arbitrary, so messages can be reordered.
- **Step:** the explorer picks one in-flight message (or one enabled timeout) and applies the matching transition in the target's current state.
- **Expression timing:** all expressions in a transition read the state *before* the transition. Assignments do not see each other's effects.
- **Dropped messages:** a message with no matching transition in the target's current state is silently dropped.
- **Timeout:** an event, not a failure. It may fire at any time while its source state is active.
- **Lose:** only messages listed in `lossy:` can be lost, up to `maxLost`. Messages not listed (such as `Refund`) are assumed reliably delivered, e.g. through a durable queue.
- **Duplicate:** any in-flight message can be duplicated, up to `maxDuplicated`.
- **Bounds:** exploration stops at `maxDepth` steps. A transition that would exceed `maxInFlight` messages is not explored, and the result is marked as truncated.
- **Failure steps** (LOST, DUPLICATED) count toward `maxDepth`.
- **Search order:** breadth-first, so every reported trace is a shortest one.
- **Reporting:** one violation per property, the shortest.
- **Final state:** no messages in flight and no enabled timeout.
- **Result meaning:** "no mishap found" means none was found within the bounds and the stated assumptions. It is not a proof of correctness.
- **Generated code:** each service is a Spring Boot app with `POST /messages/{name}`. Each transition becomes an `if (message, state)` block, and a message with no match is dropped. Transitions are synchronized per service, so each is atomic, like one explorer step. Variables read in a transition use a snapshot taken before it (`old_x`), matching the "state before the transition" rule.
- **Timeout in generated code:** a scheduled check fires the timeout once per state entry, after `mishap.timeout.ms` (default 3000).
- **Sends:** asynchronous with up to 5 retries, so services calling each other cannot deadlock.

## DSL reference

| Construct | Example | Meaning |
|---|---|---|
| `system` | `system OrderCheckout { ... }` | Top-level container |
| `init` | `init: send Order.Start` | Messages sent at startup |
| `service` | `service Payment { ... }` | A service with states, variables and transitions |
| `state` / `initial` | `state: IDLE, CHARGED` / `initial: IDLE` | Allowed states and the starting state |
| `var` | `var charges = 0` | An integer variable local to the service |
| `on ... in ... ->` | `on Charge in IDLE -> goto CHARGED, send Order.PaymentOk` | When a message arrives in a state, run the actions (`goto`, `send`, assignment) |
| `on timeout in ...` | `on timeout in AWAITING_PAYMENT -> goto CANCELLED` | A transition that may fire while the state is active |
| `failures` | `failures: lose, duplicate` | Which failures the explorer may inject |
| `lossy` | `lossy: Payment.Charge, Order.PaymentOk` | The messages that may be lost |
| `bounds` | `bounds { maxDepth: 15 ... }` | Limits for the exploration |
| `invariant` | `invariant NoDoubleCharge: Payment.charges <= 1` | Must hold in every reachable state |
| `final` | `final Name: not (...)` | Must hold in every final state |

## Failure modes injected

| Failure | Description |
|---|---|
| Message lost | A lossy message disappears in transit |
| Message duplicated | A message is delivered twice |
| Timeout fires | A service gives up waiting while a reply may still be in flight |

## Rules (properties)

| Name | Kind | Meaning |
|---|---|---|
| NoShipWithoutPayment | invariant | Never SHIPPED while `charges == 0` (sanity check, should always pass) |
| NoDoubleCharge | invariant | `charges` never exceeds 1 |
| NoPaidCancelledWithoutRefund | final | In a final state, never CANCELLED with `charges >= 1` and Payment not REFUNDED |

The third rule is the bounded, checkable form of "if an order is cancelled after payment, a refund must eventually happen".

## Spec versions and results

| File | Description | Result | States explored |
|---|---|---|---|
| `order-checkout-v1-buggy.mishap` | No idempotency, no refund | 2 mishaps | 81 |
| `order-checkout-v2-partial.mishap` | Idempotent `Charge`, refund on timeout | 1 mishap | 113 |
| `order-checkout-v3-fixed.mishap` | Payment remembers the refund (VOIDED) | No mishap within bounds | 105 |

## Mishaps found

1. **Late reply + timeout (v1):** Order times out while `PaymentOk` is still in flight and cancels. `PaymentOk` then arrives in CANCELLED and is dropped. The customer paid for nothing. A *lost* reply is not required, a merely *late* one is enough.
2. **Duplicate Charge (v1):** `Charge` in CHARGED increments `charges` again. The customer is charged twice.
3. **Refund before charge (v2):** Timeout fires while `Charge` is in flight. `Refund` finds Payment IDLE and does nothing. The late `Charge` then succeeds, and its `PaymentOk` is lost, so nobody refunds. Here the loss *is* required, because v2 handles a late `PaymentOk` by sending a refund.

### Example mishap report (v1)

```
MISHAP FOUND: NoDoubleCharge (invariant) violated
  1. Order receives Start in NEW -> goto AWAITING_PAYMENT, send Payment.Charge
  2. DUPLICATED: message Payment.Charge will be delivered twice
  3. Payment receives Charge in IDLE -> goto CHARGED, charges = 1, send Order.PaymentOk
  4. Payment receives Charge in CHARGED -> charges = 2, send Order.PaymentOk
```

## Assumptions and limits

- Bounded exploration: only states reachable within the configured bounds are checked.
- Only modeled failures are injected. Crashes and network partitions are out of scope for the MVP.
- `Refund` is assumed reliable. Modeling a lost refund needs retries, which is future work.
- Reordering is unrestricted: any in-flight message may be delivered next.
- The generated code implements exactly the transitions in the model, but MiShap does not prove the generated code correct.
- Real HTTP cannot force an exact delivery order, so the generated system does not reproduce every explored interleaving by itself. Duplicates can be injected by hand (`curl`), and timeouts by waiting.
- Transition expressions in generated code may only reference the service's own variables and states.
- In generated code, `lossy` is not enforced. Message loss and duplication must be injected, they do not occur by themselves.
- The failure tests replay two scenarios by hand. They are not derived automatically from the explorer's traces.
- The `/admin/*` endpoints (`reset`, `timeout`, `drop/{name}`) exist for testing only and must not ship to production.
- Generated services keep their state in memory only, so a restart loses it.
- Generated sends retry on errors, so a message can occasionally be delivered more than once. This is why handlers should be idempotent, as in v3.


## Diagrams

See [sequence.md](sequence.md).

## Out of scope for the MVP

Web UI, ontology integration, service crashes, risk scoring, distributed deployment.