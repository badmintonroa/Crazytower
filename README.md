# CrazyTower

Ein absurder Turmbau-Simulator im Stil von SimTower, geschrieben in Kotlin mit [KorGE](https://korge.org) und [Fleks](https://github.com/Quillraven/Fleks) (ECS).

## Starten

Voraussetzung: JDK 21.

```bash
./gradlew :game:run          # Spiel starten
./gradlew :core:test         # Simulations-Tests
./gradlew :core:playthrough --args="110 42" -Dbot=all   # die drei Bots spielen 110 Tage mit Seed 42, eine Zeile pro Tag und ein Vergleich
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
| `X` oder `$`-Knopf | Extras für reiche Türme (ab Stufe 4) |
| `M` oder Klick auf die Stufe | Mondmission (ab Stufe 5) |
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
- Wege: Lobby (nur Erdgeschoss), Flur, Treppe (eine Etage hoch), Aufzug (Schachtstücke übereinander bauen, höchstens 30 Etagen). Wer einen Aufzug oder eine Treppe weiter zieht, als das Geld reicht, bekommt beim Loslassen so viele Etagen wie bezahlbar; der Rest ist in der Vorschau rot. Dasselbe gilt für Parkplatz-Reihen.
- Das Baumenü zeigt nur, was Stufe und Sterne schon erlauben. Was seit dem letzten Blick dazugekommen ist, trägt beim ersten Anzeigen einen Stern und „NEU“, die Kategorie-Taste auch.
- Büros, Wohnungen und Imbisse werden vermietet, sobald man sie von der Straße aus erreicht. Wer einzieht, entscheidet der Turm: vom Steuerbüro bis zur Vampir-Versicherung (siehe `core/.../Tenants.kt`).
- `tools/art/make_rooms_extra.py` zeichnet die Räume, für die es noch keine generierte Grafik gibt (Wege, Wohnungen, Imbisse, Hotel, Reinigungsdienst, Luxusbüros), jeweils bei Tag, bei Nacht und verwahrlost (`extra*.png`). Luxusbüros sind 12 Felder breit, ihre Bilder auch.

## Sterne und Wege zum Ziel

Bei 0 Sternen gibt es nur Wohnungen (und Wege). Büros und Imbiss kommen mit dem 1. Stern.

Ab 3 Sternen suchen Firmen **Fachkräfte**: Neue Büromieter ziehen nur ein, solange ihre Leute Arbeit finden. Arbeitskräfte sind Pendler aus der Stadt (600 bei 3 Sternen, 1.000 bei 4, 1.600 bei 5), 1.500 mehr mit der U-Bahn-Station, und jeder, der im Turm wohnt. Sonst steht über dem Büro „Keine Fachkräfte“. Umgekehrt bringen je 150 Büro-Angestellte einen Wohnungskäufer pro Tag mehr. Ein reiner Büroturm bleibt deshalb irgendwann leer, ein reiner Wohnturm kommt nur langsam voran (wenige Käufer, 3 Bewohner pro Wohnung) und hat für 3.000 bzw. 6.000 Bewohner nicht genug Platz.

| Ab | Gebäude | Wofür |
| --- | --- | --- |
| 3 ★ | Wohnheim | 6 Bewohner auf 6 Feldern, Miete jeden Tag, kein Käufer nötig. Studis brauchen Nebenjobs (höchstens halb so viele wie Büro-Angestellte) und sind laut: Wohnungen auf derselben Etage −8 Laune |
| 3 ★ | Späti | versorgt 30 Wohnungen und Wohnheime, Bewohner bis +6 Laune |
| 3 ★ | Kita | +3 Wohnungskäufer pro Tag, höchstens 3 |
| 4 ★ | Penthouse | 12 Felder, ab der 20. Etage, 4 Bewohner, viermal Hausgeld, braucht nur einen Käufer |
| 4 ★ | U-Bahn-Station | im Keller, +1.500 Pendler für die Büros |
| 4 ★ | Konferenzzentrum | Hotelgäste zahlen 50 % mehr |

So führen mehrere Wege zu Stufe 4 und 5: Büros und Wohnungen gemischt, Büros mit Wohnheimen (die sich gegenseitig hochziehen), Büros mit U-Bahn und Hotels, oder viele Wohnungen mit Kitas und Penthouses, die irgendwann doch Büros brauchen. Drei Bots spielen das durch, siehe „Bots“ unten.

## Tagesablauf und Wirtschaft (ab M5)

- Jeder Mieter bringt Leute mit (Büro 6, Wohnung 3, Imbiss 3). Sie haben eine Schicht: Tag (9–17), Spät (14–22) oder Nacht (22–6). Angestellte kommen zur Schicht, Bewohner sind während ihrer Schicht unterwegs und sonst zu Hause. Büros haben am Wochenende zu.
- Laune: Ohne Extras pendelt sie sich bei 55 % ein. Wer heute oder gestern im Imbiss gegessen hat, landet 15 Punkte höher, der Kopierraum gibt Büroleuten 5 Punkte, der Ufo-Landeplatz allen 3. Lange Wartezeiten am Aufzug und unerreichbare Ziele drücken sie für eine Weile. Die Sterne 3, 4 und 5 brauchen 60, 64 und 68 % Laune, und solange die Laune darunter liegt, ziehen jede Nacht die unzufriedensten Mieter aus.
- Um Mitternacht: Miete rein (halbe Miete bei schlechter Laune), Unterhalt raus, sehr unzufriedene Mieter ziehen aus und nennen dabei den echten Grund (Aufzug, kein Weg, kein Essen), die Sterne werden neu vergeben.
- Geld: Miete und Gebühren steigen um 15 % pro Stern. Ab 3 Sternen kosten vermietete Büros und Imbisse jeden Tag Betriebskosten, auch am Wochenende: 5 % ihrer Grundmiete bei 3 Sternen, 10 % bei 4 und 15 % bei 5. Ein normales Büro kostet ab 3 Sternen mehr (18.000, 24.000, 30.000 $), der Abriss erstattet ein Viertel dessen, was es beim Bau gekostet hat.
- Tag und Nacht: Himmel und Räume wechseln mit der Uhrzeit (Nacht-Atlas), unzufriedene Räume verwahrlosen sichtbar (Rundown-Atlas).

## Stufen, Ereignisse, Spielstand (ab M6)

| Stufe | Name | Etagen | Voraussetzung | Neu |
| --- | --- | --- | --- | --- |
| 1 | Bruchbude | 10 | Start | Wege, Wohnungen; Büros und Imbiss ab 1 Stern |
| 2 | Hochhaus | 25 | 300 Bewohner | Kopierraum der Ewigkeit |
| 3 | Wolkenkratzer | 50 | 1.000 Bewohner, Laune 65 % | Sicherheitsdienst |
| 4 | Mega-Tower | 75 | 3.000 Bewohner, Sicherheitsdienst | Teleporter |
| 5 | CrazyTower | 100 | 6.000 Bewohner, VIP-Besuch überlebt | Ufo-Landeplatz |

Jede Stufe hat eine eigene Lobby-Grafik. Zur vollen Stunde kann etwas Absurdes passieren (`core/.../Events.kt`): Büros werden zur Clownsagentur, in Wohnungen spukt es, im Aufzug wird geheiratet, Tauben besetzen die oberste Etage, ein Influencer schickt Busladungen voller Fans, und ab Stufe 4 kommt Frau Dr. Wichtig zum VIP-Besuch. Der Sicherheitsdienst macht schlimme Ereignisse seltener. Alles landet im Tower-Ticker.

Manche Ereignisse dauern eine Weile (`core/.../LastingEvents.kt`) und zeigen ein lila Blitz-Symbol über dem Raum: Hochzeit oder Karaoke im Aufzug, Vampir-Blutspende, Clown-Aufstand, Socken-Lawine, Superschurken-Erpressung, Feueralarm durch Toaster, Termiten, Alien-Austausch, Riesenkatze auf dem Dach, der Turm auf dem Mond. Ein Klick auf den Raum zeigt im Inspektor, was los ist, und meist einen Knopf mit der Gegenmaßnahme. Ab Stufe 3 kann man einmal pro Woche über den Luftballon-Knopf oben einen Tag der offenen Tür ausrufen. Laufende Ereignisse werden mitgespeichert.

### VIP-Besuche

Ab 3 Sternen schauen ab und zu VIPs vorbei (zwischen 9 und 19 Uhr, höchstens alle 12 Stunden, mit jedem Stern öfter): Baron von Goldzahn, Zirkusdirektor Tröt-Maximus, Graf Zahlula, ab 4 Sternen Dr. Dividende, Dr. Hämisch und Prof. Morgen, ab 5 Sternen Botschafter Zorg. Dazu kommen erfundene deutsche Streamer, die unterwegs live gehen und ein paar Fans mitbringen: xXBerndZocktXx, ASMR-Annika und Vlog-Vanessa, ab 4 Sternen Speedrun-Sören und Mukbang-Mehmet, ab 5 Sternen Oma Gisela Gaming. Sie besuchen einen vermieteten Raum, am liebsten ihre Lieblingsmieter (Zahlula die Vampir-Versicherung, Tröt-Maximus die Clowns). Am Ziel urteilen sie über die Laune im Turm, die Laune im besuchten Raum, wie lange der Weg gedauert hat und wie sauber die Hotelzimmer sind. Der Weg zählt dabei am meisten: ab 90 Minuten gibt es dafür keine Punkte mehr. Gefällt es ihnen, lassen sie ein dickes Trinkgeld da (6.000 $ pro Stern oder 15 % der gestrigen Tageseinnahmen, was mehr ist), sonst nur Kleingeld und eine Beschwerde im Ticker, die verrät, was gestört hat. Das Trinkgeld steht im Finanzfenster unter „VIP-Trinkgeld“ (`core/.../Vips.kt`).

`F5` speichert, `F9` lädt (`~/.crazytower/spielstand.json`).

## Geld ausgeben ab Stufe 4 (`core/.../Endgame.kt`)

Ab Stufe 4 gibt es mehr zu kaufen als Räume. Die laufenden Kosten richten sich nach den gestrigen Einnahmen, damit sie für kleine und große Türme gleich spürbar sind.

- **Ausbau** (Inspektor): Büros, Wohnungen, Hotels, Imbisse, Läden und Wohnheime bekommen bis zu drei Stufen, jede +15 % Miete und +3 Laune. Die Namen hängen vom Mieter ab (Clowns bekommen einen Hüpfburg-Boden, Vampire einen Sarg-Ruheraum). Ausgebaute Räume tragen kleine Sterne.
- **Aufzug-Tuning** (Inspektor am Schacht): Turbo-Motor (30 % schneller), Streichquartett (Wartende werden später grantig), Liftboy mit Monokel (mehr Plätze, schnellere Türen).
- **Wunschmieter anwerben** (Inspektor an leeren Räumen): eine Zeitungsanzeige, und der gewählte Mieter zieht sofort ein.
- **Extras-Fenster** (`X`): Versicherungen gegen Tauben, Clowns, Spuk, Stromausfall und Aufzugspannen (verhindern die meisten dieser Ereignisse), eine Werbekampagne (3 Tage mehr Besucher und VIPs), das wöchentliche Turmfest (+10 Laune für 2 Tage), Deko für das Dach (Gummiente, Leuchtschrift, Discokugel, goldene Fassade, Lasershow; jede bringt mehr Schaulustige), drei Seiteneingänge aus Oma Hildegards Garten (kürzere Wege von der Straße) und das Labor für fragwürdige Wissenschaft (Teleporter ohne Verwechslung, Kaffee aus dem Kopierraum, selbstputzende Handtücher, Schallschluck-Tapete, Geduldspille).

## Mondmission ab Stufe 5 (`core/.../Mission.kt`)

Stufe 5 ist die letzte Stufe, das Spiel geht aber weiter: Auf der obersten Etage (99) entsteht Teil für Teil eine Mondstation. Jedes Teil braucht zwei Aufträge im Turm, danach wird es im Baumenü unter Spezial gebaut. Der Fortschritt steht in der Stufenanzeige und im Missionsfenster (`M`).

| Countdown | Teil | Kosten | Aufträge |
| --- | --- | --- | --- |
| 3 | Startrampe | 5 Mio. $ | 20 Nächte Regierungsbeamte gut gelaunt im Hotel; 10.000 Antragsformulare aus dem Kopierraum |
| 2 | Treibstofflager | 15 Mio. $ | 40.000 Becher Kaffee aus den Imbissen; 5 Büros für die Raketen für Anfänger GmbH |
| 1 | Kommandokapsel | 40 Mio. $ | 10 Astronauten mit über 75 % Laune; einen Tag Probezünden mit mindestens 65 % Turm-Laune |
| Abheben! | Mondlift | 100 Mio. $ | Botschafter Zorg geht zufrieden nach Hause; der Sicherheitsdienst stoppt Dr. Hämisch dreimal |

Mit jedem Teil kommen mehr Schaulustige (50 am Anfang, 1.500 nach dem Abheben). Sie fahren zur Aussichtsplattform (ab Etage 90, Eintritt 150 $) und kaufen danach im Souvenirladen ein. Dauert der Weg länger als eine Stunde oder fehlt die Plattform, gehen sie verärgert heim; sind es mehr als 30 % an einem Tag, lacht die Presse und der Countdown wartet einen Tag. Nach dem Abheben gibt es einen Abspann, und der Turm baut einfach weiter.

## Bots

`./gradlew :core:playthrough --args="<tage> <seed> <minuten>" -Dbot=twin|wide|hub|miser|all` lässt Bots ohne Grafik spielen, bis die Mondmission abhebt (oder die Tage um sind) (`core/src/test/.../bot/`). Mit `-Dcsv=<ordner>` schreibt jeder Lauf eine CSV-Zeile pro Tag. Alle Bots fragen vor jedem Raum die Wegsuche des Spiels, wie lange der Weg von der Straße dauert, heute und für jede Breite, auf die das Grundstück noch wächst (die Leute kommen vom näheren Straßenende, und das rechte Ende wandert mit). Über 72 Minuten bauen sie dort nicht.

| Bot | Platzierung | Was er baut |
| --- | --- | --- |
| `twin` Zwillingstürme | zwei schmale Türme an den Straßenenden, Express ganz außen, je zwei Aufzugssäulen; die Mitte erst, wenn beide Türme oben anstoßen; klettert, sobald die Stufe es erlaubt | Büros, Wohnheime, Hotels, Luxusbüros |
| `wide` Breitbau | ganze Grundstücksbreite, alle 24 Felder ein Aufzug, eine neue Säule erst, wenn bei den alten nichts mehr frei ist, eine neue Zone erst, wenn die untere voll ist | Wohnungen zuerst, mit Läden, Kitas, Penthouses; Büros und Wohnheime, wenn Käufer fehlen |
| `hub` Express-Treppen | keine lokalen Aufzüge über der 9. Etage: Expressaufzüge mit Treppenhaus daneben, höchstens zwei Etagen zu Fuß | Hotels, Büros, Luxusbüros, Konferenzzentrum |
| `miser` Geizhals | wie Express-Treppen | wie Express-Treppen, kauft aber keine Extras, nur was die Mondmission braucht |

Ab Stufe 4 kaufen alle Bots außer dem Geizhals Extras: Tuning für Schächte mit langen Wartezeiten, Ausbau für die Räume, die am meisten Miete bringen, Versicherungen, Turmfest, Werbung, Seiteneingänge, Forschung und während der Mission Deko. Für die Mission bauen sie Aussichtsplattform und Souvenirläden, die Räume für die offenen Aufträge, schicken den Sicherheitsdienst zu Dr. Hämisch, zünden probe, wenn die Laune reicht, und bauen die Stationsteile auf Etage 99 (wenn dort alles voll ist, reißen sie Platz frei). Die Log-Zeile zeigt ab Stufe 4 das Geld in Tagen Einnahmen und die Ausgaben je Senke, ab Stufe 5 den Countdown, die Aufträge und die Schaulustigen; die Zusammenfassung zeigt, wie viele Tage jeder Countdown-Schritt gedauert hat.

Die Aufzugssäulen der Zonen stehen in allen Zonen an derselben Stelle (je 24 Etagen, die Etage unter der nächsten Sky-Lobby über eine Treppe), damit der Umstieg in der Sky-Lobby kurz bleibt.
