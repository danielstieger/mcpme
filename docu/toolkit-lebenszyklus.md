# Toolkit-Lebenszyklus – Aufrufprotokoll zwischen DataUX-Runtime und Toolkit

## Zweck und Geltungsbereich

Dieses Dokument beschreibt, **in welcher Reihenfolge** die DataUX-Runtime die Toolkit-Schnittstelle aufruft und welche Rückmeldungen sie vom Toolkit erwartet. Die Signaturen stehen in [Signaturen](toolkit-signaturen.md), die Verträge je Methode in der [Referenz](toolkit-referenz.md), die Ereignisse vom Toolkit zur Runtime im [Rückkanal](toolkit-rueckkanal.md). Begriffe, Paketkürzel (`RT`, `OFX`, `TF`, `FX`, `H2`) und die Kennzeichnung **[V]** (Vertrag), **[T]** (turkuforms), **[F]** (fx8forms), **[H]** (h2forms) erklärt die [Einstiegsseite](toolkit-schnittstelle.md).

Alle Abläufe sind aus dem Runtime-Code abgeleitet; die maßgeblichen Klassen sind jeweils genannt.

## Beteiligte der Runtime

| Klasse (`RT.core`) | Rolle |
| --- | --- |
| `ApplicationMDI` | Eine Instanz je angemeldeter Anwendung (je Hauptfenster). Verwaltet Menü, Tiles, die Liste der laufenden Tab-Commands und das eine Prompt-Fenster. Implementiert `IApplication`. |
| `WindowController` | Startet Commands (`BasisCmdStart`, `MultiCmdStart`), steuert die Mehrfachausführung und zeigt Dialoge über `IToolkit_MainWindow`. |
| `EventCommandContainer` (extends `CommandContainer`) | Eine Instanz je laufendem Command. Besitzt die Ereignis-Queue des Commands und ruft `IToolkit_CommandContainerUi` und die Formulare auf. |
| `PagePaneSelCrtl` (extends `SelectionController`) | Selektionskontext einer Page; verteilt Daten und Selektion an die UI-Elemente und wertet Actions neu aus. |
| `RT.dsl.ui.*` (`PagePane`, `DelegateForm`, `Table`, `GridLayout`, `TabLayout`, `CustomUiElement`) | Builder, mit denen der generierte `Page Pane`-Code die Toolkit-Elemente erzeugt und konfiguriert. |
| `RT.genspecification.*Component` | Laufzeit-Gegenstück je UI-Element (`IGenSelControlled`); hält die Referenz auf das Toolkit-Element. |

## Grundregeln [V]

1. **Ein UI-Thread, strikt sequenziell.** Die Runtime ruft alle Toolkit-Methoden aus ihrer Ereignisschleife auf, also im UI-Thread. Umgekehrt müssen alle Rückrufe des Toolkits (Klick, Hotkey, Tabwechsel, Dialog-Quittierung, Selektion) im UI-Thread und nacheinander ankommen. [F] UI-Thread ist der JavaFX Application Thread. [T], [H] UI-Thread ist der jeweilige Request-Thread; [H] serialisiert die Requests einer Sitzung mit `synchronized (session)`.
2. **Ereignis-Queue je Command.** `EventCommandContainer.receiveAndProcess(UxEvent)` reiht das Ereignis ein (Kapazität 10; ein Überlauf wirft) und arbeitet die Queue sofort ab, wenn nicht schon eine Verarbeitung läuft und der Aufrufer im UI-Thread ist. Die Bedingung lautet `app.getMainWindowImpl() == null || app.getMainWindowImpl().inUiThread()`. Ereignisse, die während der Verarbeitung entstehen, werden angehängt und in derselben Schleife FIFO abgearbeitet.
3. **Rückrufe sind reentrant.** Ein einziger Rückruf des Toolkits (etwa ein Klick auf eine Conclusion) kann synchron die gesamte Kette bis zu `close()` des eigenen Fensters auslösen. Toolkit-Code darf nach einem Rückruf nicht mehr davon ausgehen, dass sein Fenster noch existiert.
4. **Dialoge blockieren nicht.** `showDialog` und `showProblemsDialog` kehren sofort zurück; die Fortsetzung steht im übergebenen `IApplication.DlgRunnable`. Die Runtime ruft beide Methoden aus ihrer laufenden Ereignisschleife heraus. Alle drei Implementierungen kehren sofort zurück; ein blockierender Dialog ist durch keine belegt.
5. **Idempotenz.** `delayedRequestFocus`, `delayedAfterFullUiInitialized`, `reevalConclusions`, `setItemEnabled`, `setEnabled` und `loadList` werden sehr häufig und wiederholt aufgerufen.

## Anwendungsstart

Das Toolkit erzeugt Hauptfenster und `ApplicationMDI` und ruft `initializeApplication(...)` auf; die Einzelheiten stehen im [Rahmen](toolkit-rahmen.md#startsequenz). Innerhalb von `ApplicationMDI.initializeApplication` läuft:

1. Sessions für Tiles und Menüauswertung anlegen; `userLoggedIn(...)` ruft `uiFactory.createConfiguredUserPrintService(mainWindow, userEnv)` und registriert die Instanz beim Monitoring.
2. `IGenAppUiModule.createStartMenu / createExtrasMenu / createHelpMenu / createTiles`.
3. Alle `CmdAction`s aus Menüs und Tiles werden mit `configure(...)` versehen (Label übersetzt, Icon, Hotkey, Tooltip).
4. `IToolkit_MainWindow.setMenuAndInit(langIndex, start, extra, help)` – genau einmal.
5. `addStatusInformation(...)` mit Benutzername und -ID.
6. `setAppInfo(appName, version, dynTitle, branding)`; danach erneut bei jeder Änderung von dynamischem Titel oder Branding.
7. `ensureHotkeyAvailable(hotkeys)` mit allen unterschiedlichen Hotkeys der Menü- und Tile-Actions.
8. `IGenAppUiModule.userLoggedIn(userEnv, userServices)`.
9. `showTiles(tiles, false)` – immer, auch wenn ein Start-Command folgt; danach `setItemEnabled` an registrierten Menüeinträgen und Tiles.
10. `IGenAppUiModule.createStartupCmd(...)`: Der Start-Command wird gestartet, außer die Start-URL verlangt denselben Command. Das führt zu [Command öffnen](#command-öffnen).

`initializeApplication` liefert `true`, wenn ein Start-Command gestartet wurde.

Schlägt einer dieser Schritte mit einer Exception fehl, zeigt die Runtime `showDialog(ERROR_…)` und ruft nach der Quittierung `internal_immediatelyShutdown()` und dann `closeApplicationAndExit()`.

Einen Command aus der Start-URL startet das Toolkit anschließend selbst, siehe [Rahmen](toolkit-rahmen.md#command-start-per-url).

## Command öffnen

Auslöser ist `CmdAction.startCommand()` (Menü, Tile, Tabellenmenü, Doppelklick, Hotkey). Die Action erzeugt ein `BasisCmdStart` und gibt es an ihren `IEventProcessor`.

```text
CmdAction.startCommand()
  -> WindowController.startCommandByEvent(BasisCmdStart)
       - läuft dieselbe Command-Instanz schon in einem Tab? -> FocusEvent(true) an diesen Tab, kein Start
       - paralleles Deployment aktiv?                        -> kein Start
       - modaler Tab offen und kein GRAPH_EDIT_CMD?          -> Exception -> Fehlerdialog
       - IOFXCmdModule.startCommand(...)                      (Command + Session)
       - uiFactory.createCommandContainer()
       - container.initContainer(...)
       - container.receiveAndProcess(InitCmdEvent)
  -> InitCmdEvent:  bg_fg_initContainer      (kein UI-Zugriff)
       - Locks anfordern, command init, command title, page init der ersten Page
       - Ergebnis: OpenUiEvent | CancelAndClose(Probleme) | CloseCntrCmdTermedEvent | CloseCntrExInfoEvent
  -> OpenUiEvent:   fg_openUserInterface
```

Endet `command init` mit einer Precondition, wird **kein Fenster** geöffnet; die Probleme erscheinen über `showProblemsDialog` im Hauptfenster. Endet der Command ohne Page (`done` im `command init`), wird ebenfalls kein Fenster geöffnet.

### `fg_openUserInterface` im Detail

| Schritt | Aufruf am Toolkit | Bemerkung |
| --- | --- | --- |
| 1 | Builder-Aufrufe: `createToolkit*Form`, `create*Editor`, `addColumn`, `addDelegate`, `addChildren`, `addTab`, `addMenuAndSetButtons`, … | Der generierte `Page Pane` baut die Formhierarchie auf; Reihenfolge siehe [Aufbau einer Page](#aufbau-einer-page). |
| 2 | `createPromptContainerUi(mainWin, fullSize)` für `GRAPH_EDIT_CMD`, sonst `createTabContainerUi(mainWin, modal)` | `modal` bei `GRAPH_OWNER_CMD(modal)`. Ein Successor übernimmt das Fenster seines Vorgängers; dann entfällt dieser Schritt. |
| 3 | `ui.setCommandContainer(container)` | Auch beim Successor. |
| 4 | `ui.setNotification(text)` | Nur wenn eine Sperrmeldung vorliegt. |
| 4a | `forceNotEditable()` an Tabellen, `setEnabled(false)` an Editoren | Nur bei einer Read-only-Session. |
| 5 | `form.setTitleText(pageTitle)` | Nur wenn ein Page Title vorhanden ist. |
| 6 | `form.setProblems(problems)` | Nur wenn `page init` Hinweise geliefert hat. |
| 7 | `ui.setConclusions(infos, globalHotkeys)` | Index 0 ist immer ESC. |
| 8 | `ui.setAdjustUrl(url)` | Nur wenn der Command eine URL deklariert. |
| 9 | `form.rootForm()`, dann `ui.show(windowTitle, form)` | Über `ApplicationMDI.showCommandContainerUI`. |
| 10 | `mainWindow.addTab(ui)`, bei modalem Tab `mainWindow.setCurrentTabModal(true)` | Nur für Fenster mit `isTabWindow() == true`. Die Runtime zählt ihren Tab-Index vor dem Aufruf selbst hoch. |
| 11 | `mainWindow.installCloseConfirmQuestion(true/false)` | Nur bei Zustandswechsel. |
| 12 | `loadList(...)` / `selectionChanged(...)` an Tabellen, `IDlgt.load` an Delegates, `setItemEnabled` an registrierten Menüeinträgen | Über `PagePaneSelCrtl.addLoadedListReference`; siehe [Daten und Selektion](#daten-und-selektion). |
| 12a | weitere `selectionChanged`/`loadList` | Selektionen, die `page init` gesetzt hat, werden übertragen. |
| 13 | `ui.setColor(color)` | Nur wenn eine Farbe ermittelt wurde. Ein `GRAPH_EDIT_CMD` erbt die Farbe des Parents. |
| 14 | `ui.delayedRequestFocus()` | Bei `ReloadOnTabChange` lädt die Runtime davor alle Elemente neu. |
| 15 | `ui.delayedAfterFullUiInitialized()` | Davor ruft die Runtime `preDelayedAfterFullUiInitialized` an ihren Komponenten. |
| 16 | `ui.reevalConclusions(infos)` | Folge des `ReevalEvent` aus Schritt 12; erst jetzt ist `enabled` der Conclusions belastbar. |

Beim Successor entfallen die Schritte 2 und 8 bis 11; stattdessen folgen `form.rootForm()`, `ui.setContent(form)` und – bei Fenstertitel-Typ `FORCE_OVERWRITE` – `ui.adjustWindowTitle(title)`. `setColor` kommt auch beim Successor, nicht aber beim Wechsel auf eine andere Page.

Die Schritte 14 und 15 dürfen sofort oder verzögert wirken: [F] stellt sie mit `Platform.runLater` hinter den gesamten Durchlauf der Runtime, also auch hinter Schritt 16. [H] setzt in Schritt 14 nur Fokusmarken an den Editoren und lässt Schritt 15 leer.

## Aufbau einer Page

Die Builder in `RT.dsl.ui` rufen die Toolkit-Fabrik in der Deklarationsreihenfolge des Modells auf. Kinder werden vor ihren Containern fertiggestellt.

### `Delegate Form`

```text
uiFactory.createToolkitDelegateForm()
form.setTitleText(label)                      (optional, Option LABEL)
form.setColLayoutConstraints(["1*","2*",...])
je Delegate, in Modellreihenfolge:
    uiFactory.create<...>Editor(...)          (im Delegate-Konstruktor)
    editor.setDelegate(delegate)
    editor.setFormatter(...) / setEditorPrompt(...)   (im Konstruktor: Datums- und Reference-Delegates)
    editor.setOption(opts...)                 (nur wenn Optionen vorhanden)
    editor.setEditorPrompt(...)               (Image: Format; Datum mit OVERWRITE FORMAT: zusätzlich setFormatter, setEnabled(false))
    editor.setLabelTooltip(text)
    editor.setLabel(text)
    editor.setIssuesUpdateConclusion()        (nur bei ISSUE UPDATE/SCANABLE)
    editor.setEnabled(false)                  (nur bei DISABLED)
    form.addDelegate(delegate)
```

Ein `Dummy`-Delegate durchläuft nur `createDummyEditor`, `setDelegate` und `addDelegate`.

### `Table`

```text
uiFactory.createToolkitTableForm()
table.setTitleText(label)                     (optional)
table.addColumn(property, label, converter, width, editable, folded, important)   je Spalte
table.addMenuAndSetButtons(menu)              (optional; Actions sind dann bereits konfiguriert)
table.setFormController(component)
table.endOfInitializationForElementClass(rowClass)
table.setEditPreview()                        (optional)
```

### `Grid Layout` und `Tab Layout`

```text
uiFactory.createToolkitFormContainer(flex)
container.setLayoutConstraints(cols, rows)
container.addChildren(child)                  je Kind, zeilenweise
container.skipFocus(index)                    (optional, Option FOCUS FORWARD)

uiFactory.createToolkitTabForm()
tabForm.addTab(child, label)                  je Tab
```

### Wurzel der Page

Die Runtime bestimmt eine Form, die für Page Title, Problemanzeige und das Menü des `Page Pane`s zuständig ist (`PagePane.buildAndClear`, `PagePaneComponent.installWithMenu/installWithoutMenu`):

| Oberstes Element | Ohne Seitenmenü | Mit Seitenmenü |
| --- | --- | --- |
| `Grid Layout` | Das Layout selbst | Das Layout selbst; `addMenuAndSetButtons(menu)` |
| `Table`, `Delegate Form` | Das Element selbst | Neuer Container `createToolkitFormContainer(false)` mit `["1*"],["1*"]` und dem Element als einzigem Kind; `addMenuAndSetButtons(menu)` am Container |
| `Tab Layout`, `Custom UI Element` | Neuer Container wie oben | Neuer Container wie oben |

Daraus folgt [V]: `setTitleText` und `setProblems` müssen `IToolkit_FormContainer`, `IToolkit_TableForm` und `IToolkit_DelegateForm` unterstützen; das Seitenmenü nur der Container. Auf genau dieser Form ruft die Runtime `rootForm()` auf. Eine `IToolkit_TabForm` ist nie Wurzel; alle drei Implementierungen werfen dort bei `setTitleText` und `setProblems`.

## Daten und Selektion

Die Runtime hält die Selektion; das Toolkit zeigt sie an und meldet Benutzerauswahl zurück.

### Laden

`PagePaneSelCrtl.addLoadedListReference(objects)` läuft nach jedem `page init`:

1. Enthält die Liste genau ein Objekt des Root-Typs oder eines Subtyps, wird es als Selektion mit dem Root-Typ gesetzt (`selectionChanged` an alle Elemente des Root-Typs). Ein Objekt eines unpassenden Typs führt zu einer Exception.
2. `reloadViews()`: `loadList(objects, selection)` an alle an den Root-Typ gebundenen Elemente; abhängige Elemente (gebunden an eine Property) erhalten `loadList` mit der Liste aus dem selektierten Objekt – oder mit einer leeren Liste, wenn nichts oder mehreres selektiert ist.
3. `CmdAction.reevalEnabled()` für alle Actions der Page → `IToolkit_EnableItem.setItemEnabled` an registrierten Elementen.
4. `ReevalEvent` → `ui.reevalConclusions(infos)` und Neuauswertung der Hauptmenü- und Tile-Actions.

Formulare erhalten kein `loadList` am Toolkit: `DelegateFormComponent` lädt die Delegates selbst (`IDlgt.load(obj)`), siehe [Editoren je Ladevorgang](#editoren-je-ladevorgang).

### Benutzer selektiert in einer Tabelle

```text
Toolkit:  component.pushSelection(new Selection(rowClass, objekte))       (IGenSelControlled, aus setFormController)
Runtime:  PagePaneSelCrtl.pushSelectionAbsolute
            -> Selektion unverändert (equals): nichts geschieht
            -> sonst selectionChanged(selection) an ALLE Elemente dieses Typs – auch an die auslösende Tabelle
            -> liefert ein Element false: Selektion wird geleert, selectionChanged(leer) an alle
            -> abhängige Elemente: loadList(liste aus dem selektierten Objekt, selection)
            -> setItemEnabled an allen registrierten Menüeinträgen der Page
            -> ReevalEvent -> reevalConclusions
```

Ein Toolkit, dessen Tabelle beim programmatischen Setzen der Auswahl selbst ein Auswahlereignis feuert, braucht deshalb einen Schutz gegen Wiedereintritt: `selectionChanged` darf nicht erneut `pushSelection` auslösen. [F] verwendet dafür zwei Schalter – einer unterdrückt den Listener beim programmatischen Setzen, der andere lässt `selectionChanged` während des eigenen `pushSelection` sofort mit `true` zurückkehren. [H] braucht keinen Schutz: `selectionChanged` und `loadList` speichern dort nur.

`selectionChanged` kann vor dem ersten `loadList` eintreffen. [F] liefert dann `false`, wenn die Tabelle leer und die Selektion nicht leer ist; die Runtime leert darauf die Selektion.

### Editoren je Ladevorgang

`BaseDelegate.load(obj)` ruft am Editor, in dieser Reihenfolge:

```text
editor.setItems(...)              (nur Reference, nur bei geändertem Scope)
editor.newObjectBound()           (wenn sich der hashCode des gebundenen Objekts ändert, und bei jedem Laden von null)
editor.setLabel(...)              (nur wenn #Meta ein Label liefert)
editor.setValidationErrorText(..) (nur wenn #Meta einen Fehler liefert; gelöscht wird hier nicht)
editor.setText(...)               bzw. setItems(...) + setSelectedIndex(...) beim Status
editor.setEnabled(...)
editor.setOptionalAfterLoad(...)  (nur Reference)
```

Nach einer Update-Conclusion werden alle Delegates erneut geladen; `load` läuft also mehrfach für dasselbe Objekt.

## Conclusion ausführen

Das Toolkit sendet `ConclusionEvent(hash, text)` an den Container (siehe [Rückkanal](toolkit-rueckkanal.md#conclusions)). `CommandContainer.fg_doConclusion`:

```text
hash == ESC (0):
    dirty GRAPH_OWNER_CMD, nicht read-only  -> showDialog(QUESTIONCLOSE_SMALL); bei Bestätigung FINAL_USER_CANCEL und Schließen
    sonst                                   -> FINAL_USER_CANCEL und Schließen
hash == UPDATE (1):
    wird auf die Conclusion mit Hotkey "UPD" abgebildet
unbekannter hash:
    Exception -> Fehlerdialog, der Command wird beendet
Conclusion nicht enabled:
    Exception -> Fehlerdialog, der Command wird beendet
sonst:
    form.setProblems([])                                     (alte Meldungen löschen)
    bei save-Conclusion:
        IToolkit_DelegateForm.checkDelegatesValidAndFocus()  je Formular
        null  -> IDlgt.store(obj) je Delegate (liest editor.getText() / getSelectedIndex())
        Text  -> form.setProblems([Problem]) und Ende; die Conclusion läuft nicht
    Conclusion-Code des Commands ausführen
```

Danach, je nach Ergebnis:

| Ergebnis der Conclusion | Aufrufe am Toolkit |
| --- | --- |
| Precondition verletzt | `loadList`/`load` (Reload), `form.setProblems(problems)`, `delayedRequestFocus`, `delayedAfterFullUiInitialized` |
| Keine Änderung der Page | Reload, `delayedRequestFocus`, `delayedAfterFullUiInitialized`, `reevalConclusions` |
| `page <aktuelle Page>` (Refresh) | `page init`, dann `form.setTitleText`, ggf. `form.setProblems`, Reload, Fokus-Aufrufe. **Kein** `setConclusions`, die Form bleibt. |
| `page <andere Page>` | `gcClear` der alten Formen, [Aufbau einer Page](#aufbau-einer-page), `setTitleText`, `setConclusions`, `form.rootForm()`, `ui.setContent(form)`, Laden, Fokus-Aufrufe |
| `done` oder Abbruch | [Command schließen](#command-schließen) |
| `done` mit Successor | Der Successor wird im selben Fenster geöffnet (`setCommandContainer`, `setContent`, `adjustWindowTitle`). |

Wenn die Formularvalidierung fehlschlägt und `uiFactory.flagValidationAdditionally()` `false` ist, erhält `setProblems` ein Problem mit **leerem Text**; der Fehlertext steht dann nur am Editor. [T], [F] liefern `true`, [H] liefert `false`.

## Command schließen

`CommandContainer.fg_closeUiAndContainer` und `ApplicationMDI.closeCommandContainerUI`:

| Schritt | Aufruf am Toolkit | Bemerkung |
| --- | --- | --- |
| 1 | `mainWindow.showProblemsDialog(problems, null)` oder `mainWindow.setToastMessage(msg)` | Nur nach `FINAL OK_CONCLUSION`: verbliebene Warnhinweise beziehungsweise die `user toast message`. Der Callback ist hier `null`. |
| 2 | `setItemEnabled` an Hauptmenü und Tiles, gegebenenfalls `setAppInfo` | Neuauswertung der Menü- und Tile-Actions. |
| 3 | `mainWindow.setCurrentTabModal(false)` | Nur bei modalem Tab. |
| 4 | `mainWindow.ensureTabClosed(ui)` | Nur Tab-Fenster. |
| 5 | `ui.close()` | |
| 6 | Fokusvergabe, siehe unten | Noch innerhalb von `closeCommandContainerUI`. |
| 7 | `mainWindow.installCloseConfirmQuestion(...)` | Nur bei Zustandswechsel. |
| 8 | `gcClear()` an Formen und Editoren | Über `PagePaneSelCrtl.gcClear`. |
| 9 | `mainWindow.distributeTermEventOnOtherInstance(event)` | Nach der Verteilung an die eigenen Container. |

Fokusvergabe in Schritt 6:

- Tab geschlossen, Parent-Tab existiert: `FocusEvent(true)` an den Parent → `mainWindow.focusTab(ui)`, Fokus-Aufrufe.
- Tab geschlossen, andere Tabs offen: `FocusEvent(true)` an den linken Nachbarn.
- Letzter Tab geschlossen: `mainWindow.showTiles(tiles, resetUrl)`. `resetUrl` ist `true`, außer ein Start-Command läuft noch und ein weiterer Command wartet.
- Prompt geschlossen: `FocusEvent(false)` an den aktuellen Tab → Fokus-Aufrufe ohne `focusTab`.

Der Parent erhält also zuerst den Fokus und verarbeitet danach das `GlobalCmdTermEvent` aus Schritt 9 (`fg_handleGlobalCmdTerminate`): Probleme löschen, `cmdTermHandler` ausführen, Page Title neu berechnen (`setTitleText`), Reload, Selektion übertragen.

## Abbrüche und Fehler

| Auslöser | Aufrufe am Toolkit |
| --- | --- |
| Precondition im `command init` oder nicht quittierbarer Abbruch | `showProblemsDialog(problems, callback)`; nach der Quittierung `FINAL CANCEL_CONCLUSION` und Schließen |
| Exception oder Guard | `showDialog(ERROR_LARGE oder ERROR_SMALL, message, callback)`; nach der Quittierung Schließen. `ERROR_SMALL` bei `uiFactory.isSilentExLogging()`. |
| Guard im `GRAPH_EDIT_CMD` | Wie oben; zusätzlich wird der Parent ohne Rückfrage geschlossen. |
| Sperre durch anderen Benutzer (`acquire locks`) | `ui.setNotification(text)` und `reevalConclusions` – der Command läuft read-only weiter; ESC wird aktiviert. Zeitpunkt siehe [Hintergrundverarbeitung](#hintergrundverarbeitung). |
| Read-only-Session | `forceNotEditable()` an Tabellen, `setEnabled(false)` an allen Editoren, Conclusions disabled |

## Tabwechsel und Tastatur

- **Tabwechsel durch den Benutzer:** Das Toolkit ruft `ApplicationMDI.onTabChangeEvent(index)`. Die Runtime übernimmt den Index, wertet Menüs und Tiles neu aus und schickt dem Tab ein `FocusEvent(false)` → `delayedRequestFocus`, `delayedAfterFullUiInitialized`. Während eigener `addTab`-, `focusTab`- und `ensureTabClosed`-Aufrufe unterdrückt die Runtime nur das `FocusEvent`; Index und Menüauswertung übernimmt sie trotzdem. Ein synchron feuernder Listener ist zulässig, muss aber den richtigen Index liefern. [H] hat keinen Tabwechsel und ruft die Methode nie.
- **Hotkey:** siehe [Rückkanal](toolkit-rueckkanal.md#hotkeys).

## Mehrfachausführung

Bei einer Mehrfachselektion erzeugt `CmdAction.startCommand()` ein `MultiCmdStart`. Der `WindowController` startet den Command je Objekt nacheinander; vor jedem Lauf schickt er dem Parent ein `PushSelEvent`, das die Selektion auf das nächste Objekt setzt (`selectionChanged`, Reload). Das Toolkit sieht eine Folge gewöhnlicher Öffnen- und Schließen-Abläufe. Dialoge (`showDialog` mit Fehler, Schließen-Frage) beenden die Mehrfachausführung. Ein Toolkit ohne Mehrfachselektion ([H]) löst sie nie aus.

## Commands ohne Fenster

Bei einer `Compound Action` oder einer Mehrfachausführung ohne Oberfläche läuft der Container im Modus *auto conclusion, no UI*: Es wird keine `IToolkit_CommandContainerUi` erzeugt, die Conclusion wird von der Runtime selbst ausgelöst. Probleme erscheinen nur über `showProblemsDialog`.

## Hintergrundverarbeitung

`uiFactory.useBackgroundThread()` entscheidet zwischen zwei Betriebsarten. Beide sind benutzter Vertrag: [F] liefert `true`, [T] und [H] liefern `false`.

### Ohne Hintergrund-Thread (`false`)

- Alles läuft synchron im aufrufenden Thread. `execEventInBackground` und `execEventInForeground` werden nie aufgerufen; [T] und [H] werfen dort. `inUiThread()` ist in beiden konstant `true`.
- Eine Sperrmeldung des Event-Bus aus einem fremden Thread wird nur in die Queue des Containers eingereiht. `setNotification` und `reevalConclusions` folgen erst mit dem nächsten Ereignis dieses Containers. Ein Kanal vom Server zum Client ist dafür nicht nötig.

### Mit Hintergrund-Thread (`true`)

- `mainWindow.execEventInBackground(sender, runnable)` kommt für `command init` (bei `SEARCH_CMD`, `GRAPH_OWNER_CMD`, `GRAPH_OWNER_CMD(modal)` oder Command-Option `BACKGROUND`) und für `page init` nach einer Conclusion eines `SEARCH_CMD`, sofern es nicht die Update-Conclusion war. Ein Successor startet nie im Hintergrund.
- Erwartet wird: das Runnable außerhalb des UI-Threads ausführen. Es greift nicht auf die UI zu und reiht seine Folgeereignisse selbst ein. Danach ruft das Toolkit **im UI-Thread** `sender.processPendingEvents()`.
- `mainWindow.execEventInForeground(sender, event)` kommt für Ereignisse aus fremden Threads (derzeit nur die Sperrmeldung des Event-Bus). Erwartet wird `sender.receiveAndProcess(event)` im UI-Thread.
- Die Runtime selbst schützt den Container während der Hintergrundarbeit nicht gegen weitere Ereignisse. Das Toolkit muss Eingaben so lange sperren.

[F] setzt das so um:

1. Im UI-Thread die Oberfläche sperren: Inhaltsbereich deaktivieren, Fortschrittsanzeige zeigen; der Tastatur-Handler verwirft währenddessen alle Tasten.
2. Einen Daemon-Thread starten, der das Runnable ausführt.
3. Danach im UI-Thread (`Platform.runLater`): entsperren, Fokus wiederherstellen, `sender.processPendingEvents()`.
4. Wirft das Runnable: im UI-Thread entsperren und `sender.receiveAndProcess(new CloseCntrExInfoEvent(exception))` – der Command endet mit einem Fehlerdialog.

`isFX8LegacyImpl()` hat genau eine Wirkung in der Runtime: Liefert die Fabrik `true` ([F]), verwirft der Container vor einem `page init` nach einer Conclusion ein bereits eingereihtes Benutzer-ESC. Andere Toolkits liefern `false`; dort bleibt das ESC in der Queue und schließt den Command danach.

## Anwendung beenden

Das Toolkit meldet den Wunsch mit `ApplicationMDI.onExitRequested(false)`:

- Kein Tab mit offener Schreib-Session: `internal_immediatelyShutdown()` (alle Commands werden mit `FINAL_USER_CANCEL` beendet, deren Fenster geschlossen), dann `mainWindow.closeApplicationAndExit()`.
- Sonst: `showDialog(QUESTIONCLOSE_SMALL, ...)`; bei Bestätigung derselbe Ablauf.

Ein Toolkit kann stattdessen selbst `internal_immediatelyShutdown()` rufen und auf die Rückfrage verzichten; [H] meldet so ab.

Beim parallelen Deployment ruft die Runtime nach einem Info-Dialog `internal_immediatelyShutdown()` und `mainWindow.parDeploymentForwardNow()`, siehe [Rahmen](toolkit-rahmen.md#paralleles-deployment).
