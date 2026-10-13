package io.github.aurellya.mishap.generator;

import io.github.aurellya.mishap.model.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Generator {
    private Generator() {}

    public static void generate(Spec spec, Path out) throws IOException {
        Map<String, Integer> ports = new LinkedHashMap<>();
        int port = 8081;
        for (Service s : spec.services()) ports.put(s.name(), port++);

        Files.createDirectories(out);
        write(out.resolve("pom.xml"), aggregatorPom(spec));

        for (Service s : spec.services()) {
            String id = s.name().toLowerCase();
            Path dir = out.resolve(id + "-service");
            Path pkg = dir.resolve("src/main/java/generated/" + id);
            Path res = dir.resolve("src/main/resources");
            Files.createDirectories(pkg);
            Files.createDirectories(res);
            write(dir.resolve("pom.xml"), servicePom(id));
            write(pkg.resolve(s.name() + "Application.java"), application(s, id));
            write(pkg.resolve(s.name() + "Controller.java"), controller(spec, s, id));
            write(res.resolve("application.properties"), properties(spec, s, ports));
        }
    }

    // B.U.I.L.D - F.I.L.E.S

    static String aggregatorPom(Spec spec) {
        StringBuilder sb = new StringBuilder();
        sb.append("<project xmlns=\"http://maven.apache.org/POM/4.0.0\">\n")
                .append("  <modelVersion>4.0.0</modelVersion>\n")
                .append("  <groupId>generated</groupId>\n")
                .append("  <artifactId>").append(spec.name().toLowerCase()).append("</artifactId>\n")
                .append("  <version>1.0.0</version>\n")
                .append("  <packaging>pom</packaging>\n")
                .append("  <modules>\n");

        for (Service s : spec.services()) {
            sb.append("    <module>").append(s.name().toLowerCase()).append("-service</module>\n");
        }
        sb.append("  </modules>\n</project>\n");
        return sb.toString();
    }

    static String servicePom(String id) {
        return """
            <project xmlns="http://maven.apache.org/POM/4.0.0">
              <modelVersion>4.0.0</modelVersion>
              <parent>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-starter-parent</artifactId>
                <version>3.3.4</version>
              </parent>
              <groupId>generated</groupId>
              <artifactId>%s-service</artifactId>
              <version>1.0.0</version>
              <properties>
                <java.version>21</java.version>
              </properties>
              <dependencies>
                <dependency>
                  <groupId>org.springframework.boot</groupId>
                  <artifactId>spring-boot-starter-web</artifactId>
                </dependency>
              </dependencies>
              <build>
                <plugins>
                  <plugin>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-maven-plugin</artifactId>
                  </plugin>
                </plugins>
              </build>
            </project>
            """.formatted(id);
    }

    static String properties(Spec spec, Service svc, Map<String, Integer> ports) {
        StringBuilder sb = new StringBuilder();
        sb.append("server.port=").append(ports.get(svc.name())).append('\n');
        sb.append("spring.application.name=").append(svc.name().toLowerCase()).append("-service\n");
        sb.append("mishap.timeout.ms=3000\n");
        ports.forEach((name, p) -> sb.append("peer.").append(name).append(".url=http://localhost:").append(p).append('\n'));
        return sb.toString();
    }

    static String application(Service svc, String id) {
        return """
            package generated.%1$s;

            import org.springframework.boot.SpringApplication;
            import org.springframework.boot.autoconfigure.SpringBootApplication;
            import org.springframework.scheduling.annotation.EnableScheduling;

            @SpringBootApplication
            @EnableScheduling
            public class %2$sApplication {
                public static void main(String[] args) {
                    SpringApplication.run(%2$sApplication.class, args);
                }
            }
            """.formatted(id, svc.name());
    }

    // C.O.N.T.R.O.L.L.E.R

    static String controller(Spec spec, Service svc, String id) {
        String cls = svc.name() + "Controller";
        StringBuilder sb = new StringBuilder();
        sb.append("package generated.").append(id).append(";\n\n")
                .append("import org.springframework.boot.context.event.ApplicationReadyEvent;\n")
                .append("import org.springframework.context.event.EventListener;\n")
                .append("import org.springframework.core.env.Environment;\n")
                .append("import org.springframework.scheduling.annotation.Scheduled;\n")
                .append("import org.springframework.web.bind.annotation.*;\n")
                .append("import org.springframework.web.client.RestClient;\n\n")
                .append("import java.util.LinkedHashMap;\n")
                .append("import java.util.Map;\n")
                .append("import java.util.Objects;\n")
                .append("import java.util.concurrent.ExecutorService;\n")
                .append("import java.util.concurrent.Executors;\n\n")
                .append("/** Generated by MiShap from system ").append(spec.name()).append(". Do not edit. */\n")
                .append("@RestController\n")
                .append("public class ").append(cls).append(" {\n\n")
                .append("    private static final ExecutorService POOL = Executors.newCachedThreadPool();\n")
                .append("    private final RestClient http = RestClient.create();\n")
                .append("    private final Environment env;\n\n")
                .append("    private String state = ").append(q(svc.initial())).append(";\n")
                .append("    private long enteredAt = System.currentTimeMillis();\n")
                .append("    private final java.util.Set<String> dropNext = new java.util.HashSet<>();\n");

        svc.vars().forEach((k, v) -> sb.append("    private int ").append(k).append(" = ").append(v).append(";\n"));

        sb.append("\n    public ").append(cls).append("(Environment env) {\n")
                .append("        this.env = env;\n")
                .append("    }\n\n");

        // message endpoint
        sb.append("    @PostMapping(\"/messages/{name}\")\n")
                .append("    public synchronized String receive(@PathVariable String name) {\n")
                .append("        if (dropNext.remove(name)) return \"dropped-by-test\";\n")
                .append("        return handle(name);\n")
                .append("    }\n\n");
        sb.append("    @PostMapping(\"/admin/reset\")\n")
                .append("    public synchronized String reset() {\n")
                .append("        state = ").append(q(svc.initial())).append(";\n")
                .append("        enteredAt = System.currentTimeMillis();\n")
                .append("        dropNext.clear();\n");

        svc.vars().forEach((k, v) -> sb.append("        ").append(k).append(" = ").append(v).append(";\n"));

        sb.append("        return \"reset\";\n    }\n\n")
          .append("    @PostMapping(\"/admin/timeout\")\n")
          .append("    public synchronized String forceTimeout() {\n")
          .append("        enteredAt = 0;\n")
          .append("        return \"timeout armed\";\n    }\n\n")
          .append("    @PostMapping(\"/admin/drop/{name}\")\n")
          .append("    public synchronized String armDrop(@PathVariable String name) {\n")
          .append("        dropNext.add(name);\n")
          .append("        return \"will drop next \" + name;\n    }\n\n");

        // state endpoint
        sb.append("    @GetMapping(\"/state\")\n")
                .append("    public synchronized Map<String, Object> status() {\n")
                .append("        Map<String, Object> m = new LinkedHashMap<>();\n")
                .append("        m.put(\"state\", state);\n");

        svc.vars().keySet().forEach(k -> sb.append("        m.put(").append(q(k)).append(", ").append(k).append(");\n"));
        sb.append("        return m;\n    }\n\n");

        // handle
        sb.append("    private String handle(String msg) {\n");
        snapshot(sb, svc);
        for (Transition t : svc.transitions()) {
            if (t.isTimeout()) continue;
            sb.append("        if (msg.equals(").append(q(t.trigger())).append(") && state.equals(")
              .append(q(t.state())).append(")) {\n");
            actions(sb, svc, t, "            ");
            sb.append("            return \"ok\";\n        }\n");
        }
        sb.append("        return \"dropped\";\n    }\n\n");

        // timeouts
        boolean hasTimeout = svc.transitions().stream().anyMatch(Transition::isTimeout);
        if (hasTimeout) {
            sb.append("    @Scheduled(fixedDelay = 200)\n")
                    .append("    public synchronized void checkTimeouts() {\n")
                    .append("        long limit = Long.parseLong(env.getProperty(\"mishap.timeout.ms\", \"3000\"));\n")
                    .append("        if (System.currentTimeMillis() - enteredAt < limit) return;\n");
            snapshot(sb, svc);
            for (Transition t : svc.transitions()) {
                if (!t.isTimeout()) continue;
                sb.append("        if (state.equals(").append(q(t.state())).append(")) {\n")
                        .append("            enteredAt = Long.MAX_VALUE; // fire once per state entry\n");
                actions(sb, svc, t, "            ");
                sb.append("            return;\n        }\n");
            }
            sb.append("    }\n\n");
        }

        // init messages
        var inits = spec.init().stream().filter(r -> r.service().equals(svc.name())).toList();
        if (!inits.isEmpty()) {
            sb.append("    @EventListener(ApplicationReadyEvent.class)\n")
                    .append("    public void onReady() {\n")
                    .append("        POOL.submit(() -> {\n")
                    .append("            pause(1500);\n");
            inits.forEach(r -> sb.append("            send(").append(q(r.service())).append(", ").append(q(r.message())).append(");\n"));
            sb.append("        });\n    }\n\n");
        }

        // send + pause
        sb.append("""
                private void send(String service, String message) {
                    String base = env.getProperty("peer." + service + ".url");
                    POOL.submit(() -> {
                        for (int i = 0; i < 5; i++) {
                            try {
                                http.post().uri(base + "/messages/" + message).retrieve().toBodilessEntity();
                                return;
                            } catch (Exception e) {
                                pause(500);
                            }
                        }
                    });
                }

                private static void pause(long ms) {
                    try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                }
            }
            """);
        return sb.toString();
    }

    private static void snapshot(StringBuilder sb, Service svc) {
        sb.append("        final String old_state = state;\n");
        svc.vars().keySet().forEach(k -> sb.append("        final int old_").append(k).append(" = ").append(k).append(";\n"));
    }

    private static void actions(StringBuilder sb, Service svc, Transition t, String ind) {
        for (Action a : t.actions()) {
            switch (a) {
                case Action.Goto g -> sb.append(ind).append("state = ").append(q(g.state()))
                        .append("; enteredAt = System.currentTimeMillis();\n");
                case Action.Assign as -> sb.append(ind).append(as.variable()).append(" = ")
                        .append(java(as.value(), svc)).append(";\n");
                case Action.Send s -> sb.append(ind).append("send(").append(q(s.target().service()))
                        .append(", ").append(q(s.target().message())).append(");\n");
            }
        }
    }

    /** Expressions read the state before the transition (old_ snapshots), like the explorer. */
    static String java(Expr e, Service svc) {
        return switch (e) {
            case Expr.IntLit i -> String.valueOf(i.value());
            case Expr.Name n -> svc.vars().containsKey(n.name()) ? "old_" + n.name() : q(n.name());
            case Expr.Ref r -> {
                if (!r.service().equals(svc.name())) {
                    throw new UnsupportedOperationException(
                        "Generator: " + r + " refers to another service inside a transition");
                }
                yield r.member().equals("state") ? "old_state" : "old_" + r.member();
            }
            case Expr.Not n -> "!(" + java(n.operand(), svc) + ")";
            case Expr.Bin b -> {
                String l = java(b.left(), svc), r = java(b.right(), svc);
                yield switch (b.op()) {
                    case "and" -> "(" + l + " && " + r + ")";
                    case "or" -> "(" + l + " || " + r + ")";
                    case "==" -> "Objects.equals(" + l + ", " + r + ")";
                    case "!=" -> "!Objects.equals(" + l + ", " + r + ")";
                    default -> "(" + l + " " + b.op() + " " + r + ")";
                };
            }
        };
    }

    private static String q(String s) { return "\"" + s + "\""; }

    private static void write(Path p, String content) throws IOException {
        Files.writeString(p, content);
    }
}