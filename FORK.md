# This fork: what has changed and why

Notes for the next person or agent working on `fang-wei-jie/cleanyourtv`. Read this before changing the launcher,
so you don't have to rediscover what is already done. [AGENTS.md](AGENTS.md) still applies to everything here.

## The TV

- TCL 55T7B ("Smart TV Pro", device G09). Google TV on Android 12 (SDK 31), firmware STT2.230203.001.AS24.
- Connected by Ethernet; the Mac doing the work is on Wi-Fi. Both are on the same LAN, and ADB over TCP works across them.
- The closest tested list is `examples/tcl-g09-google-tv/debloat.txt`. Nothing has been debloated on this TV:
  - **Disabled by us:** `com.google.android.apps.tv.launcherx` (Google TV's Home), and
    `com.google.android.tungsten.setupwraith`. Home fell to setupwraith the first time. It owns no Wi-Fi network
    here, because the TV is on Ethernet.
  - **Already disabled before:** Netflix, Prime Video and `magiconnectfree`.
  - **Not changed:** telemetry.
- Home is `com.gathin.tvhome/.MainActivity`. The TV has a second Android user (10, from Google TV profiles); the
  launcher is installed only for user 0.
- **Granted over adb:** `BLUETOOTH_CONNECT` (for the Bluetooth pill) and `ACCESS_FINE_LOCATION` (the install script
  grants it). Both grants survive `-r` reinstalls.
- **Remote keys:**
  - The gear is `KEYCODE_NOTIFICATION` (83).
  - Netflix, Prime and Web go through TCL `partnercustomizer` and open the Play Store, because those apps are off.
    The owner doesn't use those services, so they were left alone.
- **Rollback:** the backup folder `backup-2026-10-22/` has `ROLLBACK.sh` (re-enables launcherx and setupwraith and
  makes launcherx Home). The folder is local and untracked, and must never be committed.

## Working on it

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17 ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
STOCK_LAUNCHER=none launcher/build-and-install.sh <tv ip>      # build, install, keep it Home
TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py '<shell command>'
TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py --screenshot shots/x.png
python3 -m unittest discover -s tests                          # must pass before a push
```

- **Don't use `HOME_KEY_SERVICE=1`.** The accessibility helper broke holding volume up/down on this TV, so it was
  switched off. Without it, the gear key works only while Home is in front.
- **Avoid `tools/theme.sh my-theme`.** Pushing a theme clears on-TV choices: columns, rows, dock opacity, wallpaper,
  tile order and quick panel choices. The owner has 5 columns and a 30% dock set on the TV.
- **Use real keys for testing:** `input keyevent ...` and `input keyevent --longpress DPAD_CENTER`. `input tap`
  puts the UI in touch mode and focus disappears.
- **`am start` won't open `LauncherSettingsActivity`** (it isn't exported). Reach it through the quick panel's
  Launcher Settings row.
- **The APK size gate (491,520 B) is ignored here** at the owner's request. The APK is about 499 KB. CI upstream
  would fail on it.
- **Git:**
  - Remotes: `origin` is upstream (Gathin23) and `fork` is this fork. `gh` uses the `fang-wei-jie` account.
  - The local clone is shallow.
  - Commit locally, and ask the owner before every push.

## Switches added

All three are set on in this copy's `launcher/app/src/main/res/values/config.xml`. Upstream's default for each would
be off. Each has a row in `themes/README.md` and a screenshot in `docs/switches/`.

| Key | What it does | Code |
|---|---|---|
| `bluetooth_pill` | Replaces the network pill with the connected Bluetooth audio device. See [Bluetooth pill](#bluetooth-pill) below. | `BluetoothPill.java`, `MainActivity` (`bluetoothPill`) |
| `inputs_single_direct` | The Inputs pill can become one input and open it directly. See [Inputs pill](#inputs-pill) below. | `MainActivity.updateInputsButton()`, `showInputs(probe, full)`, `direct(rows)` |
| `inputs_connected_only` | The Inputs menu lists only connected inputs; Live TV and AirPlay stay. | `MainActivity.showInputs` |

### Bluetooth pill

- **Device connected:** the pill shows the name the device was given in TV Settings, which is `getAlias()`; `getName()`
  is the device's own name. Next to it is a glyph:
  - `ic_earbuds` (a PNG cut from a reference photo) for AirPods and "buds";
  - `ic_headphones` for AirPods Max and headphone device classes;
  - otherwise the Bluetooth mark.
- **Nothing connected:** "Bluetooth" with the Bluetooth mark.
- **Bluetooth off:** "Bluetooth" with a crossed-out mark.
- **Pressing it** opens TV Settings > Remotes & Accessories (`com.android.tv.settings.ACCESSORIES_CONNECTED_DEVICES`).
- **Only A2DP and HEADSET devices count**, so the remote's own Bluetooth link never shows.

### Inputs pill

- **Exactly one HDMI input active:** the pill shows the device's name (the part before " · "), or the port's if it
  has none, and opens that input. HDMI wins over AirPlay.
- **Two or more HDMI inputs:** "Inputs", and pressing it opens the menu.
- **No HDMI input and a single row** (AirPlay alone): the pill becomes that row and opens it directly.
- **A long press** always opens the full menu.
- **It updates on `onInputStateChanged`.** This TV reports idle HDMI ports as standby (state 1), not connected (0).
- **Skipped** with `icon_only_buttons`, `icon_only_inputs` or a festive skin.

## Other launcher changes

- **Quick panel** (`QuickSettingsActivity`, opened by the gear key with `settings_key_panel`):
  - **Rows** (`ROW_KEYS`), in built-in order:
    1. Display, Network (Ethernet icon), Bluetooth and Apps: the TV's own pages;
    2. TV Settings;
    3. Launcher Settings;
    4. Module cards, Edit dock and Reorder apps: the Home rows.
  - **Launcher Settings lists every row** under Quick panel. OK switches a row on or off; holding OK opens Move up /
    Move down. Choices are stored as `quick_*` booleans and the `quick_order` key list in `Config.SETTINGS_NAME`
    prefs.
  - **Launcher Settings always shows** and can't be switched off.
  - **The Home rows show only when Home opened the panel** (`EXTRA_HOME`). Module cards and Edit dock also need
    `module_cards` and `dock_row`. Pressing one sends `ACTION_LAUNCHER_SETTINGS` with `EXTRA_HOME_ROW` back to
    `MainActivity`, which runs it.
  - **Holding OK on the Settings pill** opens the same panel (`MainActivity.quickPanel()`) instead of the old text
    menu (`showSettingsMenu()`, still used when `settings_key_panel` is off).
  - **The gear key on Home** focuses the Settings pill first, so it looks and closes the same as the long press.
- **Back or Home on Home:** the first press returns to the first dock tile; a second press, already there, starts
  the screensaver through `com.android.systemui/.Somnambulator` (`atRest()`, `startScreensaver()`).
- **Strings:** title case for "TV Settings" and "Launcher Settings". New strings are in `values/strings.xml` only,
  so other locales fall back to English.
- **`my-theme/config.json`:** `tile_menu`, `app_library`, `settings_key_keycodes` [83], `settings_key_panel`,
  `bluetooth_pill`.

## Gotchas learned on this TV

- **Bluetooth settings:** `Settings.ACTION_BLUETOOTH_SETTINGS` resolves to nothing; use the accessories action above.
- **Coloured icons on pills:** the top-bar pills tint their compound drawables to follow focus, so a coloured icon
  drawable turns white. Earlier we used a coloured text span for a status dot; the owner then dropped the dot.
- **SF Symbols can't be used:** Apple's licence allows them only on Apple platforms, and repackaged SVG or PNG sets
  carry the same licence.
- **YouTube profiles:** the YouTube app switches between profiles without asking, which is a known YouTube bug.
  It is not caused by the launcher and there is no fix.

## Revisions

| Tag | Branch / PR | What |
|---|---|---|
| `r1` | `revision-1`, PR #1 on the fork | The first push: Bluetooth pill, Inputs pill, the customisable quick panel, Back/Home to the screensaver, and title case. The branch later added the single quick panel for the gear and the long press, every panel row being configurable, and a merge with the fork's `main` (upstream as of 2026-10-24); `r1` doesn't include those. |

## Parked ideas

- **An iCloud shared-album screensaver** as its own `DreamService` app (not inside the launcher). It would read one
  Shared Album with "Public Website" on and keep a small offline cache. Ask the owner for the album link before
  building it.
- **Telemetry debloat**, only if the owner asks.
