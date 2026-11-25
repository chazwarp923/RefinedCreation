# Refined Creation

Connects a **Refined Storage 2** network to **Create 6**'s logistics system, so your storage network and your
package-based logistics can work as one (NeoForge 1.21.1).

## Disclaimer
This was 100% vibe coded using claude code. I do know how to code and have written Minecraft mods in the past.
All code was reviewed by me and I am perfectly capable of fixing any bugs that may arise.
This was my first dip into utilizing AI as a coding tool.

## Features

### Logistics Bridge
Links a Refined Storage network to a Create logistics network.

- **Stock Keepers** list everything stored in Refined Storage, and orders are packaged straight out of it.
- **Deliveries** to the bridge's Packager are unpacked straight into the network.
- **Autocrafting:** items Refined Storage can craft show up in Stock Keepers too. Ordering more than is stored
  starts an RS crafting job, and the order ships once it's done.

### Logistics Dispatcher
Lets Refined Storage autocrafting use your **Create factories**. When RS runs a processing pattern, its
ingredients are packaged and sent to the factory's address, and the results come back into the network through
the bridge.

### Logistics Workbench
A crafting table that works from a **Create logistics network**, in the style of the Stock Keeper.

- Browse, search and sort everything on the network, and order items in any amount.
- Order the ingredients for as many crafts as you need. Deliveries refill the crafting grid as they arrive.
- Send leftovers back to the network from the return shelf.
- **JEI support:** fill the grid from a recipe with "+", and keep the workbench's search in sync with JEI's.

## Getting started
Every block has a **Ponder** scene: hold **W** while hovering over it in your inventory or JEI to see how to set it
up.

## Requirements
- Minecraft 1.21.1 with NeoForge
- Create 6.0.10+
- Refined Storage 2.0.9+
- JEI (optional, for recipe transfer and search sync in the Logistics Workbench)

## Building

```
./gradlew build              # jar in build/libs
./gradlew runGameTestServer  # integration tests: bridge, dispatcher, Create orders crafted by RS, Logistics Workbench
```

Dependencies: Create 6.0.10+, Refined Storage 2.0.9+. JEI is optional (recipe transfer into the Logistics Workbench).

## License

Refined Creation is licensed under the GNU Lesser General Public License v3.0 or later (LGPL-3.0-or-later). See [COPYING.LESSER](COPYING.LESSER) and [COPYING](COPYING) (the GPLv3, which the LGPL supplements).

Files derived from the NeoForged MDK template remain under the MIT license in [TEMPLATE_LICENSE.txt](TEMPLATE_LICENSE.txt).
