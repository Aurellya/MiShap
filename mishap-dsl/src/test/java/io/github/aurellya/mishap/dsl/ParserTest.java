package io.github.aurellya.mishap.dsl;

import org.antlr.v4.runtime.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ParserTest {

    static final String SPECS = "../mishap-demo/src/main/resources/specs/";

    static MishapParser.SystemContext parse(CharStream input) {
        MishapLexer lexer = new MishapLexer(input);
        lexer.removeErrorListeners();
        lexer.addErrorListener(THROWING);
        MishapParser parser = new MishapParser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        parser.addErrorListener(THROWING);
        return parser.system();
    }

    static final BaseErrorListener THROWING = new BaseErrorListener() {
        @Override
        public void syntaxError(Recognizer<?, ?> r, Object sym, int line, int col,
                                String msg, RecognitionException e) {
            throw new IllegalStateException("line " + line + ":" + col + " " + msg);
        }
    };

    @ParameterizedTest
    @ValueSource(strings = {
        "order-checkout-v1-buggy.mishap",
        "order-checkout-v2-partial.mishap",
        "order-checkout-v3-fixed.mishap"
    })
    void demoSpecsParse(String file) throws IOException {
        var tree = parse(CharStreams.fromPath(Path.of(SPECS + file)));
        assertEquals("OrderCheckout", tree.ID().getText());
    }

    @org.junit.jupiter.api.Test
    void invalidSpecIsRejected() {
        var bad = CharStreams.fromString("system X { service A { on Foo in -> goto B } }");
        assertThrows(IllegalStateException.class, () -> parse(bad));
    }
}