# Localization Files

Languages: **English (en)**, **German (de)**

## Shared UI Strings (CMP)

All shared screens, dialogs, actions, and notification content.

- `shared/src/commonMain/composeResources/values/strings.xml`
- `shared/src/commonMain/composeResources/values/plurals.xml`
- `shared/src/commonMain/composeResources/values-*/strings.xml`
- `shared/src/commonMain/composeResources/values-*/plurals.xml`

Accessed via `Res.string.*` / `Res.plurals.*` from Compose Multiplatform.

## iOS Main App

AlarmKit button labels, intent titles, and system permission descriptions.

- `iosApp/HelloMeds/en.lproj/Localizable.strings`
- `iosApp/HelloMeds/en.lproj/InfoPlist.strings`
- `iosApp/HelloMeds/*.lproj/Localizable.strings`
- `iosApp/HelloMeds/*.lproj/InfoPlist.strings`

Accessed via `String(localized:)` and `LocalizedStringResource` in Swift.

## iOS Widget Extension (AlarmLiveActivity)

Strings rendered in the Dynamic Island expanded view (separate bundle from main app).

- `iosApp/AlarmLiveActivity/en.lproj/Localizable.strings`
- `iosApp/AlarmLiveActivity/*.lproj/Localizable.strings`

## Android

- `androidApp/src/main/res/values/strings.xml`

All other Android strings come from the shared CMP resources above.
