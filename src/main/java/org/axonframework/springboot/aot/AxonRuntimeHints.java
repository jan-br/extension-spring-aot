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

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.lang.Nullable;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarFile;

/**
 * Registers runtime hints for Axon Framework classes that need reflective access in native images.
 * <p>
 * This includes:
 * <ul>
 *   <li>Tracking token classes used by token stores (serialized/deserialized at runtime)</li>
 *   <li>JPA entity classes for event store and token store persistence</li>
 *   <li>Axon internal *Definition classes instantiated via reflection</li>
 *   <li>Resource files loaded at runtime</li>
 * </ul>
 *
 * @author Gerard Klijs
 * @since 4.8.0
 */
public class AxonRuntimeHints implements RuntimeHintsRegistrar {

    private static final List<String> TOKEN_CLASSES = List.of(
            "org.axonframework.messaging.eventhandling.processing.streaming.token.GapAwareTrackingToken",
            "org.axonframework.messaging.eventhandling.processing.streaming.token.GlobalSequenceTrackingToken",
            "org.axonframework.messaging.eventhandling.processing.streaming.token.MergedTrackingToken",
            "org.axonframework.messaging.eventhandling.processing.streaming.token.ReplayToken",
            "org.axonframework.messaging.eventhandling.processing.streaming.token.WrappedToken",
            "org.axonframework.messaging.eventhandling.processing.streaming.token.store.ConfigToken"
    );

    private static final List<String> JPA_ENTITY_CLASSES = List.of(
            "org.axonframework.messaging.eventhandling.processing.streaming.token.store.jpa.TokenEntry",
            "org.axonframework.messaging.eventhandling.processing.streaming.token.store.jpa.TokenEntry$PK",
            "org.axonframework.eventsourcing.eventstore.jpa.AggregateEventEntry"
    );

    private static final List<String> AXON_DEFINITION_PACKAGES = List.of(
            "org/axonframework/eventsourcing/annotation",
            "org/axonframework/modelling/entity/annotation",
            "org/axonframework/modelling/entity/child",
            "org/axonframework/modelling/annotation",
            "org/axonframework/messaging/core/annotation",
            "org/axonframework/messaging/commandhandling/annotation",
            "org/axonframework/messaging/eventhandling/annotation",
            "org/axonframework/messaging/queryhandling/annotation",
            "org/axonframework/messaging/core/interception/annotation",
            "org/axonframework/messaging/core/timeout",
            "org/axonframework/messaging/tracing",
            "org/axonframework/messaging/eventhandling/configuration",
            "org/axonframework/extension/spring"
    );

    @Override
    public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
        MemberCategory[] allMembers = {
                MemberCategory.DECLARED_FIELDS,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_DECLARED_METHODS
        };

        for (String className : TOKEN_CLASSES) {
            registerTypeSafely(hints, className, allMembers);
        }
        for (String className : JPA_ENTITY_CLASSES) {
            registerTypeSafely(hints, className, allMembers);
        }

        // Register Axon *Definition classes that are instantiated via ConstructorUtils
        MemberCategory[] definitionCategories = {
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_DECLARED_METHODS
        };
        for (String className : discoverAxonDefinitionClasses(classLoader)) {
            registerTypeSafely(hints, className, definitionCategories);
        }

        hints.resources().registerPattern("SQLErrorCode.properties");
    }

    private void registerTypeSafely(RuntimeHints hints, String className, MemberCategory[] categories) {
        try {
            hints.reflection().registerType(TypeReference.of(className), categories);
        } catch (Exception e) {
            // Class not on classpath - skip
        }
    }

    /**
     * Scans Axon Framework JAR files for *Definition.class files in annotation/configuration packages.
     * These classes are instantiated reflectively by Axon's ConstructorUtils.
     */
    private List<String> discoverAxonDefinitionClasses(@Nullable ClassLoader classLoader) {
        List<String> classes = new ArrayList<>();
        ClassLoader cl = classLoader != null ? classLoader
                : Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = getClass().getClassLoader();
        }

        for (String pkg : AXON_DEFINITION_PACKAGES) {
            try {
                Enumeration<URL> resources = cl.getResources(pkg);
                while (resources.hasMoreElements()) {
                    URL url = resources.nextElement();
                    String jarPath = url.toString();
                    if (jarPath.startsWith("jar:file:")) {
                        String jarFile = jarPath.substring("jar:file:".length(),
                                jarPath.indexOf('!'));
                        try (JarFile jar = new JarFile(jarFile)) {
                            jar.entries().asIterator().forEachRemaining(entry -> {
                                if (entry.getName().startsWith(pkg + "/")
                                        && entry.getName().endsWith("Definition.class")
                                        && !entry.getName().contains("$")) {
                                    classes.add(entry.getName()
                                            .replace(".class", "")
                                            .replace('/', '.'));
                                }
                            });
                        }
                    }
                }
            } catch (IOException e) {
                // Skip this package
            }
        }
        return classes;
    }
}
