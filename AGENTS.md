<!-- LOVABLE:BEGIN -->
> [!IMPORTANT]
> This project is connected to [Lovable](https://lovable.dev). Avoid rewriting
> published git history — force pushing, or rebasing/amending/squashing commits
> that are already pushed — as it rewrites history on Lovable's side and the
> user will likely lose their project history.
>
> Commits you push to the connected branch sync back to Lovable and show up in
> the editor, so keep the branch in a working state.
<!-- LOVABLE:END -->

# Architecture rules
- Android app in `android/` is fully native Kotlin with no AndroidX deps — overlay bubble/sheet use WindowManager in a foreground service for reliability and small APK.
- Translation runs only in the web backend route `/api/public/translate` — keeps AI keys out of the APK; the APK reads its URL from gradle property `translateUrl`.
- Language/tone lists exist in both `src/lib/languages.ts` and Android `Core.kt` — keep them in sync, the server validates against them.
- APK is built by `.github/workflows/android.yml` using a setup-gradle pinned Gradle (no wrapper jar committed).
