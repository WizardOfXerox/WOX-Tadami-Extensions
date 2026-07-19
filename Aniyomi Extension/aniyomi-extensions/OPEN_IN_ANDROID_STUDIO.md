# Build Aniwave extension in Android Studio

## 1. Open the project

1. Open **Android Studio**.
2. **File → Open**.
3. Select this folder: **`Aniyomi Extension\aniyomi-extensions`** (the one that contains `build.gradle.kts`, `settings.gradle.kts`, and `src`).
4. Click **OK**. Let Android Studio sync Gradle (may take a few minutes and require internet for JitPack dependencies).

## 2. Build the Aniwave extension

- **Option A – Toolbar:** Open the **Build** menu → **Select Build Variant** and ensure a debug variant is selected. Then **Build → Build Bundle(s) / APK(s) → Build APK(s)**. If prompted, choose the **aniwave** or **src:all:aniwave** configuration.
- **Option B – Gradle panel:** In the right-hand **Gradle** panel, expand **aniyomi-extensions → src → all → aniwave → Tasks → build**, then double‑click **assembleDebug**.

The APK will be generated at:

`aniyomi-extensions\src\all\aniwave\build\outputs\apk\debug\aniyomi-src.all.aniwave-v14.1.apk`

(Exact name may vary slightly.)

## 3. Install on device

- Copy the APK to your phone and install it, or use **Run** with an emulator/device connected.
- Open **Aniyomi** → **Anime** → **Sources** and enable **Aniwave**.

## If Gradle sync or build fails

- **“SDK location not found”**  
  `local.properties` is already set to your Android SDK. If you moved the project or the SDK, edit `local.properties` and set `sdk.dir` to your SDK path (e.g. `C:/Users/XIA/AppData/Local/Android/Sdk`).

- **“Could not find com.github.inorichi.injekt:injekt-core”**  
  The project uses JitPack for this dependency. Try:
  1. **Sync in Android Studio:** **File → Sync Project with Gradle Files** (often resolves it).
  2. Ensure you have a stable internet connection and try again later (JitPack can be slow or return errors sometimes).
  3. **File → Invalidate Caches / Restart** → **Invalidate and Restart**, then sync and build again.
  4. From a terminal in `aniyomi-extensions`:  
     `gradlew.bat :src:all:aniwave:assembleDebug --refresh-dependencies`

- **Java version**  
  The project expects Java 17. In **File → Settings → Build, Execution, Deployment → Build Tools → Gradle**, set **Gradle JDK** to the embedded JBR (e.g. **jbr-17** or **Android Studio default**).
