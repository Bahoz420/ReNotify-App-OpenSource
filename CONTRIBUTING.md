# Contributing

Thanks for taking the time.

## Bugs

Open an issue with your phone model, Android version, the app version
(Settings, About) and what you did, what you expected and what happened. If
notifications go missing, say which app they came from and whether the phone
restricts ReNotify's battery use.

## Pull requests

- One change per pull request, with a short description of why.
- Build it before you open it: `./gradlew assembleDebug`.
- Match the code around your change: naming, comment style, no new
  dependencies without a good reason.
- Texts the user sees exist in six languages (`app/src/main/res/values*`).
  If you add one, add it to all of them; a rough translation is fine, we
  will tidy it up.
- Nothing that sends notification content anywhere, ever.

## License

By contributing you agree that your contribution is licensed under the GNU
General Public License v3.0, like the rest of the code.
