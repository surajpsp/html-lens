# HTML Lens

Open any HTML file — and see exactly what it does. HTML, MHTML/MHT, ZIP and folders, EML and Safari
web archives open with their images and styles intact, inside a sandbox, with a phishing check.
Everything runs on the user's device; there is no backend.

- Website & web app: https://htmllens.silentdev.online (app at `/app/`)
- Desktop: macOS and Windows installers on the [Releases](../../releases) page

## Project layout

| Path | What it is |
|---|---|
| `core/` | Kotlin Multiplatform engine: ZIP/MHTML/EML/webarchive parsing, threat scanner, packer, formatter, search, sandbox CSP rules. Tests in `core/src/jvmTest`. |
| `app/` | Compose Multiplatform UI for desktop (JVM + JavaFX WebView) and web (Kotlin/Wasm). |
| `web-assets/` | Browser-side bridge script and CodeMirror editor, bundled with esbuild. |
| `sandbox/` | Static files for the isolated sandbox origin (service worker that serves opened pages). |
| `site/` | Dependency-free static site generator for the SEO pages. |
| `deploy/` | Cloudflare Workers (static assets) configs and deploy script. |

## Build

Requires JDK 21+ to run Gradle; the desktop app runs and packages on Temurin 25 (provisioned automatically).

```bash
./gradlew :core:jvmTest                     # engine tests
./gradlew :app:run                          # desktop app
./gradlew :app:wasmJsBrowserDistribution    # web app
./gradlew :app:packageReleaseDmg            # macOS installer (packageReleaseMsi on Windows)
```

After changing `web-assets/src`, run `npm install && node build.mjs` in `web-assets/`.

## Deploy the website

```bash
npx wrangler login
node deploy/deploy.mjs
```

Set `DOWNLOAD_BASE=https://github.com/<owner>/html-lens/releases/latest/download` so the download page links to the latest release.

## Release the desktop apps

Push a tag such as `v1.0.1`. `.github/workflows/desktop-release.yml` builds the Apple Silicon and Intel `.dmg`
and the Windows `.msi`, and attaches them to a GitHub Release.

## License

See [LICENSE](LICENSE).
