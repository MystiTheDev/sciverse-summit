STAGING AREA — Chair server bundle (git-ignored, reproduced at build time).
======================================================================
Before running `npm run dist` for the CHAIR app, place here:

  chair/server/summit.jar   <- copy of the Spring Boot fat jar
                               (E:\sciverse_summit_all_versions\summit-v3-developement\summit-v3.0.3-alpha\summit-0.0.1-SNAPSHOT.jar)
  chair/server/jre/         <- jlink mini-runtime (contents: bin\java.exe, lib\, ...)
                               Build it with e.g.:
                               jlink --add-modules java.se,jdk.zipfs,jdk.unsupported,jdk.crypto.cryptoki,jdk.management,jdk.net,jdk.localedata --strip-debug --no-man-pages --no-header-files --compress=zip-6 --output chair\server\jre
                               (Sanctioned JDK source: Temurin 17+. jdk.zipfs is
                               required for the Spring Boot nested-jar loader.)

package.json (extraResources) packages everything under chair/server/ into the
installer as <resources>/server/, and electron/main.js launches
server/jre/bin/java.exe -jar server/summit.jar  on "Start Session Server",
with SPRING_DATASOURCE_URL pointed at per-user app data (see README.md).

The DELEGATE build does not need this folder at all.
