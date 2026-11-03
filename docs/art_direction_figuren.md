# Art Direction: Figuren

Alle Figuren im Turm sollen aussehen, als hätte sie dieselbe Person gezeichnet. Als Vorbild gelten
die Figuren, die schon im Spiel sind (`Figuren/figuren_uebersicht.png` im Projektordner, in der
ComfyUI-Zip als `art_direction_referenz.png`).

## Regeln

| Punkt | So soll es sein | So nicht |
|---|---|---|
| Technik | flache 2D-Cartoon-Zeichnung wie ein handgezeichnetes Indie-Spiel-Sprite | 3D, Low Poly, Render, Verläufe, echte Texturen |
| Proportionen | Chibi: Kopf etwa ein Drittel der Körperhöhe, kleiner Körper, kurze Beine | realistische Proportionen, Riesen und Zwerge (Größe macht das Import-Skript) |
| Gesicht | jeder Mensch hat ein Gesicht im Profil: Punktauge, Augenbraue, Nase, Mund, Ohr, witziger Ausdruck | leere Gesichter, Frontansicht |
| Umriss | kräftiger, gleichmäßiger schwarzer Umriss um die ganze Figur, dünne schwarze Linien innen | kein Umriss, farbiger Umriss |
| Schattierung | Cel-Shading: pro Farbe ein dunklerer Ton und ein kleines Glanzlicht, Licht von oben links | weiche Verläufe, Ambient Occlusion |
| Farben | kräftig, satt, freundlich; Haut in natürlichen Hauttönen | grau, blass, Pastell überall |
| Lesbarkeit | einfache Formen, eine typische Requisite pro Figur, groß genug, um bei 48×96 Pixeln erkennbar zu sein | viele kleine Details |
| Blickrichtung | Seitenansicht, schaut nach rechts (das Spiel spiegelt für links) | schräg, von vorne |
| Posen | links stehen (Füße zusammen), Mitte Schritt (hinteres Bein ist das dunkle), rechts der andere Schritt (vorderes Bein ist das dunkle) | zwei gleiche Schritte |
| Gleichheit | in allen drei Posen dasselbe Gesicht, Haar, Hut, Kleidung und dieselbe Requisite in derselben Hand | Hut oder Requisite fehlt in einer Pose |
| Hintergrund | einfarbig Magenta #FF00FF, ohne Muster, ohne Bodenschatten | Flecken, Muster, Schatten |

## Wo die Regeln stecken

- **Prompt:** Der `Stil:`-Block oben in `prompts_figuren.txt` ist diese Tabelle in Prompt-Form und steht vor jeder Figur.
  Die Figurbeschreibung danach nennt nur noch die Figur, nie den Stil.
- **Vorlage:** `vorlage_figur.png` gibt Größe, Bodenlinie, Posen und die dunklen hinteren Beine vor.
- **Import:** `tools/art/import_people.py` gleicht jede neue Figur an: Farbstärke und Helligkeit wie bei den
  bisherigen Figuren (`STYLE_SATURATION`, `STYLE_BRIGHTNESS`), geschlossener dunkler Umriss, harte Kanten,
  gleiche Größe (Erwachsene 83 von 96 Pixeln). `--raw` schaltet das Angleichen aus.

## Neue Figur beschreiben

Eine Zeile: wer (Beruf, Alter), Frisur oder Kopfbedeckung, 2–3 Kleidungsstücke mit Farbe, eine Requisite
in der Hand, ein Gesichtsausdruck. Beispiel:

> a snack bar cook: a big belly, a moustache, a crooked white cook hat, a stained white apron over a tank top, holding tongs with a sausage, sweaty grin.

Nach dem Import alle Figuren in `people_preview.png` nebeneinander ansehen. Was aus der Reihe fällt, mit
`--nur <name>` neu erzeugen.
