# Publishing ChronoTask

All five libraries are published from this repository in one Maven Central deployment.
Sample applications are not published. The Maven group is `io.github.kirovl`; Kotlin
and Java source packages remain `io.github.chronotask`.

## One-time account setup

1. Sign in to <https://central.sonatype.com/> with the publishing GitHub account.
   Confirm that `io.github.kirovl` appears as a **Verified** namespace.
2. Generate a Central Portal user token. Its username and password are publishing
   credentials, not your GitHub password.
3. Create or select a GPG signing key. Publish its public key to a keyserver supported
   by Central, such as `keyserver.ubuntu.com`. Keep its private key private.
4. In this repository's **Settings → Secrets and variables → Actions**, configure:

   | Secret | Value |
   | --- | --- |
   | `MAVEN_CENTRAL_USERNAME` | Central Portal token username |
   | `MAVEN_CENTRAL_PASSWORD` | Central Portal token password |
   | `SIGNING_KEY` | Complete ASCII-armored GPG private key, including header, footer and newlines |
   | `SIGNING_PASSWORD` | Private-key passphrase; omit if the key has none |

Never commit credentials, private keys or signing passwords. GitHub repository login
and Maven Central publishing authentication are separate.

The repository's `KEYS` file contains the public release-signing key. For this
installation, the encrypted private key and its password are backed up in the ignored
`.publishing/` directory. Keep a secure backup of that directory; it is not included
in Git or Maven artifacts. GitHub Secrets cannot be downloaded as a backup.

## Verify locally

Use JDK 17 and an Android SDK compatible with the sample application's build.
The SDK path can be set in ignored `local.properties` or through `ANDROID_HOME`.

```bash
./gradlew publishToMavenLocal test apiCheck nmcpZipAggregation
```

This verifies local artifacts and creates the aggregated deployment ZIP without
uploading it. Signing and Central credentials are not required for this local check;
the ZIP is unsigned unless a signing key is configured. The artifact verification
tests publish their dependencies before checking them, so fresh CI machines do not
depend on pre-existing Maven Local artifacts.

`gradle.properties` defines the default `publicationGroup` and `releaseVersion`.
All modules and their inter-module dependencies use those same values. To check a
different version, add `-PreleaseVersion=1.0.1`.

## Publish from GitHub Actions

After configuring the secrets, open **Actions → Release & Publish to Maven Central →
Run workflow**, select `main` and enter the desired version, initially `1.0.0`.

Alternatively, create and push a version tag:

```bash
git tag v1.0.0
git push origin v1.0.0
```

The workflow takes the version from the tag or manual input, checks the credentials,
runs tests and API/artifact checks, signs the publications, then uploads the five
libraries in one deployment. It uses `AUTOMATIC` publishing, so Central publishes the
deployment after validation succeeds. Check the workflow result and Central Portal
deployment status before announcing the release. The upload task waits up to 15 minutes
for Central to confirm publication. A timeout does not undo an accepted deployment;
check its existing status before retrying. Availability in Central and indexing can lag
behind the upload.

Release coordinates on Central cannot be overwritten. Use a new version for the
next release; do not publish the same version again from a tag and a manual run.

## Publish from a local terminal

Configure `MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD`, `SIGNING_KEY` and, if
needed, `SIGNING_PASSWORD` securely in your shell environment. Do not paste them in
chat or place them in a checked-in file. Then run:

```bash
./gradlew validateCentralPublishing
./gradlew publishToMavenLocal test apiCheck
./gradlew nmcpPublishAggregationToCentralPortal -PreleaseVersion=1.0.0
```

The Gradle properties `mavenCentralUsername`, `mavenCentralPassword`, `signing.key`
and `signing.password` in the user's private `~/.gradle/gradle.properties` are also
supported. Environment variables take precedence.

To upload and validate but confirm publication manually in Central Portal, use:

```bash
./gradlew nmcpPublishAggregationToCentralPortal \
  -PreleaseVersion=1.0.0 -PcentralPublishingType=USER_MANAGED
```

## Consumer coordinates

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.kirovl:chronotask-core:1.0.0")
    // Choose the registration approach appropriate for your project:
    ksp("io.github.kirovl:chronotask-ksp:1.0.0")
    // annotationProcessor("io.github.kirovl:chronotask-processor:1.0.0")
    // implementation("io.github.kirovl:chronotask-reflect:1.0.0")
    testImplementation("io.github.kirovl:chronotask-test:1.0.0")
}
```

References: [Central requirements](https://central.sonatype.org/publish/requirements/),
[Central signing](https://central.sonatype.org/publish/requirements/gpg/),
[Nmcp documentation](https://gradleup.com/nmcp/).
