# Publishing

One tag publishes everything. `.github/workflows/release.yml` reacts to any `v*` tag and runs four jobs in order:

1. **preflight** — `scripts/release/preflight.sh` checks that every version string in the repo (npm package, CLI `--version`, MCP `SERVER_VERSION`, plugin manifest, marketplace manifest, `VERSION_NAME`, `Dimock.VERSION` in both OkHttp artifacts, the CHANGELOG heading) equals the tag, then builds and tests the npm package. Nothing is published if this fails.
2. **maven-central** — the four AARs to Maven Central under `io.github.yudistirosaputro`.
3. **npm** — the single `dimock` package, published with provenance.
4. **github-release** — release notes; marked pre-release automatically when the tag contains a hyphen (`v0.1.0-alpha01`).

Maven Central is append-only: a version that lands there can never be deleted or replaced. Run the preflight locally before you tag.

```bash
bash scripts/release/preflight.sh 0.1.0
git tag v0.1.0 && git push origin v0.1.0
```

## One-time account setup

**Maven Central.** Create an account at central.sonatype.com and register the namespace `io.github.yudistirosaputro` — it is verified automatically through the GitHub account of the same name. Generate a user token and store it as the repository secrets `MAVEN_CENTRAL_USERNAME` and `MAVEN_CENTRAL_PASSWORD`.

**Signing key.** Central requires every artifact to be GPG-signed. `gpg --full-generate-key`, publish the public key to `keyserver.ubuntu.com`, then put the armored private key (`gpg --armor --export-secret-keys <id>`) in `SIGNING_KEY` and its passphrase in `SIGNING_PASSWORD`.

**npm.** Create an automation token on npmjs.com (Access Tokens → Granular or Classic → Automation) and store it as `NPM_TOKEN`. Automation tokens bypass 2FA, which publishing from CI requires.

**Plugin.** Nothing to register. A Claude Code marketplace is just a git repository with `.claude-plugin/marketplace.json` at its root, so pushing to GitHub is the distribution. Users add it with `claude plugin marketplace add yudistirosaputro/dimock`.

## Version bumps

Every version string is checked by the preflight, so bump them together:

| File | Field |
|---|---|
| `packages/dimock/package.json` | `version` |
| `packages/dimock/src/cli/cli.ts` | `.version("…")` |
| `packages/dimock/src/mcp/index.ts` | `SERVER_VERSION` |
| `plugin/.claude-plugin/plugin.json` | `version` |
| `.claude-plugin/marketplace.json` | `plugins[0].version` |
| `android/gradle.properties` | `VERSION_NAME` |
| `android/dimock-okhttp{,-no-op}/…/Dimock.kt` | `VERSION` |
| `CHANGELOG.md` | `## <version>` heading |

## Dry runs

```bash
cd android && ./gradlew publishToMavenLocal     # AARs into ~/.m2, point a project at mavenLocal()
npm pack -w packages/dimock                     # inspect the tarball npm would publish
npx dimock@latest --version                     # after publishing, from a clean machine
```
