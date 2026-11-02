# Musik

Die Hintergrundmusik ist im Code komponiert und wird beim Start vom Spiel selbst erzeugt (`game/.../MusicSynth.kt`). Es gibt keine Samples, keine Aufnahmen und keine fremden Stücke. Damit gehört die Musik dem Projekt wie der restliche Code und darf ohne Lizenzfragen auf Steam verkauft werden. Gleiches gilt für die Geräusche (`Sfx.kt`) und die Trailer-Musik (`tools/steam/music.py`).

| Stück | Wann | Stil |
| --- | --- | --- |
| Tag | von etwa 6 bis 20 Uhr, und immer, wenn „Tag und Nacht“ aus ist | 84 BPM, F-Dur, Lo-Fi: E-Piano, weicher Bass, Besen und Shaker, Flöte und Glockenspiel, 32 Takte (gut 1½ Minuten) |
| Nacht | von etwa 20 bis 6 Uhr | 66 BPM, a-Moll: Flächen, leises Klavier, Spieluhr, 24 Takte (knapp 1½ Minuten) |

Beide Stücke loopen nahtlos (Nachhall und ausklingende Töne laufen in den Anfang zurück). Beim Wechsel von Tag zu Nacht blendet `Music.kt` fünf Sekunden lang über; ein Stück, das ganz ausgeblendet war, beginnt beim nächsten Mal von vorn.

## Einstellungen

Optionen → „Musik“ (aus, 10 % … 100 %, voreingestellt 50 %) und „Geräusche“ für die Klicks und Effekte. Änderungen werden weich übernommen. Bei „aus“ bekommt die Soundkarte nichts mehr.

## Anhören

```bash
CRAZYTOWER_SOUNDS=out/sounds ./gradlew :game:run   # alle Geräusche plus musik_tag.wav und musik_nacht.wav
```

## Eigene Musik

Eine WAV-Datei unter `game/src/main/resources/music/day.wav` bzw. `music/night.wav` ersetzt das erzeugte Stück gleichen Namens (16 Bit, mono oder stereo, jede Abtastrate; sie wird auf 44,1 kHz umgerechnet). Die Datei sollte selbst sauber loopen. Wer fremde Musik einbaut, braucht eine Lizenz, die den kommerziellen Verkauf erlaubt (z. B. CC0 oder gekauft mit Spiele-Lizenz), und trägt Quelle und Lizenz hier ein.
