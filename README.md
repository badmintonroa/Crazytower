# CrazyTower

Ein absurder Turmbau-Simulator im Stil von SimTower, geschrieben in Kotlin mit [KorGE](https://korge.org) und [Fleks](https://github.com/Quillraven/Fleks) (ECS).

## Starten

Voraussetzung: JDK 21.

```bash
./gradlew :game:run          # Spiel starten
./gradlew :core:test         # Simulations-Tests
./gradlew :core:playthrough --args="49 42"   # Bot spielt 49 Tage mit Seed 42 und protokolliert jeden Tag
```

## Startbildschirm

Das Spiel startet im Hauptmenü: Spiel laden, Neues Spiel, Optionen, Testtürme und Beenden. Im Hintergrund läuft der zuletzt gespeicherte Turm weiter, und die Kamera fährt langsam vom Erdgeschoss bis zum Dach und zurück. Ohne Spielstand steht dort ein Testturm. Im Spiel führt Optionen → „Zum Hauptmenü“ zurück (das Spiel wird vorher gespeichert).

`CRAZYTOWER_TITLE=<stufe>` zeigt für Screenshots das Hauptmenü mit einem Testturm, jeder andere Wert mit dem Spielstand.

## Sprachen

Das Spiel gibt es auf Deutsch und Englisch, umschaltbar unter Optionen → „Sprache · Language“ (ohne Einstellung gilt die Systemsprache). Texte stehen im Code als Paar: `tr("Deutsch", "English")` für Text, der gleich angezeigt wird, `Tr(...)` für Daten wie Mieter- und Raumnamen (`core/.../I18n.kt`). Zahlen und Geld folgen der Sprache (`48.250 $` / `$48,250`). Zu lange Texte werden umbrochen (`wrapText`) oder verkleinert und notfalls gekürzt (`fitWidth`), Buttons machen das von selbst. Für Screenshots wählt `CRAZYTOWER_LANG=en` Englisch.

## Steuerung

| Eingabe | Aktion |
| --- | --- |
| Linksklick | Bauen, auswählen |
| Rechtsklick oder Esc | Werkzeug abbrechen |
| Rechte Maustaste ziehen (oder links ohne Werkzeug) | Kamera verschieben |
| Mausrad, `+` / `-` | Zoom |
| Pfeiltasten, WASD | Kamera bewegen |
| `1` bis `6` | Baukategorien |
| Leertaste | Pause |
| `F5` / `F9` | Speichern / Laden |
| Klick auf den Ticker | Zum Ort der Nachricht springen |

## Aufbau

- `core/`: reine Simulation ohne Grafik. Turm-Raster (1 Feld = 24×48 px), Wegnetz mit A*, Fleks-Welt mit Komponenten und Systemen, Uhr, Nachrichten.
- `game/`: KorGE-Oberfläche. Turmansicht mit Kamera, HUD, Baumenü, Inspektor, Ticker.
- `game/src/main/resources/rooms/atlas.*`: Raum-Atlas in 4× Auflösung.
- `tools/art/make_people.py`: erzeugt die Figuren-Sprites.
- `tools/run-screenshot.sh`: rendert ohne Bildschirm (xvfb) ein Bild vom Demo-Turm.

Jede Figur ist eine Entität mit `Position` und `Goal`. Das `PathfindingSystem` sucht per A* eine Route aus Lauf-, Treppen- und Fahrabschnitten, das `MovementSystem` läuft sie ab.

## Turm bauen (ab M4)

- Etagen über dem Erdgeschoss brauchen darunter etwas, auf dem sie stehen. Das erste Untergeschoss geht überall, tiefere brauchen eine Decke.
- Wege: Lobby (nur Erdgeschoss), Flur, Treppe (eine Etage hoch), Aufzug (Schachtstücke übereinander bauen, höchstens 30 Etagen).
- Büros, Wohnungen und Imbisse werden vermietet, sobald man sie von der Straße aus erreicht. Wer einzieht, entscheidet der Turm: vom Steuerbüro bis zur Vampir-Versicherung (siehe `core/.../Tenants.kt`).
- `tools/art/make_rooms_extra.py` zeichnet die Räume, für die es noch keine generierte Grafik gibt (Wege, Wohnungen, Imbisse, Hotel, Reinigungsdienst, Luxusbüros), jeweils bei Tag, bei Nacht und verwahrlost (`extra*.png`). Luxusbüros sind 12 Felder breit, ihre Bilder auch.

## Tagesablauf und Wirtschaft (ab M5)

- Jeder Mieter bringt Leute mit (Büro 6, Wohnung 3, Imbiss 3). Sie haben eine Schicht: Tag (9–17), Spät (14–22) oder Nacht (22–6). Angestellte kommen zur Schicht, Bewohner sind während ihrer Schicht unterwegs und sonst zu Hause. Büros haben am Wochenende zu.
- Laune: Ohne Extras pendelt sie sich bei 55 % ein. Wer heute oder gestern im Imbiss gegessen hat, landet 15 Punkte höher, der Kopierraum gibt Büroleuten 5 Punkte, der Ufo-Landeplatz allen 3. Lange Wartezeiten am Aufzug und unerreichbare Ziele drücken sie für eine Weile. Die Sterne 3, 4 und 5 brauchen 60, 64 und 68 % Laune, und solange die Laune darunter liegt, ziehen jede Nacht die unzufriedensten Mieter aus.
- Um Mitternacht: Miete rein (halbe Miete bei schlechter Laune), Unterhalt raus, sehr unzufriedene Mieter ziehen aus und nennen dabei den echten Grund (Aufzug, kein Weg, kein Essen), die Sterne werden neu vergeben.
- Geld: Miete und Gebühren steigen um 15 % pro Stern. Ab 3 Sternen kosten vermietete Büros und Imbisse jeden Tag Betriebskosten, auch am Wochenende: 5 % ihrer Grundmiete bei 3 Sternen, 10 % bei 4 und 15 % bei 5. Ein normales Büro kostet ab 3 Sternen mehr (18.000, 24.000, 30.000 $), der Abriss erstattet ein Viertel dessen, was es beim Bau gekostet hat.
- Tag und Nacht: Himmel und Räume wechseln mit der Uhrzeit (Nacht-Atlas), unzufriedene Räume verwahrlosen sichtbar (Rundown-Atlas).

## Stufen, Ereignisse, Spielstand (ab M6)

| Stufe | Name | Etagen | Voraussetzung | Neu |
| --- | --- | --- | --- | --- |
| 1 | Bruchbude | 10 | Start | Wege, Büros, Wohnungen, Imbiss |
| 2 | Hochhaus | 25 | 300 Bewohner | Kopierraum der Ewigkeit |
| 3 | Wolkenkratzer | 50 | 1.000 Bewohner, Laune 65 % | Sicherheitsdienst |
| 4 | Mega-Tower | 75 | 3.000 Bewohner, Sicherheitsdienst | Teleporter |
| 5 | CrazyTower | 100 | 6.000 Bewohner, VIP-Besuch überlebt | Ufo-Landeplatz |

Jede Stufe hat eine eigene Lobby-Grafik. Zur vollen Stunde kann etwas Absurdes passieren (`core/.../Events.kt`): Büros werden zur Clownsagentur, in Wohnungen spukt es, im Aufzug wird geheiratet, Tauben besetzen die oberste Etage, ein Influencer schickt Busladungen voller Fans, und ab Stufe 4 kommt Frau Dr. Wichtig zum VIP-Besuch. Der Sicherheitsdienst macht schlimme Ereignisse seltener. Alles landet im Tower-Ticker.

Manche Ereignisse dauern eine Weile (`core/.../LastingEvents.kt`) und zeigen ein lila Blitz-Symbol über dem Raum: Hochzeit oder Karaoke im Aufzug, Vampir-Blutspende, Clown-Aufstand, Socken-Lawine, Superschurken-Erpressung, Feueralarm durch Toaster, Termiten, Alien-Austausch, Riesenkatze auf dem Dach, der Turm auf dem Mond. Ein Klick auf den Raum zeigt im Inspektor, was los ist, und meist einen Knopf mit der Gegenmaßnahme. Ab Stufe 3 kann man einmal pro Woche über den Luftballon-Knopf oben einen Tag der offenen Tür ausrufen. Laufende Ereignisse werden mitgespeichert.

### VIP-Besuche

Ab 3 Sternen schauen ab und zu VIPs vorbei (zwischen 9 und 19 Uhr, höchstens alle 12 Stunden, mit jedem Stern öfter): Baron von Goldzahn, Zirkusdirektor Tröt-Maximus, Graf Zahlula, ab 4 Sternen Dr. Dividende, Dr. Hämisch und Prof. Morgen, ab 5 Sternen Botschafter Zorg. Dazu kommen erfundene deutsche Streamer, die unterwegs live gehen und ein paar Fans mitbringen: xXBerndZocktXx, ASMR-Annika und Vlog-Vanessa, ab 4 Sternen Speedrun-Sören und Mukbang-Mehmet, ab 5 Sternen Oma Gisela Gaming. Sie besuchen einen vermieteten Raum, am liebsten ihre Lieblingsmieter (Zahlula die Vampir-Versicherung, Tröt-Maximus die Clowns). Am Ziel urteilen sie über die Laune im Turm, die Laune im besuchten Raum, wie lange der Weg gedauert hat und wie sauber die Hotelzimmer sind. Der Weg zählt dabei am meisten: ab 90 Minuten gibt es dafür keine Punkte mehr. Gefällt es ihnen, lassen sie ein dickes Trinkgeld da (6.000 $ pro Stern oder 15 % der gestrigen Tageseinnahmen, was mehr ist), sonst nur Kleingeld und eine Beschwerde im Ticker, die verrät, was gestört hat. Das Trinkgeld steht im Finanzfenster unter „VIP-Trinkgeld“ (`core/.../Vips.kt`).

`F5` speichert, `F9` lädt (`~/.crazytower/spielstand.json`).
