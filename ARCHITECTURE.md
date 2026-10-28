# Architecture

The web version (Next.js, in the parent folder `../src`) is the spec. The
Android app ports the same game, with the same item ids, prices and rules, so a
future cloud sync can share one wallet.

## Packages (com.ankitsilwal.buttonhatesyou)

- **economy/**: pure Kotlin, no Android imports, unit tested on the JVM.
  Ports `src/economy/*.js`: `Catalog` (outfit slots and items, `lookOf`),
  `House` (furniture, wallpapers, floors, rooms and spots, foods, wake
  options), `Needs` (hunger/energy/dirt drift with the clock, moods),
  `Rules` (`applyEvent(wallet, event, now): Wallet?`, the single source of
  truth for every change), `Payouts` (adventure tiers and caps),
  `Achievements`, `Chest`. Events are a sealed class serialised with
  kotlinx.serialization so they can be replayed and synced.
- **data/**: `ProfileStore` keeps the wallet as JSON in app storage, exposes a
  `StateFlow<Wallet>`, and offers actions (`buy`, `equip`, `stock`, `feed`,
  `sleep`, `wake`, `wash`, `play`, `decor`, `recordRun`, `claim`, `openChest`)
  that build an event, run it through `Rules.applyEvent` and save. Offline only
  for now.
- **avatar/**: `Look`/`Face`/`Mood` (shared contract), `Avatar` composable
  and `DrawScope.drawButton(...)` used by both the rooms and the games.
- **games/**: `Game`/`GameSpec`/`GameContext` (shared contract), the
  `GameHost` composable (frame loop, touch, letterboxed 480x800 world), FX
  helpers, synthesized sound (`Sfx`) and `Haptics`, and one file per game.
- **ui/**: `App`, the room pager (Games | Living Room | Kitchen | Bedroom |
  Bathroom), HUD, dock, sheets (wardrobe, decorate, buy food, wake-up), the
  energy gate, achievements, chest, game screens.

## Rules of the road

- Economy numbers and ids come from the web version; when in doubt, read the
  matching file in `../src/economy/`.
- Only `economy.Rules` decides whether a change is allowed.
- Every screen fits a phone without vertical scrolling, except long lists
  (games, shops) which scroll inside themselves.
- Portrait, one-handed, big touch targets (48dp+), haptics on hits.
