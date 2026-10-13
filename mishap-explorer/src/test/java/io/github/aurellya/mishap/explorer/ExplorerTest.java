package io.github.aurellya.mishap.explorer;

import io.github.aurellya.mishap.model.Spec;
import io.github.aurellya.mishap.model.SpecParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ExplorerTest {

    static final String SPECS = "../mishap-demo/src/main/resources/specs/";

    static Result run(String file) throws IOException {
        Spec spec = SpecParser.parseFile(Path.of(SPECS + file));
        Result r = new Explorer(spec).run();
        System.out.println("=== " + file + " ===\n" + r.report());
        return r;
    }

    @Test
    void v1FindsDuplicateChargeAndLostReply() throws IOException {
        Result r = run("order-checkout-v1-buggy.mishap");
        assertTrue(r.violated("NoDoubleCharge"));
        assertTrue(r.violated("NoPaidCancelledWithoutRefund"));
        assertFalse(r.violated("NoShipWithoutPayment")); // sanity property must hold
        Violation dup = r.violations().stream()
                .filter(v -> v.property().name().equals("NoDoubleCharge")).findFirst().orElseThrow();
        assertTrue(dup.trace().stream().anyMatch(s -> s.startsWith("DUPLICATED")));
    }

    @Test
    void v2FindsOnlyTheRefundBeforeChargeRace() throws IOException {
        Result r = run("order-checkout-v2-partial.mishap");
        assertEquals(1, r.violations().size());
        assertTrue(r.violated("NoPaidCancelledWithoutRefund"));
    }

    @Test
    void v3HasNoViolationsWithinBounds() throws IOException {
        Result r = run("order-checkout-v3-fixed.mishap");
        assertEquals(0, r.violations().size());
    }
}