# Performance benchmarks

Benchmarks of Refined Creation's hot paths on a large Refined Storage network, run with:

```
./gradlew runBenchmarks
```

They are left out of `runGameTestServer`. Results are written to `build/benchmarks/latest.md`; add `-Pprofile`
to also record a JFR profile to `build/benchmarks/benchmarks.jfr`.

## Test network

- 10,001 item types: every registry item, enchanted books, potions, damaged tools and named variants, with counts
  from 1 to 100,000
- 32 storage blocks (28 × 64k, 4 creative) and a creative controller
- 34 Autocrafters holding about 300 processing patterns
- A Logistics Bridge with its Packager and Stock Link, a Logistics Dispatcher, and a Logistics Workbench

## Results

Measured on 2026-11-25. Three builds were run back to back, twice each, and each figure is the mean of the two
runs' medians:

- **Baseline:** `ac534fb`, before the optimization pass
- **main:** `0b684f2`, after the optimizations in Refined Creation's own code
- **Mixin branch:** main plus the mixin fast paths into Create, as later committed in "Add mixin fast paths into
  Create for large bridge networks"

Times vary by up to about 1.5× between runs on the test machine, so treat changes under about 1.2× as noise.

This table was measured with fixed warm-up and iteration counts, which ended the sub-millisecond benchmarks
before the JIT had finished compiling them. Their figures here (B5, B6, B7a, B7b, W6b and D1) are 2–4× higher
than with the time-based warm-up the suite now uses, which runs each phase for at least 200 ms. They still compare
fairly with each other, since every build was measured the same way, but not with newer runs.
Allocations are bytes per operation from the first run of each build. A dash means the benchmark didn't exist in
that build. The baseline's W6a rebuilt the list from scratch, so it compares with both W6a and W6a1.

| ID | Operation | Baseline | main | vs baseline | Mixin branch | vs main | Alloc baseline → main → mixin |
|---|---|---:|---:|---:|---:|---:|---|
| N0 | Server tick, large network idle | 382 µs | 352 µs | ~ | 348 µs | ~ | n/a → n/a → n/a |
| B1 | Packager stock summary rebuild after a network change | 5.85 ms | 4.68 ms | 1.3× faster | 2.02 ms | 2.3× faster | 5.3 MB → 3.9 MB → 1022.4 KB |
| B2a | Network summary (accurate), Packager summary cached | 2.35 ms | 2.50 ms | ~ | 647 µs | 3.9× faster | 3.5 MB → 3.5 MB → 576.4 KB |
| B2b | Network summary (accurate) after a network change | 9.27 ms | 8.43 ms | ~ | 2.92 ms | 2.9× faster | 12.3 MB → 7.9 MB → 1.9 MB |
| B3 | Package a 2,000-item order of one item | 15.29 ms | 8.63 ms | 1.8× faster | 6.05 ms | 1.4× faster | 20.0 MB → 4.0 MB → 1.1 MB |
| B4 | Package a 20-item-type order (100 of each) | 24.25 ms | 12.39 ms | 2.0× faster | 8.40 ms | 1.5× faster | 27.6 MB → 4.1 MB → 1.1 MB |
| B5 | Unpack a 9-stack package into the network | 38 µs | 40 µs | ~ | 39 µs | ~ | 7.7 KB → 7.7 KB → 7.7 KB |
| B6 | 20 bridge crafting ticks (one craftables refresh) | 36 µs | 41 µs | 1.1× slower | 42 µs | ~ | 14.2 KB → 14.3 KB → 14.2 KB |
| B7a | Bridge server tick, network unchanged | 0.4 µs | 0.5 µs | 1.2× slower | 0.5 µs | 1.1× faster | 762 B → 762 B → 762 B |
| B7b | Bridge server tick, network changed | 0.4 µs | 0.4 µs | ~ | 0.5 µs | 1.1× slower | 762 B → 762 B → 762 B |
| B8 | Filtered funnel extract (simulated), item in the middle of 10001 slots | 7.42 ms | 6.34 ms | 1.2× faster | 1.74 ms | 3.6× faster | 1.6 MB → 273.8 KB → 391.0 KB |
| B8b | Filtered funnel extract (simulated) after a network change | 7.05 ms | 5.79 ms | 1.2× faster | 811 µs | 7.1× faster | 1.9 MB → 273.8 KB → 391.0 KB |
| W1 | Stock sync check, stock unchanged | 2.25 ms | 234 µs | 9.6× faster | 222 µs | ~ | 3.4 MB → 250.4 KB → 251.4 KB |
| W1b | Stock sync check, summary rebuilt but stock unchanged | - | 441 µs |  | 388 µs | 1.1× faster | - → 250.4 KB → 251.4 KB |
| W2 | Stock sync send: copy + encode 10001 entries | 6.77 ms | 6.04 ms | 1.1× faster | 5.49 ms | 1.1× faster | 8.8 MB → 5.2 MB → 5.2 MB |
| W2c | Stock sync send, counts only: compare + encode | - | 1.23 ms |  | 1.07 ms | 1.1× faster | - → 704.7 KB → 706.7 KB |
| W2b | Stock packet decode (client) | 2.84 ms | 3.01 ms | ~ | 3.02 ms | ~ | 6.6 MB → 6.6 MB → 6.7 MB |
| W3 | Order 64 diamonds from the stock list | 9.33 ms | 6.77 ms | 1.4× faster | 2.46 ms | 2.7× faster | 12.3 MB → 7.9 MB → 1.9 MB |
| W4 | Order ingredients for 64 crafts of a 9-ingredient pattern | 12.62 ms | 7.19 ms | 1.8× faster | 2.64 ms | 2.7× faster | 15.4 MB → 7.9 MB → 2.0 MB |
| W5 | Recipe transfer for 8 crafts, all 9 ingredients from the network | 14.23 ms | 6.58 ms | 2.2× faster | 1.97 ms | 3.3× faster | 15.4 MB → 7.9 MB → 2.0 MB |
| W6a | Client stock list: stock update (names + sort) | 11.48 ms | 3.64 ms | 3.2× faster | 3.58 ms | ~ | 4.9 MB → 873.0 KB → 873.0 KB |
| W6a1 | Client stock list: first stock after opening (names + sort) | - | 5.89 ms |  | 6.00 ms | ~ | - → 5.1 MB → 5.1 MB |
| W6a2 | Client stock list: counts update (apply + sort by count) | - | 1.86 ms |  | 1.89 ms | ~ | - → 575.6 KB → 575.7 KB |
| W6b | Client stock list: one keystroke in the search box | 310 µs | 358 µs | 1.2× slower | 336 µs | ~ | 75.3 KB → 75.3 KB → 75.3 KB |
| W6c | Client stock list: change sort | 3.35 ms | 3.64 ms | ~ | 3.37 ms | ~ | 80.3 KB → 158.4 KB → 158.4 KB |
| D1 | Dispatcher step: simulate + execute, 9 ingredients | 24 µs | 19 µs | 1.2× faster | 17 µs | ~ | 5.0 KB → 5.0 KB → 5.0 KB |
| D2 | Start an RS task for 64 crafts through the dispatcher | 838 µs | 831 µs | ~ | 816 µs | ~ | 1.3 MB → 1.3 MB → 1.3 MB |
| D3 | Server tick while RS dispatches a 64-craft task | 280 µs | 320 µs | 1.1× slower | 320 µs | ~ | n/a → n/a → n/a |

## Not measured

- The JEI recipe-transfer stock lookup, because JEI isn't on the benchmark server. It's a single pass over the
  stock list per update.
