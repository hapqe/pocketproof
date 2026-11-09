# Pocket Proof

An Android game that teaches linear algebra by letting you build real proofs. Drag axioms and theorems onto the goal, one rewrite at a time, across 106 levels from the vector space axioms up to determinants. Every theorem you prove becomes a tool for the next level.

The app starts in German. English can be selected on the Progress tab.

## Install

Download the APK from the [latest release](../../releases/latest) and open it on an Android 10 or newer device.

## Build

Requires JDK 17 and the Android SDK.

```sh
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/`, and the UI tests save screenshots to `app/build/shots/`.

## Fonts

Math and text containing math are set in Computer Modern: [Latin Modern Math](https://www.gust.org.pl/projects/e-foundry/lm-math) (GUST Font License) and the KaTeX math italic (SIL Open Font License). The license texts are in [`licenses/`](licenses).
