package io.github.aurellya.mishap.generator;

import io.github.aurellya.mishap.model.Spec;
import io.github.aurellya.mishap.model.SpecParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class GeneratorTest {

    static final String SPECS = "../mishap-demo/src/main/resources/specs/";

    @Test
    void generatesOneProjectPerService(@TempDir Path out) throws IOException {
        Spec spec = SpecParser.parseFile(Path.of(SPECS + "order-checkout-v3-fixed.mishap"));
        Generator.generate(spec, out);

        assertTrue(Files.exists(out.resolve("pom.xml")));
        for (String s : new String[]{"order", "payment", "inventory"}) {
            assertTrue(Files.exists(out.resolve(s + "-service/pom.xml")), s);
            assertTrue(Files.exists(out.resolve(s + "-service/src/main/resources/application.properties")), s);
        }
        String payment = Files.readString(
            out.resolve("payment-service/src/main/java/generated/payment/PaymentController.java"));
        assertTrue(payment.contains("if (msg.equals(\"Charge\") && state.equals(\"IDLE\"))"));
        assertTrue(payment.contains("charges = (old_charges + 1);"));

        String order = Files.readString(
            out.resolve("order-service/src/main/java/generated/order/OrderController.java"));
        assertTrue(order.contains("checkTimeouts"));
        assertTrue(order.contains("send(\"Order\", \"Start\")"));   // init message

        assertTrue(payment.contains("/admin/reset"));
    }
}