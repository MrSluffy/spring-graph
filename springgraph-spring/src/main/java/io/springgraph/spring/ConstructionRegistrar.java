/*
 * Copyright 2026 John Andrew Camu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.springgraph.spring;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;

/**
 * Integration experiment for generated construction (not a product API; see docs/architecture/spring-integration.md).
 *
 * <p><b>Registration ownership:</b> component scanning owns every bean definition. This registrar never registers a
 * definition. It attaches an instance supplier to the scanned definition with the same name, so there is exactly one
 * definition and one name, and nothing relies on bean-definition overriding or registration order. A missing or mismatched
 * definition fails the context refresh.
 *
 * <p><b>Construction:</b> the supplier obtains dependencies from the Spring container and then calls the constructor.
 * Lifecycle callbacks, BeanPostProcessors, proxies and singleton caching stay with Spring.
 */
public final class ConstructionRegistrar implements BeanDefinitionRegistryPostProcessor {

    /**
     * What a generator would emit for one component.
     *
     * @param beanName    the name component scanning gives the bean
     * @param type        the component class; must match the scanned definition's bean class
     * @param constructor creates the instance; must obtain every dependency from the given {@link BeanFactory}, never
     *                    with {@code new}, so dependencies are the container's (possibly proxied) beans
     */
    public record Factory<T>(String beanName, Class<T> type, Function<BeanFactory, T> constructor) {
        public Factory {
            Objects.requireNonNull(beanName, "beanName");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(constructor, "constructor");
        }
    }

    private final List<Factory<?>> factories;

    /** Set in {@link #postProcessBeanFactory}, which Spring calls before any bean is instantiated. */
    private volatile BeanFactory beanFactory;

    public ConstructionRegistrar(List<Factory<?>> factories) {
        this.factories = List.copyOf(factories);
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        for (Factory<?> factory : factories) {
            scannedDefinition(registry, factory)
                    .setInstanceSupplier(() -> factory.constructor().apply(beanFactory()));
        }
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }

    /** The definition component scanning registered for this factory, checked to be safe to take over. */
    private static AbstractBeanDefinition scannedDefinition(BeanDefinitionRegistry registry, Factory<?> factory) {
        String name = factory.beanName();
        if (!registry.containsBeanDefinition(name)) {
            throw new IllegalStateException("No scanned definition named '" + name
                    + "'; generated construction does not register beans on its own.");
        }
        BeanDefinition definition = registry.getBeanDefinition(name);
        if (!(definition instanceof AbstractBeanDefinition abstractDefinition)) {
            throw new IllegalStateException(
                    "Definition '" + name + "' is a " + definition.getClass().getName()
                            + ", not an AbstractBeanDefinition; it cannot take an instance supplier.");
        }
        if (!factory.type().getName().equals(definition.getBeanClassName())) {
            throw new IllegalStateException("Definition '" + name + "' is for " + definition.getBeanClassName()
                    + ", not " + factory.type().getName() + ".");
        }
        if (abstractDefinition.getInstanceSupplier() != null) {
            throw new IllegalStateException("Definition '" + name + "' already has an instance supplier.");
        }
        return abstractDefinition;
    }

    private BeanFactory beanFactory() {
        BeanFactory current = beanFactory;
        if (current == null) {
            throw new IllegalStateException("A generated factory ran before the bean factory was available.");
        }
        return current;
    }
}
