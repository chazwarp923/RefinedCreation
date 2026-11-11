# Refined Creation

Connects a **Refined Storage 2** network to **Create 6**'s logistics system (NeoForge 1.21.1).

## Disclaimer
This was 100% vibe coded using claude code. I do know how to code and have written Minecraft mods in the past.
All code was reviewed by me and I am perfectly capable of fixing any bugs that may arise.
This was my first dip into utilizing AI as a coding tool.

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

## Autocrafting with Create factories

The **Logistics Dispatcher** lets Refined Storage autocrafting use a Create factory to process ingredients.

1. Set up a Logistics Bridge as above. Its Packager's address is where results come back.
2. Place an RS **Autocrafter** facing a **Logistics Dispatcher**, and put the external (processing) patterns in it.
3. Place a Create **Packager** against the dispatcher, facing *away* from it, and put a sign on the Packager with the
   address of your factory.
4. Have your factory send its output as a package addressed to the bridge's Packager.

When RS runs one of those patterns, the dispatcher packs the ingredients for each crafting step and the Packager
sends them to the factory. When the result package is unpacked through the bridge, RS counts it toward the task
like any other external pattern output.

- Patterns may only use items. Patterns containing fluids are rejected, because packages can't carry fluids.
- Ingredients are split across several packages if they don't fit in one (9 stacks; bulky items travel alone).
- The dispatcher stops taking work while its Packager has `dispatcherMaxQueuedPackages` (default 10) packages queued.
  The Autocrafter's "lock until connected machine is empty" mode waits for the Packager's queue to clear.
- If the factory never sends the result back, the RS task waits until you cancel it, as with any external pattern.
- Don't point an Autocrafter at the Logistics Bridge itself. It would just push the ingredients back into the
  network.

## Notes

- Uses RS energy (`bridgeEnergyUsage` in `config/refinedcreation-common.toml`, default 8/tick).
- Don't point an RS **External Storage** at the bridge. It would read the network into itself and show items twice.
  A guard stops this from looping forever or duplicating items, but the counts will be wrong.
- Each item type appears as one virtual slot with its full network count. Extracting takes at most one stack at a time.

## Building

```
./gradlew build              # jar in build/libs
./gradlew runGameTestServer  # runs the integration tests (bridge packager round trip; autocrafting via dispatcher)
```

Dependencies: Create 6.0.10+, Refined Storage 2.0.9+.

## License

Refined Creation is licensed under the GNU Lesser General Public License v3.0 or later (LGPL-3.0-or-later). See [COPYING.LESSER](COPYING.LESSER) and [COPYING](COPYING) (the GPLv3, which the LGPL supplements).

Files derived from the NeoForged MDK template remain under the MIT license in [TEMPLATE_LICENSE.txt](TEMPLATE_LICENSE.txt).
