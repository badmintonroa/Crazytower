# CrazyTower auf Steam: Demo und Vollversion

## Was der Build kann

- **Demo bis 2 Sterne.** Mit `-Pdemo=true` gebaut, vergibt das Spiel höchstens zwei Sterne. Sobald der zweite Stern gefeiert ist, bleibt das Spiel stehen und das Fenster „Ende der Testversion“ zeigt, was die Vollversion noch hat (bis 5 Sterne, Wolkenkratzer, VIPs, Penthouses, U-Bahn, Mondmission). Knöpfe: „Vollversion auf Steam“ (nur wenn eine Shopseite eingetragen ist), „Zum Hauptmenü“, „Beenden“.
- In der Demo gibt es keine Testtürme, das Hauptmenü trägt „TESTVERSION · bis 2 Sterne“, das Fenster heißt „CrazyTower – Demo“.
- **Spielstand übernehmen.** Die Demo speichert in `%USERPROFILE%\.crazytower\spielstand_demo.json` und überschreibt so nie einen Spielstand der Vollversion. Die Vollversion lädt den Demo-Turm, solange sie keinen eigenen hat, und baut ihn einfach weiter.
- **Fertiger Ordner für Steam.** `jpackage` baut einen Ordner mit `CrazyTower.exe` (bzw. `CrazyTowerDemo.exe`) und eigenem, abgespecktem Java (gut 130 MB). Spieler brauchen kein Java.

## Bauen

Am einfachsten auf GitHub: **Actions → „Steam build“ → „Run workflow“**. Dort Version und Shop-Adresse eintragen, z. B. `steam://store/1234560` (öffnet die Seite direkt im Steam-Client; die Zahl ist die App-ID der Vollversion). Nach etwa 10 Minuten liegen unten beim Lauf zwei Downloads: `CrazyTower-windows-…` und `CrazyTowerDemo-windows-…`. Ein Tag wie `v1.0.0` startet den Build auch.

Lokal auf einem Windows-PC mit JDK 21 geht es genauso:

```bat
gradlew.bat :game:packageApp -Pdemo=true -PstoreUrl=steam://store/1234560 -PappVersion=1.0.0
gradlew.bat :game:packageApp -PappVersion=1.0.0
```

Die Ordner liegen dann in `game\build\jpackage\CrazyTowerDemo\` und `game\build\jpackage\CrazyTower\`. `jpackage` baut immer für das System, auf dem es läuft; unter Linux entsteht also eine Linux-Version (praktisch zum Ausprobieren: `./gradlew :game:run -Pdemo=true`).

## Was du bei Steam selbst machen musst

Das geht nur mit deinem Konto, deshalb hier Schritt für Schritt:

1. **Steamworks-Konto.** Auf [partner.steamgames.com](https://partner.steamgames.com) registrieren, Bank-, Steuer- und Identitätsdaten angeben und die Steam-Direct-Gebühr (100 US-$ pro Spiel) für **CrazyTower** bezahlen. Eine Demo kostet nichts extra, hängt aber immer an der Hauptapp.
2. **Demo anlegen.** In Steamworks die App CrazyTower öffnen, unter „Alle zugehörigen Pakete, DLCs, Demos und Tools“ eine Demo hinzufügen. Die Demo bekommt eine eigene **App-ID** und eine **Depot-ID**.
3. **Startoption eintragen.** Bei der Demo unter „Installation → Allgemein“ eine Startoption anlegen: ausführbare Datei `CrazyTowerDemo.exe`, Betriebssystem Windows. Bei der Vollversion genauso mit `CrazyTower.exe`. Danach jeweils unter „Veröffentlichen“ die Änderungen veröffentlichen.
4. **Hochladen.**
   - [SteamCMD](https://developer.valvesoftware.com/wiki/SteamCMD) herunterladen und `steamcmd.exe` nach `tools\steam\` legen.
   - In `tools\steam\app_build_demo.vdf` `DEMO_APP_ID` und `DEMO_DEPOT_ID` durch die Zahlen aus Schritt 2 ersetzen.
   - Den Download `CrazyTowerDemo-windows-…` nach `tools\steam\content\CrazyTowerDemo\` entpacken (dort muss direkt `CrazyTowerDemo.exe` liegen).
   - `tools\steam\upload_demo.bat <dein Steam-Benutzername>` starten, Passwort und Steam-Guard-Code eingeben.
   - Für die Vollversion dasselbe mit `app_build_full.vdf`, `content\CrazyTower\` und `upload_full.bat`.
5. **Build freischalten.** In Steamworks unter „SteamPipe → Builds“ den neuen Build zuerst auf einen Beta-Zweig setzen und über Steam installieren und durchspielen, bis das Ende-Fenster kommt. Danach auf `default` setzen.
6. **Shopseite und Freigabe.** Die Shopseite muss mindestens zwei Wochen als „Demnächst erhältlich“ sichtbar sein, bevor das Spiel erscheint, und Valve prüft Shopseite und Build vorab (ein paar Tage). Die Demo erscheint danach als „Demo herunterladen“-Knopf auf der Seite von CrazyTower; sie eignet sich auch für das Steam Next Fest.

## Noch nicht drin

- Steam Cloud für den Spielstand (lässt sich später über „Steam Cloud → Auto-Cloud“ einrichten).
- Steamworks-SDK (Errungenschaften, Overlay-Funktionen). Das Steam-Overlay selbst sollte trotzdem erscheinen.
- Ein signiertes Programm. Über Steam ist das nicht nötig.
