package io.github.tt432.dokimobs.config;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * @author TT432
 */
public class DokimobsConfig {
    public static final DokimobsConfig CONFIG;
    public static final ForgeConfigSpec CONFIG_SPEC;
    public final ForgeConfigSpec.DoubleValue spawnChance;

    private DokimobsConfig(ForgeConfigSpec.Builder builder) {
        spawnChance = builder.defineInRange("spawn_chance", 0.2, 0.0, 1.0);
    }

    static {
        Pair<DokimobsConfig, ForgeConfigSpec> pair =
                new ForgeConfigSpec.Builder().configure(DokimobsConfig::new);

        CONFIG = pair.getLeft();
        CONFIG_SPEC = pair.getRight();
    }
}
