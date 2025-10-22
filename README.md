# 🏹 Survival Games Plugin

Hi, this is MrOstrich — glad you're here! This is a Minecraft plugin I built for a school gaming competition, with help from Copilot (AI really came through). It’s designed for LAN servers and inspired by the UHC RUN plugin, but with natural regeneration and no golden heads — keeping it easy and fun for players.

---

## 🎮 Features

- **Match Lifecycle**: `WAITING → GRACE → FIGHT → FINAL_FIGHT → ENDED`
- **Grace Period**: PvP disabled, players scattered randomly, one life only
- **Final Fight Trigger**: Based on scoreboard alive count (not raw player list)
- **Exempt User Support**: Admins like `"Recorder"` are excluded from stats and match logic
- **Custom Death Messages**: Weapon-based, ranged kills with distance, bare hands fallback
- **Sound Effects**:
  - Start: `UI_TOAST_CHALLENGE_COMPLETE`
  - Grace End: `BLOCK_BEACON_ACTIVATE`
  - Final Fight: `ENTITY_WITHER_SPAWN`
  - Death: `ENTITY_LIGHTNING_BOLT_THUNDER`
  - Victory: `ENTITY_FIREWORK_ROCKET_LAUNCH` + `ENTITY_PLAYER_LEVELUP` (looped 5x)
- **Scoreboard & UI**:
  - Dynamic team tracking
  - Glowing red outline during final fight
  - Title + ActionBar messages
- **Border Control**: Configurable via `BorderUtil`, shrink logic per phase
- **Inventory Wipe**: On game end, armor and potion effects cleared

---

## 📦 Installation

1. Clone or download this repository
2. Build the plugin using IntelliJ + Maven  
   ⚠️ *Note: The entire source code is UTF-8 encoded*
3. Place the compiled `.jar` into your server’s `/plugins` folder
4. Restart the server

---

## 🚀 Commands

All administrative actions are handled via the `/game` command. Requires `survival.admin` permission (default: OP).

| Command               | Description                                      |
|----------------------|--------------------------------------------------|
| `/game enable`       | Enables the plugin and prepares match logic      |
| `/game disable`      | Disables the plugin and resets all state         |
| `/game start`        | Starts the match (transitions to GRACE phase)    |
| `/game status`       | Displays current match phase and alive count     |
| `/game forcestop`    | Forcefully ends the match and triggers cleanup   |
| `/game neutral`      | Sets match state to NEUTRAL (idle, no active phase)|

---

## 🔧 Configuration

Edit `config.yml` to customize:

```yaml
plugin-enabled: false
grace-seconds: 600                # Grace period time limit (in seconds)
initial-border-diameter: 1500.0  # Starting world border diameter
shrink-rate-fight: 0.5           # Shrink rate after grace period
shrink-rate-final: 1.5           # Shrink rate after final fight begins
final-fight-threshold: 10        # Alive players required to trigger final fight
min-border-diameter: 32.0        # Minimum border size after shrinking
exempt-users:                    # Add usernames of exempt admins/mods
  - "Recorder"
