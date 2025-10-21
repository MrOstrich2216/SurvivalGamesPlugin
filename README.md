# 🏹 Survival Games Plugin
Hi, this is MrOstrich. Im happy that you are here. This is a Minecraft Plugin i've build for a Gaming Competition in my School using Copilot (though ai this is good). This is mainly built in thought of LAN servers.
So it is kinda based upon UHC RUN plugin. It's just this has natural regeneration so no golden heads. Keeping it easy for players.

---

## 🎮 Features

- **Match Lifecycle**: WAITING → GRACE → FIGHT → FINAL_FIGHT → ENDED
- **Grace Period**: PvP disabled, players scattered randomly, one life only
- **Final Fight Trigger**: Based on scoreboard alive count, not raw player list
- **Recorder Support**: Moderator "Recorder" spawns at center, enters Creative mode, invisible, excluded from stats
- **Custom Death Messages**: Weapon-based, ranged kills with distance, bare hands fallback
- **Sound Effects**:
  - Start: `EVENT_RAID_HORN`
  - Grace End: `BEACON_ACTIVATE` or `DRAGON_GROWL`
  - Final Fight: `WITHER_SPAWN`
  - Death: `LIGHTNING_THUNDER`
  - Victory: `FIREWORK_LAUNCH` + `LEVELUP` (5x loop)
- **Scoreboard & UI**:
  - Dynamic team tracking
  - Glowing red outline during final fight
  - Title + ActionBar messages
- **Border Control**: Configurable via `BorderUtil`, shrink logic per phase
- **Inventory Wipe**: On game end, armor and potion effects cleared

---

## 📦 Installation

1. Clone or download this repo
2. Build the plugin using IntelliJ + Gradle or Maven (WARNING THE ENTIRE SOURCE CODE IS MADE IN UTF-8 ENCODING)
3. Place the compiled `.jar` into your server’s `/plugins` folder
4. Restart the server

---

## 🚀 Commands

| Command         | Description                          |
|----------------|--------------------------------------|
| `/uhc enable`  | Enables the plugin                   |
| `/uhc start`   | Starts the match                     |
| `/uhc reset`   | (Optional) Resets match state        |
| `/uhc reload`  | (Optional) Reloads config            |

---

## 🔧 Configuration

Edit `config.yml` to customize:

```yaml
plugin-enabled: false
grace-seconds: 600 //Grace Period time limit
initial-border-diameter: 1500.0 //Area of world under border
shrink-rate-fight: 0.5 //Shink rate after grace period
shrink-rate-final: 1.5 //Shrink rate after final fight required no. of players are there
final-fight-threshold: 10 //Amount of players required for final fight phase to start
min-border-diameter: 32.0 //Minimum size of border after shink
exempt-users: //Simply add the names of admins under here
  - "Recorder"

