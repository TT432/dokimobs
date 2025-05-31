package io.github.tt432.dokimobs.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * @author TT432
 */
public class DokimobsConfig {
    public static final DokimobsConfig CONFIG;
    public static final ModConfigSpec CONFIG_SPEC;
    public final ModConfigSpec.DoubleValue spawnChance;

    private DokimobsConfig(ModConfigSpec.Builder builder) {
        spawnChance = builder.defineInRange("spawn_chance", 0.2, 0.0, 1.0);
    }

    static {
        Pair<DokimobsConfig, ModConfigSpec> pair =
                new ModConfigSpec.Builder().configure(DokimobsConfig::new);

        CONFIG = pair.getLeft();
        CONFIG_SPEC = pair.getRight();
    }
}
