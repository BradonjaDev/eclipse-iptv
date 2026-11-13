#!/bin/sh
# Schnelle Typpruefung ohne Android-SDK: uebersetzt den Code gegen android.jar und die Bibliotheken.
# Erzeugt keine APK, findet aber Tippfehler in Sekunden. Die echte APK baut GitHub Actions.
set -e
cd "$(dirname "$0")/.."
SDK="${ECLIPSE_SDK:-$HOME/.cache/eclipse-sdk}"
JAVAC="${JAVAC:-$(mise where java@temurin-17)/bin/javac}"
CP="$SDK/android.jar:$(ls "$SDK"/lib/*.jar | tr '\n' ':')"
rm -rf out/check && mkdir -p out/check/gen/de/eclipseiptv
# Platzhalter fuer die von Android erzeugte R-Klasse
{
  echo "package de.eclipseiptv; public final class R {"
  echo "  public static final class drawable {"
  find app/src/main/res -path '*drawable*' -type f | while read -r f; do n=$(basename "$f"); echo "    public static final int ${n%.*} = 0;"; done | sort -u
  echo "  }"
  echo "  public static final class mipmap { public static final int ic_launcher = 0; }"
  echo "  public static final class string { public static final int app_name = 0; }"
  echo "}"
} > out/check/gen/de/eclipseiptv/R.java
"$JAVAC" -Xlint:-options -source 17 -target 17 -cp "$CP" -d out/check/classes \
  $(find app/src/main/java -name '*.java') out/check/gen/de/eclipseiptv/R.java 2>&1 | grep -vE "^Note:|unknown enum constant|^[0-9]+ warnings?$" || true
echo "check fertig"
