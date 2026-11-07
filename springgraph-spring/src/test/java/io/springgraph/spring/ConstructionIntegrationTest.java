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

import static org.junit.jupiter.api.Assertions.*;

import io.springgraph.spring.ConstructionRegistrar.Factory;
import io.springgraph.spring.fixture.*;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

class ConstructionIntegrationTest {

    static final List<String> SEEN_BY_BPP = new ArrayList<>();

    /** Wraps AuditService in a JDK proxy, as a transaction/AOP post-processor would. */
    static class ProxyingPostProcessor implements BeanPostProcessor {
        @Override
        public Object postProcessAfterInitialization(Object bean, String name) {
            SEEN_BY_BPP.add(name);
            if (bean instanceof AuditService) {
                return Proxy.newProxyInstance(
                        bean.getClass().getClassLoader(),
                        new Class<?>[] {AuditService.class},
                        (p, m, a) -> m.invoke(bean, a));
            }
            return bean;
        }
    }

    @Configuration
    @ComponentScan("io.springgraph.spring.fixture")
    static class Base {
        @Bean
        Clock clock() {
            return Clock.systemUTC();
        } // stands in for a Boot-provided bean

        @Bean
        static BeanPostProcessor proxying() {
            return new ProxyingPostProcessor();
        }
    }

    @Configuration
    static class WithGenerated {
        @Bean
        static ConstructionRegistrar registrar() {
            return new ConstructionRegistrar(GeneratedFactories.all());
        }
    }

    @BeforeEach
    void reset() {
        SEEN_BY_BPP.clear();
        GeneratedFactories.CALLS.set(0);
        DefaultPaymentGateway.POST_CONSTRUCT.set(0);
        DefaultPaymentGateway.PRE_DESTROY.set(0);
    }

    @Test
    void generatedConstructionPreservesSpringBehavior() {
        var ctx = new AnnotationConfigApplicationContext(Base.class, WithGenerated.class);

        // generated callbacks really ran
        assertEquals(3, GeneratedFactories.CALLS.get());

        // availability, identity, no duplicates
        OrderService order = ctx.getBean(OrderService.class);
        assertSame(order, ctx.getBean("orderService"));
        assertEquals(1, ctx.getBeanNamesForType(OrderService.class).length);
        assertEquals(1, ctx.getBeanNamesForType(PaymentGateway.class).length);

        // dependencies are the container's instances
        assertSame(ctx.getBean(PaymentGateway.class), order.gateway());
        assertSame(ctx.getBean(Clock.class), order.clock());

        // dependents receive the proxy, not the raw target
        assertTrue(Proxy.isProxyClass(order.audit().getClass()));
        assertSame(ctx.getBean(AuditService.class), order.audit());

        // the proxied service itself got the single gateway instance
        assertEquals(1, DefaultPaymentGateway.POST_CONSTRUCT.get());

        // BeanPostProcessor saw the generated-constructed beans
        assertTrue(SEEN_BY_BPP.containsAll(List.of("orderService", "defaultAuditService", "defaultPaymentGateway")));

        // destruction
        ctx.close();
        assertEquals(1, DefaultPaymentGateway.PRE_DESTROY.get());
    }

    @Test
    void behaviorMatchesPlainSpringConstruction() {
        var plain = new AnnotationConfigApplicationContext(Base.class);
        var generated = new AnnotationConfigApplicationContext(Base.class, WithGenerated.class);
        // plain context never calls generated code; generated context calls it exactly once per bean
        assertEquals(3, GeneratedFactories.CALLS.get());
        // same fixture bean names, except the registrar bean itself
        var plainNames = new java.util.TreeSet<>(List.of(plain.getBeanDefinitionNames()));
        var generatedNames = new java.util.TreeSet<>(List.of(generated.getBeanDefinitionNames()));
        generatedNames.remove("registrar");
        generatedNames.remove("io.springgraph.spring.ConstructionIntegrationTest$WithGenerated");
        generatedNames.removeIf(n -> n.contains("WithGenerated"));
        assertEquals(plainNames, generatedNames);
        assertTrue(Proxy.isProxyClass(plain.getBean(OrderService.class).audit().getClass()));
        assertTrue(
                Proxy.isProxyClass(generated.getBean(OrderService.class).audit().getClass()));
        plain.close();
        generated.close();
    }

    @Test
    void failsFastWhenScannedDefinitionIsMissing() {
        var ctx = new AnnotationConfigApplicationContext();
        ctx.registerBean(
                ConstructionRegistrar.class,
                () -> new ConstructionRegistrar(List.of(new Factory<>("ghost", OrderService.class, bf -> null))));
        assertThrows(IllegalStateException.class, ctx::refresh);
    }

    @Test
    void plainContextAllowsOverridingSoRegistrationOrderCannotBeRelied() {
        // Why the registrar attaches a supplier to the scanned definition instead of registering
        // a second one: plain contexts allow overriding (Boot disables it), so a duplicate
        // definition would silently replace the scanned one depending on registration order.
        var ctx = new AnnotationConfigApplicationContext(Base.class);
        assertTrue(ctx.getDefaultListableBeanFactory().isAllowBeanDefinitionOverriding());
        ctx.close();
    }
}
