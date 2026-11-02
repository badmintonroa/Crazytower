# Neue Figuren mit ComfyUI (img2img)

Die ComfyUI-Dateien liegen im Projektordner unter `crazytower/comfyui/`.

## 1. Erzeugen

| Datei | Wofür |
|---|---|
| `batch_figuren.bat` | schickt alle Figuren aus `prompts_figuren.txt` an ComfyUI, 3 Varianten je Figur, Stärke 0.85 |
| `prompts_figuren.txt` | ein Prompt pro fehlender Figur, `Name:` ist gleich der Name im Spiel |
| `vorlage_figur.png` | Figur in den drei Posen (Stehen, Schritt links, Schritt rechts), 1536×512 auf Magenta: Kleidung grau, Haut in Hautfarbe, damit die neuen Figuren keine graue Haut bekommen |
| `vorlage_figur_bunt.png` | dieselbe Figur ganz in Farbe, falls die Figuren immer noch zu blass werden (dann färbt eher das hellblaue Hemd ab) |
| `vorlage_figur_klein.png`, `vorlage_figur_tier.png` | kleinere Vorlagen (Kind, Pinguin) für kleine Figuren |
| `ct_figur_img2img_krea2.json` | derselbe Ablauf zum Ausprobieren in ComfyUI (Workflow laden, Vorlage und Prompt tauschen) |

Die Vorlage gibt Größe, Stand auf dem Boden und die drei Posen vor, der Prompt bestimmt die Figur.
Im Prompt-Block kann man pro Figur `Bild: vorlage_figur_tier.png` und `Denoise: 0.9` setzen.
Bleibt die Figur zu nah an der Vorlage (grau, blass, Hemd mit Krawatte), Denoise erhöhen (0.9 bis 0.95)
oder mit `--image vorlage_figur_bunt.png` die farbige Vorlage nehmen;
zerfallen die Posen, Denoise senken (0.75 bis 0.8).

Die Bilder landen in `ComfyUI/output/crazytower/figuren/`, z.B. `npc_cook_v2_00001_.png`.

## 2. Ins Spiel holen

Die guten Varianten in einen Ordner legen (die übrigen löschen) und im Repo ausführen:

```
python tools/art/import_people.py "D:\ai\ComfyUI\output\crazytower\figuren"
```

Das Skript stanzt das Magenta aus, schneidet die drei Posen aus, skaliert sie auf die Größe der
übrigen Figuren (Erwachsene 83 von 96 Pixeln) und schreibt
`game/src/main/resources/people/<name>.png` (144×96, drei Bilder zu 48×96) samt Eintrag in
`index.txt`. Im selben Ordner wie die Bilder entsteht `people_preview.png`: links die Figur, die das
Spiel bisher gezeigt hat, rechts die neue.

- Nur nachsehen, ohne etwas ins Spiel zu schreiben: `--dry-run`
- Größe ändern: `--size npc_dragon=1.1` (1.0 = Erwachsener). Feste Werte für Roboter, Drache und Co.
  stehen oben im Skript in `SIZES`.
- Gibt es mehrere Bilder für dieselbe Figur, gewinnt das neueste. Einzelne Dateien kann man auch direkt angeben.

## 3. Was danach passiert

Die Mieter und VIPs in `Tenants.kt` und `Vips.kt` benutzen die neuen Namen schon. Solange ein Bild
fehlt, zeigt das Spiel die bisherige Figur aus `core/.../Looks.kt` (`standIns`). Sobald das Bild
importiert ist, erscheint die neue Figur, ohne dass Code geändert werden muss.

Fehlende Figuren und wer sie benutzt:

| Name | Wer | Bis dahin |
|---|---|---|
| `npc_influencer` | Influencerin Lara Like | `npc_student_gamer` |
| `npc_retiree_2` | Herr Ömmel (Ehepaar Ömmel) | `npc_retiree` |
| `npc_popstar` | Popstar Glitzer-Gabi | `npc_student_party` |
| `npc_heir` | Erbe Konstantin von Yacht | `npc_vip` |
| `npc_astronaut` | Astronauten-WG | `npc_timetraveller` |
| `npc_banker` | Hedgefonds Geld & Mehr Geld | `npc_office_boss` |
| `npc_dragon` | Drachenbank Goldhort | `npc_supervillain` |
| `npc_atlantean` | Konsulat von Atlantis | `npc_penguin` |
| `npc_bride`, `npc_groom` | Hochzeitsgäste | `npc_family_mom`, `npc_family_dad` |
| `npc_rocker` | Rockband Die Lärmschutzwände | `npc_student_party` |
| `npc_alien_tourist` | Weltraumtouristen | `npc_alien` |
| `npc_queen` | Die Königin von Klein-Irgendwo | `npc_vip` |
| `npc_diva` | Filmdiva Dolores Drama | `npc_student_artist` |
| `npc_billionaire_bathrobe` | Milliardär im Bademantel | `npc_office_boss` |
| `npc_robot_vacuum` | Saugroboter-Schwarm | `npc_office_it` |
| `npc_cook` | Imbiss Zum Schiefen Turm | `npc_family_dad` |
| `npc_sushi_chef` | Sushi am Band | `npc_startup` |
| `npc_barista` | Café Koffeinschock | `npc_office_intern` |
| `npc_spaeti` | Späti Alles Da | `npc_family_dad` |
| `npc_robo_auntie` | Tante Robo (Automatenladen) | `npc_office_it` |
| `npc_baron_goldzahn` | VIP Baron von Goldzahn | `npc_vip` |
| `npc_vlogger` | VIP Vlog-Vanessa | `npc_tourist` |
| `npc_alien_ambassador` | VIP Botschafter Zorg | `npc_alien` |

Eine ganz neue Figur: Namen in `Looks.kt` mit einer Ersatzfigur eintragen, im Mieter benutzen,
Prompt in `prompts_figuren.txt` ergänzen.
