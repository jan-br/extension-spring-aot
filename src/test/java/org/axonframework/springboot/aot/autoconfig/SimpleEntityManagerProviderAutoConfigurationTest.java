/*
 * Copyright (c) 2010-2025. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.springboot.aot.autoconfig;

import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.common.jpa.SimpleEntityManagerProvider;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the {@link SimpleEntityManagerProviderAutoConfiguration} providing a
 * {@link SimpleEntityManagerProvider} that replaces the default
 * {@code ContainerManagedEntityManagerProvider} for native image compatibility.
 *
 * @author Gerard Klijs
 */
@SpringBootTest(classes = SimpleEntityManagerProviderAutoConfigurationTest.TestContext.class)
class SimpleEntityManagerProviderAutoConfigurationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void simpleEntityManagerProviderIsConfigured() {
        EntityManagerProvider entityManagerProvider = context.getBean(EntityManagerProvider.class);
        assertNotNull(entityManagerProvider);
        assertTrue(entityManagerProvider instanceof SimpleEntityManagerProvider,
                   "Expected SimpleEntityManagerProvider but got " +
                           entityManagerProvider.getClass().getName());
    }

    @EnableAutoConfiguration
    static class TestContext {

    }
}
