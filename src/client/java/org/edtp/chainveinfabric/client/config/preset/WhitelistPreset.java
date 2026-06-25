package org.edtp.chainveinfabric.client.config.preset;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class WhitelistPreset {
    public static final String DEFAULT_ID = "default";
    public static final String DEFAULT_NAME = "Default";

    public String id = UUID.randomUUID().toString();
    public String name = DEFAULT_NAME;
    public Set<String> entries = new HashSet<>();

    public static WhitelistPreset create(String id, String name, Set<String> entries) {
        WhitelistPreset preset = new WhitelistPreset();
        preset.id = id;
        preset.name = name;
        preset.entries = new HashSet<>(entries);
        return preset;
    }
}
