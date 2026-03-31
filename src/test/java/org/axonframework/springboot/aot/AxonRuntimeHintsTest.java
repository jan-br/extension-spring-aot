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

package org.axonframework.springboot.aot;


import org.axonframework.messaging.eventhandling.processing.streaming.token.GapAwareTrackingToken;
import org.axonframework.messaging.eventhandling.processing.streaming.token.GlobalSequenceTrackingToken;
import org.axonframework.messaging.eventhandling.processing.streaming.token.MergedTrackingToken;
import org.axonframework.messaging.eventhandling.processing.streaming.token.ReplayToken;
import org.axonframework.messaging.eventhandling.processing.streaming.token.store.ConfigToken;
import org.junit.jupiter.api.*;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.util.ClassUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests Axon runtime hints
 *
 * @author Gerard Klijs
 */
class AxonRuntimeHintsTest {

    private RuntimeHints hints;

    @BeforeEach
    void setup() {
        this.hints = new RuntimeHints();
        SpringFactoriesLoader.forResourceLocation("META-INF/spring/aot.factories")
                             .load(RuntimeHintsRegistrar.class).forEach(registrar -> registrar
                                     .registerHints(this.hints, ClassUtils.getDefaultClassLoader()));
    }

    @Test
    void allTrackingTokenClassesHaveReflectionHints() {
        testForType(GlobalSequenceTrackingToken.class);
        testForType(GapAwareTrackingToken.class);
        testForType(MergedTrackingToken.class);
        testForType(ReplayToken.class);
        testForType(ConfigToken.class);
    }

    @Test
    void jpaEntityClassesHaveReflectionHints() {
        testForType(TypeReference.of(
                "org.axonframework.messaging.eventhandling.processing.streaming.token.store.jpa.TokenEntry"));
        testForType(TypeReference.of(
                "org.axonframework.eventsourcing.eventstore.jpa.AggregateEventEntry"));
    }

    @Test
    void axonDefinitionClassesHaveReflectionHints() {
        // Spot-check that at least some *Definition classes were discovered from the Axon JARs
        assertTrue(hints.reflection().typeHints().count() > 10,
                   "Expected at least 10 type hints from tracking tokens + JPA entities + Definition classes");
    }

    @Test
    void resourcePatternsArePresent() {
        assertTrue(RuntimeHintsPredicates.resource().forResource("SQLErrorCode.properties").test(this.hints));
    }

    private void testForType(Class<?> clazz) {
        assertTrue(RuntimeHintsPredicates.reflection().onType(clazz)
                                         .test(this.hints), "No reflection hints for " + clazz.getSimpleName());
    }

    private void testForType(TypeReference typeRef) {
        assertTrue(hints.reflection().getTypeHint(typeRef) != null,
                   "No reflection hints for " + typeRef.getName());
    }
}
