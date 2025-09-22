# Fishing Frenzy

**Fishing Frenzy** is a Minecraft Paper plugin for 1.21.8 that introduces an exciting, server-wide fishing event. Every hour, players have a chance to participate in a limited-time event where fishing yields rare and powerful loot!

## Features

- **Hourly Fishing Frenzy Event:**  
  Every hour, a Fishing Frenzy event starts automatically and lasts for 10 minutes.
- **Event Announcements:**  
  - 5-minute warning before the event starts (title popup).
  - Countdown action bar notifications as the event approaches.
  - Title and action bar notifications when the event starts and ends.
  - Action bar displays time remaining during the event.
- **Enhanced Fishing Loot:**  
  During the event, fishing has a 20% chance to yield special loot, including:
  - Enchanted books (with rare and useful enchantments)
  - Enchanted fishing rods and tridents
  - Rare potions (Water Breathing, Night Vision, Slow Falling)
  - Netherite scrap, diamonds, emeralds, golden apples (including enchanted)
  - Nautilus shells, Heart of the Sea, prismarine items
  - Totem of Undying, enchanted diamond helmet, enchanted elytra
  - Steve's Lucky Head (custom player head)
  - Bundles (if available in your version)
  - Cooked cod (as a fallback)

## How It Works

1. **Event Timing:**
   - The event is scheduled every hour (configurable in code).
   - A 5-minute warning is broadcast to all players.
   - The event lasts for 10 minutes.

2. **Player Notifications:**
   - **5 minutes before:** Title popup: "Fishing Frenzy soon! Starts in 5 minutes!"
   - **1 minute before:** Action bar countdown every 10 seconds.
   - **Event start:** Title: "Fishing Frenzy! Catch rare loot for 10 minutes!"
   - **During event:** Action bar: "Fishing Frenzy! Time left: [mm:ss]"
   - **Event end:** Title: "Fishing Frenzy Ended. Back to normal fishing."

3. **Fishing During the Event:**
   - When a player catches a fish, there is a 20% chance to receive a special loot drop in addition to the normal catch.
   - Players are notified with a message when they receive special loot.

## Installation

1. Place the plugin JAR in your server's `plugins` folder.
2. Start or reload your Paper server (version 1.21.8).
3. The event will run automatically; no configuration is required.

## Customization

- **Event Timing and Loot:**  
  To change event frequency, duration, or loot, edit the relevant values in the source code (`FishingFrenzyManager.java` and `FishingFrenzyListener.java`).

## Requirements

- PaperMC 1.21.8 or compatible.
- Java 21 or newer.

## License

This plugin is provided as-is. You may modify and redistribute it as you wish.


