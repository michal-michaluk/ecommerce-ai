# Element 06 — effective price

Derived concept. Resolves what a product costs at a given instant from the Price and
Discount definitions of element 04 §8.

**Role:** Derived — immutable output, recomputed, never set by a command.

## Formula

```text
EffectivePrice(product, at) =
    base = the Price    where validFrom ≤ at and (validTo = null or at < validTo)
    disc = the Discount where validFrom ≤ at and (validTo = null or at < validTo)
    if base = none      → none
    else if disc = none → base.amount
    else                → base.amount × (1 − disc.percent / 100)
    rounded HALF_UP to 2 decimals, currency = base.currency
```

`validFrom` is **inclusive**, `validTo` is **exclusive** (RULE-38) — so a range
`2019-07-01 … 2019-07-31` covers the whole of 31 July and stops at 1 August 00:00.

## Scenario table

Reference product `p-2019-0442`: base `259.00 PLN` valid from `2019-07-01` open-ended;
discount `10 %` valid `2019-07-01 … 2019-07-31`; expired price `249.00 PLN` valid
`2019-06-01 … 2019-06-30`.

| # | `at` | base | discount | → result |
|---|---|---|---|---|
| 1 | 2019-06-15 | none | none | `none` |
| 2 | 2019-07-15 | `259.00` | `10 %` | `233.10 PLN` |
| 3 | 2019-08-01 | `259.00` (open-ended) | none | `259.00 PLN` |
| 4 | 2019-07-01 00:00 | `259.00` | `10 %` | `233.10 PLN` — `validFrom` inclusive |
| 5 | 2019-08-01 00:00 | `259.00` | none | `259.00 PLN` — `validTo` exclusive |
| 6 | 2019-07-15, no discount row | `259.00` | none | `259.00 PLN` |
| 7 | 2019-06-30 23:59 | `249.00` (expired row) | none | `249.00 PLN` |
| 8 | 2019-07-15, discount `15 %` also active | — | — | **undefined** — overlapping discounts are forbidden (RULE-25) |

## Variants and configurations

None. One formula, one rounding rule; there is no per-product or per-channel variant in this
increment.

## Parameter and value-object origins

| Parameter | Origin |
|---|---|
| `Price.amount`, `Price.currency` | Sales, via Price (Definition) — element 04 §8 |
| `Discount.percent` | Sales, via Discount (Definition) — element 04 §8 |
| `at` (instant) | the caller: the shop read model, the UI preview, the price banner |
| rounding `HALF_UP`, 2 decimals | **Q18** |
| currency `PLN` | example value only; `currency` is carried on each Price |

## Domain rules

- RULE-37: exactly one Price applies at any instant; with none, `EffectivePrice = none` and `PRICE_REQUIRED` is missing.
- RULE-38: `validFrom` inclusive, `validTo` exclusive.
- RULE-39: `EffectivePrice` is `none` when no Price applies, regardless of discounts.
- RULE-45: the result carries `base.currency`; a discount never changes the currency.
- RULE-46: rounding is applied once, to the final result — never to `base`, never to the discount.
- RULE-47: `percent` is a decimal string in `(0, 100)`; `0 %` is not represented as a Discount row.

## Open

- **Q18** — currency, rounding and the no-overlap rule.
