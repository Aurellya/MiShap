package io.github.aurellya.mishap.explorer;

import io.github.aurellya.mishap.model.Spec;

import java.util.List;

public record Result(Spec spec, int statesExplored, List<Violation> violations, boolean truncated) {

    public boolean violated(String propertyName) {
        return violations.stream().anyMatch(v -> v.property().name().equals(propertyName));
    }

    public String report() {
        StringBuilder sb = new StringBuilder();
        for (Violation v : violations) {
            sb.append("MISHAP FOUND: ").append(v.property().name())
              .append(" (").append(v.property().kind().name().toLowerCase()).append(") violated\n");
            int i = 1;
            for (String step : v.trace()) {
                sb.append("  ").append(i++).append(". ").append(step).append('\n');
            }
            sb.append('\n');
        }
        if (violations.isEmpty()) {
            sb.append("No mishap found within bounds.\n");
        }
        sb.append("Explored ").append(statesExplored).append(" states (maxDepth ")
          .append(spec.bounds().maxDepth()).append(").\n");
        if (truncated) {
            sb.append("Note: bounds were hit, so some states were not explored.\n");
        }
        sb.append("This is a bounded check under the stated assumptions, not a proof of correctness.\n");
        return sb.toString();
    }
}