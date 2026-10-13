package io.github.aurellya.mishap.model;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public record Spec(String name, List<MsgRef> init, List<Service> services,
                   Set<String> failures, List<MsgRef> lossy,
                   Bounds bounds, List<Property> properties) {

    public Optional<Service> service(String name) {
        return services.stream().filter(s -> s.name().equals(name)).findFirst();
    }
}