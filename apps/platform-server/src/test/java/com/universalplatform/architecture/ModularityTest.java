package com.universalplatform.architecture;

import com.universalplatform.PlatformServerApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTest {
    @Test
    void moduleBoundariesAreValid() {
        ApplicationModules.of(PlatformServerApplication.class).verify();
    }
}
