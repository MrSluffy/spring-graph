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
package io.springgraph.spring.fixture;

import io.springgraph.spring.ConstructionRegistrar.Factory;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Handwritten stand-in for generator output. Dependencies come from the container
 * (getBean), never from `new`.
 */
public final class GeneratedFactories {
    public static final AtomicInteger CALLS = new AtomicInteger();

    private GeneratedFactories() {}

    public static List<Factory<?>> all() {
        return List.of(
                new Factory<>("defaultPaymentGateway", DefaultPaymentGateway.class, bf -> {
                    CALLS.incrementAndGet();
                    return new DefaultPaymentGateway();
                }),
                new Factory<>("defaultAuditService", DefaultAuditService.class, bf -> {
                    CALLS.incrementAndGet();
                    return new DefaultAuditService(bf.getBean(PaymentGateway.class));
                }),
                new Factory<>("orderService", OrderService.class, bf -> {
                    CALLS.incrementAndGet();
                    return new OrderService(
                            bf.getBean(PaymentGateway.class), bf.getBean(Clock.class), bf.getBean(AuditService.class));
                }));
    }
}
