# Localization

## Shared UI strings (CMP)

All shared screens, dialogs, actions, and notification content.

- `shared/src/commonMain/composeResources/values/strings.xml`
- `shared/src/commonMain/composeResources/values/plurals.xml`
- `shared/src/commonMain/composeResources/values-*/strings.xml`
- `shared/src/commonMain/composeResources/values-*/plurals.xml`

Accessed via `Res.string.*` and `Res.plurals.*`.

## iOS main app

AlarmKit button labels, intent titles, system permission descriptions.

- `iosApp/HelloMeds/en.lproj/Localizable.strings`
- `iosApp/HelloMeds/en.lproj/InfoPlist.strings`
- `iosApp/HelloMeds/*.lproj/Localizable.strings`
- `iosApp/HelloMeds/*.lproj/InfoPlist.strings`

Accessed via `String(localized:)` and `LocalizedStringResource`.

## iOS widget extension (AlarmLiveActivity)

Strings in the expanded view. Separate bundle from the
main app.

- `iosApp/AlarmLiveActivity/en.lproj/Localizable.strings`
- `iosApp/AlarmLiveActivity/*.lproj/Localizable.strings`

## Android

- `androidApp/src/main/res/values/strings.xml`

All other Android strings come from the shared CMP resources above.
