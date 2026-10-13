# Sequence diagrams

## Happy path

```mermaid
sequenceDiagram
    participant O as Order
    participant P as Payment
    participant I as Inventory

    Note over O: NEW
    O->>O: Start
    Note over O: AWAITING_PAYMENT
    O->>P: Charge
    Note over P: charges = 1, CHARGED
    P->>O: PaymentOk
    Note over O: PAID
    O->>I: Reserve
    Note over I: RESERVED
    I->>O: Reserved
    Note over O: SHIPPED
```

## v1 mishap: late reply, then timeout

```mermaid
sequenceDiagram
    participant O as Order
    participant P as Payment

    O->>P: Charge
    Note over P: charges = 1, CHARGED
    P-->>O: PaymentOk (still in flight)
    Note over O: Timeout fires, CANCELLED
    Note over O: PaymentOk arrives in CANCELLED, dropped
    Note over O,P: Customer paid, order cancelled, no refund
```

## v1 mishap: duplicate Charge

```mermaid
sequenceDiagram
    participant O as Order
    participant P as Payment

    O->>P: Charge
    O->>P: Charge (DUPLICATE)
    Note over P: already CHARGED, not idempotent
    Note over P: charges = 2
    Note over O,P: Customer charged twice
```

## v2 fix: timeout triggers refund

```mermaid
sequenceDiagram
    participant O as Order
    participant P as Payment

    O->>P: Charge
    Note over P: CHARGED, charges = 1
    P--xO: PaymentOk (LOST)
    Note over O: Timeout, CANCELLED
    O->>P: Refund
    Note over P: REFUNDED
    Note over O,P: No money lost
```

## v2 mishap: refund arrives before charge

```mermaid
sequenceDiagram
    participant O as Order
    participant P as Payment

    O->>P: Charge (delayed in flight)
    Note over O: Timeout, CANCELLED
    O->>P: Refund
    Note over P: IDLE, nothing to refund
    O-->>P: Charge arrives
    Note over P: CHARGED, charges = 1
    P--xO: PaymentOk (LOST)
    Note over O,P: Customer paid, order cancelled, no refund
```

## v3 fix: Payment remembers the refund

```mermaid
sequenceDiagram
    participant O as Order
    participant P as Payment

    O->>P: Charge (delayed in flight)
    Note over O: Timeout, CANCELLED
    O->>P: Refund
    Note over P: IDLE, so VOIDED
    Note over P: Late Charge arrives in VOIDED
    Note over P: No transition, dropped
    Note over O,P: Customer never charged
```