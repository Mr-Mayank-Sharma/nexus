package com.nexus.oms.entitlement;

import java.util.EnumSet;
import java.util.Set;

/** What a plan includes before add-ons. */
public enum Edition {
    OMS(EnumSet.of(ProductModule.PLATFORM, ProductModule.OMS)),
    WMS(EnumSet.of(ProductModule.PLATFORM, ProductModule.WMS)),
    SUITE(EnumSet.of(ProductModule.PLATFORM, ProductModule.OMS, ProductModule.WMS));

    private final Set<ProductModule> modules;

    Edition(Set<ProductModule> modules) {
        this.modules = modules;
    }

    public Set<ProductModule> modules() {
        return EnumSet.copyOf(modules);
    }
}
