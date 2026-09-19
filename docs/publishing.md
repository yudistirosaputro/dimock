# Publishing (Wave 5)

One tag publishes everything: `git tag v0.1.0 && git push --tags` runs `.github/workflows/release.yml`, which publishes the four AARs to Maven Central, `@dimock/core`, `@dimock/mcp` and `dimock` to npm, and creates the GitHub release. Before the first tag, four things only the owner can do:

1. **Names are set**: Maven group `io.github.yudistirosaputro` (`android/gradle.properties`), GitHub `yudistirosaputro/dimock`, npm `dimock`. Kotlin package `com.yudistirosaputro.dimock` is independent of the Maven group; `io.github.<user>` is verified automatically by Central Portal through the GitHub account.
2. **Maven Central namespace.** Create an account at central.sonatype.com, register the namespace `io.github.yudistirosaputro` (verified through a temporary public repo they name), and generate a user token. Save it as the repo secrets `MAVEN_CENTRAL_USERNAME` / `MAVEN_CENTRAL_PASSWORD`.
3. **Signing key.** `gpg --gen-key`, then export the armored private key (`gpg --armor --export-secret-keys <id>`) into `SIGNING_KEY` and its passphrase into `SIGNING_PASSWORD`. Publish the public key to `keyserver.ubuntu.com`.
4. **npm.** Claim the `dimock` name now (`npm publish` of the built `packages/cli` at 0.0.1 is enough) and create an automation token in `NPM_TOKEN`. The `@dimock` scope needs an org of that name on npmjs.com.

Also: put the full Apache-2.0 text into `LICENSE`, bump `version` in `.claude-plugin/marketplace.json`, `plugin/.claude-plugin/plugin.json`, `Dimock.VERSION`, and `android/gradle.properties` together (the release job checks the marketplace manifest matches the tag), and add a `CHANGELOG.md` entry.

Local dry run: `cd android && ./gradlew publishToMavenLocal` then point a sample project at `mavenLocal()`; `npm pack -w packages/cli` to inspect the tarball.
