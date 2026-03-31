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

import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.annotation.AnnotatedHandlerInspector;
import org.axonframework.messaging.core.annotation.ClasspathParameterResolverFactory;
import org.axonframework.messaging.core.annotation.MessageHandlingMember;
import org.axonframework.messaging.core.annotation.MultiParameterResolverFactory;
import org.axonframework.messaging.core.annotation.ParameterResolver;
import org.axonframework.messaging.core.annotation.ParameterResolverFactory;
import org.axonframework.messaging.queryhandling.annotation.QueryHandlingMember;
import org.axonframework.modelling.entity.annotation.EntityMember;
import org.axonframework.extension.spring.config.MessageHandlerLookup;
import org.springframework.aot.generate.GenerationContext;
import org.springframework.aot.hint.BindingReflectionHintsRegistrar;
import org.springframework.aot.hint.ExecutableMode;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.ReflectionHints;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor;
import org.springframework.beans.factory.aot.BeanFactoryInitializationCode;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

import org.axonframework.messaging.core.unitofwork.ProcessingContext;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * BeanFactoryInitializationAotProcessor that registers message handler methods declared on beans for reflection. This
 * means that all methods annotated with {@code @MessageHandler} will be available for reflection.
 * <p/>
 * Additionally, the payload types for these methods are registered for reflective access, as well as the classes
 * containing the methods.
 *
 * @author Allard Buijze
 * @since 4.8.0
 */
public class MessageHandlerRuntimeHintsRegistrar implements BeanFactoryInitializationAotProcessor {

    @Override
    public BeanFactoryInitializationAotContribution processAheadOfTime(ConfigurableListableBeanFactory beanFactory) {
        Set<Class<?>> messageHandlingClasses =
                MessageHandlerLookup.messageHandlerBeans(messageType(), beanFactory, true)
                                    .stream()
                                    .map(beanFactory::getType)
                                    .collect(Collectors.toSet());

        Set<Class<?>> detectedClasses = new HashSet<>();
        messageHandlingClasses.forEach(c -> registerEntityMembers(c, detectedClasses));

        List<MessageHandlingMember<?>> messageHandlingMembers = detectedClasses
                .stream()
                .flatMap(beanType ->
                         {
                             AnnotatedHandlerInspector<?> inspector = AnnotatedHandlerInspector.inspectType(
                                     beanType,
                                     new MultiParameterResolverFactory(
                                             ClasspathParameterResolverFactory.forClass(beanType),
                                             new LenientParameterResolver()
                                     ));
                             return Stream.concat(inspector.getAllHandlers().values().stream(),
                                                  inspector.getAllInterceptors().values().stream());
                         })
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
        return new MessageHandlerContribution(detectedClasses, messageHandlingMembers);
    }

    private void registerEntityMembers(Class<?> entityType, Set<Class<?>> reflectiveClasses) {
        if (!reflectiveClasses.add(entityType)) {
            return;
        }

        for (Field field : getAllFields(entityType)) {
            EntityMember entityMember = field.getAnnotation(EntityMember.class);
            if (entityMember != null) {
                if (Map.class.isAssignableFrom(field.getType())) {
                    resolveMemberGenericType(field, 1)
                            .ifPresent(t -> registerEntityMembers(t, reflectiveClasses));
                } else if (Collection.class.isAssignableFrom(field.getType())) {
                    resolveMemberGenericType(field, 0)
                            .ifPresent(t -> registerEntityMembers(t, reflectiveClasses));
                } else {
                    registerEntityMembers(field.getType(), reflectiveClasses);
                }
            }
        }
    }

    private List<Field> getAllFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        Class<?> current = type;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                fields.add(field);
            }
            current = current.getSuperclass();
        }
        return fields;
    }

    private Optional<Class<?>> resolveMemberGenericType(Field field, int typeIndex) {
        Type genericType = field.getGenericType();
        if (genericType instanceof ParameterizedType parameterizedType) {
            Type[] typeArguments = parameterizedType.getActualTypeArguments();
            if (typeArguments.length > typeIndex && typeArguments[typeIndex] instanceof Class<?> clazz) {
                return Optional.of(clazz);
            }
        }
        return Optional.empty();
    }

    /**
     * Trick to return a Class of a generic type
     *
     * @param <T> The generic type - anything works, as long as it's a Message
     * @return Message.class, gift-wrapped in generics
     */
    @SuppressWarnings("unchecked")
    private <T> Class<T> messageType() {
        return (Class<T>) Message.class;
    }

    private static class MessageHandlerContribution implements BeanFactoryInitializationAotContribution {

        private final BindingReflectionHintsRegistrar registrar = new BindingReflectionHintsRegistrar();

        private final Set<Class<?>> messageHandlingClasses;

        private final List<MessageHandlingMember<?>> messageHandlingMembers;

        public MessageHandlerContribution(
                Set<Class<?>> messageHandlingClasses,
                List<MessageHandlingMember<?>> messageHandlingMembers) {
            this.messageHandlingClasses = messageHandlingClasses;
            this.messageHandlingMembers = messageHandlingMembers;
        }

        @Override
        public void applyTo(GenerationContext generationContext,
                            BeanFactoryInitializationCode beanFactoryInitializationCode) {
            ReflectionHints reflectionHints = generationContext.getRuntimeHints().reflection();
            MemberCategory[] handlerCategories = {
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                    MemberCategory.INVOKE_DECLARED_METHODS,
                    MemberCategory.DECLARED_FIELDS
            };
            MemberCategory[] payloadCategories = {
                    MemberCategory.DECLARED_FIELDS,
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS
            };
            messageHandlingClasses.forEach(c -> reflectionHints.registerType(c, handlerCategories));
            messageHandlingMembers.forEach(m -> {
                m.unwrap(Method.class).ifPresent(mm -> reflectionHints.registerMethod(mm, ExecutableMode.INVOKE));
                m.unwrap(Constructor.class).ifPresent(mm -> reflectionHints.registerConstructor(mm,
                                                                                                ExecutableMode.INVOKE));
                registrar.registerReflectionHints(reflectionHints, m.payloadType());
                if (m instanceof QueryHandlingMember<?> queryHandlingMember) {
                    registrar.registerReflectionHints(reflectionHints, queryHandlingMember.resultType());
                }
            });
            registerAxonServerGrpcHints(generationContext.getRuntimeHints());
        }

        /**
         * Registers reflection hints for Axon Server gRPC protobuf classes.
         * Without these, the gRPC event channel calls hang silently in native image
         * because protobuf message (de)serialization fails.
         */
        private void registerAxonServerGrpcHints(RuntimeHints hints) {
            MemberCategory[] cats = {
                    MemberCategory.DECLARED_FIELDS,
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                    MemberCategory.INVOKE_DECLARED_METHODS
            };
            try {
                ClassLoader cl = Thread.currentThread().getContextClassLoader();
                if (cl == null) cl = getClass().getClassLoader();
                Enumeration<URL> resources = cl.getResources("io/axoniq/axonserver/grpc");
                while (resources.hasMoreElements()) {
                    URL url = resources.nextElement();
                    String jarPath = url.toString();
                    if (jarPath.startsWith("jar:file:")) {
                        String jarFile = jarPath.substring("jar:file:".length(), jarPath.indexOf('!'));
                        try (JarFile jar = new JarFile(jarFile)) {
                            jar.entries().asIterator().forEachRemaining(entry -> {
                                if (entry.getName().startsWith("io/axoniq/axonserver/grpc/")
                                        && entry.getName().endsWith(".class")
                                        && !entry.getName().contains("$")) {
                                    String className = entry.getName()
                                            .replace(".class", "")
                                            .replace('/', '.');
                                    try {
                                        hints.reflection().registerType(
                                                org.springframework.aot.hint.TypeReference.of(className), cats);
                                    } catch (Exception e) {
                                        // skip
                                    }
                                }
                            });
                        }
                    }
                }
            } catch (IOException e) {
                // skip
            }
        }
    }

    private static class LenientParameterResolver implements ParameterResolverFactory, ParameterResolver<Object> {

        @Override
        public ParameterResolver<Object> createInstance(Executable executable,
                                                        Parameter[] parameters,
                                                        int parameterIndex) {
            return this;
        }

        @Override
        public CompletableFuture<Object> resolveParameterValue(ProcessingContext processingContext) {
            throw new UnsupportedOperationException(
                    "This parameter resolver is not meant for production use. Only for detecting handler methods.");
        }

        @Override
        public boolean matches(ProcessingContext processingContext) {
            return true;
        }
    }
}
