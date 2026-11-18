# Toolkit-Referenz – Interfaces und Methoden

## Zweck und Geltungsbereich

Dieses Dokument beschreibt jede Methode der Toolkit-Schnittstelle `org.modellwerkstatt.dataux.runtime.toolkit`: wer sie wann aufruft, welche Wirkung erwartet wird und was die drei Implementierungen konkret tun. Die Signaturen stehen in [Signaturen](toolkit-signaturen.md), die Aufrufreihenfolgen im [Lebenszyklus](toolkit-lebenszyklus.md), die Gegenrichtung im [Rückkanal](toolkit-rueckkanal.md). Paketkürzel und Kennzeichnung erklärt die [Einstiegsseite](toolkit-schnittstelle.md).

Lesehilfe für die Tabellen:

- **Vertrag** – was die Runtime erwartet; aus ihrem Code abgeleitet.
- **turkuforms**, **fx8forms**, **h2forms** – Verhalten der jeweiligen Implementierung; als Beispiel zu lesen, nicht als Vorgabe. „leer“ heißt: Die Methode tut nichts. „Default“ heißt: Die Default-Implementierung des Interfaces gilt.
- **Pflicht** – `ja`: Ohne die Methode funktioniert die Runtime nicht korrekt. `leer`: Eine leere Implementierung ist zulässig und durch mindestens eine Implementierung belegt. `bedingt`: abhängig von einer genannten Bedingung.

## Kapitellandkarte

| Interface | Erzeugt durch | Aufgabe |
| --- | --- | --- |
| [`IToolkit_UiFactory`](#itoolkit_uifactory) | Spring-Bean der `OFXConfig` | Fabrik für alle UI-Bausteine und Träger der Laufzeiteinstellungen |
| [`IToolkit_MainWindow`](#itoolkit_mainwindow) | Toolkit selbst | Hauptfenster: Menü, Tiles, Tabs, Dialoge, Meldungen |
| [`IToolkit_CommandContainerUi`](#itoolkit_commandcontainerui) | `createTabContainerUi`, `createPromptContainerUi` | Fenster eines Commands mit Inhalt und Conclusion-Buttons |
| [`IToolkit_Form`](#itoolkit_form) | – (Basis) | Gemeinsamer Vertrag aller Formen |
| [`IToolkit_DelegateForm`](#itoolkit_delegateform) | `createToolkitDelegateForm` | Formular aus Delegates |
| [`IToolkit_TableForm`](#itoolkit_tableform) | `createToolkitTableForm` | Tabelle |
| [`IToolkit_FormContainer`](#itoolkit_formcontainer) | `createToolkitFormContainer` | Raster-Layout und Träger des Seitenmenüs |
| [`IToolkit_TabForm`](#itoolkit_tabform) | `createToolkitTabForm` | Tab-Layout innerhalb einer Page |
| [`IToolkit_TextEditor`](#itoolkit_texteditor) | `createTextEditor`, `createTextAreaEditor`, `createDummyEditor` | Editor eines Delegates |
| [`IToolkit_StatusEditor`](#itoolkit_statuseditor) | `createStatusEditor` | Auswahl eines Statuswerts |
| [`IToolkit_ReferenceEditor`](#itoolkit_referenceeditor) | `createReferenceEditor` | Auswahl eines referenzierten Objekts |
| [`IToolkit_DateOrTimeEditor`](#itoolkit_dateortimeeditor) | `createDateEditor`, `createDateAndTimeEditor` | Datum und Uhrzeit |
| [`IToolkit_ImageEditor`](#itoolkit_imageeditor-und-itoolkit_uploadeditor) | `createImageEditor` | Bildanzeige |
| [`IToolkit_UploadEditor`](#itoolkit_imageeditor-und-itoolkit_uploadeditor) | `createUploadEditor` | Datei-Upload |
| [`IToolkit_EnableItem`](#itoolkit_enableitem) | Toolkit selbst, je Menüeintrag | Schaltfläche oder Menüeintrag einer `CmdAction` |

Die Bausteine eines Toolkits bilden eine geschlossene Familie: Formen, Editoren und Fenster werden nur mit Objekten derselben Implementierung kombiniert. Die Rückgabetypen `Object` (`getEditor`, `getLabel`, `getRightPartComponent`, `myRequestFocus`) wertet die Runtime nicht aus; sie dienen dem Toolkit intern.

## `IToolkit_UiFactory`

Erweitert `OFX.IOFXApplicationFactory` (und damit `IOFXCoreReporter`). Eine Implementierung leitet von `RT.core.BaseUiFactory` ab und ergänzt `implements IToolkit_UiFactory`. `BaseUiFactory` liefert alle Einstellungen und Infrastrukturmethoden; die Unterklasse liefert die Fabrikmethoden. Der Konstruktor von `BaseUiFactory` verlangt eine `MoWareTranslations.TranslationSelection`; [F] übergibt `MAIN_TRANSLATIONS`, [H] `H1_TRANSLATIONS`.

### Vom Toolkit zu implementieren

| Methode | Vertrag | turkuforms | fx8forms | h2forms |
| --- | --- | --- | --- | --- |
| `createToolkitFormContainer(flex)` | Container für ein `Grid Layout`. `flex == true` steht für die Option `FLEXIBLE`. | `false` → `TurkuGridLayout`, `true` → `SliderLayout` (vertikale Splitter, nur eine Spalte) | `FX8FormContainer`; `flex` ignoriert – mehrzeilige Container sind immer ein verstellbarer `SplitPane` | `FormContainer`; `flex` ignoriert |
| `createToolkitTabForm()` | Tab-Layout | `TurkuTabForm` | `FX8TabForm` | `TabForm` (Akkordeon) |
| `createToolkitTableForm()` | Tabelle | `TurkuTable` | `FX8TableForm` | `TableForm` |
| `createToolkitDelegateForm()` | Formular | `TurkuDelegatesForm` | `FX8DelegateForm` | `DelegateForm` |
| `createCustomForm(CustomForm)` | Sonderform; einziger Wert ist `PASSIVE_HTML_FULLSCREEN`: Anzeige von HTML, das über `setTitleText(html)` geliefert wird. | `PassiveHtmlForm` | `FX8HtmlContent`: ein Label, zeigt das HTML als Rohtext; andere Werte werfen | `HtmlContent`: gibt das HTML ungefiltert aus; andere Werte werfen |
| `createTabContainerUi(mainWindow, modal)` | Command-Fenster als Tab; `modal` für `GRAPH_OWNER_CMD(modal)`. | `CmdUiTab` | `FX8CmdUiTab` | `CommandContainerUi`; `modal` ignoriert |
| `createPromptContainerUi(mainWindow, fullSize)` | Command-Fenster für `GRAPH_EDIT_CMD`. `fullSize`, wenn der Command oder die Page maximale Fläche verlangt. | `CmdUiPrompt` | `FX8CmdUiPrompt` | dieselbe Klasse `CommandContainerUi`; `fullSize` ignoriert |
| `createTextEditor()` | Einzeiliger Texteditor; auch für `Integer` und `BigDecimal`. | `TextEditor` | `FX8TextEditor` | `TextEditor` |
| `createTextAreaEditor(numOfLines)` | Mehrzeiliger Texteditor (Option `NUM OF LINES`). | `TextAreaEditor` | `FX8AreaEditor` | `TextEditor(n)`; bei n > 1 nur Anzeige, das Delegate wird zwangsweise disabled |
| `createDummyEditor()` | Platzhalter ohne Inhalt. | `DummyEditor` | `FX8DummyEditor` | disabled `TextEditor` |
| `createReferenceEditor(alter)` | Auswahl aus einer Liste von Texten. `alter` für die Option `ALTER`. | `ReferenceEditor`; `alter` ignoriert | `FX8ReferenceEditor`; `alter` ignoriert | `ReferenceEditor`; `alter` → Liste statt Dropdown |
| `createStatusEditor(alter)` | Auswahl aus einer Liste von Texten über den Index. | `alter` → `RadioStatusEditor`, sonst `StatusEditor` (Dropdown) | `FX8StatusEditor` (ComboBox); `alter` ignoriert | `StatusEditor`; `alter` → Liste statt Dropdown |
| `createDateEditor(withPicker)` | Datum. | `DatePickerEditor`, sonst `TextEditor` | `FX8DatePicker`, sonst `FX8DateEditor` (feste Maske `__.__.__`) | `DateOrTimeEditor`; mit Picker `<input type="date">` |
| `createDateAndTimeEditor(withPicker, timeOnly)` | Datum mit Uhrzeit; `timeOnly` für `TIME PICKER ONLY`. | `timeOnly` hat Vorrang: `TimePickerEditor`; sonst `DateTimePickerEditor` oder `TextEditor` | immer `FX8DateAndTimeEditor`; beide Parameter ignoriert | Texteditor; beide Parameter ignoriert |
| `createImageEditor()` | Bildanzeige. | `ImageViewer` | `FX8ImageEditor` | `ImageEditor` |
| `createUploadEditor()` | Datei-Upload. | `UploadEditor` | `FX8UploadEditor`: nur ein Label, kein Upload | `UploadEditor` (Foto) |
| `useBackgroundThread()` | `true`: Die Runtime nutzt `execEventInBackground`/`execEventInForeground`. | `false` | `true` | `false` |
| `flagValidationAdditionally()` | `true`: Ein Validierungsfehler eines Formulars wird zusätzlich als Problem mit Text über `setProblems` gemeldet; sonst als Problem mit leerem Text. | `true` | `true` | `false` |
| `isFX8LegacyImpl()` | Aus `BaseUiFactory`: `false`. Wirkung siehe [Lebenszyklus](toolkit-lebenszyklus.md#hintergrundverarbeitung). | `false` | `true` | `false` |

### Aus `BaseUiFactory` übernommen

Diese Methoden sind fertig implementiert; die Werte kommen aus Properties der Spring-Bean. Die vollständige Liste mit Defaults steht im [Rahmen](toolkit-rahmen.md#einstellungen-der-appfactory).

| Gruppe | Methoden |
| --- | --- |
| Infrastruktur | `createCommandContainer`, `createPagePaneInitializerFqName`, `createNewSession`, `getModuleByInstanceName`, `getEventBus`, `getTransProvider`, `getPlatform`, `getAllCmdUrlDefaults`, `initCmdUrlDefaults`, `initExtAuthProviders`, `getAllExtAuthProviders`, `report` |
| Texte | `getSystemLabel(langIndex, key)` – Systemtexte wie Button-Beschriftungen; Schlüssel in `RT.utils.MoWareTranslations.Key` |
| Druck | `createConfiguredUserPrintService(tecHandle, userEnv)` – delegiert an die `IOFXPrintFactory`-Bean |
| Verhalten der Runtime | `getReloadOnTabChange`, `getCollectSelections`, `getAlwaysRollbackSession`, `getCheckAlsoPredecessorDuplication`, `getEventBusLocking`, `getDecimalDelegateInsensibleThousand`, `isSilentExLogging`, `isFX8LegacyImpl` |
| Vom Toolkit auszuwerten (in der Runtime ohne Verwender) | `isScrollAdjust`, `getExperimentalFeatures`, `getUploadLocationRetrieve`, `getUploadLocationStore`, `getUploadFsLocationStore`, `getUploadMaxSize` |
| Betrieb | `getMowareTrace`, `getAppTrace`, `getSkipLoggingTcnException`, `isAutoParDeploymentForwardGracefully`, `isCheckDeployedVersion` |

`RT.core.FakeUiFactory` ist die kleinste gültige Unterklasse: Alle `create*`-Methoden liefern `null`, alle Schalter `false`. Sie eignet sich nur für den Betrieb ohne Oberfläche, zeigt aber genau, welche Methoden ein Toolkit ergänzen muss: die Fabrikmethoden der Tabelle oben samt `useBackgroundThread` und `flagValidationAdditionally`.

## `IToolkit_MainWindow`

Eine Instanz je laufender Anwendung. Aufrufer ist `ApplicationMDI`, für Dialoge `WindowReporter`/`WindowController`.

| Methode | Aufrufer und Zeitpunkt | Vertrag | Pflicht | turkuforms | fx8forms | h2forms |
| --- | --- | --- | --- | --- | --- | --- |
| `setMenuAndInit(langIndex, start, extra, help)` | `initializeApplication`, einmal | Hauptmenü aus drei `MenuAction`-Bäumen aufbauen; je `CmdAction` ein Bedienelement, das `startCommand()` aufruft. Aufbau siehe [Menüs](#menüs-aus-menuaction). | ja | Drei Dropdowns; ergänzt „Logout“ (ruft `onExitRequested(false)`) und „About“. Im Kompaktmodus nur das Startmenü als Drawer. | `MenuBar` mit Start, Extra, Hilfe; ergänzt „Version“ und „Beenden“ (ruft `onExitRequested(false)`); Hotkey im Label; keine Tastenregistrierung | Nur das Startmenü, flach über `getAllCmdActionsOfMenu()`; Extras und Hilfe ignoriert; Bedienbarkeit einmalig per `reevalEnabled()` |
| `setAppInfo(appName, version, dynTitle, brandIng)` | Start und bei Änderung von `dynamicStatusInfo` oder Branding | Name, Version und dynamischen Titel anzeigen; `brandIng` wählt ein Branding. | leer | Fenstertitel `appName version`, Kopfzeile `appName dynTitle`, Logo über CSS-Klasse | Fenstertitel; Branding ignoriert | leer; Name und Version kommen aus eigener Konfiguration, das Branding aus der User Environment |
| `addStatusInformation(info)` | Start, einmal (`"user <name> / <id>"`) | Statusinformation anzeigen. | leer | Kurze Notification unten links | Statusleiste | leer |
| `ensureHotkeyAvailable(hotkeys)` | Start, einmal | Die genannten Hotkeys anwendungsweit abfangen. Im Interface als `@Deprecated` markiert, wird aber aufgerufen. | leer | Registriert die Hotkeys global, zusätzlich Shift+F5 (`DBG_GRAPH`) und Shift+F6 (`DBG_SESSION`) | leer – das Fenster meldet ohnehin jede Taste | leer – keine Hotkeys |
| `showTiles(tiles, resetUrl)` | Start und immer, wenn kein Command mehr offen ist | Startoberfläche mit einer Kachel je `Tile` zeigen: Label `getCmdAction().labelText`, Farbe `getColor()` (darf `null` sein), Klick ruft `getCmdAction().startCommand()`. Folgeaufrufe liefern dieselben Kacheln mit möglicherweise geänderten Labels. `resetUrl`: die Adresse auf die Startadresse zurücksetzen. | ja | Erster Aufruf baut Buttons, Folgeaufrufe aktualisieren nur die Labels. Wirft, wenn noch Tabs offen sind. | Erster Aufruf baut, Folgeaufrufe aktualisieren die Labels; Farbe als Rahmen- und Textfarbe; `resetUrl` ignoriert | Liste jedes Mal neu; Bedienbarkeit per `reevalEnabled()`; `resetUrl` ignoriert |
| `addTab(ui)` | `showCommandContainerUI`, nach `ui.show(...)` | Das Command-Fenster als neuen, aktiven Tab aufnehmen. Die Reihenfolge der Tabs entspricht der Reihenfolge der Aufrufe. | ja | Ersetzt die Tiles durch die Tab-Ansicht | Tab anhängen und auswählen | `currentCommandUI = ui` – nur dieses Fenster ist sichtbar |
| `focusTab(ui)` | `ApplicationMDI.focusTab` | Den Tab in den Vordergrund holen. | ja | | Tab auswählen | `currentCommandUI = ui` |
| `ensureTabClosed(ui)` | `closeCommandContainerUI`, vor `ui.close()` | Den Tab entfernen. | ja | | Tab entfernen und dessen Formen aufräumen | `currentCommandUI = null` |
| `setCurrentTabModal(lock)` | Öffnen und Schließen eines modalen Tabs | `true`: Hauptmenü und alle anderen Tabs sperren; `false`: freigeben. | leer, wenn neben einem Command-Fenster nichts bedienbar ist | Menüleiste und andere Tabs deaktivieren | Andere Tabs und Menü deaktivieren | leer |
| `installCloseConfirmQuestion(install)` | Bei Wechsel des Zustands „ein Tab hat eine offene Schreib-Session“ | Vor ungewolltem Schließen der Anwendung warnen. | leer | `beforeunload`-Handler des Browsers | Default | Default |
| `showDialog(type, text, callback)` | Fehler, Hinweise, Rückfragen | Dialog zeigen und sofort zurückkehren. `callback.run(confirmed)` genau einmal aufrufen; `callback` darf `null` sein. Typen und Zeitpunkt siehe [Dialogtypen](#dialogtypen). | ja | `PromptWindow` | Eigenes, anwendungsmodales Fenster, nicht blockierend | Ein Dialog-Slot; siehe Dialogtypen |
| `showProblemsDialog(problems, callback)` | Abbruch eines Commands, verbliebene Hinweise | Liste von Problemen zeigen (`getSimpleUserText()`; `isWarningOnly()` als Hinweis darstellen); `callback.run(true)` genau einmal; `callback` darf `null` sein. Die Runtime übergibt höchstens 15 Einträge. | ja | | Ein Label je Problem; OK oder F12 quittiert | Nicht modal: Banner in der nächsten Seite; der Callback läuft sofort |
| `setToastMessage(info)` | Nach `FINAL OK_CONCLUSION` mit `user toast message` | Kurzlebige, nicht blockierende Meldung. | ja | Notification, 4 s | Statusleiste und Einblendung für etwa 2 s | Einmal gerenderter Hinweis in der nächsten Seite |
| `showGraphDebugger(graph, controllerInfo)` | `KeyEvent "DBG_GRAPH"` | Entwicklerwerkzeug: geladene Objekte anzeigen. | leer | JSON-Darstellung in einem großen Info-Dialog | Baumansicht der Objekte | leer |
| `closeApplicationAndExit()` | Nach `internal_immediatelyShutdown()` | Fenster schließen, Sitzung beenden. Die Runtime ist bereits heruntergefahren. | ja | Session abmelden, Weiterleitung auf die Logout-Seite | Monitoring abmelden, Fenster schließen, Event-Bus schließen | Merkt vor, dass die Sitzung nach der Antwort endet |
| `parDeploymentForwardNow()` | Paralleles Deployment, nach Shutdown | Den Benutzer auf die neue Version der Anwendung umleiten. | bedingt (nur bei parallelem Deployment) | Session invalidieren, Weiterleitung auf `/login` | leer | Redirect auf die letzte Adresse mit `fromversion`; Sitzung endet |
| `distributeTermEventOnOtherInstance(event)` | Nach jeder Command-Termination | Das Ereignis an andere `ApplicationMDI`-Instanzen desselben Benutzers weitergeben (`reciveForeignTermEvent`). | leer bei Einzelinstanz | Verteilt an die anderen Browser-Tabs derselben HTTP-Session | leer | leer |
| `execEventInBackground(sender, runnable)` | siehe [Hintergrundverarbeitung](toolkit-lebenszyklus.md#hintergrundverarbeitung) | | bedingt (`useBackgroundThread()`) | wirft | Oberfläche sperren, Daemon-Thread, danach im UI-Thread `processPendingEvents()` | wirft |
| `execEventInForeground(sender, event)` | wie oben | | bedingt | wirft | `Platform.runLater(sender.receiveAndProcess(event))` | wirft |
| `inUiThread()` | Bei jedem `receiveAndProcess` | `true`, wenn der aufrufende Thread auf die UI zugreifen darf. | ja | konstant `true` | `Platform.isFxApplicationThread()` | konstant `true` |

### Dialogtypen

| `DlgType` | Verwendung | `confirmed` |
| --- | --- | --- |
| `INFO_SMALL` | Kurzer Hinweis | `true` |
| `INFO_LARGE` | Langer Text (Debug-Ausgaben) | `true` |
| `ERROR_SMALL` | Fehler ohne technische Details (`isSilentExLogging`) | `true` |
| `ERROR_LARGE` | Fehler mit Stacktrace | `true` |
| `QUESTIONCLOSE_SMALL` | Rückfrage vor dem Verwerfen ungespeicherter Änderungen; Bestätigen mit Systemtext `DISCARD_BUTTON`, Ablehnen mit `NO_BUTTON` | `true` bei Bestätigung, `false` sonst |

Die Runtime wertet `confirmed` nur bei `QUESTIONCLOSE_SMALL` aus.

Vertrag [V]: Das Runnable läuft genau einmal. Bei Fehlern und Rückfragen (`ERROR_*`, `QUESTIONCLOSE_SMALL`) läuft es erst, nachdem der Benutzer quittiert hat; die Runtime schließt in vielen Fällen erst im Runnable den betroffenen Command.

Bei Hinweisen (`INFO_*`, `showProblemsDialog`) unterscheiden sich die Implementierungen:

| | turkuforms | fx8forms | h2forms |
| --- | --- | --- | --- |
| `ERROR_*` | Dialog mit OK | Fenster mit OK; ESC wirkt nicht; F12 und Enter quittieren | Die Antwort besteht nur aus dem Dialog mit OK; der Server nimmt bis zur Quittierung nichts anderes an |
| `QUESTIONCLOSE_SMALL` | Dialog mit zwei Schaltflächen | Abbrechen und ESC liefern `false`, OK und F12 `true`; vorbelegt ist „Nein“ | Zwei Schaltflächen, sonst wie `ERROR_*` |
| `INFO_*`, `showProblemsDialog` | Dialog mit OK; das Runnable läuft nach der Quittierung | wie `ERROR_*`; `*_LARGE` in voller Größe, scrollbar | Kein Dialog: Das Runnable läuft sofort im selben Request, der Text erscheint einmal als Banner in der nächsten Seite |
| Gleichzeitig offene Dialoge | mehrere | mehrere | genau einer; ein zweiter Dialog über einem offenen blockierenden Dialog wirft |

Das Verhalten von [H] zeigt, dass Hinweise ohne Quittierung auskommen. Betroffen sind unter anderem die Precondition im `command init` und der Hinweis beim parallelen Deployment.

## `IToolkit_CommandContainerUi`

Eine Instanz je Command mit Oberfläche. Aufrufer ist der `CommandContainer` des Commands, für `show` und `close` die `ApplicationMDI`.

| Methode | Aufrufer und Zeitpunkt | Vertrag | Pflicht | turkuforms | fx8forms | h2forms |
| --- | --- | --- | --- | --- | --- | --- |
| `setCommandContainer(container)` | Vor `show`; erneut, wenn ein Successor das Fenster übernimmt | Ziel für alle Rückrufe merken und bei erneutem Aufruf ersetzen. | ja | | | |
| `show(windowTitle, content)` | Erstanzeige | Fenster mit Titel und Form anzeigen. Bei `isTabWindow() == true` folgt `mainWindow.addTab(ui)`; ein Fenster mit `isTabWindow() == false` muss sich selbst öffnen. | ja | Der Prompt zeigt den Titel nicht an. | Tab: nicht schließbarer Reiter. Prompt: eigenes modales Fenster ohne Titel, öffnet sich selbst. | Merkt Titel und Form; sichtbar wird das Fenster durch `addTab` |
| `setContent(content)` | Wechsel auf eine andere Page; Successor | Die Form ersetzen. Die Buttons kommen getrennt über `setConclusions`. | ja | | | Setzt die Scrollposition zurück |
| `adjustWindowTitle(title)` | Successor mit Fenstertitel-Typ `(overwrite predecessor)` | Titel ersetzen. | ja für Fenster mit sichtbarem Titel | Im Prompt leer | Im Prompt leer | Ersetzt den Titel |
| `setNotification(text)` | Sperre durch anderen Benutzer; kann mehrfach kommen | Dauerhaft sichtbaren Hinweis im Fenster zeigen; ein erneuter Aufruf ersetzt den Text. | ja | Schließbare Leiste oben | Schließbare Leiste oben | Rote Karte, nicht schließbar |
| `setColor(color)` | Nach dem Aufbau und bei jedem Successor, nur wenn ungleich `null` | Thematische Farbe des Fensters. Die Runtime reicht die Zeichenkette aus dem Modell ungeprüft durch, siehe [Farben](#farben). | leer | Tab-Reiter, Fensterhintergrund (Farbe mit Alpha) und oberer Rand | Obere Rahmenlinie | Kopfzeile; bei `#…` zusätzlich eine dunklere Variante |
| `setConclusions(infos, additionalHotkeys)` | Je neuer Page | Button-Leiste neu aufbauen. Siehe [Conclusions](#conclusions). | ja | Registriert Hotkeys | ESC links, Rest rechts; `additionalHotkeys` ignoriert; keine Hotkey-Registrierung | ESC als „X“; `UPD` und `GO` gesondert; `additionalHotkeys` ignoriert |
| `reevalConclusions(infos)` | Nach jeder Selektionsänderung, jedem Reload, jeder Conclusion | `enabled` je Button übernehmen. Gleiche Länge und Reihenfolge wie bei `setConclusions`. | ja | Blendet bei `hideWhenDisabled` zusätzlich aus | Nur deaktivieren; `hideWhenDisabled` ignoriert | Übernimmt `enabled` per Index und prüft, dass die Hashes übereinstimmen |
| `delayedRequestFocus()` | Letzter Schritt jeder Interaktion | Fokus setzen: `content.myRequestFocus()` auf der aktuellen Form. | ja | | Per `Platform.runLater` verzögert | Setzt Fokusmarken an den Editoren |
| `delayedAfterFullUiInitialized()` | Unmittelbar danach | Abschlussarbeiten: `content.afterFullUiInitialized()`. | leer | Der Tab setzt zusätzlich die Farbe der Kopfzeile. | Per `Platform.runLater` verzögert | leer |
| `setAdjustUrl(url)` | Vor `show`, nur bei Commands mit `URL`-Option | Die Adresse der Anwendung auf diesen Command setzen. | leer | Nur Tabs: `history.replaceState` beim Aktivieren des Tabs | Default | Default |
| `close()` | Nach `ensureTabClosed` beziehungsweise direkt | Fenster schließen, Referenzen lösen. Danach erfolgt kein Aufruf mehr. | ja für Fenster, die sich selbst geöffnet haben | Der Prompt schließt seinen Dialog. | Tab: leer (aufgeräumt wird in `ensureTabClosed`). Prompt: schließt sein Fenster. | leer |
| `isTabWindow()` | Buchführung der `ApplicationMDI` | Entscheidet, wie die Runtime das Fenster führt: `true` als Tab (`addTab`, `ensureTabClosed`), `false` als das eine Prompt-Fenster. Höchstens ein Fenster mit `false` ist gleichzeitig offen. | ja | Tab `true`, Prompt `false` | Tab `true`, Prompt `false` | konstant `true` – auch für `GRAPH_EDIT_CMD` |
| `isModalTabWindow()` | wie oben | `true`, wenn mit `modal == true` erzeugt. | ja | | Konstruktorwert | konstant `false` |

Ein Toolkit mit nur einem sichtbaren Fenster darf alles als Tab führen ([H]); die Runtime kennt diesen Fall ausdrücklich. Ein `GRAPH_EDIT_CMD` läuft dann als weiterer Tab über seinem Parent.

### Conclusions

`OFX.OFXConclusionInformation` hat öffentliche Felder:

| Feld | Bedeutung |
| --- | --- |
| `conclusionHashCode` | Kennung für den Rückruf. `0` = ESC (`CommandContainer.ESC_CONCLUSION_CODE`), `1` ist für Update reserviert (`UPDATE_CONCLUSION_CODE`); sonst der Identitäts-Hash des Conclusion-Objekts – nur für die aktuelle Page gültig und zwischen Läufen nicht stabil. |
| `buttonTitle` | Übersetzte Beschriftung |
| `iconName` | Name des Icons; für ESC `arrow_back` |
| `hotkey` | Hotkey in der [Hotkey-Notation](toolkit-rueckkanal.md#hotkey-notation); für ESC `"ESC"`. Ohne Hotkey ist der Wert in der Regel `""`; ein Toolkit sollte auch `null` vertragen. |
| `hideWhenDisabled` | Button ausblenden, wenn nicht enabled. Für ESC `false`. |
| `enabled` | Bedienbarkeit. Bei `setConclusions` nur für ESC gesetzt; für alle anderen erst mit `reevalConclusions` gültig. |

Regeln [V]:

- **Index 0 ist immer ESC.** Das Label ist „Schließen“, wenn die Page keine Conclusions hat oder der Command ein `SEARCH_CMD` ist, sonst „Abbrechen“. Bei `NO_ESC` ist `enabled == false`.
- Die Zuordnung zwischen `setConclusions` und `reevalConclusions` läuft über den Index.
- Das Toolkit sendet `ConclusionEvent(conclusionHashCode, text)` nur für eine Conclusion, die enabled ist. Eine nicht bedienbare Nicht-ESC-Conclusion führt in der Runtime zu einer Exception: Fehlerdialog, der Command wird beendet. Für ESC prüft die Runtime `enabled` nicht nach.
- Eine Conclusion mit Hotkey `UPD` (`SCAN/UPDATE`) ist kein gewöhnlicher Button. [T], [F] stellen sie nicht dar und lösen sie über Editoren aus. [H] bietet für sie ein Scan-Feld und einen Kamera-Button.
- `additionalHotkeys` sind die globalen Hotkeys der Actions im Menü des `Page Pane`s. Nur [T] wertet die Liste aus.

`hideWhenDisabled` wertet nur [T] aus; [F] und [H] deaktivieren lediglich.

Darstellung: [T] stellt ESC links als nachrangigen Button dar, die letzte Conclusion als Primärbutton, und blendet einen disabled ESC-Button dauerhaft aus. [T] und [F] hängen den Hotkey in Klammern an die Beschriftung an und verändern dabei `buttonTitle` des übergebenen Objekts. [H] behandelt die letzte bedienbare Conclusion als Standardaktion für Enter.

### Farben

`setColor` und `Tile.getColor()` liefern eine Zeichenkette aus dem Modell: den Farbwert des Commands, den statischen Wert am `Page Pane` oder die Farbe eines Statuselements. Die Runtime prüft und normalisiert sie nicht. Alle drei Implementierungen setzen sie als CSS-Farbwert ein ([F] in JavaFX-CSS); üblich ist `#RRGGBB`. Ein Toolkit ohne CSS sollte unbekannte Notationen ignorieren.

## `IToolkit_Form`

Basis aller Formen. Der Typparameter ist der gebundene Entity- oder DTO-Typ.

| Methode | Aufrufer und Zeitpunkt | Vertrag | turkuforms | fx8forms | h2forms |
| --- | --- | --- | --- | --- | --- |
| `loadList(objects, selection)` | `TableComponent.loadList`, `ICustomDataUxElement` | Den Datenbestand vollständig ersetzen und die übergebene Selektion darstellen. | Nur `TurkuTable` und die Charts; sonst leer. | Nur `FX8TableForm`; sonst leer | Nur `TableForm`: Liste und Selektion merken |
| `selectionChanged(selection)` | `TableComponent.selectionChanged` | Die Selektion darstellen. `true`, wenn alle selektierten Objekte dargestellt werden können; `false` führt dazu, dass die Runtime die Selektion des Typs leert. | Nur `TurkuTable`; alle anderen Formen liefern `false` und werden dafür nicht aufgerufen. | wie turkuforms | `TableForm`: merken, `true` |
| `setTitleText(text)` | Option `LABEL`; Page Title (`PagePaneComponent.setHeading`) | Überschrift der Form. `\n` trennt Titel und Untertitel. Kann wiederholt mit neuem Text kommen. | `FormHeading` | Titelbereich wird bei Bedarf angelegt | Getrennt gespeichert; leer → kein Titel |
| `setProblems(problems)` | `PagePaneComponent.setProblems` – nur auf der Wurzelform | Meldungen oberhalb des Inhalts anzeigen; jeder Aufruf ersetzt die Liste, eine leere Liste löscht. Die Runtime übergibt höchstens 8 Einträge. Siehe [Problemanzeige](#problemanzeige). | `FormHeading` | Labels je Problem, roter Rahmen am Inhalt | Liste merken; die Wurzel rendert sie |
| `rootForm()` | Auf der Wurzelform, vor `show`/`setContent` | Hinweis, dass diese Form Page Title und Probleme trägt. | Kennzeichnet die Überschrift als Seitentitel. | Titel im Hauptstil | Default; die Wurzel erkennt das Template selbst |
| `myRequestFocus()` | `ui.delayedRequestFocus()` | Den Fokus auf das erste geeignete Element setzen und dieses zurückgeben; `null`, wenn die Form keinen Fokus annimmt. Container fragen ihre Kinder der Reihe nach; die Runtime stellt dafür `RT.core.FocusController` bereit. | siehe einzelne Formen | siehe einzelne Formen | siehe einzelne Formen |
| `afterFullUiInitialized()` | `ui.delayedAfterFullUiInitialized()` | Arbeiten, die eine vollständig aufgebaute und geladene Oberfläche voraussetzen. Wird nach jeder Interaktion aufgerufen. | | Splitter-Positionen, Bildgrößen | wird nie aufgerufen |
| `gcClear()` | Abbau der Page | Referenzen auf Runtime-Objekte lösen. | | Listener entfernen, Tabelle leeren | weitgehend leer |

### Problemanzeige

`OFX.IOFXProblem`:

| Methode | Darstellung |
| --- | --- |
| `getSimpleUserText()` | Meldungstext. Kann leer sein, wenn nur ein Formular-Validierungsfehler vorliegt. |
| `isWarningOnly()` | Als Hinweis darstellen (`WARNING_HINT`), sonst als Fehler. |
| `getResolveActionOrNull()` | Ist der Wert gesetzt (`IOFXSession.IUxEventActionable`), eine Schaltfläche mit `getLabelText()` anbieten; der Klick ruft `performAction()` (`resolve with`). `getTriggerCode()` liefert eine Kennung der Aktion; [H] adressiert sie damit im Request. |

Die Korrektur-Schaltfläche bieten alle drei Implementierungen in `setProblems` an; im `showProblemsDialog` von [F] fehlt sie.

## `IToolkit_DelegateForm`

| Methode | Aufrufer und Zeitpunkt | Vertrag | turkuforms | fx8forms | h2forms |
| --- | --- | --- | --- | --- | --- |
| `setColLayoutConstraints(constraints)` | Aufbau, vor `addDelegate` | Spaltengewichte, je Spalte `"1*"` bis `"5*"`. | Vaadin-`FormLayout`; Spaltenzahl = Summe der Gewichte, unter 760 px einspaltig | Je Spalte ein Raster aus Label und Editor | leer; immer eine Spalte |
| `addDelegate(delegate)` | Aufbau, in Modellreihenfolge | Den Editor `delegate.getDelegateUiImpl()` aufnehmen. Die Delegates füllen die Spalten zeilenweise; Delegate *n* erhält das Gewicht der Spalte *n mod Spaltenzahl*. | Label links (`getLabel()`), Eingabe rechts (`getRightPartComponent()`); `WIDE` gibt dem Editor die volle Breite. | wie turkuforms; `WIDE` nur bei Text, mehrzeiligem Text und Datum als Text | Merkt Delegate und Editor |
| `checkDelegatesValidAndFocus()` | Vor jeder `save`-Conclusion, außer bei Read-only | Für alle Delegates `delegate.isInputValid()` aufrufen. Rückgabe `null`, wenn alle gültig sind; sonst den Fehlertext des ersten ungültigen Delegates, das dabei den Fokus erhält. | | Prüft alle Delegates, nicht nur bis zum ersten Fehler | wie fx8forms; setzt die Fokusmarke |

Fokus [V]: `myRequestFocus()` fokussiert das erste Delegate mit `isRequestFocus() == true`, sonst das erste mit `isEnabled() == true`, und liefert `null`, wenn keines editierbar ist. Alle drei Implementierungen halten das ein.

Tastatur: [T] Enter springt zum nächsten editierbaren Delegate, Shift+Enter zurück. [F] Enter und Tab springen weiter, Shift+Tab zurück.

## `IToolkit_TableForm`

| Methode | Aufrufer und Zeitpunkt | Vertrag | turkuforms | fx8forms | h2forms |
| --- | --- | --- | --- | --- | --- |
| `addColumn(property, label, converter, width, editable, folded, important)` | Aufbau, je Spalte | Spalte für `property` (Pfad am Zeilenobjekt, lesbar mit `RT.utils.MoJSON.get`). Zellentext = `converter.convert(wert)`. `width` in Prozent. `editable`: Option `EDITABLE`; `folded`: Option `FOLD`; `important`: Option `IMPORTANT`. | `folded`: Breite 0, per Doppelklick auf den Tabellenkopf ein- und ausblendbar. `important`: fett. | `folded`: unsichtbar, umschaltbar. `important` ignoriert. `editable` nur für `BigDecimal` und Status, sonst wirft. | `editable`, `folded` ignoriert; `label` nie angezeigt; `width` auf ein 12er-Raster gerundet; mehr als 12 Spalten werfen |
| `addTableItemColor(property, converter)` | In der Runtime ohne Aufrufer | – | leer | leer | leer |
| `addMenuAndSetButtons(menu)` | Aufbau, nach den Spalten | Tabellenmenü darstellen, siehe [Menüs](#menüs-aus-menuaction). | Menüleiste über der Tabelle und Kontextmenü; lokale Hotkeys | Schaltflächen, Dropdowns und Kontextmenü; lokale Hotkeys für alle Actions | Flache Liste aller Actions; keine Hotkeys |
| `setFormController(controller)` | Ende des Aufbaus | `IGenSelControlled` merken: Ziel für `pushSelection` und `convertAsCsv`. | | | |
| `endOfInitializationForElementClass(cls)` | Ende des Aufbaus | Zeilentyp für neue `Selection`-Objekte merken; der Aufbau ist abgeschlossen. | Setzt Spaltenbreiten und Edit-Listener. | Merkt die Klasse | Ignoriert die Klasse |
| `setEditPreview()` | Optional nach dem Aufbau | Die Tabelle dient nur der Anzeige innerhalb eines Bearbeitungsablaufs: keine Auswahl durch den Benutzer, kein Fokus. | Suche, Export und Sortierung aus; `myRequestFocus()` liefert `null`. | Tabelle deaktiviert, Suche und Sortierung aus | leer |
| `forceNotEditable()` | Read-only-Session | Inline-Bearbeitung sperren. | Die Zelle öffnet weiterhin; die Übernahme wird mit einer Meldung abgelehnt. | Spalten nicht mehr editierbar | leer |
| `setSelectionSummaryLineText(text)` | Nach `loadList` und `selectionChanged`, wenn `SELECTION SUMMARY LINE` modelliert ist | Zusammenfassung der Auswahl anzeigen; leerer Text leert. | Fußzeile rechts | Fußzeile rechts | leer |
| `setTableSummaryLineText(text)` | Nach `loadList`, wenn `TABLE SUMMARY LINE` modelliert ist | Zusammenfassung aller Zeilen. | Fußzeile links | Fußzeile links | Fußzeile |
| `loadList(objects, selection)` | siehe `IToolkit_Form` | Zeilen ersetzen; die Selektion darstellen, soweit die Objekte enthalten sind. | Wendet den Suchfilter an. | Wendet den Suchfilter an; meldet eine leere Selektion, wenn der Filter die Selektion verdeckt | Merkt Liste und Selektion |
| `selectionChanged(selection)` | siehe `IToolkit_Form` | Auswahl ersetzen. | `false`, wenn ein Objekt durch den Suchfilter verdeckt ist. | `false`, wenn ein Objekt fehlt oder die Tabelle leer ist | Merkt die Selektion; `true` |

`ITableCellStringConverter<T>` (`RT.extensions`):

| Methode | Bedeutung |
| --- | --- |
| `convert(wert)` | Anzeigetext der Zelle |
| `convertBack(text)` | Wert aus der Eingabe einer editierbaren Zelle; nicht jeder Converter unterstützt das. |
| `isRightAligned()` | Zelle rechtsbündig |
| `getBgColor(wert)` | Farbe der Zelle oder `null`. Für Statusspalten die Farbe des Statuselements. |
| `getColorMapOrNull()` | Feste Farbmenge eines Status |
| `hasDynamicColor()` | Farbe abhängig vom Wert (Option `COLOR`) |

[T] hebt eine Zelle farbig hervor, wenn `getColorMapOrNull()` oder `hasDynamicColor()` es anzeigen. [F] und [H] fragen nur `getBgColor(wert)` für jede Zelle ab und verwenden das Ergebnis als Schriftfarbe.

Leistungsmerkmale der Tabelle – keines davon verlangt die Runtime:

| Merkmal | Mechanik | turkuforms | fx8forms | h2forms |
| --- | --- | --- | --- | --- |
| Mehrfachselektion | `pushSelection` mit mehreren Objekten in Anzeigereihenfolge | ja | ja | nein |
| Hauptaktion | Doppelklick und Enter führen die Action des Tabellenmenüs mit Hotkey `ENTER` aus: `reevalEnabled()`, bei `true` `startCommand()` | ja | ja | Zeilenklick löst die einzige Action aus; bei mehreren klappt eine Leiste auf |
| Inline-Bearbeitung | Eingabe mit `convertBack` umwandeln und mit `RT.utils.ValueObjectReplacementFacility.put` in das Zeilenobjekt schreiben | nur `BigDecimal`, mit Grenzprüfung | `BigDecimal` und Status, ohne Grenzprüfung | nein |
| Export | `controller.convertAsCsv(alle, selektierte, spalten)`; `spalten` ist eine Liste von `IToolkit_TableForm.TableColumn` mit aktuellem `visible` | Ctrl+C | Ctrl+C | nein |
| Volltextsuche, Sortierung, Positionszähler | nur im Toolkit | ja | ja | nein |

## `IToolkit_FormContainer`

| Methode | Aufrufer und Zeitpunkt | Vertrag | turkuforms | fx8forms | h2forms |
| --- | --- | --- | --- | --- | --- |
| `setLayoutConstraints(cols, rows)` | Aufbau, vor `addChildren` | Gewichte je Spalte und Zeile. Die Builder der Runtime liefern `"-1"` (so groß wie der Inhalt) oder `"1*"` bis `"5*"` (Anteil am verbleibenden Platz). | Andere Werte führen zu einer Exception. | Deutet über `RT.utils.LayoutConstraint` zusätzlich jede nicht negative Ganzzahl als Pixelwert | leer; Kinder stehen untereinander |
| `addChildren(child)` | Aufbau, je Kind | Kinder füllen das Raster zeilenweise; es gibt keine Zellverbindung. | Mehr Kinder als Zellen führen zu einer Exception. | Mehr Kinder erzeugen weitere Zeilen | Beliebig viele |
| `addMenuAndSetButtons(menu)` | Nur an der Wurzelform einer Page | Menü des `Page Pane`s darstellen. | Menü rechts neben der Überschrift | Schaltflächen oben rechts | `getTopBarActions()` als Schaltflächen, `getDropDownActions()` als Dropdown |
| `skipFocus(index)` | Option `FOCUS FORWARD` | Die Fokussuche beginnt beim Kind mit diesem Index. | | über `FocusController` | über `FocusController` |

## `IToolkit_TabForm`

| Methode | Vertrag | turkuforms | fx8forms | h2forms |
| --- | --- | --- | --- | --- |
| `addTab(form, label)` | Tab mit übersetzter Beschriftung und genau einer Form; Reihenfolge der Aufrufe = Reihenfolge der Tabs. | | Nicht schließbarer Tab | Akkordeon-Abschnitt; alle Abschnitte werden gerendert |
| `getToolkitImplementation()` | Liefert die Form selbst. | | | |

`myRequestFocus()` delegiert an den sichtbaren Tab ([H]: an alle Kinder). Als Wurzel einer Page wird eine `IToolkit_TabForm` immer in einen Container gewickelt; `setTitleText` und `setProblems` müssen deshalb nicht unterstützt werden – alle drei Implementierungen werfen dort.

## `IToolkit_TextEditor`

Jeder Editor gehört zu genau einem Delegate (`RT.extensions.IDlgt`). Der Editor liefert nur Text; die Umwandlung in den fachlichen Typ, die Prüfung und die Formatierung übernimmt das Delegate.

| Methode | Aufrufer und Zeitpunkt | Vertrag | Abweichungen der Implementierungen |
| --- | --- | --- | --- |
| `setDelegate(delegate)` | Konstruktor des Delegates | Ziel für Rückrufe merken. | [H] hält das Delegate nicht, sondern liest nur dessen Typ für die Wahl der Tastatur. |
| `setOption(options...)` | Aufbau; nur wenn Optionen vorhanden sind | Optionen merken, siehe [Optionen](#optionen-idlgtopt). | |
| `setLabel(text)` | Aufbau; erneut bei jedem Laden, wenn `#Meta` ein Label liefert | Beschriftung setzen. | |
| `setLabelTooltip(text)` | Aufbau | Langbeschreibung der Property als Tooltip am Label. | [H] leer |
| `setEditorPrompt(text)` | Datums-Delegates (Eingabemaske), Reference (Bedienhinweis), Image (siehe unten) | Platzhaltertext im leeren Editor. | [H] leer, außer beim Bild |
| `setEnabled(enabled)` | Bei jedem Laden | Editierbarkeit. Wird sehr häufig aufgerufen. | [T] zwischengespeichert. [H] rendert einen disabled Editor als reinen Text ohne Eingabefeld. |
| `newObjectBound()` | Vor dem Laden eines anderen Objekts | Objektbezogenen Zustand des Editors zurücksetzen. | [T], [F] setzen die Erkennung doppelter Update-Conclusions zurück. [H] leer. |
| `setText(text)` | Bei jedem Laden | Inhalt setzen; Formate siehe [Textformate](#textformate-je-delegate). | |
| `getText()` | Validierung und Speichern | Aktuellen Inhalt liefern. | |
| `setValidationErrorText(text)` | `isInputValid()` des Delegates: zuerst `""`, im Fehlerfall der Fehlertext | Fehler am Editor anzeigen; leerer Text löscht die Anzeige. | [F] roter Rahmen und Tooltip. [H] rotes Label unter dem Feld. |
| `setIssuesUpdateConclusion()` | Aufbau, bei Option `ISSUE UPDATE/SCANABLE` | Der Editor kann ab jetzt die Update-Conclusion der Page auslösen, siehe [Rückkanal](toolkit-rueckkanal.md#update-conclusion). | [T], [F] ergänzen einen Aktualisieren-Button neben dem Feld. [H] markiert das Feld als Ziel eines Scans. [F] wirft beim mehrzeiligen Editor. |
| `enableKeyReleaseEvents()` | `BaseDelegate.setDelegateDataUxElementHook` | Ab jetzt bei Änderungen `delegate.keyReleasedEvent()` aufrufen, siehe [Rückkanal](toolkit-rueckkanal.md#hook-für-tastatureingaben). | Nur [F] setzt es um. [T] wirft, [H] leer. |
| `getEditor()` | Nur Toolkit-intern | Das fokussierbare Element. | |
| `getLabel()` | Nur Toolkit-intern | Das Label-Element. | |
| `getRightPartComponent()` | Nur Toolkit-intern | Das Element, das im Formular rechts vom Label steht (Editor oder Editor mit Zusatzknopf). | |
| `gcClear()` | Abbau der Page | Referenz auf das Delegate lösen. | |

### Optionen (`IDlgt.Opt`)

| Option | DataUX-Option | Auswertung | turkuforms | fx8forms | h2forms |
| --- | --- | --- | --- | --- | --- |
| `ALTER_OR_PICKER` | `ALTER`, `PICKER` | Durch das Delegate: Parameter `alter` beziehungsweise `withPicker` der Fabrikmethode | | | |
| `DATETIMEDLGT_TIMEONLY` | `TIME PICKER ONLY` | Durch das Delegate: Parameter `timeOnly` | | | |
| `ISSUE_UPDATE_CONCLUSION` | `ISSUE UPDATE/SCANABLE` | Durch die Runtime: `setIssuesUpdateConclusion()` | | | |
| `OPTIONAL` | `OPTIONAL` | Durch das Delegate | | | |
| `FORCE_DISABLED` | `DISABLED` | Durch das Delegate: `setEnabled(false)` | | | |
| `WIDE` | `WIDE` | Durch das Toolkit: Label ausblenden, Editor über die volle Breite | ja | nur Text, mehrzeiliger Text, Datum als Text | ja; das Label wird Platzhalter |
| `REFERENCEDLGT_PROVIDE_HINT` | – | Durch das Toolkit: Der Reference-Editor nimmt freien Text als Suchhinweis an. | ja | ja | nein |
| `STRINGDLGT_FORCE_NUMERICAL_EDITOR` | `FORCE NUMERIC EDITOR` | Durch das Toolkit | nein | nein | numerische Tastatur |
| `FOLDED` | `FOLD` | Durch das Toolkit | nein | nein | verstecktes Feld |

### Textformate je Delegate

| Delegate (`RT.delegates`) | `setText` liefert | `getText` muss liefern |
| --- | --- | --- |
| `StringDelegate` | Wert, `""` bei `null` | Eingabe unverändert (kein Trimmen) |
| `IntegerDelegate` | Dezimaldarstellung, `""` bei `null` | Text, der mit `Integer.parseInt` lesbar ist; leer nur bei `OPTIONAL` |
| `DecimalDelegate` | Text im Dezimalformat der Benutzersprache, `""` bei `null` | Text im selben Format |
| `LocalDateDelegate`, `DateTimeDateDelegate` | Text im Datumsformat, `""` bei `null` | Text im selben Format; leer nur bei `OPTIONAL` |
| `DateTimeDelegate` | Text im Format Datum, Leerzeichen, Uhrzeit | Text im selben Format |
| `ReferenceDelegate` | Den Anzeigetext des Objekts. Mit Scope ist das ein Eintrag der Liste aus `setItems`; ohne Scope kommt der Text, ohne dass `setItems` je gerufen wurde. Liegt der Wert außerhalb des Scopes und ist das Delegate disabled, unterbleibt `setText`. | Genau einen Eintrag der Liste, sonst `null` oder `""` für „nichts gewählt“; jeder andere Text führt zu einer Exception. |
| `StatusDelegate` | – (verwendet `setItems`/`setSelectedIndex`) | – |
| `ImageDelegate` | Den Rohwert der Property (Adresse, Name oder Pfad) | Wird nicht aufgerufen. |
| `UploadDelegate` | Dateiname | Dateiname der hochgeladenen Datei; `null` oder leer bedeutet „keine Datei“. |

Die Formate (Joda-Time-Muster) kommen aus der Business Property oder aus der Option `OVERWRITE FORMAT`, sonst aus dem `IOFXTranslationProvider` der Benutzersprache. Ein Editor mit eigener Darstellung (Datumsauswahl) muss den Text in genau diesem Format liefern. Für eine nicht lesbare Eingabe liefert er einen ungültigen Text, damit das Delegate den Fehler meldet: [T] liefert `"_"`, [F] den unvollständigen Maskentext wie `12.__.__`. [H] wandelt den Wert des Browser-Datumsfelds (`yyyy-MM-dd`) mit dem Formatter in das Delegate-Format.

## `IToolkit_StatusEditor`

| Methode | Aufrufer und Zeitpunkt | Vertrag |
| --- | --- | --- |
| `setItems(items)` | Beim Laden, wenn sich die Liste geändert hat | Auswahlmöglichkeiten ersetzen; die Auswahl ist danach leer. Die Texte sind die übersetzten Langbeschreibungen der zulässigen Statuselemente. Als **letzter** Eintrag folgt der Text für „kein Wert“ (Default `---`), wenn der Status optional ist oder wenn der Wert `null` und das Delegate disabled ist. |
| `setSelectedIndex(i)` | Bei jedem Laden, nach `setItems` | Eintrag `i` auswählen. |
| `getSelectedIndex()` | Validierung und Speichern | Index des gewählten Eintrags; kleiner als 0 bedeutet „nichts gewählt“. |

`setText` und `getText` verwendet die Runtime für Status nicht; alle drei Implementierungen werfen dort.

## `IToolkit_ReferenceEditor`

| Methode | Aufrufer und Zeitpunkt | Vertrag |
| --- | --- | --- |
| `setItems(items)` | Beim Laden, wenn sich der Scope geändert hat; vor `setText` | Anzeigetexte der auswählbaren Objekte. Ein Text entsteht aus den Properties der `reference description`, durch Leerzeichen verbunden. |
| `setOptionalAfterLoad(optional)` | Nach jedem Laden | `true`: Der Benutzer darf die Auswahl leeren. |

Mit der Option `REFERENCEDLGT_PROVIDE_HINT` übergibt der Editor freien Text mit `((ReferenceDelegate) delegate).setHintForScope(text)` und löst die Update-Conclusion aus; die Page kann daraufhin einen engeren Scope setzen. Die Runtime setzt den Hinweis vor jedem Laden zurück.

Umsetzung: [F] Eingabefeld mit gefilterter Vorschlagsliste; `getText()` vergleicht ohne Beachtung der Groß- und Kleinschreibung. [H] überträgt nicht den Text, sondern den Index des Eintrags; zwei gleiche Anzeigetexte führen dort zu einer Exception.

## `IToolkit_DateOrTimeEditor`

| Methode | Aufrufer und Zeitpunkt | Vertrag |
| --- | --- | --- |
| `setFormatter(format, locale, langIndex)` | Konstruktor des Delegates und bei `OVERWRITE FORMAT` | `format` ist das Joda-Time-Muster, in dem `setText` liefert und `getText` liefern muss; `locale` ein Sprach-Tag (`de-AT`). Ein reiner Texteditor kann den Aufruf ignorieren. |

Ein mit `OVERWRITE FORMAT` versehenes Datums-Delegate wird von der Runtime nicht editierbar gesetzt.

Umsetzung: [F] ignoriert den Aufruf bei den Texteditoren (feste Masken `__.__.__` und `__:__`); nur die Datumsauswahl wertet das Muster aus. [H] wertet ihn nur mit Picker aus und holt den Formatter über `uiFactory.getTransProvider().getLocalDateFormatter(langIndex, format)`.

## `IToolkit_ImageEditor` und `IToolkit_UploadEditor`

Beide ergänzen `IToolkit_TextEditor` um keine Methoden. Die Runtime reicht nur Text durch; was er bedeutet, legt das Toolkit fest.

**Bild:**

| | `setText` | `setEditorPrompt` (Format der Option `OVERWRITE FORMAT`) |
| --- | --- | --- |
| turkuforms | Vollständige Adresse (`http…`) oder Name relativ zu `uiFactory.getUploadLocationRetrieve()` | `"<maxBreite>,<maxHöhe>"` in Pixeln, `0` für unbegrenzt |
| fx8forms | Lokaler Dateipfad | `"<x>,<y>"`: Pixel, die von Breite und Höhe des Fensters abgezogen werden |
| h2forms | Name; die Adresse entsteht mit `ImageDelegate.adjustPath(uploadLocationRetrieve, name)` | `"<maxBreite>,<maxHöhe>"` in Pixeln, `0` für unbegrenzt |

**Upload:**

- [T] nimmt eine Datei bis `uiFactory.getUploadMaxSize()` Bytes entgegen, legt sie unter `uiFactory.getUploadFsLocationStore()` ab und liefert den Dateinamen über `getText()`.
- [F] kein Upload; der Editor zeigt nur den Dateinamen.
- [H] Der Client lädt die Datei mit einem eigenen Request an die Adresse `getUploadLocationStore()`; der Server legt sie unter `getUploadFsLocationStore()` ab. Der Dateiname kommt mit der nächsten Conclusion als Feldwert. `getUploadMaxSize()` wird nicht geprüft.

## `IToolkit_EnableItem`

```java
void setItemEnabled(boolean enabled);
```

Das Interface ist der Push-Weg für die Bedienbarkeit einer `CmdAction`. Ein Toolkit implementiert es für ein Bedienelement und registriert es mit `action.attachButton1(item)`; für ein zweites Element derselben Action (etwa im Kontextmenü) steht `attachButton2(item)` bereit. Die Runtime ruft `setItemEnabled` aus `CmdAction.reevalEnabled()` nach jeder Selektionsänderung und jedem Reload – aber nur an registrierten Elementen.

Die Registrierung ist optional. Der Pull-Weg ist gleichwertig: `action.reevalEnabled()` liefert die Bedienbarkeit als Rückgabewert. [T] und [F] registrieren ihre Bedienelemente; [H] registriert nichts und fragt beim Rendern jeder Seite ab.

`action.hideWhenDisabled` verlangt, ein nicht bedienbares Element auszublenden. Nur [T] setzt das um.

### Menüs aus `MenuAction`

`RT.genspecification.MenuAction.getAllItems()` liefert die Einträge eines Menüs:

| Eintrag | Bedeutung |
| --- | --- |
| `CmdAction` | `Action`: Bedienelement mit `labelText`, `image` (Icon-Name), `hotKey`, `getToolTip()`, `hideWhenDisabled`. Auslösen ruft `startCommand()`. |
| Kein `CmdAction`, `labelText == null` | Trennstrich. Diese Prüfung kommt vor der Prüfung auf `MenuAction`. |
| `MenuAction` mit nicht leerem `labelText` | `Submenu` mit Beschriftung; Einträge rekursiv über `getAllItems()` |
| `MenuAction` mit `labelText == ""` | Overflow-Menü ohne Beschriftung |

Für Oberflächen ohne Hierarchie bietet `MenuAction` fertige Abflachungen:

| Methode | Liefert |
| --- | --- |
| `getAllCmdActionsOfMenu()` | Alle `CmdAction`s des Menüs samt Submenüs als flache Liste |
| `getTopBarActions()` | Die `CmdAction`s der obersten Ebene |
| `getDropDownActions()` | Die `CmdAction`s der Submenüs |
| `getAllPlainMenus()` | Das Menü selbst und alle Submenüs |

Wenn `setMenuAndInit` oder `addMenuAndSetButtons` aufgerufen wird, sind alle Actions bereits konfiguriert und die Texte übersetzt. Auf der obersten Ebene eines Tabellen- oder Seitenmenüs stehen `CmdAction`s als direkt sichtbare Schaltflächen, gefolgt von Submenüs.

Darstellung: [T] und [F] zeigen Actions der obersten Ebene als Buttons, Submenüs als Dropdown, und bieten die Einträge der Submenüs einer Tabelle zusätzlich als Kontextmenü an. [H] verwendet die Abflachungen.

## Custom UI Elements

Ein `Custom UI Element` des Modells nennt eine Implementierungsklasse. Die Runtime instanziiert sie mit dem parameterlosen Konstruktor (`RT.dsl.ui.PagePane.createCustomUiElement`). Die Klasse implementiert `RT.extensions.ICustomDataUxElement<T>` – sie ist Runtime-Komponente (`IGenSelControlled`) und liefert über `getToolkitImplementation()` ihre `IToolkit_Form`.

| Methode | Bedeutung |
| --- | --- |
| `setOption(key, value)` | Option des Modells |
| `addDelegateInfo(delegateName, path, label, format)` | Ein im Modell angegebenes Delegate: Property-Pfad, Beschriftung, Format |
| `initializeGen(uiFactory, pageController, outerBinding, outerMenu)` | Aufbau der Oberfläche |
| `loadList`, `selectionChanged`, `saveAndValidate`, `collectDelegateChanges`, `forceNotEditable`, `gcClear` | wie bei den eingebauten Elementen |

Die Runtime nennt in `RT.customcomponents.ExtCmpt` feste Klassennamen für Diagramme: `org.modellwerkstatt.addons.components.PieChartUxElement` und `LineChartUxElement`. Ein Toolkit, das Modelle mit diesen Elementen ausführen soll, stellt Klassen unter genau diesen Namen bereit. Nur [T] tut das; in [F] und [H] scheitert ein solches Modell beim Öffnen der Page, weil die Klasse fehlt.

Die Runtime-Komponente hinter `PASSIVE_HTML_FULLSCREEN` (`RT.extensions.HtmlContent`) liefert in `selectionChanged` immer `false`.
