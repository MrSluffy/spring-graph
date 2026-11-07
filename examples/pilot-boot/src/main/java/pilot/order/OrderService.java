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
package pilot.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pilot.payment.PaymentGateway;

@Service
public class OrderService {
    private final OrderRepository repository;
    private final PaymentGateway paymentGateway;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public OrderService(
            OrderRepository repository, PaymentGateway paymentGateway, Clock clock, ObjectMapper objectMapper) {
        this.repository = repository;
        this.paymentGateway = paymentGateway;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Order place(String item) {
        if (!paymentGateway.charge(item)) {
            throw new IllegalStateException("payment failed");
        }
        return repository.save(new Order(item, clock.instant()));
    }

    public String describe(Order order) throws Exception {
        return objectMapper.writeValueAsString(order.getItem());
    }
}
