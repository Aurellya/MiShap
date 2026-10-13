package io.github.aurellya.mishap.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ModelTest {

    static final String SPECS = "../mishap-demo/src/main/resources/specs/";

    @ParameterizedTest
    @ValueSource(strings = {
        "order-checkout-v1-buggy.mishap",
        "order-checkout-v2-partial.mishap",
        "order-checkout-v3-fixed.mishap"
    })
    void demoSpecsAreValid(String file) throws IOException {
        Spec spec = SpecParser.parseFile(Path.of(SPECS + file));
        assertEquals(List.of(), Validator.validate(spec));
    }

    @Test
    void v1HasExpectedStructure() throws IOException {
        Spec spec = SpecParser.parseFile(Path.of(SPECS + "order-checkout-v1-buggy.mishap"));
        assertEquals("OrderCheckout", spec.name());
        assertEquals(3, spec.services().size());
        assertEquals(3, spec.properties().size());
        assertEquals(2, spec.lossy().size());
        assertEquals(15, spec.bounds().maxDepth());
        assertEquals(3, spec.service("Payment").orElseThrow().transitions().size());
        assertTrue(spec.service("Order").orElseThrow().transitions().stream().anyMatch(Transition::isTimeout));
    }

    @Test
    void unknownServiceIsReported() {
        Spec spec = SpecParser.parseString("""
            system X {
              init: send A.Go
              service A {
                state: S, T
                initial: S
                on Go in S -> goto T, send B.Hi
              }
            }""");
        assertTrue(Validator.validate(spec).stream().anyMatch(e -> e.contains("unknown service B")));
    }

    @Test
    void unknownStateIsReported() {
        Spec spec = SpecParser.parseString("""
            system X {
              init: send A.Go
              service A {
                state: S, T
                initial: S
                on Go in Q -> goto T
              }
            }""");
        assertTrue(Validator.validate(spec).stream().anyMatch(e -> e.contains("unknown state Q")));
    }
}