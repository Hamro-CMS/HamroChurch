# Hamro Church

![Hamro Church logo](website/assets/images/logo.svg)

Hamro Church is bilingual church presentation software built for Nepali and English worship services. It supports scripture projection, hymn and lyric slides, media playback, stage display, remote control, and multi-output presentation workflows.

---

## Open Source Credits & GPL Compliance

HamroChurch is a specialized, modified fork of **FreeShow**. We gratefully acknowledge and respect the original creators for building the core presentation engine that made this localized project possible.

* **Original Project (Upstream):**
  * Project Name: FreeShow
  * Website: [freeshow.app](https://freeshow.app/)
  * Source Code: [GitHub - ChurchApps/FreeShow](https://github.com/ChurchApps/FreeShow)
  * Original Author/Copyright: Kristoffer Vassbø / ChurchApps

* **Modified Project (This Repository):**
  * Project Name: HamroChurch
  * Organization Website: [hamrocms.com](https://hamrocms.com)
  * Key Modifications: Full native Nepali user interface language, localized Nepali Calendar, and Nepali Bible modules.
  * Data & Lyrics Acknowledgement: Special thanks to [Nepali Christian Songs](https://www.nepalichristiansongs.com) for providing the public lyrics database utilized for our Nepali/Bhutanese Bhajan workflows.

* **License Statement:**
  * This software is distributed under the **GNU General Public License v3.0 (GPL-3.0)**.
  * HamroChurch includes modifications to FreeShow and remains 100% open-source. All custom additions and derivative works in this repository are distributed under the exact same GPL-3.0 license.

We sincerely thank the FreeShow maintainers, ChurchApps, and the wider community for sharing their foundational work!

---

## Links

- Website: https://hamrocms.com
- Documentation: https://hamrocms.com/docs/
- FAQ: https://hamrocms.com/faq
- GitHub Repository: https://github.com/Hamro-CMS/HamroChurch
- Releases: https://github.com/Hamro-CMS/HamroChurch/releases
- Issue Tracker: https://github.com/Hamro-CMS/HamroChurch/issues

## Downloads

Current release downloads will be published through GitHub Releases:

- Windows installer: https://github.com/Hamro-CMS/HamroChurch/releases
- macOS installer: https://github.com/Hamro-CMS/HamroChurch/releases
- Linux installer: https://github.com/Hamro-CMS/HamroChurch/releases

## Core Capabilities

- Bilingual Nepali and English interface support
- Nepali Bible import and localized scripture presentation
- Nepali hymn workflows and slide generation
- Stage display, controller, remote, and output-stream views
- Media playback and multi-output presentation control
- Nepali number and date formatting in supported UI surfaces

## Development Setup

1. Install Node.js.
2. Install Python 3.12 and `setuptools`.
3. On Windows, install Visual Studio Build Tools with C++ desktop components and the Windows SDK.
4. On Linux, install `libfontconfig1-dev`.
5. From the repository root, run `npm install`.

## Build Commands

- `npm start`
  Starts the desktop app in development mode.

- `npm run build:frontend:prod`
  Builds the frontend bundle into `public/build`.

- `npm run build:servers:prod`
  Builds the remote, stage, controller, and output-stream bundles into `build/electron`.

- `npm run build:electron:prod`
  Compiles the Electron TypeScript code.

- `npm run build`
  Runs the full production build chain.

- `npm run test:svelte`
  Runs `svelte-check` for Svelte and TypeScript validation.

- `npm run release`
  Builds and publishes the current platform release using `electron-builder` and the GitHub release target.

- `npm run pack`
  Creates an unpacked desktop build for manual packaging verification.

## Website Bundle

The production-ready static website for `hamrocms.com` is stored in the `website/` folder.

- Homepage: `website/index.html`
- Download page: `website/download/index.html`
- Docs: `website/docs/**`
- FAQ: `website/faq/index.html`
- Deployment notes: `website/DEPLOYMENT.md`

## Validation

Recommended validation before release:

1. Run `npm run test:svelte`.
2. Run `npm run build`.
3. Verify branding, scripture, hymn, stage, remote, and output flows manually.

## License

Hamro Church is licensed under the GNU General Public License v3.0. See the `LICENSE` file in this repository for full details.
