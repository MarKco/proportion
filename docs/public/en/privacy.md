# Privacy

ProPortion is an offline app. This document says exactly what it does and does not do with your
data.

- **No account.** There is no sign-up, no login, and no user profile.
- **No synchronisation and no server.** The app has no backend of its own: there is no server it
  talks to, so there is nowhere for your data to be sent.
- **No data collection and no usage analytics.** The app includes no analytics, tracking or
  telemetry library of any kind.
- **No network access beyond what Android itself requires.** The app does not open network
  connections of its own.
- **All data stays on the device**, in a single local Room database: recipes, ingredients, tags,
  saved scalings, the shopping list and preferences.
- **A `.proportion` file is written only when you explicitly ask for it** — by sharing a recipe,
  exporting the library, or making a backup. The app never writes or sends anything on its own.
- **You choose where a backup goes.** When you make a backup, it is Android itself (through the
  system picker for choosing where to save a file) that decides where the file goes: the app has
  no path or hidden folder of its own, and it does not request storage access permissions.

In short: unless you share or back up something yourself, no data from your library ever leaves
the phone.
