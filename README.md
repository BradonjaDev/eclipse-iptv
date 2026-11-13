<div align="center">

<img src="app/src/main/res/drawable-xhdpi/banner.png" alt="Umbra" width="360">

# Umbra

**IPTV für große Playlists.** Sender, Filme und Serien aus einer M3U-Playlist oder einem
Xtream-Zugang, übersichtlich sortiert und komplett mit der Fernbedienung bedienbar.

[![Version](https://img.shields.io/github/v/release/BradonjaDev/umbra-tv?label=Version&color=ffb84d)](https://github.com/BradonjaDev/umbra-tv/releases/latest)
[![Build](https://github.com/BradonjaDev/umbra-tv/actions/workflows/build.yml/badge.svg)](https://github.com/BradonjaDev/umbra-tv/actions/workflows/build.yml)
[![Download](https://img.shields.io/badge/Download-umbra--tv.apk-ffb84d?logo=android&logoColor=white)](https://github.com/BradonjaDev/umbra-tv/releases/latest/download/umbra-tv.apk)
![Android](https://img.shields.io/badge/Android-7.0%2B-3ddc84?logo=android&logoColor=white)

</div>

---

Umbra ist aus [Oma TV](https://github.com/BradonjaDev/oma-tv) hervorgegangen. Oma TV zeigt
bewusst nur eine Handvoll Sender; Umbra ist das Gegenstück für Playlists mit zehntausenden
Einträgen.

## Was die App kann

- **Große Playlists:** zehntausende Sender, Filme und Serien werden in eine lokale Datenbank eingelesen und sind danach sofort durchsuchbar
- **Listen entstehen von selbst:** die Kategorien der Playlist werden übernommen, getrennt nach Sendern, Filmen und Serien
- **Xtream-Zugänge werden erkannt:** aus einem Link der Form `…/get.php?username=…&password=…` holt die App zusätzlich Beschreibungen, Besetzung, Bewertungen, Trailer und das laufende Programm
- **Programm zum Sender:** was läuft gerade, wie lange noch, was kommt danach
- **Filme und Serien mit Detailseite:** Poster, Handlung, Trailer (über die YouTube-App), Staffeln und Folgen
- **Weiterschauen:** die App merkt sich, wo du aufgehört hast, und spielt bei Serien die nächste Folge
- **Favoriten, „Zuletzt gesehen“ und Suche** über alles
- **Player:** Umschalten mit ▲▼, Senderliste auf OK, Zifferneingabe, Tonspur, Untertitel und Bildformat; spielt auch AC3, E-AC3 und DTS
- **Jugendschutz:** Inhalte ab 18 sind standardmäßig ausgeblendet; einzelne Kategorien lassen sich verbergen
- **Einrichtung vom Handy:** QR-Code scannen, Link einfügen, fertig
- Kein Konto, keine Werbung, keine Datensammlung

## Installieren

Die aktuelle Version liegt immer hier:

```
https://github.com/BradonjaDev/umbra-tv/releases/latest/download/umbra-tv.apk
```

Auf dem TV-Stick am einfachsten mit der App **Downloader**: Adresse eintippen, installieren, fertig.
Ältere Versionen und die Änderungen stehen unter [Releases](https://github.com/BradonjaDev/umbra-tv/releases).

## Einrichten

Beim ersten Start zeigt Umbra einen QR-Code und eine Adresse wie `192.168.178.50:8080`.

1. Handy ins selbe WLAN bringen, QR-Code scannen oder die Adresse im Browser öffnen.
2. M3U-Link einfügen oder die Xtream-Zugangsdaten eintragen und auf **Playlist laden** tippen.
3. Der Fernseher lädt die Playlist und wechselt danach von selbst zur Startseite.

Wer eine Tastatur am Gerät hat, kann den Link über **Am Gerät eingeben** auch direkt eintippen.
Weitere Playlists lassen sich später unter **Optionen → Playlists verwalten** hinzufügen.

## Bedienung

| Wo | Taste | Was passiert |
|---|---|---|
| Listen | ◀ ▶ ▲ ▼, OK | bewegen und öffnen |
| Listen | OK lang auf einem Eintrag | Favorit setzen oder entfernen |
| Listen | OK lang auf einer Kategorie | Kategorie ausblenden |
| Überall | Zurück | eine Ebene zurück, auf der Startseite zweimal zum Beenden |
| Sender | ▲ / ▼ oder P+ / P− | nächster / vorheriger Sender |
| Sender | OK | Senderliste |
| Sender | ◀ | zurück zum vorherigen Sender |
| Sender | ▶ | Einblendung mit Programm |
| Sender | Ziffern | Sender direkt wählen |
| Film | OK | Pause / weiter |
| Film | ◀ ▶ | spulen (gedrückt halten: schneller) |
| Film | ▼ | Tonspur, Untertitel, Bildformat, nächste Folge |
| Player | OK lang oder Menü | Optionen |

## Selbst bauen

Es ist kein Android Studio nötig. GitHub Actions baut die APK bei jedem Push und jedem Pull Request.

**Neue Version veröffentlichen:** in `app/build.gradle` `versionCode` um 1 und `versionName`
erhöhen und auf `main` pushen. Der Workflow setzt dann den Tag `v<version>` genau auf diesen
Commit und erstellt den Release mit `umbra-tv.apk`. Pushes ohne neue Versionsnummer werden nur gebaut.

Lokal geht es mit JDK 17 und dem Android SDK so:

```
./gradlew assembleRelease
```

Ohne Signaturschlüssel wird die APK mit dem Debug-Schlüssel signiert. Der echte Schlüssel
liegt in den GitHub-Secrets `UMBRA_KEYSTORE_BASE64` und `UMBRA_KEYSTORE_PASSWORD`.

`tools/check.sh` prüft den Code in wenigen Sekunden ohne Android SDK (nur Typprüfung, keine APK).

## Aufbau

| Paket / Datei | Aufgabe |
|---|---|
| `data/Importer`, `M3u`, `Xtream`, `Json` | Playlist einlesen: M3U zeilenweise, Xtream über die Schnittstelle, alles im Datenstrom |
| `data/Db` | SQLite: Quellen, Kategorien, Einträge, Folgen, Favoriten und Abspielpositionen |
| `data/Sync`, `Epg` | Einlesen im Hintergrund, Programminfos mit Zwischenspeicher |
| `MainActivity` | Leiste links und die Bereiche Suche, Start, Sender, Filme, Serien, Optionen |
| `ui/BrowseView`, `HomeView`, `SearchView`, `SettingsView` | die Bereiche |
| `ui/TvRecycler`, `Cards`, `Sheet`, `Ui` | Listen für die Fernbedienung, Karten, Auswahlfenster, Farben und Maße |
| `DetailActivity` | Film- und Serienseite |
| `PlayerActivity` | Player (Media3/ExoPlayer) |
| `SetupActivity`, `SetupServer` | Einrichtung, kleiner Webserver für das Handy |

Java ohne Kotlin, Oberfläche komplett im Code, Media3/ExoPlayer 1.9, ab Android 7.0 (API 24).

## Gut zu wissen

- Umbra bringt **keine Inhalte mit** und ist an keinen Anbieter gebunden. Verwende nur Playlists, deren Inhalte du nutzen darfst. Frei zugängliche Sender sammelt zum Beispiel [iptv-org/iptv](https://github.com/iptv-org/iptv).
- Zugangsdaten bleiben auf dem Gerät. Sie werden nur an den Server der eingetragenen Playlist geschickt.
- Die Einrichtungsseite ist nur erreichbar, solange der Einrichtungsbildschirm offen ist.
