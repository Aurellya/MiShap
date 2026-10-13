package io.github.aurellya.mishap.cli;

import io.github.aurellya.mishap.explorer.Explorer;
import io.github.aurellya.mishap.explorer.Result;
import io.github.aurellya.mishap.generator.Generator;
import io.github.aurellya.mishap.model.MishapSyntaxException;
import io.github.aurellya.mishap.model.Spec;
import io.github.aurellya.mishap.model.SpecParser;
import io.github.aurellya.mishap.model.Validator;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public final class Cli {

    private static final String USAGE = """
        MiShap: find failure-related design bugs in microservice workflows

        usage:
          mishap check <file.mishap>
          mishap generate <file.mishap> <outDir> [--force]

        generate refuses to run if a mishap is found, unless --force is given.
        exit codes: 0 = ok, 1 = mishap found, 2 = bad input""";

    public static void main(String[] args) {
        System.exit(run(args));
    }

    static int run(String[] args) {
        boolean force = Arrays.asList(args).contains("--force");
        String[] a = Arrays.stream(args).filter(x -> !x.equals("--force")).toArray(String[]::new);

        boolean check = a.length == 2 && a[0].equals("check");
        boolean generate = a.length == 3 && a[0].equals("generate");
        if (!check && !generate) {
            System.err.println(USAGE);
            return 2;
        }
        try {
            Spec spec = SpecParser.parseFile(Path.of(a[1]));
            List<String> errors = Validator.validate(spec);
            if (!errors.isEmpty()) {
                errors.forEach(e -> System.err.println("error: " + e));
                return 2;
            }
            Result result = new Explorer(spec).run();
            System.out.println(result.report());
            boolean mishap = !result.violations().isEmpty();

            if (check) return mishap ? 1 : 0;

            if (mishap && !force) {
                System.err.println("Not generating: fix the mishaps above, or use --force.");
                return 1;
            }
            Generator.generate(spec, Path.of(a[2]));
            System.out.println("Generated " + spec.services().size() + " services in " + a[2]);
            return mishap ? 1 : 0;
        } catch (MishapSyntaxException e) {
            System.err.println("syntax error: " + e.getMessage());
            return 2;
        } catch (UnsupportedOperationException e) {
            System.err.println("cannot generate: " + e.getMessage());
            return 2;
        } catch (IOException e) {
            System.err.println("io error: " + e.getMessage());
            return 2;
        }
    }
}