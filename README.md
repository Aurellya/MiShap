# MiShap

<img src="./docs/MiShap.png" width="150" alt="MiShap Icon">

Find failure-related design bugs in your microservice workflows before you write the code, then generate the Spring Boot project.

You describe services, messages, failures, and rules in a small DSL. MiShap explores every reachable state within bounds (including lost messages, duplicated messages, and timeouts) and prints a **mishap report**: the shortest trace that breaks one of your rules. If the design holds, it generates one Spring Boot project per service from the same model.

## Requirements

- Java 21 (check with `java -version`)
- Maven 3.9+
- `curl` and a bash shell (for example Git Bash on Windows), only needed for the failure tests

## Build and test

From the repo root:

```bash
mvn clean install
```

This compiles all modules, runs all tests (parser, model, explorer, generator), and builds the runnable jar at `mishap-cli/target/mishap.jar`. To skip the tests for a quick rebuild:

```bash
mvn clean install -DskipTests
```

You only need to rebuild when you change code. If you only change a `.mishap` spec, just run the tool again.

## Run a check

Check one spec file:

```bash
java -jar mishap-cli/target/mishap.jar check mishap-demo/src/main/resources/specs/order-checkout-v1-buggy.mishap
```

On Windows you can use the wrapper script in the repo root instead:

```bash
./mishap.bat check mishap-demo/src/main/resources/specs/order-checkout-v1-buggy.mishap
```

`mishap.bat` just runs the jar. It uses the `java` on your `PATH`, unless Java 21 is not your default: in that case copy `mishap.env.example.bat` to `mishap.env.bat` and set `JAVA_HOME` there. The `mishap.env.bat` file is git-ignored, so your local path stays out of the repo.

## Exit codes

| Code | Meaning                                                                |
|------|------------------------------------------------------------------------|
| `0`  | No mishap found within bounds                                          |
| `1`  | Mishap found (`check`), or generation refused because of a mishap      |
| `2`  | Bad input (wrong command, unreadable file, syntax or validation error) |

This lets CI pipelines such as GitHub Actions fail a build when a design has a mishap. For example, a workflow step like this fails if the spec has a mishap:

```yaml
- name: Check workflow design
  run: java -jar mishap-cli/target/mishap.jar check path/to/spec.mishap
```

## Example

`order-checkout-v1-buggy.mishap` contains a payment service that is not idempotent. MiShap reports:

```
MISHAP FOUND: NoDoubleCharge (invariant) violated
1. Order receives Start in NEW -> goto AWAITING_PAYMENT, send Payment.Charge
2. DUPLICATED: message Payment.Charge will be delivered twice
3. Payment receives Charge in IDLE -> goto CHARGED, charges = 1, send Order.PaymentOk
4. Payment receives Charge in CHARGED -> charges = 2, send Order.PaymentOk
```

## Demo specs

| File                               | Description                            | Result             | States explored |
|------------------------------------|----------------------------------------|--------------------|-----------------|
| `order-checkout-v1-buggy.mishap`   | No idempotency, no refund              | 2 mishaps (exit 1) | 81              |
| `order-checkout-v2-partial.mishap` | Idempotent `Charge`, refund on timeout | 1 mishap (exit 1)  | 113             |
| `order-checkout-v3-fixed.mishap`   | Payment remembers the refund           | No mishap (exit 0) | 105             |

The three versions show a progression: obvious bugs, then a subtle race (the refund arrives before the charge), then a design that holds within the bounds.

## Generate Spring Boot services

```bash
./mishap.bat generate mishap-demo/src/main/resources/specs/order-checkout-v3-fixed.mishap generated-out/v3
```

`generate` runs the check first and **refuses to generate if a mishap is found**, with exit code 1. Use `--force` to generate anyway (useful for reproducing a bug):

```bash
./mishap.bat generate mishap-demo/src/main/resources/specs/order-checkout-v1-buggy.mishap generated-out/v1 --force
```

It creates one Spring Boot project per service, built from the same model the explorer checked. Each service has `POST /messages/{name}` and `GET /state`.

### Run the generated system

Build once (needs internet the first time, to download Spring dependencies):

```bash
cd generated-out/v3
mvn package -DskipTests
cd ../..
```

Then start the three services, **one terminal each** (Java 21), from the repo root. Start Order last:

```bash
cd generated-out/v3/payment-service   && mvn spring-boot:run
cd generated-out/v3/inventory-service && mvn spring-boot:run
cd generated-out/v3/order-service     && mvn spring-boot:run
```

Order sends `Start` about 1.5 seconds after startup. Then check the states:

```bash
curl localhost:8081/state   # Order:     {"state":"SHIPPED"}
curl localhost:8082/state   # Payment:   {"state":"CHARGED","charges":1}
curl localhost:8083/state   # Inventory: {"state":"RESERVED"}
```

### Reproduce the bug

Generate v1 (with `--force`), build it, and start the services from `generated-out/v1` instead. Then replay the duplicate from the mishap report:

```bash
curl -X POST localhost:8082/messages/Charge
curl localhost:8082/state # v1: charges = 2 (bug). v3: charges stays 1.
```

## Failure tests

With the three generated services running, `scripts/failure-tests.sh` reproduces two of the explorer's mishaps against the real generated code. It uses the generated `/admin` test hooks, which exist for testing only and should not ship to production:

| Endpoint                     | Effect                                         |
|------------------------------|------------------------------------------------|
| `POST /admin/reset`          | Returns the service to its initial state       |
| `POST /admin/timeout`        | Fires the service's timeout immediately        |
| `POST /admin/drop/{message}` | Drops the next incoming message with that name |

```bash
bash scripts/failure-tests.sh v3   # fixed design: protections hold
bash scripts/failure-tests.sh v1   # buggy design: the bugs reproduce
```

The argument selects the expected results, not the services. Running `v1` against `v3` services (or the other way around) fails by design, which is how you can tell the generated code matches its spec.

Start the services from the matching generated folder (`generated-out/v1` or `generated-out/v3`), and wait about 5 seconds before running the script. Make sure no old services still hold ports 8081-8083.

| Scenario                 | v1 (buggy)                                 | v3 (fixed)                            |
|--------------------------|--------------------------------------------|---------------------------------------|
| Duplicate `Charge`       | `charges = 2`                              | `charges = 1`                         |
| Late reply, then timeout | Order `CANCELLED`, Payment stays `CHARGED` | Order `CANCELLED`, Payment `REFUNDED` |

`v2` is checked by the explorer only (`mishap check`). Its race (refund overtakes charge) needs a controlled delivery order, which plain HTTP cannot force, so the failure tests do not cover it.

## Project structure

| Path               | Purpose                                               |
|--------------------|-------------------------------------------------------|
| `mishap-dsl`       | ANTLR grammar and parser                              |
| `mishap-model`     | Typed model, parse tree builder, validator            |
| `mishap-explorer`  | Bounded state-space exploration and mishap reports    |
| `mishap-cli`       | Command line tool (`mishap check`, `mishap generate`) |
| `mishap-generator` | Spring Boot code generation                           |
| `mishap-demo`      | Example specs                                         |
| `scripts/`         | Failure tests against the generated services          |
| `docs/`            | Design note and sequence diagrams                     |

## Limits

"No mishap found" means none was found within the configured bounds and the modeled assumptions. It is not a proof of correctness. Only modeled failures are injected (message loss, duplication, timeouts). Real HTTP cannot force an exact delivery order, so the generated system does not reproduce every explored interleaving by itself. Generated services keep their state in memory only.

## Todos

- More concise docs (will tidy it up soon)
- Diagrams (state machine and sequence diagrams) generated from specs and traces
- Derive failure tests automatically from the explorer's counterexample traces
- Cover the `v2` race with a controlled delivery order (hold queue)
- Export the model to mCRL2
- Web UI for editing a spec and viewing the report and diagrams

## More

- Design note, semantics, and assumptions: [docs/design-note.md](docs/design-note.md)
- Sequence diagrams: [docs/sequence.md](docs/sequence.md)
- Why I built this: [docs/why-mishap.md](docs/why-mishap.md)