# Android App

Native Android version of the attendance tracker, implemented separately from the web app in this folder.

## Stack

- Kotlin
- Jetpack Compose
- SharedPreferences for local persistence

## Notes

- This app does not modify the existing web version in the repository root.
- Current persistence key inside Android shared preferences: `student-attendance-tracker-native-v1`
- The project now includes a Gradle wrapper pinned to `8.10.2`.
- If Android Studio still fails to sync, set Gradle JDK to an installed JDK 17 or JDK 21 instead of JDK 25.
