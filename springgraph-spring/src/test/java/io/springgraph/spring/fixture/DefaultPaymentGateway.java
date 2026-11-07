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

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
public class DefaultPaymentGateway implements PaymentGateway {
    public static final AtomicInteger POST_CONSTRUCT = new AtomicInteger();
    public static final AtomicInteger PRE_DESTROY = new AtomicInteger();

    @PostConstruct
    void start() {
        POST_CONSTRUCT.incrementAndGet();
    }

    @PreDestroy
    void stop() {
        PRE_DESTROY.incrementAndGet();
    }
}
