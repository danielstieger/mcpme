# Toolkit-Rückkanal – Ereignisse vom Toolkit an die DataUX-Runtime

## Zweck und Geltungsbereich

Dieses Dokument beschreibt, was ein Toolkit bei Benutzerereignissen an der Runtime aufruft. Die Gegenrichtung steht in der [Referenz](toolkit-referenz.md), die Abläufe im [Lebenszyklus](toolkit-lebenszyklus.md), die Signaturen in [Signaturen](toolkit-signaturen.md). Paketkürzel und die Kennzeichnung **[V]** / **[T]** / **[F]** / **[H]** erklärt die [Einstiegsseite](toolkit-schnittstelle.md).

Der Rückkanal ist kein eigenes Interface. Er besteht aus Methoden an Objekten, die das Toolkit über die Schnittstelle erhält, und aus Methoden an `ApplicationMDI`, die das Toolkit selbst erzeugt.

## Kapitellandkarte

| Benutzerereignis | Aufruf des Toolkits | Ziel erhalten über | Nötig |
| --- | --- | --- | --- |
| Klick auf Menüeintrag, Tile, Schaltfläche einer Action | `CmdAction.startCommand()` | `setMenuAndInit`, `showTiles`, `addMenuAndSetButtons` | ja |
| Klick auf Conclusion-Button | `ICommandContainer.receiveAndProcess(new ConclusionEvent(hash, text))` | `setCommandContainer` | ja |
| Abbrechen, ESC | `ConclusionEvent(0, text)` oder `KeyEvent(false, "ESC")` | `setCommandContainer` | ja |
| Quittierung eines Dialogs | `IApplication.DlgRunnable.run(confirmed)` | `showDialog`, `showProblemsDialog` | ja |
| Zeilenauswahl in einer Tabelle | `IGenSelControlled.pushSelection(new Selection(...))` | `setFormController` | ja |
| Klick auf die Korrekturaktion eines Problems | `IOFXProblem.getResolveActionOrNull().performAction()` | `setProblems` | ja, sofern angeboten |
| Hotkey im Command-Fenster | `ConclusionEvent` oder `ICommandContainer.receiveAndProcess(new KeyEvent(mod, key))` | `setCommandContainer` | nein |
| Hotkey außerhalb eines Command-Fensters | `ApplicationMDI.onKeyPressEvent(new KeyEvent(mod, key))` | eigene Instanz | nein |
| Doppelklick oder Enter auf einer Tabellenzeile | `CmdAction.reevalEnabled()`, dann `startCommand()` der `ENTER`-Action | `addMenuAndSetButtons` | nein |
| Eingabe in einem Editor mit `ISSUE UPDATE/SCANABLE` abgeschlossen | `IDlgt.issueUpdateConclusionAfterContentChange()` oder die `UPD`-Conclusion direkt | `setDelegate`, `setConclusions` | nein |
| Tastatureingabe in einem Editor mit Hook | `IDlgt.keyReleasedEvent()` | `setDelegate` | nein |
| Wechsel des aktiven Tabs durch den Benutzer | `ApplicationMDI.onTabChangeEvent(index)` | eigene Instanz | nur mit Tabwechsel |
| Anwendung schließen, Abmelden | `ApplicationMDI.onExitRequested(false)` oder `internal_immediatelyShutdown()` | eigene Instanz | ja |
| Ende einer Hintergrundarbeit | `ICommandContainer.processPendingEvents()` | `execEventInBackground` | nur mit Hintergrund-Thread |

## Grundregeln [V]

- Alle Aufrufe erfolgen im UI-Thread, einer nach dem anderen.
- Ein Benutzerereignis erzeugt genau einen Aufruf. Doppelte Meldungen desselben Tastendrucks oder Klicks führen zu doppelter Ausführung.
- Jeder Aufruf kann synchron dazu führen, dass die Runtime das auslösende Fenster schließt oder seinen Inhalt ersetzt. Nach dem Aufruf darf das Toolkit nicht mehr auf den vorherigen Zustand zugreifen.
- Werte liest die Runtime selbst: Das Toolkit meldet keine Feldinhalte. Vor einer `save`-Conclusion ruft die Runtime `checkDelegatesValidAndFocus()` und liest anschließend `getText()` beziehungsweise `getSelectedIndex()` der Editoren. Ein Toolkit mit entfernter Oberfläche muss die Werte des Clients deshalb in seine Editor-Objekte übernehmen, **bevor** es die Conclusion meldet; siehe [Portierung: Das Protokoll von h2forms](toolkit-portierung.md#das-protokoll-von-h2forms).

## Actions

Eine `CmdAction` (`RT.genspecification.CmdAction`) steht für einen Command-Aufruf mit Parametern aus dem aktuellen Selektionskontext.

| Feld oder Methode | Verwendung im Toolkit |
| --- | --- |
| `labelText` | Übersetzte Beschriftung |
| `image` | Icon-Name oder `null` |
| `hotKey` | Hotkey oder `null`/leer |
| `getToolTip()` | Tooltip |
| `hideWhenDisabled` | Nicht bedienbares Element ausblenden |
| `globalHotkey` | `true`: Hotkey gilt im ganzen Command-Fenster beziehungsweise in der ganzen Anwendung; `false`: nur mit Fokus im Element (Tabelle) |
| `attachButton1(item)`, `attachButton2(item)` | Optional: Bedienelement für `setItemEnabled` registrieren |
| `reevalEnabled()` | Bedienbarkeit neu berechnen; liefert das Ergebnis und ruft `setItemEnabled` an den registrierten Elementen |
| `startCommand()` | Command starten |

Regeln [V]:

- Ein Klick ruft `startCommand()`. Ein nicht bedienbares Element darf nicht auslösen.
- Die Bedienbarkeit erfährt das Toolkit auf einem von zwei Wegen: durch Registrierung eines `IToolkit_EnableItem` (Push; [T], [F]) oder durch Aufruf von `reevalEnabled()` bei der Darstellung (Pull; [H]).
- Vor dem Auslösen ohne vorherige Darstellung der Bedienbarkeit – über Tastatur, Doppelklick oder einen Request des Clients – ruft das Toolkit `reevalEnabled()` und startet nur bei `true`. [H] beantwortet ein `false` mit dem unveränderten aktuellen Zustand.
- Bei einer Mehrfachselektion entscheidet `startCommand()` selbst über die [Mehrfachausführung](toolkit-lebenszyklus.md#mehrfachausführung).
- Eine Action im Menü einer Tabelle bezieht ihre Parameter aus der Selektion. [H] setzt deshalb vor dem Start die Selektion auf die angeklickte Zeile: `pushSelection`, `reevalEnabled()`, `startCommand()`.

## Conclusions

```java
container.receiveAndProcess(new ConclusionEvent(info.conclusionHashCode, info.buttonTitle));
```

- `info` ist der Eintrag der zuletzt mit `setConclusions` gelieferten Liste; `enabled` kommt aus dem letzten `reevalConclusions`.
- Der zweite Parameter dient nur der Protokollierung.
- **Bedienbarkeit prüft das Toolkit.** Ein `ConclusionEvent` für eine nicht bedienbare Nicht-ESC-Conclusion oder mit unbekanntem Hash führt in der Runtime zu einer Exception; der Command endet mit einem Fehlerdialog. Ein Toolkit, das Ereignisse von einem entfernten Client annimmt, muss veraltete Requests daher selbst abweisen.
- **Abbrechen** ist die Conclusion mit Hash `0` an Index 0. Die Rückfrage bei ungespeicherten Änderungen stellt die Runtime über `showDialog(QUESTIONCLOSE_SMALL, ...)`.
- Bietet ein Toolkit ein eigenes Bedienelement zum Schließen eines Command-Fensters an (Schließen-Kreuz), muss es ebenfalls die ESC-Conclusion senden und sie bei `NO_ESC` (ESC nicht enabled) unterlassen: Ein Fenster verschwindet nur durch `close()`.

## Hotkeys

Hotkeys sind für ein Toolkit optional. [H] kennt keine Tasten-Hotkeys; dort sind alle Conclusions und Actions nur über Schaltflächen erreichbar.

### Hotkey-Notation

Hotkeys sind Zeichenketten aus den `Static Ressources` des Modells. Der Vergleich in der Runtime ist ein exakter Zeichenkettenvergleich; das Toolkit meldet einen Tastendruck deshalb mit genau der Zeichenkette, die es als Hotkey erhalten hat.

| Zeichenkette | Taste |
| --- | --- |
| `A` … `Z` (ein einzelnes Zeichen) | **Ctrl** + Buchstabe |
| `F1` … `F12` | Funktionstaste |
| `ENTER`, `ESC`, `BACKSPACE`, `DELETE`, `INSERT` | gleichnamige Taste |
| `UP`, `DOWN`, `LEFT`, `RIGHT`, `HOME`, `END` | Navigationstaste |
| `PLUS`, `MINUS`, `EQUALS`, `ADD`, `SUBTRACT`, `MULTIPLY`, `DIVIDE` | Zeichen- beziehungsweise Nummernblocktaste |
| `UPD` | Semantischer Hotkey `SCAN/UPDATE`: keine Taste; ausgelöst durch Editoren oder Scan |
| `GO` | Semantischer Hotkey `GO/OK`: Standardaktion der Page auf MDE-Geräten |
| `DBG_GRAPH`, `DBG_SESSION`, `DBG_LOCKING` | Entwicklerwerkzeuge; kein Hotkey des Modells |

Feste Vorgaben der Runtime sind nur `"ESC"` als Hotkey der ESC-Conclusion, `"UPD"` und die drei `DBG_`-Werte. Alle anderen Zeichenketten stammen aus dem Modell; welche dort vorkommen, ist nicht belegt.

Die Implementierungen verstehen unterschiedliche Mengen:

- [T] zusätzlich `ESCAPE`, `N0` … `N9`, `KEYPAD-0` … `KEYPAD-9`, `PAGE-UP`, `PAGE-DOWN`.
- [F] meldet den Namen der JavaFX-Taste in Großbuchstaben. Das ergibt die Werte der Tabelle; die turkuforms-Schreibweisen `ESCAPE`, `N0`, `KEYPAD-0`, `PAGE-UP` entstehen dabei nicht (JavaFX nennt die Tasten `ESC`, `PAGE UP`, `NUMPAD 0`). Tiles nehmen nur einbuchstabige Hotkeys an.
- [H] wertet nur `UPD` und `GO` aus.

### `KeyEvent` und Modifier

`KeyEvent(withModifier, key)` ist ein reiner Datenträger. Die Runtime verwendet `isWithModifier()` an genau einer Stelle: Eine Conclusion wird per `KeyEvent` nur ausgeführt, wenn der Wert `false` ist. Für Hauptmenü-, Tile- und Page-Actions spielt er keine Rolle.

- [T] setzt `withModifier` nach der Regel „ein einzelnes Zeichen bedeutet Ctrl“.
- [F] setzt `withModifier = Ctrl oder Alt gedrückt`. Eine Conclusion mit einbuchstabigem Hotkey lässt sich in [F] deshalb nicht per Taste auslösen.

### Zwei Wege

Ein Toolkit mit Tasten wählt einen von zwei Wegen:

| | Registrieren ([T]) | Durchreichen ([F]) |
| --- | --- | --- |
| Prinzip | Das Toolkit registriert die Hotkeys, die es über die Schnittstelle erhält, und meldet nur diese. | Das Toolkit meldet jede Taste als `KeyEvent`; die Runtime ordnet zu. |
| Conclusion der aktuellen Page | In `setConclusions` registriert; bei enabled `ConclusionEvent`, sonst verwerfen | `KeyEvent` an die Runtime; sie führt die bedienbare Conclusion mit diesem Hotkey aus |
| Action im Menü des `Page Pane`s | `additionalHotkeys` in `setConclusions` registriert; `KeyEvent` an den Container | `KeyEvent`; `additionalHotkeys` wird ignoriert |
| Action im Menü einer `Table` | In `addMenuAndSetButtons` registriert; wirksam mit Fokus in der Tabelle; `reevalEnabled()`, dann `startCommand()` | wie turkuforms: Tasten-Handler der Tabelle |
| Action in Hauptmenü oder Tile | In `ensureHotkeyAvailable` registriert; `applicationMDI.onKeyPressEvent(...)` | `onKeyPressEvent` für jede Taste; `ensureHotkeyAvailable` ist leer |
| Escape-Taste | `ConclusionEvent(0, …)` | `KeyEvent(false, "ESC")` |

Die Runtime verarbeitet ein `KeyEvent` so:

- `ApplicationMDI.onKeyPressEvent`: `DBG_LOCKING` zeigt einen Hinweis. Außerhalb eines modalen Tabs startet die erste bedienbare Hauptmenü- oder Tile-Action mit diesem Hotkey. Sonst geht das Ereignis an den Container des aktiven Tabs – nie an ein offenes Prompt-Fenster.
- `CommandContainer.fg_conclusionKey`: `DBG_GRAPH` und `DBG_SESSION` öffnen Entwicklerdialoge. Sonst wird die bedienbare Conclusion mit diesem Hotkey ausgeführt (nur ohne Modifier), sonst die bedienbare globale Action der Page.

Daraus folgt [V]: Ein Prompt-Fenster meldet seine Tasten selbst an seinen Container (`container.receiveAndProcess(new KeyEvent(...))`).

Weitere Einzelheiten:

- [T] verhindert zusätzlich Browser-Funktionen auf F3, F4, F5, F6, F7, F10 und F12 und verwirft einen Hotkey, der im selben Request ein zweites Mal gemeldet wird. Entwicklerwerkzeuge liegen auf Shift+F5 und Shift+F6.
- [F] verwirft alle Tasten, solange die Oberfläche wegen Hintergrundarbeit gesperrt ist. Entwicklerwerkzeuge liegen auf Ctrl+Alt+F5, F6 und F7.

### `UPD` und `GO`

Beide Werte bezeichnen keine Taste, sondern eine Rolle der Conclusion:

| | `UPD` (`SCAN/UPDATE`) | `GO` (`GO/OK`) |
| --- | --- | --- |
| Bedeutung | Die Page nach einer Eingabe aktualisieren | Standardaktion der Page |
| turkuforms | Kein Button; Editoren lösen aus | Nicht belegt |
| fx8forms | Kein sichtbarer Button; Editoren lösen aus | Keine Behandlung; die Conclusion ist ein gewöhnlicher Button mit dem Zusatz „(GO)“ |
| h2forms | Verstecktes Scan-Feld und Kamera-Button; ein Barcode-Scan oder die Auswahl in einem Reference-Editor löst aus | Eigener „GO“-Button beziehungsweise die GO-Taste der Geräte-Tastenleiste; auf manchen Geräten Enter in einem Textfeld |

Die Runtime behandelt `GO` nirgends gesondert. Ein Toolkit kann die Conclusion über ihren Hash auslösen oder eine Taste als `KeyEvent(false, "GO")` melden.

## Tabellen

### Selektion

```java
controller.pushSelection(new Selection(rowClass, selectedObjects));   // Auswahl
controller.pushSelection(new Selection(rowClass));                    // leere Auswahl
```

- `controller` ist das `IGenSelControlled` aus `setFormController`, `rowClass` die Klasse aus `endOfInitializationForElementClass`. Die Runtime ordnet eine Selektion nur über die exakt gleiche Klasse zu. Mit der Laufzeitklasse einer Zeile (`new Selection(objekt)`) funktioniert die Zuordnung nur, solange die Zeilen keine abgeleiteten Klassen sind; sonst wird die Selektion stillschweigend nicht übernommen. [H] verwendet die Laufzeitklasse.
- Die Objekte sind dieselben Instanzen, die mit `loadList` geliefert wurden, bei mehreren in Anzeigereihenfolge.
- Gemeldet wird eine Änderung durch den Benutzer. `selectionChanged` löst keine Meldung aus.
- Ändert sich die Selektion, ruft die Runtime während `pushSelection` synchron `selectionChanged` an derselben Tabelle auf.

Bedienung:

- [T], [F] Klick wählt eine Zeile, Ctrl+Klick ergänzt, Shift+Klick wählt einen Bereich, Pfeiltasten bewegen die Auswahl, Ctrl+A wählt alles, ein Rechtsklick wählt die Zeile vor dem Öffnen des Kontextmenüs.
- [H] Es gibt keine eigenständige Auswahl. Ein Klick auf eine Zeile meldet die Selektion dieser einen Zeile und startet unmittelbar danach eine Action des Tabellenmenüs.

### Hauptaktion

[T], [F] Doppelklick auf eine Zeile und Enter mit Fokus in der Tabelle führen die Action des Tabellenmenüs aus, deren `hotKey` `ENTER` ist. Ohne solche Action geschieht nichts.

[H] Hat das Tabellenmenü genau eine Action, löst der Zeilenklick sie aus; bei mehreren klappt eine Leiste mit Schaltflächen auf. Ohne Menü sind die Zeilen nicht anklickbar.

### Inline-Bearbeitung

Für eine `EDITABLE`-Spalte schreibt das Toolkit den Wert selbst in das Zeilenobjekt:

1. `converter.convertBack(text)` wandelt die Eingabe um.
2. `ValueObjectReplacementFacility.put(objekt, property, wert)` (`RT.utils`) schreibt den Wert; für Pfade in ein Value Object ersetzt die Funktion das Value Object am Eigentümer.

[T] unterstützt nur `BigDecimal` und prüft vorher die Grenzen aus den Metadaten der Property (`IOFXMetaRangeScale`: Minimum, Maximum, Skala). [F] unterstützt `BigDecimal` und Status ohne Grenzprüfung. [H] kennt keine Inline-Bearbeitung.

Nach `forceNotEditable()` wird keine Eingabe mehr übernommen.

### Export

`controller.convertAsCsv(alleObjekte, selektierteObjekte, spalten)` liefert den Exporttext (Standard: Tabulator-getrennt, Kopfzeile, nur selektierte Zeilen und sichtbare Spalten; anpassbar mit `CUSTOM CSV EXPORT`). [T] und [F] legen ihn mit Ctrl+C in die Zwischenablage.

## Editoren

### Update-Conclusion

Ein Editor nach `setIssuesUpdateConclusion()` soll die Conclusion mit Hotkey `UPD` der aktuellen Page auslösen, wenn der Benutzer seine Eingabe abgeschlossen hat. Die Runtime führt sie mit Validierung und Speichern aller Formularwerte aus und lädt die Page neu. Zwei Wege sind belegt:

| | Über das Delegate ([T], [F]) | Als Conclusion ([H]) |
| --- | --- | --- |
| Aufruf | `delegate.issueUpdateConclusionAfterContentChange()` | `ConclusionEvent(hash der UPD-Conclusion, …)` |
| In der Runtime | Erzeugt die Update-Conclusion mit Hash `1`, den die Runtime auf die `UPD`-Conclusion abbildet | Gewöhnliche Conclusion |
| Fokus danach | Der auslösende Editor erhält wieder den Fokus (`isRequestFocus()`), sofern der Command keinen anderen anfordert | Keine Rückkehr in das auslösende Feld |
| `page init` im Hintergrund | Unterbleibt | Wie bei jeder Conclusion |

Auf dem Weg über das Delegate ruft der Editor nur, wenn er enabled ist, und löst während des Aufrufs nicht erneut aus – das Neuladen setzt seinen Text. Eine Prüfung, ob sich der Inhalt seit dem letzten Auslösen geändert hat, vermeidet Wiederholungen; der Aktualisieren-Button von [F] löst auch ohne Änderung aus.

Was als „Eingabe abgeschlossen“ gilt, legt das Toolkit fest:

| Editor | turkuforms | fx8forms | h2forms |
| --- | --- | --- | --- |
| Text | Enter; Aktualisieren-Button | Enter oder Tab bei gültiger, geänderter Eingabe; Aktualisieren-Button | Barcode-Scan in das Feld |
| Mehrzeiliger Text | wie Text | nicht unterstützt (wirft) | – (nur Anzeige) |
| Status | Auswahländerung | Auswahländerung, Enter oder Tab; Button | Auswahländerung |
| Reference | Auswahländerung; mit `REFERENCEDLGT_PROVIDE_HINT` auch freier Text | Enter, Tab oder Klick in der Liste; Button | Auswahländerung |
| Datum mit Auswahl | Wertänderung | keine Wirkung | – |
| Datum mit Uhrzeit, Uhrzeit | Nur der Aktualisieren-Button | Enter oder Tab im Zeitfeld; Button | – |

### Hook für Tastatureingaben

Nach `enableKeyReleaseEvents()` ruft der Editor bei Änderungen `delegate.keyReleasedEvent()`. Das Delegate informiert damit einen registrierten `IDataUxElementHook` (`RT.extensions`) über die Änderung, sofern es enabled ist. Hook und Update-Conclusion schließen sich an einem Delegate aus.

Nur [F] setzt den Hook um: Text und mehrzeiliger Text melden jede Taste, sofern die Eingabe gültig ist; Status jede Taste; Reference die Auswahl eines Eintrags. [T] wirft bei `enableKeyReleaseEvents()`, [H] ignoriert den Aufruf.

Den Hook installiert die Anwendung: Eine im Modell genannte Klasse (`IDataUxElementHook`) erhält in `initialize(selController, delegates)` die Delegates des Formulars und ruft an den gewünschten `setDelegateDataUxElementHook(this)`.

### Weitere Methoden des Delegates

Ein Formular verwendet am `IDlgt`:

| Methode | Verwendung im Toolkit |
| --- | --- |
| `getDelegateUiImpl()` | Der Editor des Delegates (`IToolkit_TextEditor`) für `addDelegate` |
| `isInputValid()` | In `checkDelegatesValidAndFocus()`: `null`, wenn gültig, sonst der Fehlertext. Das Delegate ruft dabei selbst `setValidationErrorText`. |
| `isEnabled()` | Fokusnavigation: nicht editierbare Felder überspringen |
| `isRequestFocus()` | `myRequestFocus()`: Dieses Feld soll den Fokus erhalten (`#Meta.requestFocus()`, Rückkehr nach Update-Conclusion). |

`load`, `store`, `setValue`, `getValue`, `setOptional` und `setEnabled` ruft nur die Runtime. Ausnahme: [H] ruft `setDisableFORCE()` an Delegates mehrzeiliger Editoren.

## Dialoge

`IApplication.DlgRunnable.run(boolean confirmed)` wird genau einmal aufgerufen. `confirmed` ist `true` für OK beziehungsweise die Bestätigung, `false` für die Ablehnung einer Rückfrage. Der Wert `null` für das Runnable ist zulässig und kommt vor, bei `showDialog` wie bei `showProblemsDialog`.

Für Fehler (`ERROR_*`) und Rückfragen (`QUESTIONCLOSE_SMALL`) gilt [V]:

- Das Runnable läuft erst nach der Quittierung durch den Benutzer – nicht beim Anzeigen.
- Der Dialog darf nicht auf anderem Weg verschwinden (ESC ohne Zuordnung, Klick außerhalb), ohne dass das Runnable läuft: Die Runtime schließt in vielen Fällen erst im Runnable den betroffenen Command.
- Solange ein solcher Dialog offen ist, nimmt das Toolkit keine anderen Ereignisse an.

Für Hinweise (`INFO_*`, `showProblemsDialog`, Toast) darf das Runnable sofort laufen und der Text nachträglich erscheinen; [H] verfährt so, [T] und [F] warten auf die Quittierung. Einzelheiten stehen in der [Referenz](toolkit-referenz.md#dialogtypen).

## Hauptfenster

`ApplicationMDI` hat für das Toolkit diese Einstiegspunkte (öffentliche Methoden der Klasse, nicht Teil von `IApplication`):

| Methode | Wann |
| --- | --- |
| `initializeApplication(urlParams, systemName, userEnv, connectionInfo, startupInfo)` | Einmal nach der Anmeldung, siehe [Rahmen](toolkit-rahmen.md#startsequenz). Liefert `true`, wenn ein Start-Command gestartet wurde. |
| `onKeyPressEvent(KeyEvent)` | Tastendruck außerhalb eines Prompt-Fensters |
| `onTabChangeEvent(index)` | Der Benutzer aktiviert einen anderen Tab. `index` zählt in der Reihenfolge der `addTab`-Aufrufe ab 0, bereinigt um geschlossene Tabs. |
| `onExitRequested(doNotCheckBackOnOpenGO)` | Abmelden oder Hauptfenster schließen. `false`: Rückfrage, wenn ungespeicherte Änderungen offen sind; `true`: ohne Rückfrage. |
| `internal_immediatelyShutdown()` | Alle Commands ohne Rückfrage beenden und die Instanz herunterfahren. Für das Ende von außen (Sitzungsende) und als Abmeldeweg ohne Rückfrage ([H]). Darf je Instanz nur einmal laufen. |
| `internal_closeAllRunningCmds()` | Alle laufenden Commands beenden, die Anwendung bleibt angemeldet. [H] ruft das, wenn der Client die Startseite neu anfordert. |
| `execAfterStartupOrNow(Runnable)` | Arbeit ausführen, sobald ein laufender Start-Command beendet ist (Command-Start per URL) |
| `getUrlDefaultFor(...)`, `cmdStartForUrlDefault(...)`, `receiveAndProcess(UxEvent)` | Command-Start per URL, siehe [Rahmen](toolkit-rahmen.md#command-start-per-url) |
| `isStartupCmdRunning()`, `isCommandRunning()`, `inShutdownMode()`, `noGraphsToCommit()` | Zustand abfragen: Läuft der Start-Command, läuft irgendein Command, ist die Instanz heruntergefahren, gibt es keine offene Schreib-Session |
| `getLastRequestedRedirectAndReset()` | Die Adresse, die die Anwendung zuletzt mit `openUrl` angefordert hat, abholen und löschen |
| `getMainWindowEvalSession()`, `getUserEnvironment()`, `getUserName()` | Session für Auswertungen des Hauptfensters; Benutzerkontext |
| `reciveForeignTermEvent(GlobalCmdTermEvent)` | Termination aus einer anderen Instanz desselben Benutzers übernehmen |
| `fullApplicationCrtlState()` | Textuelle Zustandsbeschreibung für die Fehlersuche |

Schreibweise wie im Quelltext: `reciveForeignTermEvent`.

## Was die Runtime nicht erfährt

Diese Funktionen liegen vollständig im Toolkit; die Runtime kennt sie nicht:

- Fokusnavigation zwischen Feldern, Öffnen von Auswahllisten, Textmarkierung.
- Sortierung, Suche und Spaltenanordnung in Tabellen; das Ein- und Ausblenden gefalteter Spalten.
- Position, Größe und Darstellung von Fenstern, Tabs und Dialogen.
- Wechsel zwischen den Tabs eines `Tab Layout`s innerhalb einer Page.
