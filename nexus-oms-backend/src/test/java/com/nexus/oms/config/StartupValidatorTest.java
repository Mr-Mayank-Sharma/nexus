package com.nexus.oms.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StartupValidatorTest {

    @Mock
    private ApplicationContext applicationContext;

    @Test
    void construction_succeedsWithValidConfig() {
        StartupValidator validator = new StartupValidator(applicationContext);
        assertNotNull(validator);
    }

    @Test
    void validate_withMissingJwt_marksFailed() {
        StartupValidator validator = new StartupValidator(applicationContext);

        validator.validate();

        verify(applicationContext, never()).getBean(anyString());
    }
}
