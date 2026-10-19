# RMG Khata — RMG Traders

Android starter app for an Urdu/right-to-left tailoring khata.

## Features
- Kotlin + Jetpack Compose interface
- Customer name, order details, total and advance
- Automatic remaining-balance calculation
- Offline local bill storage
- Paired Bluetooth Classic thermal-printer selection and basic ESC/POS receipt output
- RMG Traders receipt branding

## Build
Open this repository in Android Studio with Android SDK 35 installed, sync Gradle, and run the `app` configuration on a paired Android device.

## Bluetooth
Pair the printer in Android system settings first. This starter sends basic text over the Bluetooth Classic SPP UUID. Printer firmware and Urdu glyph support vary, so test with the exact printer before business use.

## Data
Records are stored on the device. There is no cloud backup yet. Add export/restore and test recovery before relying on it as the only copy of business records.

**Status:** starter source project; not a signed APK and not yet production-tested.
