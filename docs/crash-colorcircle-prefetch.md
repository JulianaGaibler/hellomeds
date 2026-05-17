# Crash report: NPE in `ColorCircle` during LazyColumn prefetch

## Symptom

Play Console crash, release build:

```
java.lang.NullPointerException: Attempt to invoke virtual method
  'java.lang.Class java.lang.Object.getClass()' on a null object reference
  at me.juliana.hellomeds.ui.components.medication.MedicationInputComponentsKt
       .ColorCircle (MedicationInputComponents.kt:580)
  at me.juliana.hellomeds.ui.components.medication.MedicationInputComponentsKt
       .ColorCircle$lambda$70 (MedicationInputComponents.kt:282)
  at androidx.compose.runtime.RecomposeScopeImpl.compose (RecomposeScopeImpl.java:204)
  ...
  at androidx.compose.runtime.PausedCompositionImpl.resume (PausableComposition.kt:260)
  at androidx.compose.ui.layout.LayoutNodeSubcompositionsState$precomposePaused$2
       .resume (LayoutNodeSubcompositionsState.java:1222)
  at androidx.compose.foundation.lazy.layout.PrefetchHandleProvider$HandleAndRequestImpl
       .performPausableComposition (LazyLayoutPrefetchState.kt:766)
  at androidx.compose.foundation.lazy.layout.PrefetchHandleProvider$HandleAndRequestImpl
       .executeRequest (LazyLayoutPrefetchState.kt:654)
  at androidx.compose.foundation.lazy.layout.AndroidPrefetchScheduler
       .runRequest (PrefetchScheduler.android.kt:181)
  at androidx.compose.foundation.lazy.layout.AndroidPrefetchScheduler
       .run (PrefetchScheduler.android.kt:160)
  at android.os.Handler.handleCallback ...
```

`-dontobfuscate` is set in `androidApp/proguard-rules.pro`, so the class/method
names in the trace are real. R8 shrink+optimize is still enabled in release
(`isMinifyEnabled = true`, `proguard-android-optimize.txt`).

## User-reported reproduction

> "Add medication flow → switch from the templates view to the more complex
> (customize) version → scroll → crash."

That matches the icon step in the add-medication wizard:

- `shared/.../ui/features/medication/steps/MedicationIconStep.kt:54`
  - `customizing == false` → `LazyVerticalGrid` of `MedicationIconPresets`
    (the "templates" view).
  - `customizing == true` → **`LazyColumn`** that hosts the
    `medicationIconCustomizer(...)` items — this is the "more complex" view
    the user describes.

## Call path into the crashing composable

`MedicationIconStep.kt:63` opens a `LazyColumn` and adds:

1. `item { ScreenHeader(...) }`
2. `item { OutlinedButton("Use preset") }`
3. `medicationIconCustomizer(...)` →
   `shared/.../ui/components/medication/MedicationIconCustomizer.kt:22`, which
   adds three lazy items:
   - `stickyHeader { MedicationIconPreview(...) }`
   - `item { MedicationShapePickers(...) }`
   - `item { MedicationColorPickers(...) }` ← **the prefetched item**

`MedicationColorPickers` lives in
`shared/.../ui/components/medication/MedicationInputComponents.kt:767` and
expands to:

```kotlin
FlowRow(...) {
    NullColorCircle(isSelected = color1 == null, onClick = { onColor1Change(null) })
    MedicationColor.all.forEach { color ->
        ColorCircle(
            color = color,
            isSelected = color1 == color,
            onClick = { onColor1Change(color) },
            useBackgroundColor = false,
        )
    }
}
```

`ColorCircle` is at `MedicationInputComponents.kt:559`. The crash line (580) is
inside its only `Box(...)` call:

```kotlin
// 558
@Composable
private fun ColorCircle(
    color: MedicationColor,
    isSelected: Boolean,
    onClick: () -> Unit,
    useBackgroundColor: Boolean = false,
) {
    val composeColor = if (useBackgroundColor) {
        color.toBackgroundColor()
    } else {
        color.toForegroundColor()
    }
    val colorName = stringResource(color.toColorNameRes())

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = colorName
            },
        contentAlignment = Alignment.Center,   // ← line 580
    ) {
        if (isSelected) { ... } else { ... }
    }
}
```

Note: there is a second `ColorCircle` definition for a `LazyRow`
(`ColorSelectionRow` at line 289) but it has no call sites — it can be ignored
for this crash. The live call site is the `FlowRow` inside
`MedicationColorPickers`, hosted inside the outer `LazyColumn`.

## What's happening on the runtime side

The bottom half of the stack tells us this is **paused composition prefetch**
for a lazy layout (Compose Foundation):

- `AndroidPrefetchScheduler.run` is invoked by a `Handler` message on the main
  looper.
- It calls `PrefetchHandleProvider.HandleAndRequestImpl.performPausableComposition`,
  which drives `PausedCompositionImpl.resume` to compose a lazy item in
  bite-sized chunks across multiple frames.
- The crash happens while resuming the composition of the
  `MedicationColorPickers` item — i.e. while pre-composing a `ColorCircle`
  ahead of it being scrolled on-screen.

So the crash is not on the regular composition path; it's specifically the
prefetch/paused path. Users only see it while scrolling, which matches the
report.

## Likely cause of the NPE

`"Attempt to invoke virtual method 'java.lang.Class java.lang.Object.getClass()'
on a null object reference"` is the JVM message produced by the
`Intrinsics.checkNotNull` / `getClass()` style null check that Kotlin (and the
Compose compiler) inject for non-null parameters and for some captured values.

In `ColorCircle`, the candidates for "non-null thing that is null" are
parameters and the captured-locals used in the restart lambda
(`ColorCircle$lambda$70`):

- `color: MedicationColor`
- `onClick: () -> Unit`
- `composeColor: Color` (value class, can be ruled out — not nullable + inlined to long)
- `colorName: String`

Booleans and `Color` (value class over `Long`) can't produce this error, so the
suspect is one of: `color`, `onClick`, or `colorName`.

The line number (580) points at the `Box(...)` argument list, but R8 with
`proguard-android-optimize.txt` reorders/inlines bytecode and the
`LineNumberTable` it emits for synthetic restart lambdas does not always map
cleanly back to the source line where the null is actually dereferenced. The
restart lambda referenced as `ColorCircle$lambda$70` re-invokes `ColorCircle`'s
body on recompose; it captures the parameters and re-applies them. If any
captured parameter is null, the re-entry into the body throws here before any
useful work runs.

### Two plausible root causes

1. **Compose Foundation paused-composition prefetch bug (most likely).**
   The project uses Compose Multiplatform **1.10.0-beta02**
   (`gradle/libs.versions.toml:18`). Paused composition for lazy-layout prefetch
   is a relatively new code path and has had several fixes in the
   1.9.x/1.10.x stream. The trace shows the failure happens specifically when
   `PausedCompositionImpl.resume` re-enters a restart scope; if the saved scope
   state is corrupted or a captured parameter slot is missing on resume, the
   restart lambda receives `null` where the composable expects a non-null and
   throws exactly this NPE shape.

   Supporting signals: regular composition of the same screen works fine
   (otherwise this would crash on first open, not on scroll); we only see this
   on `runRequest` → `performPausableComposition` paths.

2. **`MedicationColor` data-object initialization stripped/reordered by R8
   (less likely but possible).**
   `core/designsystem/src/commonMain/kotlin/.../MedicationColor.kt` is a
   `sealed class` whose subclasses are `data object`s, collected into
   `MedicationColor.all`. `proguard-rules.pro` has no keep rule for
   `me.juliana.hellomeds.ui.theme.**`. R8 with `-allowaccessmodification` and
   `proguard-android-optimize.txt` has historically had edge cases with sealed
   classes / data objects where the `INSTANCE` field can be inlined in a way
   that interacts badly with paused composition's re-entry. If any element of
   `MedicationColor.all` resolved to `null` on resume, `color` would be null in
   the restart lambda and would hit exactly this error.

### Why this presents only on scroll

Before scrolling, `MedicationColorPickers` is composed on the main thread via
the normal `LazyColumn` flow. When the user scrolls, `LazyColumn` schedules a
prefetch for items about to enter the viewport — those items are composed
through `PausedCompositionImpl` and split across handler messages
(`AndroidPrefetchScheduler`). The crash is exclusive to that second path.

## Relevant files

| Path | Role |
|---|---|
| `shared/src/commonMain/kotlin/me/juliana/hellomeds/ui/features/medication/steps/MedicationIconStep.kt:63` | Outer `LazyColumn` (the "customize" view) |
| `shared/src/commonMain/kotlin/me/juliana/hellomeds/ui/components/medication/MedicationIconCustomizer.kt:22` | Adds the `MedicationColorPickers` item to the LazyColumn |
| `shared/src/commonMain/kotlin/me/juliana/hellomeds/ui/components/medication/MedicationInputComponents.kt:767` | `MedicationColorPickers` — iterates `MedicationColor.all`, calls `ColorCircle` |
| `shared/src/commonMain/kotlin/me/juliana/hellomeds/ui/components/medication/MedicationInputComponents.kt:559` | `ColorCircle` — crash site (line 580 inside its `Box`) |
| `core/designsystem/src/commonMain/kotlin/me/juliana/hellomeds/ui/theme/MedicationColor.kt` | Sealed class + `MedicationColor.all` companion list |
| `androidApp/proguard-rules.pro` | No keep rule for `me.juliana.hellomeds.ui.theme.**` |
| `gradle/libs.versions.toml:18` | `composeMultiplatform = "1.10.0-beta02"` |

## Suggested next steps (for the engineer)

In rough order of cost-vs-confidence:

1. **Try to reproduce locally on a release/profile build**, scrolling the
   customize view (templates → "Customize" button → scroll). Debug builds skip
   R8 and use a different prefetch heuristic, so you likely need
   `assembleGoogleRelease` or the `profile` build type.
2. **Bump Compose Multiplatform** off the beta to the latest 1.10.x release/RC
   and re-test. The `PausedCompositionImpl` / lazy prefetch interaction is
   actively being fixed upstream; this may already be fixed.
3. **Disable prefetch for this specific list as a probe** to confirm the path.
   `LazyColumn(prefetchStrategy = remember { LazyListPrefetchStrategy(0) })` (or
   equivalent for the CMP version in use) — if the crash goes away, root cause
   is confirmed to be in paused composition / prefetch.
4. **Mitigations that are cheap and probably worth shipping regardless:**
   - Hoist the `stringResource(color.toColorNameRes())` read out of `ColorCircle`
     and pass `colorName: String` in from the caller, so the per-item composable
     captures fewer composition locals across pause/resume boundaries.
   - Convert `MedicationColor` from a `sealed class` with `data object` variants
     to an `enum class`. Stable, trivially `@Immutable`, no R8 surprises, and
     `.entries` replaces `.all`. (Mechanical change across ~15-20 call sites.)
   - Add `-keep class me.juliana.hellomeds.ui.theme.** { *; }` to
     `proguard-rules.pro` as defense-in-depth if option 4b is deferred.
5. If a CMP upgrade is not viable on closed-beta and the crash needs to be
   blunted before launch, **replace the outer `LazyColumn` in
   `MedicationIconStep` with a regular scrollable `Column`** for the customize
   view. The customizer only has ~5 items total (header, "use preset" button,
   sticky preview, shape pickers, color pickers), so the lazy layout buys very
   little and removing it removes the prefetch path entirely.

## What we know we *don't* know

- We have one stack trace and no device/OS breakdown. If the Play Console shows
  this concentrated on a specific Android version or OEM, that would be useful
  signal (e.g. handler scheduling differences on 14+ vs 15+).
- We have not reproduced locally yet — the steps in §2 are based on the user
  report, not a confirmed repro.
- The exact captured-local that is null at line 580 can't be determined from
  the optimized bytecode without either repro + breakpoint, or a non-optimized
  release build (drop `proguard-android-optimize.txt`, keep
  `proguard-android.txt`, re-ship-to-internal-track, wait for it to recur).
