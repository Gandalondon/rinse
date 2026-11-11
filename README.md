# Rinse

A one-tap Android app for a Bosch dishwasher on Home Connect. Every night it starts Eco 50; that is most of it.

- **App:** one screen. Black at night, White when the dishes are clean, Brick when it cannot start (door open, remote start off, dishwasher offline).
- **Widget:** the same states on the home screen. Tap it to start tonight's wash.
- **Quick settings tile:** pull down, tap Rinse, done.
- **Notification:** a silent Clean notification when the wash finishes, waiting for you in the morning.
- **Tonight:** the cog picks a different programme for one night. Back to Eco 50 the next day.

## Install on the Pixel

1. On the Pixel, open the latest release of this repo and tap `Rinse.apk`.
2. Allow Chrome to install unknown apps if asked, then Install. If Play Protect warns, choose Install anyway.

Later builds install over the top and keep the sign-in.

## Link the dishwasher (once)

Open Rinse and tap Sign in. The Bosch page opens: sign in with the Bosch app account and approve Rinse. Back in Rinse, it finds the dishwasher by itself (the oven is ignored).

## How it works

- Home Connect API with the OAuth Device Flow, scopes `IdentifyAppliance Dishwasher`.
- Start: checks the appliance is online, the door is closed and remote start is allowed, switches it on if needed, then sets `Dishcare.Dishwasher.Program.Eco50` as the active programme.
- While a wash runs, a background check (WorkManager) looks in every half hour and just after the expected finish, to update the widget and post Clean.
- The Home Connect client ID and secret are the repo secrets `HC_CLIENT_ID` and `HC_CLIENT_SECRET`, injected at build time. Signing uses `SIGNING_KEYSTORE_B64` and `SIGNING_PASSWORD`, as in Hugin.
- Bosch limits the API to about 1,000 calls a day; Rinse polls only while the app is open or a wash is running.

## Not yet

Salt, rinse aid and filter reminders. Home Connect only sends these as live events, so they need a listening connection; next step.
