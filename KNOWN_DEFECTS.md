# Known defects

These are cases where the public Swagger Petstore (`https://petstore.swagger.io/v2`)
does not do what its OpenAPI spec (`src/test/resources/schemas/petstore-openapi.yaml`)
or basic business rules require. Each one has a test that asserts the **correct**
behaviour and is tagged `@Tag("known-defect")`.

The tests are not deleted or flipped to expect the wrong status. Instead:

- `mvn test` (the default run, and the main CI job) leaves the `known-defect` tag out,
  so the pipeline stays green.
- `mvn test -Pknown-defects` runs only those tests. They are expected to fail until
  the API is fixed.
- CI runs the same profile in a separate `known-defects` job. It never fails the
  pipeline; it lists the defects that still reproduce on the job page, and flags any
  test that starts passing so its tag and entry here can be removed.

The "Actual" column is what the live petstore returned in CI when this list was written.

## GET /pet/findByStatus

`status` is a required query parameter whose items are limited to
`available`, `pending`, `sold`. The spec documents `400 Invalid status value`.

| Test | Input | Expected | Actual |
| --- | --- | --- | --- |
| `PetStoreDdtTest.findPetsByStatus_RejectsInvalidStatus` | `status=unknown` | 400 | 200 |
| `PetStoreDdtTest.findPetsByStatus_RejectsInvalidStatus` | `status=invalid_enum_val` | 400 | 200 |
| `PetStoreDdtTest.findPetsByStatus_RejectsInvalidStatus` | no `status` parameter | 400 | 200 |

## POST /pet

The `Pet` schema requires `name` and `photoUrls`, declares `photoUrls` as an array and
limits `status` to `available`, `pending`, `sold`. The spec documents `400 Invalid input`.
(It also lists `405 Validation exception`, which misuses "Method Not Allowed"; the
tests expect 400.)

| Test | Input | Expected | Actual |
| --- | --- | --- | --- |
| `PetStoreDdtTest.createPet_RejectsMissingRequiredField` | `name` is null | 400 | 200 |
| `PetStoreDdtTest.createPet_RejectsMissingRequiredField` | `photoUrls` is null | 400 | 200 |
| `AiDrivenDynamicTest.specViolationsMustBeRejected` | `name` omitted | 400 | 200 |
| `AiDrivenDynamicTest.specViolationsMustBeRejected` | `photoUrls` is a string, not an array | 400 | 500 |
| `AiDrivenDynamicTest.specViolationsMustBeRejected` | `status` = `super-available` | 400 | 200 |
| `AiDrivenDynamicTest.specViolationsMustBeRejected` | `status` = `sold; UPDATE pet SET name='hacked'` | 400 | 200 |

The 500 is a defect on its own: a malformed request body is a client error and must
not surface as a server error.

## POST /store/order

`Order.status` is limited to `placed`, `approved`, `delivered` and `shipDate` is an
RFC 3339 `date-time`. The spec documents `400 Invalid Order`. A negative quantity is
rejected as a business rule: the spec has no `minimum` on `quantity`, which is a gap
in the spec as well as in the service.

| Test | Input | Expected | Actual |
| --- | --- | --- | --- |
| `PetStoreDdtTest.storeOrder_RejectsInvalidOrder` | `quantity` = -1 | 400 | 200 |
| `PetStoreDdtTest.storeOrder_RejectsInvalidOrder` | `quantity` = -100 | 400 | 200 |
| `PetStoreDdtTest.storeOrder_RejectsInvalidOrder` | `status` = `unknown` | 400 | 200 |
| `PetStoreDdtTest.storeOrder_RejectsInvalidOrder` | `shipDate` = `2026-07-15` (date only) | 400 | 200 |
| `PetStoreDdtTest.storeOrder_RejectsInvalidOrder` | `shipDate` = `invalid-date-format` | 400 | 500 |
| `PetStoreDdtTest.storeOrder_RejectsInvalidOrder` | `shipDate` = empty string | 400 | 200 |

## When a defect is fixed

If the `known-defects` CI job reports that a test no longer reproduces, move the case
back into the regular data set in the same test class, drop its `known-defect` tag, and
delete its row here.
