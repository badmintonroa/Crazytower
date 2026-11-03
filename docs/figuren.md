# Figuren mit ComfyUI (img2img)

Die ComfyUI-Dateien liegen im Projektordner unter `crazytower/comfyui/`. Alle 64 Figuren des Spiels
(40 bisherige und 24 neue) werden mit demselben Stil, derselben Vorlage und demselben Import erzeugt,
damit sie zusammenpassen. Die Regeln dafür stehen in `art_direction_figuren.md`.

## 1. Erzeugen

### Empfohlen: in zwei Stufen (Qwen-Image 2.1 Turbo Edit)

Damit alle drei Posen dieselbe Figur zeigen, entsteht erst nur das Stehbild, danach die zwei Schritte dazu.

1. **Zum Ausprobieren:** `test_stufe1.bat` macht nur 3 Stehbilder (Koch, Drache, Königin), einmal mit Krea und einmal
   mit Qwen 2.1, zum Vergleichen.
   Für alle Figuren: `batch_figuren_stufe1.bat` (4 Varianten pro Figur).
   Ergebnis: `ComfyUI/output/crazytower/figuren_stufe1_krea2/` und `figuren_stufe1_qwen21-edit/`.
   Qwen bekommt als Vorlage `vorlage_figur_stand.png` (eine stehende Puppe) und als Stil-Referenzen
   `stil_referenz.png` und `ref_stand_1–5.png`. Krea zeichnet das Stehbild ohne Vorlage nur aus dem Prompt,
   weil die graue Puppe bei Krea sonst durchdrückt (graue Haut, graue Extra-Figuren daneben).
2. In diesen Ordnern die Stehbilder löschen, die nicht gefallen. Pro Figur darf eins oder mehrere übrig bleiben.
3. `test_stufe2.bat` bzw. `batch_figuren_stufe2.bat`: schneidet jedes übrige Stehbild aus, setzt es so groß wie die Puppe
   auf deren Bodenlinie links in die 3-Posen-Vorlage und lässt
   nur die zwei Schritte daneben zeichnen, mit dem Stehbild als Vorbild. Ergebnis: `figuren_stufe2_<modell>/`.
   Qwen bekommt das Stehbild als Bild 1. Krea kann keine Referenzen, dort bleibt das linke Drittel per Maske
   (`maske_schritte.png`) stehen und nur Mitte und rechts werden neu gezeichnet.
   Liegt dein ComfyUI nicht unter `D:\ai\ComfyUI`, in der .bat den Pfad bei `OUT` anpassen.
4. `figuren_stufe2_<modell>` wie unten beschrieben mit `import_people.py` ins Spiel holen.

### In einem Schritt

| Datei | Wofür |
|---|---|
| `batch_figuren.bat` | schickt alle Figuren aus `prompts_figuren.txt` an ComfyUI, 3 Varianten je Figur, Stärke 0.85 |
| `prompts_figuren.txt` | oben der `Stil:`-Block (Art Direction), dann ein Prompt pro Figur, `Name:` ist gleich der Name im Spiel |
| `art_direction_referenz.png` | die bisherigen Figuren als Vorbild für den Stil |
| `vorlage_figur.png` | gezeichnete Puppe in den drei Posen (Stehen, Schritt mit vorderem Bein vorn, Schritt mit hinterem Bein vorn), 1536×512 auf Magenta; das hintere Bein und der hintere Arm sind dunkler. Neu zeichnen: `python tools/art/make_figure_template.py <Ordner>` |
| `vorlage_figur_bunt.png` | dieselbe Figur ganz in Farbe, falls die Figuren immer noch zu blass werden (dann färbt eher das hellblaue Hemd ab) |
| `vorlage_figur_klein.png`, `vorlage_figur_tier.png` | kleinere Vorlagen (Kind, Pinguin) für kleine Figuren |
| `ct_figur_img2img_krea2.json` | derselbe Ablauf zum Ausprobieren in ComfyUI (Workflow laden, Vorlage und Prompt tauschen) |

Die Vorlage gibt Größe, Stand auf dem Boden und die drei Posen vor, der Prompt bestimmt die Figur.
Im Prompt-Block kann man pro Figur `Bild: vorlage_figur_tier.png` und `Denoise: 0.9` setzen.
Bleibt die Figur zu nah an der Vorlage (grau, blass, Hemd mit Krawatte), Denoise erhöhen (0.9 bis 0.95)
oder mit `--image vorlage_figur_bunt.png` die farbige Vorlage nehmen;
zerfallen die Posen, Denoise senken (0.75 bis 0.8).

### Mit Qwen statt Krea

| Datei | Wofür |
|---|---|
| `batch_figuren_qwen21.bat` | **Empfohlen:** Qwen-Image 2.1 Turbo Edit. Bild 1 ist die Vorlage (wird bearbeitet), Bild 2–7 sind Stil-Referenzen: `stil_referenz.png` und `ref_figur_1.png` bis `ref_figur_5.png` (bisherige Figuren in den drei Posen). 8 Schritte, ohne Prompt-Verbesserer. Weitere Referenzen: Datei `ref_figur_6.png` usw. dazulegen und in `STYLE_REFS` eintragen (bis zu 9) |
| `ct_figur_qwen21_edit_api.json` | derselbe Ablauf zum Reinziehen in ComfyUI |
| `batch_figuren_qwen.bat` | dasselbe mit Qwen-Image (img2img mit der Vorlage, echter Negativ-Prompt, 20 Schritte) |
| `batch_figuren_qwen-edit.bat` | Qwen-Image-Edit 2509: bekommt die Vorlage (Posen) **und** `stil_referenz.png` (vier bisherige Figuren) als Bilder und zeichnet im Stil der Referenz. Für einen einheitlichen Stil der beste Weg |
| `ct_figur_qwen_api.json`, `ct_figur_qwen_edit_api.json` | dieselben Abläufe zum Laden in ComfyUI (API-Format, per Drag & Drop) |

**Eigener Workflow (z.B. Qwen-Image 2.1 mit eigenem Lader):** Den Workflow in ComfyUI so bauen, dass er
mit einem Bild läuft, im `Load Image`-Node `vorlage_figur.png` wählen und über *Workflow > Export (API)*
als `mein_workflow_api.json` in den comfyui-Ordner speichern. Dann `batch_figuren_eigen.bat` starten.
Der Batch setzt pro Figur Prompt, Vorlage, Seed, Denoise und Dateinamen ein, der Rest bleibt wie in
deinem Workflow. Das Prompt-Feld findet er selbst (Text am `positive`-Eingang); klappt das nicht, ins
Prompt-Feld `CT_PROMPT` schreiben und neu exportieren. Einen Prompt-Verbesserer (PE-Modell) am besten
ausschalten, sonst schreibt er die Art Direction um. Ohne `Load Image` im Workflow entsteht das Bild nur
aus dem Prompt, dann stimmen Größe und Posen weniger genau.

Erwartete Dateinamen für die eingebauten Qwen-Abläufe: `qwen_image_fp8_e4m3fn.safetensors`, `qwen_image_edit_2509_fp8_e4m3fn.safetensors`,
`qwen_2.5_vl_7b_fp8_scaled.safetensors`, `qwen_image_vae.safetensors`. Heißen deine anders, oben in
`batch_krea2.py` bei `MODELS` ändern oder `--unet <datei> --clip <datei>` mitgeben. Die Bilder landen in
`output/crazytower/figuren_qwen/` bzw. `figuren_qwen-edit/`.

Nur einen Teil erzeugen: `batch_figuren.bat --nur npc_clown,npc_cook` (der Anfang des Namens reicht).

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

Bisherige Figuren werden dabei ersetzt, die Vorschau zeigt alt und neu nebeneinander. Am einheitlichsten
wird es, wenn alle Figuren aus demselben Durchlauf kommen und zusammen importiert werden.

- Nur nachsehen, ohne etwas ins Spiel zu schreiben: `--dry-run`
- Das Skript gleicht Farbstärke, Helligkeit und Umriss an die Art Direction an. Ohne das: `--raw`
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
