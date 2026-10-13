package io.github.aurellya.mishap.model;

import io.github.aurellya.mishap.dsl.MishapLexer;
import io.github.aurellya.mishap.dsl.MishapParser;
import org.antlr.v4.runtime.*;

import java.io.IOException;
import java.nio.file.Path;

public final class SpecParser {
    private SpecParser() {}

    private static final BaseErrorListener THROWING = new BaseErrorListener() {
        @Override
        public void syntaxError(Recognizer<?, ?> r, Object sym, int line, int col,
                                String msg, RecognitionException e) {
            throw new MishapSyntaxException("line " + line + ":" + col + " " + msg);
        }
    };

    public static Spec parseFile(Path path) throws IOException {
        return parse(CharStreams.fromPath(path));
    }

    public static Spec parseString(String text) {
        return parse(CharStreams.fromString(text));
    }

    private static Spec parse(CharStream input) {
        MishapLexer lexer = new MishapLexer(input);
        lexer.removeErrorListeners();
        lexer.addErrorListener(THROWING);
        MishapParser parser = new MishapParser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        parser.addErrorListener(THROWING);
        return new ModelBuilder().build(parser.system());
    }
}