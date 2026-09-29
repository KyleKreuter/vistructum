![Vistructum](https://raw.githubusercontent.com/KyleKreuter/vistructum/main/docs/logo.png)

Vistructum finds swastikas that players build from blocks on your Paper server and reports them to your staff. Two small neural networks check new builds shortly after players finish them and scan whole worlds once a day. Every detection becomes a **finding** that staff confirm or mark as a false alarm.

Vistructum never kicks, bans, or rolls back on its own. Every decision stays with your staff.

The gallery shows the web app and the replay with demo data. Symbols in the screenshots are pixelated.

## Why use it

- Staff can't watch every build. Vistructum points them to the few builds that matter, with location, players, and a preview.
- It also finds old symbols: the daily scan covers whole worlds, including the Nether, the End, and structures hidden underground.
- Optional evidence recording shows who built a symbol, block by block, as a 3D replay.
- It runs on your own server. Detection needs no external service.

## Features

- **Live check:** Detects symbols a few seconds after players finish them, including rotated, mirrored, irregular, and carved variants from 5×5 blocks upward.
- **Daily scan:** Reads the region files of whole worlds without loading chunks. It checks the surface and finds symbols built underground or hidden inside terrain. Staff can also start a scan by command.
- **Review in game:** Chat alerts with **[Open]** and **[TP]** buttons, and a review menu with preview images, player faces, location, and probability.
- **Web app (optional):** Review findings in the browser. It shows the finding list with filters, a detail view with 2D layers and a heatmap of what the model relied on, a 3D view of the terrain, player pages, statistics per day and per reviewer, and an activity log. Staff sign in with a one-time link from `/vis web`.
- **Evidence recording (optional):** When the live check creates a finding, Vistructum secures the blocks before the build, every block change with player and time, and the movement of nearby players. The web app plays this back as a 3D replay with player skins.
- **Public evidence links (optional):** Share the replay of a confirmed finding through a link that needs no sign-in. The page hides the world name and the coordinates.
- **Local or remote inference:** Runs the models inside the server, or on a separate sidecar with a local fallback.
- **Plugin API:** Query findings, submit reviews, start scans, and listen to events from your own plugin.
- **Retention:** Deletes false alarms after 90 days and confirmed findings after 365 days. Both periods are configurable.

## What you install

This version has two files. Install both.

| File | Purpose |
|---|---|
| `vistructum-<version>.jar` (primary file) | Detection, storage, and the public API. It has no commands. |
| `vistructum-ui-<version>.jar` (additional file) | The `/vis` command, the review menu, chat alerts, the resource pack, and the web app. It uses only the public API, so you can replace it with your own plugin. |

## Requirements

- Paper 1.21.4 and Java 21
- ViaVersion and ViaBackwards, only if players join with other 1.21.x clients

## Installation

1. Copy both jars into `plugins/`.
2. In `plugins/vistructum-ui/config.yml`, set `pack.public-url` to an address your players can reach. The default `http://localhost:8765/pack.zip` works only on the machine that runs the server.
3. Restart the server.

On the first start, Paper downloads SQLite JDBC and ONNX Runtime from Maven Central through the `libraries` entry in `plugin.yml`.

The web app and evidence recording are off by default. The [Web App and Evidence](https://github.com/KyleKreuter/vistructum/wiki/Web-App-and-Evidence) wiki page explains how to turn them on and off, how to run the web app behind HTTPS, and what data they store.

## Commands and permissions

| Command | Description |
|---|---|
| `/vis`, `/vis review [count]` | Opens the review list |
| `/vis show <id>` | Opens a finding |
| `/vis tp <id>` | Teleports to a finding |
| `/vis confirm <id>`, `/vis falsealarm <id>` | Reviews a finding |
| `/vis status` | Shows the inference mode, loaded models, tracked changes, open findings, and running scans |
| `/vis stats` | Shows the share of confirmed findings per detection path |
| `/vis scan [world\|stop]` | Starts a world scan or stops all scans |
| `/vis export` | Exports reviewed findings as training data |
| `/vis web` | Sends a one-time sign-in link to the web app |
| `/vis evidence <id> [share\|unshare]` | Opens the replay of a finding, or turns its public link on or off |

| Permission | Default | Grants |
|---|---|---|
| `vistructum.staff` | op | Chat alerts, reviews, `/vis stats`, and sign-in to the web app |
| `vistructum.admin` | op | `vistructum.staff`, `/vis scan`, and `/vis export` |
| `vistructum.evidence.share` | op | Public evidence links. Not included in `vistructum.admin`. |

## Network connections

Vistructum connects to these services. With the default `inference.mode: local`, detection runs inside your server and sends no world data anywhere.

| Service | When | What it sends | Turn off |
|---|---|---|---|
| bStats (`bstats.org`) | Every 30 minutes | Anonymous usage data: server and player counts, the inference mode, the model update setting, whether the daily scan is on, the number of scan worlds, and whether `vistructum-ui` runs. No findings, reviews, or player names. | `enabled: false` in `plugins/bStats/config.yml` |
| GitHub (`api.github.com`, `github.com`) | On start and every `updates.check-hours` | A request for the latest model release. Vistructum installs new models only with `updates.auto-update-models: true`; otherwise it logs that an update is available. | Can't be turned off. It only reads the public release list. |
| Mojang account services (`sessionserver.mojang.com`, `api.minecraftservices.com`, `textures.minecraft.net`) | When staff open a finding or a replay | The UUID of a player in the finding, or the player's name on offline-mode servers, to load the skin. | Can't be turned off. Skins are cached for 24 hours. |
| Mojang game files (`piston-meta.mojang.com`, `piston-data.mojang.com`) | Once per Minecraft version, only with `web.textures.download: true` | A download request for the client jar, to show block textures in the web app | `web.textures.download: false` (default) |
| Your sidecar | Only with `inference.mode: remote` | The block data of each check | `inference.mode: local` (default) |

## Configuration

| File | Content |
|---|---|
| `plugins/vistructum/config.yml` | Inference mode, model updates, live check timing, evidence recording, daily scan, retention |
| `plugins/vistructum-ui/config.yml` | Resource pack and web app port, public URLs, web app switch, texture download |
| `plugins/vistructum-ui/messages.yml` | All chat and menu texts in MiniMessage format |

The [Configuration](https://github.com/KyleKreuter/vistructum/wiki/Configuration) wiki page describes every key.

## More

- [Wiki](https://github.com/KyleKreuter/vistructum/wiki): plugin API, Docker setup, building from source
- [Source code](https://github.com/KyleKreuter/vistructum) and [issues](https://github.com/KyleKreuter/vistructum/issues)

## License

Vistructum is licensed under the [GNU General Public License v3.0 only](https://github.com/KyleKreuter/vistructum/blob/main/LICENSE). You can use it on any server, including commercial ones. If you distribute a modified version, you must publish its source code under the same license.
