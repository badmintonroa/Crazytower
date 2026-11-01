# CrazyTower auf Steam: Demo und Vollversion

## Was der Build kann

- **Demo bis 2 Sterne.** Mit `-Pdemo=true` gebaut, vergibt das Spiel höchstens zwei Sterne. Sobald der zweite Stern gefeiert ist, bleibt das Spiel stehen und das Fenster „Ende der Testversion“ zeigt, was die Vollversion noch hat (bis 5 Sterne, Wolkenkratzer, VIPs, Penthouses, U-Bahn, Mondmission). Knöpfe: „Vollversion auf Steam“ (nur wenn eine Shopseite eingetragen ist), „Zum Hauptmenü“, „Beenden“.
- In der Demo gibt es keine Testtürme, das Hauptmenü trägt „TESTVERSION · bis 2 Sterne“, das Fenster heißt „CrazyTower – Demo“.
- **Spielstand übernehmen.** Die Demo speichert in `%USERPROFILE%\.crazytower\spielstand_demo.json` und überschreibt so nie einen Spielstand der Vollversion. Die Vollversion lädt den Demo-Turm, solange sie keinen eigenen hat, und baut ihn einfach weiter.
- **Fertige Ordner für Steam, Windows und Linux.** `jpackage` baut einen Ordner mit `CrazyTower.exe` (bzw. `CrazyTowerDemo.exe`) und eigenem, abgespecktem Java (gut 130 MB). Die Linux-Version startet mit `bin/CrazyTower` (bzw. `bin/CrazyTowerDemo`) und läuft auf dem Steam Deck nativ, ohne Proton. Spieler brauchen kein Java.

## Bauen

Am einfachsten auf GitHub: **Actions → „Steam build“ → „Run workflow“**. Dort Version und Shop-Adresse eintragen, z. B. `steam://store/1234560` (öffnet die Seite direkt im Steam-Client; die Zahl ist die App-ID der Vollversion). Nach etwa 10 Minuten liegen unten beim Lauf vier Downloads: `CrazyTower-windows-…`, `CrazyTowerDemo-windows-…`, `CrazyTower-linux-…` und `CrazyTowerDemo-linux-…`. Die Linux-Versionen stecken als `.tar.gz` im Download, weil sonst das Startprogramm seine Ausführungsrechte verliert. Ein Tag wie `v1.0.0` startet den Build auch.

Lokal auf einem Windows-PC mit JDK 21 geht es genauso:

```bat
gradlew.bat :game:packageApp -Pdemo=true -PstoreUrl=steam://store/1234560 -PappVersion=1.0.0
gradlew.bat :game:packageApp -PappVersion=1.0.0
```

Die Ordner liegen dann in `game\build\jpackage\CrazyTowerDemo\` und `game\build\jpackage\CrazyTower\`. `jpackage` baut immer für das System, auf dem es läuft; unter Linux entsteht also eine Linux-Version (praktisch zum Ausprobieren: `./gradlew :game:run -Pdemo=true`).

## Was du bei Steam selbst machen musst

Das geht nur mit deinem Konto, deshalb hier Schritt für Schritt:

1. **Steamworks-Konto.** Auf [partner.steamgames.com](https://partner.steamgames.com) registrieren, Bank-, Steuer- und Identitätsdaten angeben und die Steam-Direct-Gebühr (100 US-$ pro Spiel) für **CrazyTower** bezahlen. Eine Demo kostet nichts extra, hängt aber immer an der Hauptapp.
2. **Demo anlegen.** In Steamworks die App CrazyTower öffnen, unter „Alle zugehörigen Pakete, DLCs, Demos und Tools“ eine Demo hinzufügen. Die Demo bekommt eine eigene **App-ID**.
3. **Zwei Depots pro App.** Unter „SteamPipe → Depots“ bei der Demo zwei Depots anlegen, eins mit Betriebssystem **Windows**, eins mit **Linux + SteamOS**. Bei der Vollversion genauso. Die Depot-IDs brauchst du gleich.
4. **Startoptionen eintragen.** Unter „Installation → Allgemein“ zwei Startoptionen anlegen:
   - ausführbare Datei `CrazyTowerDemo.exe`, Betriebssystem Windows
   - ausführbare Datei `bin/CrazyTowerDemo`, Betriebssystem Linux

   Bei der Vollversion genauso mit `CrazyTower.exe` und `bin/CrazyTower`. Danach jeweils unter „Veröffentlichen“ die Änderungen veröffentlichen.
5. **Hochladen.** Das geht unter Linux oder unter Windows in WSL (Ubuntu aus dem Microsoft Store). Von Windows direkt hochgeladen, verliert das Linux-Startprogramm seine Ausführungsrechte und das Spiel startet auf dem Deck nicht.
   - In WSL einmal SteamCMD installieren: `sudo apt install steamcmd unzip` (oder nach der [Anleitung von Valve](https://developer.valvesoftware.com/wiki/SteamCMD)).
   - In `tools/steam/app_build_demo.vdf` `DEMO_APP_ID`, `DEMO_WINDOWS_DEPOT_ID` und `DEMO_LINUX_DEPOT_ID` durch die Zahlen aus Schritt 2 und 3 ersetzen.
   - Die beiden Demo-Downloads von GitHub so, wie sie sind, übergeben:
     `tools/steam/upload.sh demo <dein Steam-Benutzername> CrazyTowerDemo-windows-1.0.0.zip CrazyTowerDemo-linux-1.0.0.tar.gz.zip`
     Das Skript entpackt beides nach `tools/steam/content/`, prüft Exe und Startprogramm und lädt Windows und Linux zusammen als einen Build hoch. Passwort und Steam-Guard-Code fragt SteamCMD ab.
   - Für die Vollversion dasselbe mit `full`, `app_build_full.vdf` und den beiden `CrazyTower-…`-Downloads.
6. **Build freischalten.** In Steamworks unter „SteamPipe → Builds“ den neuen Build zuerst auf einen Beta-Zweig setzen und über Steam installieren und durchspielen, bis das Ende-Fenster kommt (am besten auch einmal auf dem Deck). Danach auf `default` setzen.
7. **Shopseite und Freigabe.** Die Shopseite muss mindestens zwei Wochen als „Demnächst erhältlich“ sichtbar sein, bevor das Spiel erscheint, und Valve prüft Shopseite und Build vorab (ein paar Tage). Die Demo erscheint danach als „Demo herunterladen“-Knopf auf der Seite von CrazyTower; sie eignet sich auch für das Steam Next Fest.

## Steam Deck

- Die Linux-Version läuft nativ. In Steamworks unter „Installation → Linux-Laufzeitumgebung“ am besten **Steam Linux Runtime 3.0 (sniper)** wählen, dann läuft das Spiel auf jedem Linux in derselben Umgebung.
- Das Spiel ist für 1280 × 800 gebaut, genau die Auflösung des Decks. Gesteuert wird mit Touchscreen und Trackpads als Maus.
- Unter „Steam Input“ eine Standard-Steuerung festlegen. Ausgangspunkt ist die Vorlage **„Tastatur und Maus“**, die du anpasst, zum Beispiel: rechtes Trackpad als Maus, R2 Linksklick, L2 Rechtsklick, Steuerkreuz als Pfeiltasten (Kamera), A als Leertaste (Pause), B als Esc (Werkzeug abbrechen), Schultertasten als `+`/`-` (Zoom).
- Danach unter „Steam Deck-Kompatibilität“ die Prüfung durch Valve anfordern. Weil kleine Texte auf dem 7-Zoll-Bildschirm schwer lesbar sein können, lohnt sich vorher ein Blick auf die Option „Oberfläche“ (Größe der Bedienelemente).

## Noch nicht drin

- Steam Cloud für den Spielstand (lässt sich später über „Steam Cloud → Auto-Cloud“ einrichten).
- Steamworks-SDK (Errungenschaften, Overlay-Funktionen). Das Steam-Overlay selbst sollte trotzdem erscheinen.
- Ein signiertes Programm. Über Steam ist das nicht nötig.
