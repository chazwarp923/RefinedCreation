# Refined Creation

Connects a **Refined Storage 2** network to **Create 6**'s logistics system (NeoForge 1.21.1).

## How to use

1. Place a **Logistics Bridge** so that it touches your RS network (directly or via cable). Its sides light up once
   the network is powered.
2. Place a Create **Packager** facing *away* from the bridge (the Packager's back touches the bridge).
3. Attach a **Stock Link** to the Packager and link it to your logistics network frequency.

You get:

- **Stock Keepers / Stock Tickers** list every item in the RS network.
- **Requests** from Stock Keepers and **Factory Gauges** are packaged straight out of RS storage.
- **Packages delivered** to that Packager (by Frogport, chain conveyor, etc.) are unpacked directly into the RS
  network. A package is only accepted if the whole thing fits.

The bridge also works as a plain inventory for funnels, chutes and hoppers: they can pull from and push into the
network through it.

## Notes

- Uses RS energy (`bridgeEnergyUsage` in `config/refinedcreation-common.toml`, default 8/tick).
- Don't point an RS **External Storage** at the bridge. It would read the network into itself and show items twice.
  A guard stops this from looping forever or duplicating items, but the counts will be wrong.
- Each item type appears as one virtual slot with its full network count. Extracting takes at most one stack at a time.

## Building

```
./gradlew build              # jar in build/libs
./gradlew runGameTestServer  # runs the integration test (RS network + bridge + Create packager round trip)
```

Dependencies: Create 6.0.10+, Refined Storage 2.0.9+.
