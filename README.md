# CrazyTower

Ein absurder Turmbau-Simulator im Stil von SimTower, geschrieben in Kotlin mit [KorGE](https://korge.org) und [Fleks](https://github.com/Quillraven/Fleks) (ECS).

## Starten

Voraussetzung: JDK 21.

```bash
./gradlew :game:run          # Spiel starten
./gradlew :core:test         # Simulations-Tests
./gradlew :core:playthrough --args="110 42" -Dbot=all   # die drei Bots spielen 110 Tage mit Seed 42, eine Zeile pro Tag und ein Vergleich
./gradlew :core:bots --args="200 1-8" -Dbot=all -Djobs=6   # alle Bots mit Seeds 1 bis 8, sechs Läufe gleichzeitig, Zusammenfassung je Bot
```

Nur beim Start über `./gradlew :game:run` (Entwicklermodus, `DevMode.kt`) zeigt `F7` oben links die FPS und wie viele Millisekunden jedes System pro Frame braucht, das langsamste zuerst. Im Installer, auf Steam und in der Demo gibt es die Anzeige nicht; KorGEs eigene Debugansicht auf `F7`/`F12` ist überall aus. Ein fertiges Paket lässt sich mit `CRAZYTOWER_DEV=1` trotzdem prüfen. `CRAZYTOWER_PROFILE=<sekunden>` schreibt dieselben Zahlen jede Sekunde in die Konsole.

Die Anzeige zählt auch die Draw Calls pro Frame, also wie oft KorGE etwas an die Grafikkarte schickt. KorGE fasst alles zusammen, was dieselben (höchstens vier) Texturen benutzt; jede weitere Textur, jeder Clip-Container und jeder Wechsel der Glättung beginnt einen neuen Draw Call. Deshalb packt `Assets` beim Start alle Raumatlanten, alle Figuren und die kleinen Bilder aus `WorldSprites.kt` (Sterne, Badges, Kabinen) auf eine einzige Textur (`TexturePacker.kt`), Räume schneiden ihre Bilder zu statt zu clippen, und alle Figuren werden gleich geglättet (die 12x24-Figuren werden dafür auf 4x vergrößert). Der Turm braucht so etwa 10 Draw Calls statt mehrerer Tausend. Wer neue Bilder in den Turm bringt, lädt sie über `Assets` statt als eigene Bitmap oder `graphics { }`. Zum Messen richtet `CRAZYTOWER_CAMERA=<zoom>,<etage>` die Kamera auf die Turmmitte, z. B. `CRAZYTOWER_TEST=6 CRAZYTOWER_CAMERA=0.35,15 CRAZYTOWER_PROFILE=10`.

## Steam: Demo und Windows-Build

```bash
./gradlew :game:run -Pdemo=true          # die Demo spielen: endet mit dem 2. Stern
./gradlew :game:packageApp -Pdemo=true   # Ordner mit Exe und eigenem Java für Steam (game/build/jpackage/)
```

Die Versionen für Windows und Linux / Steam Deck baut GitHub unter Actions → „Steam build“. Wie sie zu Steam kommt, steht in [docs/steam.md](docs/steam.md). Das App-Symbol zeichnet `tools/art/make_icon.py`.

## Startbildschirm

Das Spiel startet im Hauptmenü: Spiel laden, Neues Spiel, Optionen, Testtürme und Beenden. Im Hintergrund läuft der zuletzt gespeicherte Turm weiter, und die Kamera fährt langsam vom Erdgeschoss bis zum Dach und zurück. Ohne Spielstand steht dort ein Testturm. Im Spiel führt Optionen → „Zum Hauptmenü“ zurück (das Spiel wird vorher gespeichert).

`CRAZYTOWER_TITLE=<stufe>` zeigt für Screenshots das Hauptmenü mit einem Testturm, jeder andere Wert mit dem Spielstand.

## Sprachen

Das Spiel gibt es auf Deutsch und Englisch, umschaltbar unter Optionen → „Sprache · Language“ (ohne Einstellung gilt die Systemsprache). Texte stehen im Code als Paar: `tr("Deutsch", "English")` für Text, der gleich angezeigt wird, `Tr(...)` für Daten wie Mieter- und Raumnamen (`core/.../I18n.kt`). Zahlen und Geld folgen der Sprache (`48.250 $` / `$48,250`). Zu lange Texte werden umbrochen (`wrapText`) oder verkleinert und notfalls gekürzt (`fitWidth`), Buttons machen das von selbst. Für Screenshots wählt `CRAZYTOWER_LANG=en` Englisch.

## Steam-Shopseite und Trailer

Trailer, Screenshots und Shop-Grafiken kommen direkt aus dem Spiel, ohne Bildschirm:

```bash
tools/steam/film.sh out/clips de              # alle Trailer-Szenen als mp4 (1920×1080, 30 fps, ohne Ton)
tools/steam/screenshots.sh out/shots de       # 10 Store-Screenshots
python3 -I tools/steam/trailer.py out/clips out/videos de   # Trailer und Gameplay-Video mit Musik und Texttafeln
python3 -I tools/steam/capsules.py <keyart> out/grafiken    # Capsules, Bibliotheksbilder, Logo, Icons
```

Der Aufnahmemodus (`game/.../Film.kt`, `CRAZYTOWER_FILM=<datei.mp4|.png>`) läuft mit festem Zeitschritt und schickt jedes Bild an ffmpeg, deshalb ruckelt nichts, egal wie langsam der Rechner rendert. Kamerafahrten (`CRAZYTOWER_CAM`), Verwandlungen, Ereignisse und Fenster (`CRAZYTOWER_ACTIONS`) werden per Umgebungsvariable gesteuert; die Beschreibung steht oben in `Film.kt`. Die Musik erzeugt `tools/steam/music.py` selbst, ohne Samples.

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
| `,` / `.` | Langsamer / schneller |
| `N` | Nachts vorspulen an/aus |
| `F` oder Klick aufs Geld | Finanzen |
| `O` | Optionen |
| `F1` oder `H` | Tastenübersicht |
| `C` | Gewählten Raum kopieren (gleichen Raumtyp zum Bauen wählen) |
| `Strg+Z` | Letzten Bau oder Abriss zurücknehmen |
| Esc (ohne offenes Fenster) | Pausenmenü: Weiterspielen, Speichern, Optionen, Tasten, Hauptmenü |
| `F5` / `F9` | Speichern / Laden |
| `X` oder `$`-Knopf | Extras für reiche Türme (ab 4 Sternen) |
| `M` oder Klick auf die Sterne-Anzeige | Mondmission (ab 5 Sternen) |
| Klick auf den Ticker | Zum Ort der Nachricht springen |

Bleibt die Maus kurz auf einem Knopf der Kopfleiste oder des Baumenüs, erklärt ein Hinweis, was er tut, welche Taste dasselbe macht und warum er gerade gesperrt ist (Schloss). Gekürzte Texte zeigen dort ihren ganzen Text. Kein Text im Spiel ist kleiner als 13 px (`MIN_TEXT` in `ui/Widgets.kt`), zu lange Texte werden lieber gekürzt als winzig. Die Tastenübersicht (`Shortcuts` in `HelpWindow.kt`) ist die eine Liste aller Tasten; wer eine Taste hinzufügt, trägt sie dort und hier ein. Für Screenshots zeigt `CRAZYTOWER_HOVER=x,y` den Hinweis an dieser Stelle, `CRAZYTOWER_HELP=1` die Tastenübersicht.

## Aufbau

- `core/`: reine Simulation ohne Grafik. Turm-Raster (1 Feld = 24×48 px), Wegnetz mit A*, Fleks-Welt mit Komponenten und Systemen, Uhr, Nachrichten.
- `game/`: KorGE-Oberfläche. Turmansicht mit Kamera, HUD, Baumenü, Inspektor, Ticker.
- `game/src/main/resources/rooms/atlas.*`: Raum-Atlas in 4× Auflösung.
- `tools/art/make_people.py`: erzeugt die Figuren-Sprites.
- `tools/run-screenshot.sh`: rendert ohne Bildschirm (xvfb) ein Bild vom Demo-Turm.

Jede Figur ist eine Entität mit `Position` und `Goal`. Das `PathfindingSystem` sucht per A* eine Route aus Lauf-, Treppen- und Fahrabschnitten, das `MovementSystem` läuft sie ab.

### Mehrere CPU-Kerne

Die Simulation verteilt die Wegsuche und die Aufzugsschächte auf Hilfsthreads (`core/.../Workers.kt`), standardmäßig so viele, wie der Rechner Kerne hat, minus einen (höchstens 8). Die Hilfsthreads rechnen nur, was niemand sonst anfasst (eine Wegsuche, einen Schacht mit seinen Fahrgästen). Was die Welt ändert (Komponenten, Geld, Zufallszahlen, Nachrichten), passiert danach auf dem Spielthread in fester Reihenfolge, deshalb läuft das Spiel mit jeder Threadzahl bitgenau gleich (`WorkersTest`). Wer an einem System etwas parallelisiert, hält sich an diese Regel. `-Dcrazytower.threads=1` oder `CRAZYTOWER_THREADS=1` schaltet die Hilfsthreads ab, zum Vergleichen: `./gradlew :core:bench -Dcrazytower.threads=1`. Die F7-Entwickleranzeige zeigt die Threadzahl.

## Turm bauen (ab M4)

- Etagen über dem Erdgeschoss brauchen darunter etwas, auf dem sie stehen. Das erste Untergeschoss geht überall, tiefere brauchen eine Decke.
- Wege: Lobby (nur Erdgeschoss), Flur, Treppe (eine Etage hoch), Aufzug (Schachtstücke übereinander bauen, höchstens 30 Etagen). Wer einen Aufzug oder eine Treppe weiter zieht, als das Geld reicht, bekommt beim Loslassen so viele Etagen wie bezahlbar; der Rest ist in der Vorschau rot. Dasselbe gilt für Parkplatz-Reihen.
- Das Baumenü zeigt nur, was die Sterne schon erlauben. Was seit dem letzten Blick dazugekommen ist, trägt beim ersten Anzeigen einen Stern und „NEU“, die Kategorie-Taste auch.
- Ist ein Raum gewählt, klappt das Baumenü zu einer kleinen Karte zusammen (Raum, Preis, „Wechseln“, „Abbrechen“), damit man beim Platzieren den Turm sieht. Die Details stehen im Hinweis der Karte. „Wechseln“, ein Klick auf die Karte oder die Kategorie-Taste zeigen wieder die ganze Liste. „Kopieren“ (oder `C`) im Infofenster eines gebauten Raums, einer Treppe oder eines Aufzugs wählt denselben Typ und öffnet diese kleine Karte, um gleich noch einen zu platzieren; gesperrt, wenn der Typ nicht im Baumenü steht oder sein Limit erreicht ist. Für Screenshots wählt `CRAZYTOWER_BUILD=<raum-id>` (mit `CRAZYTOWER_CATEGORY`) einen Raum vor.
- Büros, Wohnungen und Imbisse werden vermietet, sobald man sie von der Straße aus erreicht. Wer einzieht, entscheidet der Turm: vom Steuerbüro bis zur Vampir-Versicherung (siehe `core/.../Tenants.kt`).
- `tools/art/make_rooms_extra.py` zeichnet die Räume, für die es noch keine generierte Grafik gibt (Wege, Wohnungen, Imbisse, Hotel, Reinigungsdienst, Luxusbüros), jeweils bei Tag, bei Nacht und verwahrlost (`extra*.png`). Luxusbüros sind 12 Felder breit, ihre Bilder auch.
- `tools/art/import_rooms.py <Ordner>` setzt generierte Raumbilder (z. B. aus ComfyUI, Dateiname = Raumname wie `food_sushi_00001_.png`) in diese Atlanten ein: schneidet Außenwände und Himmel weg, skaliert auf die Raumgröße und leitet Nacht- und Verwahrlost-Look per Filter ab. Nach `make_rooms_extra.py` erneut laufen lassen, sonst sind die Bilder wieder weg.

## Sterne und Wege zum Ziel

Die Sterne sind die eine Fortschrittsleiter des Turms: Jeder Stern ist zugleich die nächste Ausbaustufe (Bruchbude, Hochhaus, Wolkenkratzer, Mega-Tower, CrazyTower) mit höherem Etagenlimit, neuer Lobby und neuen Räumen. Oben in der Kopfleiste stehen links die Sterne mit dem Namen des Turms, rechts der nächste Stern und was ihm noch fehlt. Geprüft wird zu jeder vollen Stunde, Sterne werden nie wieder abgezogen (`core/.../Rating.kt`, `Stages.kt`).

| Stern | Turm | Etagen | Voraussetzung | Neu |
| --- | --- | --- | --- | --- |
| 0 | Baustelle | 5 | Start | Wege, Wohnungen |
| 1 | Bruchbude | 10 | 10 Bewohner | Büros, Imbiss |
| 2 | Hochhaus | 25 | 300 Bewohner, Laune 50 % | Kopierraum, Express-Aufzug, Wohnheim, Späti, Kita, Grundstück 128 breit (Ende der Demo) |
| 3 | Wolkenkratzer | 50 | 1.000 Bewohner, Laune 60 %, Imbiss | Sicherheitsdienst, Hotel, Penthouse, U-Bahn, Konferenzzentrum, Keller, Grundstück 160 breit, Fachkräfte |
| 4 | Mega-Tower | 75 | 3.000 Bewohner, Laune 64 %, Sicherheitsdienst | Luxusbüros, Extras |
| 5 | CrazyTower | 100 | 6.000 Bewohner, Laune 68 %, VIP-Besuch überlebt | Ufo-Landeplatz, Mondmission |

Der erste Stern kommt schnell, und die ersten 12 Haushalte eines neuen Turms ziehen sofort ein, ohne auf die Wohnungskäufer des nächsten Tages zu warten. Danach finden neue Wohnungen nur ein paar Käufer am Tag.

Ab 3 Sternen suchen Firmen **Fachkräfte**: Neue Büromieter ziehen nur ein, solange ihre Leute Arbeit finden. Arbeitskräfte sind Pendler aus der Stadt (600 bei 3 Sternen, 1.000 bei 4, 1.600 bei 5), 1.500 mehr mit der U-Bahn-Station, und jeder, der im Turm wohnt. Sonst steht über dem Büro „Keine Fachkräfte“. Umgekehrt bringen je 150 Büro-Angestellte einen Wohnungskäufer pro Tag mehr. Ein reiner Büroturm bleibt deshalb irgendwann leer, ein reiner Wohnturm kommt nur langsam voran (wenige Käufer, 3 Bewohner pro Wohnung) und hat für 3.000 bzw. 6.000 Bewohner nicht genug Platz.

| Ab | Gebäude | Wofür |
| --- | --- | --- |
| 2 ★ | Wohnheim | 6 Bewohner auf 6 Feldern, Miete jeden Tag, kein Käufer nötig. Studis brauchen Nebenjobs (höchstens halb so viele wie Büro-Angestellte) und sind laut: Wohnungen auf derselben Etage −8 Laune |
| 2 ★ | Späti | versorgt 30 Wohnungen und Wohnheime, Bewohner bis +6 Laune |
| 2 ★ | Kita | +3 Wohnungskäufer pro Tag, höchstens 3 |
| 3 ★ | Penthouse | 12 Felder, ab der 20. Etage, 4 Bewohner, viermal Hausgeld, braucht nur einen Käufer |
| 3 ★ | U-Bahn-Station | im Keller, +1.500 Pendler für die Büros; ist sie mit dem Turm verbunden, steigen dort 40 % der Leute aus und ein |
| 3 ★ | Konferenzzentrum | Hotelgäste zahlen 50 % mehr |

So führen mehrere Wege zu 4 und 5 Sternen: Büros und Wohnungen gemischt, Büros mit Wohnheimen (die sich gegenseitig hochziehen), Büros mit U-Bahn und Hotels, oder viele Wohnungen mit Kitas und Penthouses, die irgendwann doch Büros brauchen. Drei Bots spielen das durch, siehe „Bots“ unten.

## Tagesablauf und Wirtschaft (ab M5)

- Jeder Mieter bringt Leute mit (Büro 6, Wohnung 3, Imbiss 3). Sie haben eine Schicht: Tag (9–17), Spät (14–22) oder Nacht (22–6). Angestellte kommen zur Schicht, Bewohner sind während ihrer Schicht unterwegs und sonst zu Hause. Büros haben am Wochenende zu.
- Laune: Ohne Extras pendelt sie sich bei 55 % ein. Wer heute oder gestern im Imbiss gegessen hat, landet 15 Punkte höher, der Kopierraum gibt Büroleuten 5 Punkte, der Ufo-Landeplatz allen 3. Lange Wartezeiten am Aufzug und unerreichbare Ziele drücken sie für eine Weile. Die Sterne 3, 4 und 5 brauchen 60, 64 und 68 % Laune, und solange die Laune darunter liegt, ziehen jede Nacht die unzufriedensten Mieter aus.
- Um Mitternacht: Miete rein (halbe Miete bei schlechter Laune), Unterhalt raus, sehr unzufriedene Mieter ziehen aus und nennen dabei den echten Grund (Aufzug, kein Weg, kein Essen).
- Geld: Miete und Gebühren steigen um 15 % pro Stern. Ab 3 Sternen kosten vermietete Büros und Imbisse jeden Tag Betriebskosten, auch am Wochenende: 5 % ihrer Grundmiete bei 3 Sternen, 10 % bei 4 und 15 % bei 5. Ein normales Büro kostet ab 3 Sternen mehr (18.000, 24.000, 30.000 $), der Abriss erstattet ein Viertel dessen, was es beim Bau gekostet hat.
- Tag und Nacht: Himmel und Räume wechseln mit der Uhrzeit (Nacht-Atlas), unzufriedene Räume verwahrlosen sichtbar (Rundown-Atlas).

## Ereignisse, Spielstand (ab M6)

Jeder Stern bringt eine eigene Lobby-Grafik. Zur vollen Stunde kann etwas Absurdes passieren (`core/.../Events.kt`): Büros werden zur Clownsagentur, in Wohnungen spukt es, im Aufzug wird geheiratet, Tauben besetzen die oberste Etage, ein Influencer schickt Busladungen voller Fans, und ab 4 Sternen kommt Frau Dr. Wichtig zum VIP-Besuch. Der Sicherheitsdienst macht schlimme Ereignisse seltener. Alles landet im Tower-Ticker.

Manche Ereignisse dauern eine Weile (`core/.../LastingEvents.kt`) und zeigen ein lila Blitz-Symbol über dem Raum: Hochzeit oder Karaoke im Aufzug, Vampir-Blutspende, Clown-Aufstand, Socken-Lawine, Superschurken-Erpressung, Feueralarm durch Toaster, Termiten, Alien-Austausch, Riesenkatze auf dem Dach, der Turm auf dem Mond. Ein Klick auf den Raum zeigt im Inspektor, was los ist, und meist einen Knopf mit der Gegenmaßnahme. Ab 3 Sternen kann man einmal pro Woche über den Luftballon-Knopf oben einen Tag der offenen Tür ausrufen. Laufende Ereignisse werden mitgespeichert.

### VIP-Besuche

Ab 3 Sternen schauen ab und zu VIPs vorbei (zwischen 9 und 19 Uhr, höchstens alle 12 Stunden, mit jedem Stern öfter): Baron von Goldzahn, Zirkusdirektor Tröt-Maximus, Graf Zahlula, ab 4 Sternen Dr. Dividende, Dr. Hämisch und Prof. Morgen, ab 5 Sternen Botschafter Zorg. Dazu kommen erfundene deutsche Streamer, die unterwegs live gehen und ein paar Fans mitbringen: xXBerndZocktXx, ASMR-Annika und Vlog-Vanessa, ab 4 Sternen Speedrun-Sören und Mukbang-Mehmet, ab 5 Sternen Oma Gisela Gaming. Sie besuchen einen vermieteten Raum, am liebsten ihre Lieblingsmieter (Zahlula die Vampir-Versicherung, Tröt-Maximus die Clowns). Am Ziel urteilen sie über die Laune im Turm, die Laune im besuchten Raum, wie lange der Weg gedauert hat und wie sauber die Hotelzimmer sind. Der Weg zählt dabei am meisten: ab 90 Minuten gibt es dafür keine Punkte mehr. Gefällt es ihnen, lassen sie ein dickes Trinkgeld da (6.000 $ pro Stern oder 15 % der gestrigen Tageseinnahmen, was mehr ist), sonst nur Kleingeld und eine Beschwerde im Ticker, die verrät, was gestört hat. Das Trinkgeld steht im Finanzfenster unter „VIP-Trinkgeld“ (`core/.../Vips.kt`).

`F5` speichert, `F9` lädt (`~/.crazytower/spielstand.json`). Jedes Speichern schreibt erst in eine neue Datei und behält den Stand davor als `spielstand.bak.json`; lädt der Spielstand nicht, kommt die Sicherung.

### Sicherheitsnetz (`core/.../Undo.kt`)

- **Rückgängig** (`Strg+Z`): Der letzte Bau oder Abriss lässt sich eine Spielstunde lang zurücknehmen. Ein Bau bringt den ganzen Preis zurück, solange niemand eingezogen ist und nichts darauf steht; ein ganzer Schacht gilt als ein Schritt. Ein Abriss kommt nur zurück, wenn die Räume leer waren, und kostet die Erstattung wieder. Lobbys und Teile der Mondstation sind ausgenommen.
- **Rückfrage**: Vor dem Abriss eines vermieteten Raums fragt das Spiel nach und sagt, was es kostet (Mieter ziehen aus, Kaufpreis an die Besitzer). Abschaltbar unter Optionen → „Abriss nachfragen“.
- **Pausenmenü**: Esc ohne offenes Fenster hält das Spiel an; zu ist es wieder im alten Tempo.

## Geld ausgeben ab 4 Sternen (`core/.../Endgame.kt`)

Ab 4 Sternen gibt es mehr zu kaufen als Räume. Die laufenden Kosten richten sich nach den gestrigen Einnahmen, damit sie für kleine und große Türme gleich spürbar sind.

- **Ausbau** (Inspektor): Büros, Wohnungen, Hotels, Imbisse, Läden und Wohnheime bekommen bis zu drei Stufen, jede +15 % Miete und +3 Laune. Die Namen hängen vom Mieter ab (Clowns bekommen einen Hüpfburg-Boden, Vampire einen Sarg-Ruheraum). Ausgebaute Räume tragen kleine Sterne.
- **Aufzug-Tuning** (Inspektor am Schacht): Turbo-Motor (30 % schneller), Streichquartett (Wartende werden später grantig), Liftboy mit Monokel (mehr Plätze, schnellere Türen).
- **Wunschmieter anwerben** (Inspektor an leeren Räumen): eine Zeitungsanzeige, und der gewählte Mieter zieht sofort ein.
- **Extras-Fenster** (`X`): Versicherungen gegen Tauben, Clowns, Spuk, Stromausfall und Aufzugspannen (verhindern die meisten dieser Ereignisse), eine Werbekampagne (3 Tage mehr Besucher und VIPs), das wöchentliche Turmfest (+10 Laune für 2 Tage), Deko für das Dach (Gummiente, Leuchtschrift, Discokugel, goldene Fassade, Lasershow; jede bringt mehr Schaulustige), drei Seiteneingänge aus Oma Hildegards Garten (kürzere Wege von der Straße) und das Labor für fragwürdige Wissenschaft (Kaffee aus dem Kopierraum, selbstputzende Handtücher, Schallschluck-Tapete, Geduldspille).

## Mondmission ab 5 Sternen (`core/.../Mission.kt`)

5 Sterne sind das Ende der Leiter, das Spiel geht aber weiter: Auf der obersten Etage (99) entsteht Teil für Teil eine Mondstation. Jedes Teil braucht zwei Aufträge im Turm, danach wird es im Baumenü unter Spezial gebaut. Der Fortschritt steht in der Sterne-Anzeige und im Missionsfenster (`M`).

| Countdown | Teil | Kosten | Aufträge |
| --- | --- | --- | --- |
| 3 | Startrampe | 5 Mio. $ | 20 Nächte Regierungsbeamte gut gelaunt im Hotel; 10.000 Antragsformulare aus dem Kopierraum |
| 2 | Treibstofflager | 15 Mio. $ | 40.000 Becher Kaffee aus den Imbissen; 5 Büros für die Raketen für Anfänger GmbH |
| 1 | Kommandokapsel | 40 Mio. $ | 10 Astronauten mit über 75 % Laune; einen Tag Probezünden mit mindestens 65 % Turm-Laune |
| Abheben! | Mondlift | 100 Mio. $ | Botschafter Zorg geht zufrieden nach Hause; der Sicherheitsdienst stoppt Dr. Hämisch dreimal |

Mit jedem Teil kommen mehr Schaulustige (50 am Anfang, 1.500 nach dem Abheben). Sie fahren zur Aussichtsplattform (ab Etage 90, Eintritt 150 $, auf einer schmutzigen Plattform nur die Hälfte) und kaufen danach im Souvenirladen ein. Dauert der Weg länger als zweieinhalb Stunden oder fehlt die Plattform, gehen sie verärgert heim; sind es mehr als 40 % an einem Tag, lacht die Presse und der Countdown wartet einen Tag. Nach dem Abheben gibt es einen Abspann, und der Turm baut einfach weiter.

## Bots

`./gradlew :core:playthrough --args="<tage> <seed> <minuten>" -Dbot=twin|wide|hub|miser|all` lässt Bots ohne Grafik spielen, bis die Mondmission abhebt (oder die Tage um sind) (`core/src/test/.../bot/`). Mit `-Dcsv=<ordner>` schreibt jeder Lauf eine CSV-Zeile pro Tag. Alle Bots fragen vor jedem Raum die Wegsuche des Spiels, wie lange der Weg von der Straße dauert, heute und für jede Breite, auf die das Grundstück noch wächst (die Leute kommen vom näheren Straßenende, und das rechte Ende wandert mit). Über 72 Minuten bauen sie dort nicht.

| Bot | Platzierung | Was er baut |
| --- | --- | --- |
| `twin` Zwillingstürme | zwei schmale Türme an den Straßenenden, Express ganz außen, je zwei Aufzugssäulen; die Mitte erst, wenn beide Türme oben anstoßen; klettert, sobald die Sterne es erlauben | Büros, Wohnheime, Hotels, Luxusbüros |
| `wide` Breitbau | ganze Grundstücksbreite, alle 24 Felder ein Aufzug, eine neue Säule erst, wenn bei den alten nichts mehr frei ist, eine neue Zone erst, wenn die untere voll ist | Wohnungen zuerst, mit Läden, Kitas, Penthouses; Büros und Wohnheime, wenn Käufer fehlen |
| `hub` Express-Treppen | keine lokalen Aufzüge über der 9. Etage: Expressaufzüge mit Treppenhaus daneben, höchstens zwei Etagen zu Fuß | Hotels, Büros, Luxusbüros, Konferenzzentrum |
| `miser` Geizhals | wie Express-Treppen | wie Express-Treppen, kauft aber keine Extras, nur was die Mondmission braucht |

Ab 4 Sternen kaufen alle Bots außer dem Geizhals Extras: Tuning für Schächte mit langen Wartezeiten, Ausbau für die Räume, die am meisten Miete bringen, Versicherungen, Turmfest, Werbung, Seiteneingänge, Forschung und während der Mission Deko. Für die Mission bauen sie Aussichtsplattform und Souvenirläden, die Räume für die offenen Aufträge, schicken den Sicherheitsdienst zu Dr. Hämisch, zünden probe, wenn die Laune reicht, und bauen die Stationsteile auf Etage 99 (wenn dort alles voll ist, reißen sie Platz frei). Die Log-Zeile zeigt ab 4 Sternen das Geld in Tagen Einnahmen und die Ausgaben je Senke, ab 5 Sternen den Countdown, die Aufträge und die Schaulustigen; die Zusammenfassung zeigt, wie viele Tage jeder Countdown-Schritt gedauert hat.

### Viele Bot-Läufe parallel

`./gradlew :core:bots --args="<tage> <seeds> <minuten>" -Dbot=twin,hub|all -Djobs=<n>` startet jeden Lauf (Bot × Seed) als eigenen Prozess mit einem Thread, so viele gleichzeitig wie `-Djobs` sagt (ohne Angabe: Kerne − 1). Seeds gehen als `1-8`, `1,5,9` oder Mischung. Jeder Lauf schreibt sein ganzes Log nach `core/build/bots/<bot>-<seed>.log` (dazu die CSV pro Tag), auf der Konsole steht nur eine Zeile, wenn ein Lauf fertig ist. Am Ende kommen eine Tabelle aller Läufe und eine Zusammenfassung je Bot (Median und Spanne für Abheben, Stufe 5, 5 Sterne, Leute, Laune, Geld), gespeichert als `zusammenfassung.txt` und `ergebnisse.csv`. Weitere Schalter: `-Dout=<ordner>`, `-Dxmx=<heap je Lauf>` (Standard 2g). Die Ergebnisse sind dieselben wie bei `:core:playthrough`.

Unter Windows in PowerShell im Projektordner (die `-D`-Schalter in Anführungszeichen):

```powershell
.\gradlew.bat :core:bots --args="200 1-8" "-Dbot=all" "-Djobs=6"
```

Die Aufzugssäulen der Zonen stehen in allen Zonen an derselben Stelle (je 24 Etagen, die Etage unter der nächsten Sky-Lobby über eine Treppe), damit der Umstieg in der Sky-Lobby kurz bleibt.
