# Seeded mistakes: first line of each message

Captured from the seeded-mistake runs on `examples/pilot-boot` (see `developer-experience.md`). SpringGraph messages continue with
the dependency path and fixes; Spring messages are the root cause of the failed context start.

## missing-implementation

- SpringGraph: `SG001: No static binding found for pilot.seed.ShippingProvider.`
- Spring (context startup): `NoSuchBeanDefinitionException: No qualifying bean of type 'pilot.seed.ShippingProvider' available: expected at least 1 bean which qualifies as autowire candidate. Dependency annotations: {}`

## ambiguous-implementation

- SpringGraph: `SG007: Ambiguous binding for pilot.payment.PaymentGateway. Candidates: pilot.payment.DefaultPaymentGateway, pilot.seed.AltPaymentGateway.`
- Spring (context startup): `NoUniqueBeanDefinitionException: No qualifying bean of type 'pilot.payment.PaymentGateway' available: expected single matching bean but found 2: defaultPaymentGateway,altPaymentGateway`

## constructor-cycle

- SpringGraph: `SG003: Eager constructor dependency cycle:` followed by the cycle path (`pilot.seed.CycleA -> pilot.seed.CycleB -> pilot.seed.CycleA`)
- Spring (context startup): `BeanCurrentlyInCreationException: Error creating bean with name 'cycleA': Requested bean is currently in creation: Is there an unresolvable circular reference or an asynchronous initialization dependency?`
