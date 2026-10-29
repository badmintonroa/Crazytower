# CrazyTower

Ein absurder Turmbau-Simulator im Stil von SimTower, geschrieben in Kotlin mit [KorGE](https://korge.org) und [Fleks](https://github.com/Quillraven/Fleks) (ECS).

## Starten

Voraussetzung: JDK 21.

```bash
./gradlew :game:run          # Spiel starten
./gradlew :core:test         # Simulations-Tests
```

## Startbildschirm

Das Spiel startet im Hauptmenü: Spiel laden, Neues Spiel, Optionen, Testtürme und Beenden. Im Hintergrund läuft der zuletzt gespeicherte Turm weiter, und die Kamera fährt langsam vom Erdgeschoss bis zum Dach und zurück. Ohne Spielstand steht dort ein Testturm. Im Spiel führt Optionen → „Zum Hauptmenü“ zurück (das Spiel wird vorher gespeichert).

`CRAZYTOWER_TITLE=<stufe>` zeigt für Screenshots das Hauptmenü mit einem Testturm, jeder andere Wert mit dem Spielstand.

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
- `tools/art/make_rooms_extra.py` zeichnet die Räume, für die es noch keine generierte Grafik gibt (Wege, Wohnungen, Imbisse).

## Tagesablauf und Wirtschaft (ab M5)

- Jeder Mieter bringt Leute mit (Büro 6, Wohnung 3, Imbiss 3). Sie haben eine Schicht: Tag (9–17), Spät (14–22) oder Nacht (22–6). Angestellte kommen zur Schicht, Bewohner sind während ihrer Schicht unterwegs und sonst zu Hause. Büros haben am Wochenende zu.
- Mittag- und Abendessen gibt es im nächsten Imbiss, das bringt Umsatz und gute Laune. Lange Wartezeiten am Aufzug und unerreichbare Ziele drücken die Laune.
- Um Mitternacht: Miete rein (halbe Miete bei schlechter Laune), Unterhalt raus, sehr unzufriedene Mieter ziehen aus, die Sterne werden neu vergeben.
- Tag und Nacht: Himmel und Räume wechseln mit der Uhrzeit (Nacht-Atlas), unzufriedene Räume verwahrlosen sichtbar (Rundown-Atlas).

## Stufen, Ereignisse, Spielstand (ab M6)

| Stufe | Name | Etagen | Voraussetzung | Neu |
| --- | --- | --- | --- | --- |
| 1 | Bruchbude | 10 | Start | Wege, Büros, Wohnungen, Imbiss |
| 2 | Hochhaus | 25 | 300 Bewohner | Kopierraum der Ewigkeit |
| 3 | Wolkenkratzer | 50 | 1.000 Bewohner, Laune 60 % | Sicherheitsdienst |
| 4 | Mega-Tower | 75 | 3.000 Bewohner, Sicherheitsdienst | Teleporter |
| 5 | CrazyTower | 100 | 6.000 Bewohner, VIP-Besuch überlebt | Ufo-Landeplatz |

Jede Stufe hat eine eigene Lobby-Grafik. Zur vollen Stunde kann etwas Absurdes passieren (`core/.../Events.kt`): Büros werden zur Clownsagentur, in Wohnungen spukt es, im Aufzug wird geheiratet, Tauben besetzen die oberste Etage, ein Influencer schickt Busladungen voller Fans, und ab Stufe 4 kommt Frau Dr. Wichtig zum VIP-Besuch. Der Sicherheitsdienst macht schlimme Ereignisse seltener. Alles landet im Tower-Ticker.

`F5` speichert, `F9` lädt (`~/.crazytower/spielstand.json`).
