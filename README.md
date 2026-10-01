# Chat Translate

Build a complete Android APK-ready personal floating multilingual translator app.

IMPORTANT PRODUCT REQUIREMENTS

This is a personal-use Android app. It must work as a floating translator over other apps such as WhatsApp, Telegram, Instagram, etc.

The most important UX requirement is:

The user must NOT have to switch away from the app they are currently using.

When the floating bubble is tapped, open the translator directly as a compact bottom sheet/overlay on top of the current app. Do not open a separate full-screen translator app unless absolutely required by Android.

The bottom sheet should cover approximately the lower half of the screen, while the WhatsApp/other app remains visible behind it.

Do NOT add translation history.

---

1. VISUAL DESIGN

Create a premium, modern, minimal dark UI.

Design characteristics:

- Dark charcoal/black background
- Subtle purple/indigo accent
- Smooth rounded corners
- Soft shadows
- Clean modern typography
- Spacious but compact
- Premium Android-native feel
- No unnecessary gradients or excessive decoration
- No advertisements
- No branding badges
- No clutter

The floating bubble can have a polished modern appearance, but the exact bubble design is flexible.

The bottom sheet is the most important visual component.

It should feel like a native Android bottom sheet rather than a web page.

---

2. FLOATING BUBBLE

Implement a real Android floating bubble using the appropriate native Android overlay mechanism.

The bubble must:

- Stay above other apps after permission is granted
- Be draggable
- Be tappable
- Open the translator without requiring the user to leave the current app
- Have a close/remove action
- Remember its approximate position
- Avoid blocking important areas of the screen

Request the required Android "Display over other apps" / overlay permission through the proper Android permission flow.

Do not request unnecessary permissions.

---

3. BOTTOM SHEET BEHAVIOR

When the user taps the floating bubble:

Open a bottom sheet directly over the current application.

The sheet should:

- Start from the bottom of the screen
- Cover roughly 45–60% of the screen
- Have rounded top corners
- Have a drag handle
- Animate smoothly upward
- Keep the underlying app visible
- Not force the user to switch apps
- Be dismissible by swiping down or tapping close
- Be compact enough for quick messaging

The user should be able to see the conversation behind the translator while writing the translation.

---

4. MAIN TRANSLATOR UI

Inside the bottom sheet:

Top bar:

- App name
- Small close button

Language row:

FROM LANGUAGE
[ Hinglish ▼ ]

Swap button:
[ ⇄ ]

TO LANGUAGE
[ English ▼ ]

The language selectors must be easy to tap.

Below that:

Large rounded text input area:

"Type or paste your message..."

Include:

- Character counter if appropriate
- Clear button
- Keyboard-friendly layout

Then:

Tone selector:

[ Natural ▼ ]

Available styles:

- Natural
- Casual
- Gen Z
- Slang
- Match Original
- Friendly
- Flirty
- Formal

Do NOT force slang into every translation. Gen Z wording, abbreviations and slang should only be used when natural for the context.

Then a prominent:

[ Translate ]

button.

---

5. TRANSLATION RESULT

After translation, display a clean result card inside the same bottom sheet.

Example:

TRANSLATION

"bro why didn't u come yesterday lol"

Provide a prominent:

[ Copy ]

button.

Copy must place the result directly into the Android clipboard.

Show a small confirmation such as:

"Copied"

Do not automatically switch to another application.

The user can then dismiss the sheet and paste the message into WhatsApp/Instagram/etc.

---

6. NATURAL HUMAN TRANSLATION

The translation must NOT behave like a literal dictionary translator.

It should preserve:

- Meaning
- Context
- Tone
- Emotion
- Informality
- Slang
- Abbreviations
- Swearing/strong language when present
- Relationship/context where inferable from the text

For example, if the original contains casual Gen-Z wording or profanity, do not unnecessarily sanitize it into formal language.

The AI should produce language that sounds like something a real person would actually type in a chat.

Do not invent additional meaning, emotions, flirting, insults or context that was not present in the original.

---

7. HINGLISH SUPPORT

Hinglish must be treated as a first-class input/output language.

Examples:

Hinglish:
"yaar tu kal kyun nhi aaya tha"

Natural English:
"bro why didn't u come yesterday"

Hinglish:
"mujhe samajh nhi aa raha woh kya keh rahi hai"

Natural English:
"I don't get what she's saying"

The system should understand Roman Urdu/Hindi/Hinglish rather than requiring Devanagari or Urdu script.

---

8. MULTILINGUAL SUPPORT

Support at least these languages:

- English
- Hinglish
- Hindi
- Urdu
- Arabic
- Chinese
- Japanese
- Korean
- Russian
- Spanish
- Portuguese
- French
- German
- Swiss German
- Italian
- Turkish
- Dutch
- Swedish
- Norwegian
- Danish
- Polish
- Greek
- Indonesian
- Malay
- Bengali
- Punjabi
- Persian
- Thai
- Vietnamese
- Ukrainian

The language picker must have:

- Search
- Scrollable language list
- Clearly displayed language names
- Easy selection
- Recent/default selections only if useful

Do not make the picker visually cluttered.

---

9. ROMANIZED / NATIVE OUTPUT

Add a setting called:

Translation Output

Options:

1. Native
2. Romanized
3. Both

This setting controls how languages using non-Latin scripts are displayed.

Examples:

Korean:

Native:
안녕하세요

Romanized:
Annyeonghaseyo

Both:
안녕하세요
Annyeonghaseyo

Japanese:

Native:
こんにちは

Romanized:
Konnichiwa

Chinese:

Native:
你好

Romanized:
Nǐ hǎo

Use the appropriate standard romanization system for each language, such as Pinyin for Mandarin and appropriate romanization for Korean/Japanese.

Do NOT simply transliterate every language using an arbitrary system.

For Latin-script languages, Romanized mode should naturally behave as normal text.

---

10. ENGLISH → HINGLISH

Reverse translation must work just as well.

Example:

English:
"ngl I was kinda pissed when u ghosted me"

Hinglish:
"ngl jab tu mujhe ghost karke chala gaya tha na toh mujhe thoda gussa aaya tha"

The translator should preserve casual tone and internet language where appropriate.

---

11. SETTINGS SCREEN

Create a clean settings screen.

Sections:

Translation

Default source language

Default target language

Translation Output:

- Native
- Romanized
- Both

Default Tone:

- Natural
- Casual
- Gen Z
- Slang
- Match Original
- Friendly
- Flirty
- Formal

Floating Bubble

- Enable/disable bubble
- Bubble position
- Bubble size if practical

Behavior

- Auto-focus input when translator opens
- Close translator after copying
- Remember selected languages

Do NOT add translation history.

Do NOT add unnecessary settings.

---

12. PERMISSIONS

Implement all permissions required for the actual Android functionality.

The app should correctly handle:

- Overlay / draw-over-other-apps permission
- Any other permission genuinely required by the implementation

Ask for permissions only when needed.

Provide a clean permission explanation screen.

If permission is denied, show a clear way to open the appropriate Android settings page.

Do not use Accessibility Service unless it is genuinely necessary.

Do not request contacts, SMS, microphone, location, camera, storage or other unrelated permissions.

---

13. ANDROID IMPLEMENTATION

This must be a real Android-capable implementation, not just a browser UI.

Create/configure all required Android project files, native services, components, permissions, Gradle configuration, manifests and bridge/native integration needed for the APK.

Use a stable Android-compatible architecture.

If the existing Lovable project uses Capacitor, preserve the existing architecture where appropriate and add the required native Android implementation rather than replacing working project infrastructure unnecessarily.

Implement the floating overlay as a native Android component/service.

Make sure lifecycle handling is correct so the floating bubble does not leak or crash.

Handle Android versions and permission differences appropriately.

---

14. AI TRANSLATION INTEGRATION

Use Lovable's available AI integration/backend capabilities where appropriate instead of requiring the user to manually configure a separate external API.

Keep API credentials/secrets out of the client APK.

Do not hardcode private API keys in frontend/native source code.

Create the necessary backend/server-side integration and environment configuration if required by the available Lovable AI infrastructure.

The translation request should send:

- Source language
- Target language
- Input text
- Selected tone
- Romanized/native output preference

The AI response should return clean translation text suitable for immediate display and copying.

Add sensible error handling for:

- Network failure
- Empty input
- AI/service failure
- Rate limits
- Invalid response

Show a simple user-friendly error instead of crashing.

---

15. PERFORMANCE

The translator must feel instant and lightweight.

- Smooth bottom-sheet animation
- No unnecessary loading screens
- Disable Translate when input is empty
- Show a small loading state while AI translation is processing
- Prevent duplicate requests from repeated taps
- Keep the overlay responsive
- Avoid memory leaks
- Handle rotation/configuration changes appropriately

---

16. GITHUB / APK BUILD

Keep the project fully compatible with GitHub synchronization.

Create/update all files required for building the Android APK through GitHub Actions.

Include:

- Android project configuration
- Gradle configuration
- Gradle wrapper if required
- Capacitor/native configuration if applicable
- AndroidManifest configuration
- Required native source files
- Required assets
- GitHub Actions workflow for Android build

The GitHub Actions workflow should build a debug APK successfully.

Place the generated APK in a GitHub Actions artifact.

Do not leave placeholder files or pseudo-code.

---

17. QUALITY REQUIREMENT

Before considering the implementation complete:

- Check the project for build errors
- Check Android configuration
- Check manifest permissions
- Check native bridge integration
- Check overlay service lifecycle
- Check bottom-sheet behavior
- Check language picker
- Check translation result
- Check Copy button
- Check settings
- Check GitHub Actions configuration

Fix implementation issues rather than merely describing them.

The final result should be a complete, buildable Android application rather than a design prototype.

IMPORTANT

Prioritize the actual user experience:

WhatsApp/chat stays open → floating bubble → tap → bottom sheet appears over the chat → type/paste → translate → copy → dismiss.

The user should not have to manually switch between applications just to translate a message.

Do not add translation history.

Do not overcomplicate the UI.

Make the interface feel like a polished personal Android utility.

This project was built with [Lovable](https://lovable.dev).

## Build with Lovable

Continue developing this project in the [Lovable editor](https://lovable.dev/projects/3e310989-5d32-407f-8925-94df8ec83889).

- **Ship faster**: describe what you want to build and Lovable handles the code.
- **Stay in sync**: every change made in Lovable is committed straight to this repository.
- **Full ownership**: this code is yours. Push to `main` on GitHub and your changes sync back into Lovable, ready for your next prompt.

## Development

Prefer working locally? You need Node.js and npm — [install with nvm](https://github.com/nvm-sh/nvm#installing-and-updating).

```sh
git clone <this-repository-url>
cd <repository-name>
npm i
npm run dev
```
