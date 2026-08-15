# Nova — Building entirely from your phone (no computer needed)

Android Studio itself can't run on a phone — but you can build the APK in the
cloud using GitHub, entirely from your phone's browser, for free.

## Part A — Get the code onto GitHub (~10 min)

1. Open your phone browser, go to **github.com**, create a free account if you don't have one
2. Tap the **+** icon (top right) → **New repository**
   - Name it `nova-app` (or anything)
   - Keep it **Private** if you want (your choice)
   - Tap **Create repository**
3. On the new repo page, tap **"uploading an existing file"** (a link in the middle of the empty repo page)
4. From your phone's file browser, select the `NovaApp` folder **zipped as one file** — but GitHub's uploader doesn't unzip automatically, so instead:
   - Extract the zip on your phone first (most Android file manager apps can "Extract" a zip in place — long-press the zip → Extract)
   - Then use GitHub's upload page and select **all the extracted files/folders** — modern mobile browsers (Chrome) let you multi-select files including nested folders when you tap "choose files"
   - If your browser won't let you select whole folders: install the free **"Working Copy"-style app "GitHub" itself is not needed** — instead use a file manager app like **Solid Explorer** or **Files by Google**, which can upload folder structures more reliably, OR ask me and I'll walk you through the alternate "create file one by one" method for the ~25 files
5. Commit the upload (there's a "Commit changes" button after selecting files)

## Part B — Trigger the cloud build

1. In your repo, tap the **Actions** tab
2. You should see **"Build Nova APK"** listed — tap it
3. Tap **"Run workflow"** → **Run workflow** (confirm)
4. Wait 3-6 minutes (refresh the page) — a green checkmark means it succeeded
5. Tap into the completed run → scroll down to **Artifacts** → tap **nova-debug-apk** to download it
   (this downloads a `.zip` containing the APK — extract it to get `app-debug.apk`)

## Part C — Install the APK on your phone

1. Open the downloaded `app-debug.apk` file from your phone's Downloads
2. Android will ask to allow installing from this source (your browser or file manager) — allow it once
3. Tap **Install**

## Part D — Load Nova's AI brain (no adb needed)

1. In your phone browser, go to **huggingface.co/litert-community/gemma-4-E2B-it-litert-lm**
2. Sign in / create a free Hugging Face account if prompted (needed to accept Google's model license)
3. Download the `.task` file from the Files tab (~2-3GB — use WiFi)
4. Open the **Nova app** → tap **"Load AI Model"** → a file picker opens → navigate to your Downloads folder → select the `.task` file you just downloaded
5. Nova copies it into her own storage and loads it — this takes a minute for a file this size, watch the status text

After this one-time setup, Nova works fully offline for her core thinking.

## If a build fails on GitHub Actions

Tap into the failed run → tap the red ✕ step → copy the error text shown →
send it to me. Same idea as before — I read the real error and tell you the fix,
just no Android Studio needed on your end.

## Re-building after future updates

Whenever I give you new code, repeat Part A (upload the changed/new files to
the same repo — GitHub lets you overwrite existing files) then Part B again.
