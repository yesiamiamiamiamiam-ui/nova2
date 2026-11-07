# NOVA Gallery

A basic native Android gallery app with Photos, Albums, and Videos views, filename/folder search, thumbnail grid, and full-screen preview.

## Build using only an Android phone (GitHub Actions)

1. Open https://github.com in your phone browser and sign in or create an account.
2. Create a **New repository**. Name it `NOVA-Gallery`, set it to **Public** or **Private**, and create it.
3. In the repository, choose **Add file → Upload files**. Extract this ZIP on your phone first, then upload the *contents* of the `NOVA_Gallery_Android` folder (including the hidden `.github` folder if your file manager shows it). Commit the upload to the `main` branch.
4. Open the repository's **Actions** tab. The workflow named **Build NOVA Gallery APK** should start automatically. If it does not, open it and tap **Run workflow**.
5. When the run finishes successfully, open that run, scroll to **Artifacts**, and download `NOVA-Gallery-debug-APK`.
6. Open the downloaded artifact ZIP and install `app-debug.apk`. Android may ask you to allow your browser or file manager to install apps.

The APK is unsigned for Play Store distribution but is suitable for testing on your own Android phone. The source project is built in GitHub's cloud; no PC or Android Studio is needed.
