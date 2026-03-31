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

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.common.jpa.SimpleEntityManagerProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Replaces Axon's {@code ContainerManagedEntityManagerProvider} (which uses {@code @PersistenceContext} setter
 * injection that doesn't work in native image) with {@link SimpleEntityManagerProvider} backed by a
 * Spring-injected {@link EntityManager}.
 *
 * @author Gerard Klijs
 * @since 4.8.0
 */
@AutoConfiguration(
        afterName = "org.axonframework.extension.springboot.autoconfig.JpaAutoConfiguration"
)
@ConditionalOnBean(EntityManagerFactory.class)
public class SimpleEntityManagerProviderAutoConfiguration {

    @Bean
    @Primary
    public EntityManagerProvider simpleEntityManagerProvider(EntityManager entityManager) {
        return new SimpleEntityManagerProvider(entityManager);
    }
}
