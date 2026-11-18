# Toolkit-Portierung – Leitfaden für eine neue Implementierung

## Zweck und Geltungsbereich

Dieser Leitfaden beschreibt, wie eine neue Implementierung der Toolkit-Schnittstelle entsteht – für Swing, eine React-Oberfläche oder einen MCP-Server zur Fernsteuerung. Er legt Mindestumfang, Reihenfolge und Abnahmekriterien fest und verweist für Einzelheiten auf [Referenz](toolkit-referenz.md), [Lebenszyklus](toolkit-lebenszyklus.md), [Rückkanal](toolkit-rueckkanal.md), [Rahmen](toolkit-rahmen.md) und [Signaturen](toolkit-signaturen.md).

Die Aussagen zum Vertrag sind aus dem Runtime-Code abgeleitet und an drei Implementierungen geprüft. Für eine Oberfläche im Prozess ist fx8forms ([F]) die Vorlage, für eine entfernte Oberfläche h2forms ([H]): Dort hält der Server den Zustand aller Toolkit-Objekte als Daten und rendert ihn je Request – dieselbe Gestalt, die ein MCP-Server braucht. Die Empfehlungen für React und MCP gehen darüber hinaus und sind als Entwurf gekennzeichnet.

## Arbeitsweise

1. Die Interfaces im Paket `org.modellwerkstatt.dataux.runtime.toolkit` sind die Vorgabe; ihre [Signaturen](toolkit-signaturen.md) entscheiden bei Widersprüchen zur Prosa dieser Dokumentation.
2. Das erwartete Verhalten steht in [Referenz](toolkit-referenz.md) und [Lebenszyklus](toolkit-lebenszyklus.md). Wo sich die drei Implementierungen unterscheiden, ist das Verhalten kein Vertrag.
3. Übernommen wird die Aufgabenteilung, nicht die Technik einer vorhandenen Implementierung.
4. Eine Implementierung enthält keine fachliche Logik und keine Kenntnis einzelner Anwendungen.

## Bausteine einer Implementierung

| Baustein | Interface oder Basisklasse | Anzahl |
| --- | --- | --- |
| AppFactory | `BaseUiFactory` + `IToolkit_UiFactory` | 1 |
| Starter | – (lädt Konfiguration, meldet an, erzeugt `ApplicationMDI`) | 1 |
| Hauptfenster | `IToolkit_MainWindow` | 1 |
| Command-Fenster | `IToolkit_CommandContainerUi` | 1 ([H]) oder 2 (Tab, Prompt) |
| Formen | `IToolkit_DelegateForm`, `IToolkit_TableForm`, `IToolkit_FormContainer`, `IToolkit_TabForm`, HTML-Form | 5 |
| Editoren | `IToolkit_TextEditor` und Ableitungen | 6 ([H]) bis 11 |
| Menü | `IToolkit_EnableItem` je Eintrag | 0 ([H]) oder 1 |
| Druckdienst | `IOFXPrintFactory`, `IPrintingServiceImpl` | 0 ([F]) oder 1 |
| Diagramme | `ICustomDataUxElement` unter den Klassennamen aus `ExtCmpt` | optional |

Zum Vergleich: Jede der drei Implementierungen umfasst 7.400 bis 9.000 Zeilen Java. Was davon Pflicht ist, zeigt [Einstieg: Pflicht und Kür](toolkit-schnittstelle.md#pflicht-und-kür).

## Stufenplan

Jede Stufe endet mit einer lauffähigen Anwendung. Als Testanwendung eignet sich ein kleines Modell mit einem `SEARCH_CMD`, einem `GRAPH_OWNER_CMD` und einem `GRAPH_EDIT_CMD`.

### Stufe 1 – Start bis zu den Tiles

- AppFactory als Unterklasse von `BaseUiFactory`; alle `create*`-Methoden werfen zunächst `UnsupportedOperationException`, `useBackgroundThread()` liefert `false`, `flagValidationAdditionally()` liefert `true`.
- Starter nach [Rahmen: Startsequenz](toolkit-rahmen.md#startsequenz); Anmeldung über `LoginController` mit festen oder abgefragten Zugangsdaten.
- Hauptfenster: `setMenuAndInit`, `showTiles`, `showDialog`, `showProblemsDialog`, `setToastMessage`, `inUiThread`, `closeApplicationAndExit`; alle übrigen Methoden zunächst leer.
- Menüeinträge und Tiles rufen `startCommand()`. Die Bedienbarkeit kommt über `attachButton1` oder über `reevalEnabled()` bei der Darstellung.

**Abnahme:** Die Anwendung startet, zeigt Menü und Tiles; nicht bedienbare Einträge sind gesperrt; ein Fehler im Start erscheint als Dialog; Abmelden beendet die Anwendung.

### Stufe 2 – Ein Command mit Formular

- `createTabContainerUi`, `createPromptContainerUi` mit `show`, `setContent`, `setConclusions`, `reevalConclusions`, `close`, `delayedRequestFocus`.
- Hauptfenster: `addTab`, `focusTab`, `ensureTabClosed`; bei mehreren sichtbaren Tabs zusätzlich `setCurrentTabModal` und die Meldung `onTabChangeEvent`.
- `createToolkitDelegateForm` mit `addDelegate`, `checkDelegatesValidAndFocus`, `setTitleText`, `setProblems`, `myRequestFocus`.
- `createTextEditor`, `createDummyEditor`, `createStatusEditor`.
- `createToolkitFormContainer(false)` mit einer Zelle – die Runtime erzeugt ihn als Wurzel-Wrapper.

**Abnahme:** Ein `GRAPH_OWNER_CMD` mit `Delegate Form` lässt sich öffnen, bearbeiten, speichern und abbrechen. Eine verletzte Precondition erscheint über dem Formular, ein Eingabefehler am Feld. Bei ungespeicherten Änderungen fragt die Anwendung vor dem Abbruch nach. `NO_ESC` sperrt den Abbruch.

### Stufe 3 – Tabelle, Layouts, Menüs

- `createToolkitTableForm`: Spalten, `loadList`, `selectionChanged`, Meldung der Selektion, Tabellenmenü.
- `createToolkitFormContainer` mit mehreren Kindern, `addMenuAndSetButtons`, `skipFocus`; `createToolkitTabForm`.
- `createReferenceEditor`, Datumseditoren.

**Abnahme:** Master-Detail funktioniert ohne eigenen Code: Die Auswahl in einer Tabelle lädt abhängige Tabellen und Formulare. Actions werden mit der Auswahl bedienbar und gesperrt. Ein `GRAPH_EDIT_CMD` öffnet über dem Tab, und nach seinem Abschluss zeigt der Parent die geänderten Daten.

### Stufe 4 – Leistungsmerkmale

Nach Bedarf der Zielplattform:

- Hotkeys; Hauptaktion auf Doppelklick und Enter; Mehrfachselektion und Mehrfachausführung.
- `setIssuesUpdateConclusion`, mehrzeiliger Text, Bild, Upload.
- Tabelle: Inline-Bearbeitung, Summenzeilen, Farben, gefaltete Spalten, Export, `setEditPreview`, `forceNotEditable`.
- Command-Fenster: `setNotification`, `setColor`, `adjustWindowTitle`.
- `createCustomForm(PASSIVE_HTML_FULLSCREEN)`, Diagramme.
- Druckdienst, `installCloseConfirmQuestion`, `showGraphDebugger`.
- Sprache, Kompaktmodus und Branding aus der User Environment.

**Abnahme:** Die für die Zielplattform zutreffenden Punkte der [Abnahme-Checkliste](#abnahme-checkliste) sind erfüllt.

## Entwurfsentscheidungen

### Datenhaltung der Editoren

Die Runtime liest Editorwerte synchron (`getText()`, `getSelectedIndex()`). Im Prozess liefert das Bedienelement den Wert direkt. Bei einer entfernten Oberfläche hält der Server je Editor ein Zustandsobjekt; die Werte des Clients müssen darin stehen, bevor der Server eine Conclusion an die Runtime meldet. [H] übernimmt sie genau dann: mit dem Request einer Nicht-ESC-Conclusion, für alle enabled Editoren.

### Bedienbarkeit: Push oder Pull

- **Push:** Bedienelemente als `IToolkit_EnableItem` registrieren; die Runtime ruft `setItemEnabled` ([T], [F]). Passend, wenn Bedienelemente dauerhaft existieren.
- **Pull:** Bei jeder Darstellung `CmdAction.reevalEnabled()` aufrufen und das Ergebnis verwenden ([H]). Passend, wenn der Zustand je Interaktion neu beschrieben wird.

Für Conclusions gibt es nur den Push über `reevalConclusions`.

### Wiedereintritt

Meldungen an die Runtime lösen synchron Aufrufe an denselben Objekten aus (siehe [Lebenszyklus: Grundregeln](toolkit-lebenszyklus.md#grundregeln-v)). Bewährt haben sich:

- ein Schalter, der Meldungen unterdrückt, während das Toolkit selbst Auswahl oder Text setzt ([F]);
- eine Prüfung auf „noch aktiv“ nach jedem Rückruf, bevor das Toolkit weiter auf sein Fenster zugreift.

Ein Toolkit, dessen Objekte nur Zustand speichern und erst nach der Interaktion dargestellt werden, braucht beides nicht ([H]).

### Häufige Aufrufe

`setEnabled`, `setText`, `setItemEnabled` und `reevalConclusions` kommen nach jeder Interaktion für alle Elemente. Ein Vergleich mit dem zuletzt gesetzten Wert vermeidet unnötige Aktualisierungen einer Oberfläche im Prozess.

### Hintergrundverarbeitung

Mit `useBackgroundThread() == false` läuft jede Interaktion vollständig im aufrufenden Thread ([T], [H]). Für Request/Response-Toolkits ist das die übliche Wahl; `execEventInBackground` und `execEventInForeground` dürfen dann werfen. Eine Desktop-Oberfläche reagiert bei langen Datenbankzugriffen so lange nicht.

Mit `true` führt das Toolkit `command init` und `page init` in einem Arbeitsthread aus, sperrt währenddessen alle Eingaben und ruft danach `processPendingEvents()` im UI-Thread; [F] zeigt den Ablauf (siehe [Lebenszyklus: Hintergrundverarbeitung](toolkit-lebenszyklus.md#hintergrundverarbeitung)).

### Ein Fenster oder viele

Die Runtime führt Tabs und das Prompt-Fenster selbst. Ein Toolkit kann sich auf ein sichtbares Command-Fenster beschränken ([H]): `isTabWindow()` liefert immer `true`, `addTab` und `focusTab` merken das aktuelle Fenster, `ensureTabClosed` vergisst es; `setCurrentTabModal` und `onTabChangeEvent` entfallen. Solange ein Command offen ist, sind Menü und Tiles dann nicht erreichbar.

## Das Protokoll von h2forms

h2forms ist die einzige vorhandene Implementierung mit entfernter Oberfläche ohne eigenes UI-Framework auf dem Server. Der Abschnitt beschreibt, wie sie die prozessinterne, synchrone Schnittstelle auf Requests abbildet.

### Zustand auf dem Server

Alle Toolkit-Objekte sind reine Datenhalter:

- Command-Fenster: Titel, Farbe, Notification, Conclusions, Wurzelform.
- Formen: Kinder, Spalten, Objektliste, Selektion, Titel, Probleme.
- Editoren: Text beziehungsweise Index, enabled, Fehlertext, Label, Fokusmarke.
- Hauptfenster: das eine aktuelle Command-Fenster, der eine Dialog, Tiles, Startmenü.

Aufrufe der Runtime ändern diese Daten. Dargestellt wird erst nach Abschluss der Interaktion, immer als vollständige Seite; Zwischenstände erreichen den Client nie.

### Ablauf eines Requests

1. Sperre je Sitzung: Ein Request je Sitzung, weitere warten.
2. Veralteten Request erkennen (siehe unten); ein veralteter wird nicht ausgeführt.
3. Ist ein blockierender Dialog offen, wird nur dessen Quittierung angenommen: Dialog entfernen, `runnable.run(confirmed)`.
4. Sonst das Ereignis an das aktuelle Command-Fenster:
   - Korrekturaktion eines Problems → `performAction()`.
   - Conclusion → bei Nicht-ESC zuerst die Feldwerte des Requests in die enabled Editoren übernehmen, dann `receiveAndProcess(new ConclusionEvent(hash, text))`.
   - Action → die Form suchen, zu der sie gehört; in einer Tabelle zuerst `pushSelection(Zeile)`; dann `reevalEnabled()` und bei `true` `startCommand()`.
5. Runnables nicht blockierender Dialoge (Hinweise, Probleme) sofort ausführen.
6. Antwort erzeugen: genau eines von Redirect (Abmelden, paralleles Deployment, `openUrl`), blockierendem Dialog allein, Seite des aktuellen Command-Fensters oder Startseite mit Tiles und Startmenü.
7. Sitzung beenden, wenn die Anwendung es vorgemerkt hat.

### Kennungen

| Element | Kennung im Request | Gültig |
| --- | --- | --- |
| Conclusion | `conclusionHashCode` der Runtime | für die aktuelle Page |
| Action | Identitäts-Hash der `CmdAction`, dazu der Hash der Form und der Zeilenindex | solange die Page besteht |
| Tabellenzeile | Index in der zuletzt geladenen Liste | bis zum nächsten `loadList` |
| Editor | Präfix plus Identitäts-Hash des Editors | solange der Editor besteht |
| Eintrag eines Reference- oder Status-Editors | Index aus `setItems` | bis zum nächsten `setItems` |
| Korrekturaktion | `IUxEventActionable.getTriggerCode()` | – |
| Tile, Startmenü-Eintrag | Position (`_tilemenu<i>`, `_startmenu<i>`) | solange die Reihenfolge gleich bleibt |
| Dialog | fest: OK oder ESC | es gibt nur einen |

Diese flüchtigen Kennungen tragen, weil jede Antwort die vollständige Seite mit frischen Kennungen liefert und veraltete Seiten abgewiesen werden.

### Veraltete Requests

- Jede Antwort trägt eine Sequenznummer, die der Server je Antwort erhöht. Ein Request mit älterer Nummer wird nicht ausgeführt und mit dem aktuellen Zustand beantwortet. Das deckt Browser-Zurück, Neuladen und Doppel-Submit ab.
- Eine Action, die keine Form kennt oder die für die Zeile nicht bedienbar ist, sowie ein fehlender Feldwert eines enabled Editors führen zu einer internen Ausnahme: nur Protokoll, der aktuelle Zustand wird neu geliefert.
- Für Conclusions prüft h2forms die Bedienbarkeit nicht serverseitig. Trifft dennoch ein `ConclusionEvent` für eine nicht bedienbare oder unbekannte Conclusion ein, beendet die Runtime den Command mit einem Fehlerdialog.

### Feldwerte

| Editor | Im Request | In `getText()` / `getSelectedIndex()` |
| --- | --- | --- |
| Text | Text | unverändert, kein Trimmen |
| Datum mit Auswahl | `yyyy-MM-dd` des Browsers | mit dem Formatter aus `setFormatter` in das Delegate-Format gewandelt |
| Reference | Index des Eintrags | Anzeigetext des Eintrags oder `null` |
| Status | Index des Eintrags | Index |
| Upload | Dateiname, nachdem der Client die Datei mit einem eigenen Request hochgeladen hat | Dateiname |
| disabled Editor | nichts | bisheriger Wert |

Feldwerte kommen nur mit einer Nicht-ESC-Conclusion. Bei Actions und ESC verwirft h2forms getippte Werte.

### Was h2forms nicht hat

- Keinen nebenwirkungsfreien Lesezugriff: Der Zustand kommt nur als Antwort einer Interaktion. Ein Aufruf der Startadresse ohne Ereignis beendet alle laufenden Commands.
- Keinen Kanal vom Server zum Client: Sperrmeldungen erscheinen mit der nächsten Interaktion (siehe [Lebenszyklus: Hintergrundverarbeitung](toolkit-lebenszyklus.md#hintergrundverarbeitung)).
- Keine eigenständige Zeilenauswahl, keine Mehrfachselektion, keinen Tabwechsel, keine Tasten-Hotkeys.

## Hinweise je Zielplattform

### Swing und JavaFX

- UI-Thread ist der Event Dispatch Thread beziehungsweise der JavaFX Application Thread; `inUiThread()` prüft darauf.
- Es gibt eine `ApplicationMDI` je Prozess; `distributeTermEventOnOtherInstance` bleibt leer.
- Tabs: `JTabbedPane` beziehungsweise `TabPane`; der Auswahl-Listener darf `onTabChangeEvent` auch während `addTab`, `focusTab` und `ensureTabClosed` melden, muss aber den richtigen Index liefern.
- Prompt: modaler Dialog ohne eigenes Schließen-Kreuz oder mit Zuordnung zur ESC-Conclusion. Er meldet seine Tasten selbst an seinen Container.
- Dialoge: nicht blockierend umsetzen. [F] zeigt ein anwendungsmodales Fenster mit `show()` und ruft das Runnable im Handler der Schaltfläche. Ein blockierender Dialog würde das Runnable innerhalb der laufenden Ereignisschleife der Runtime ausführen; das ist durch keine Implementierung belegt.
- Tasten: am einfachsten jede Taste als `KeyEvent` durchreichen ([F]); siehe [Rückkanal: Hotkeys](toolkit-rueckkanal.md#zwei-wege).
- Schließen des Hauptfensters: `onExitRequested(false)` aufrufen und das Fenster erst in `closeApplicationAndExit()` schließen.
- `setAdjustUrl`, `resetUrl`, `parDeploymentForwardNow` und `installCloseConfirmQuestion` bleiben leer.
- Druck: die FOP-Basis der ObjectFlow-Runtime öffnet Dateien mit der Standardanwendung des Systems.
- `isFX8LegacyImpl()` bleibt `false`.

### React und andere Web-Clients (Entwurf)

Die Schnittstelle ist prozessintern und synchron; ein Web-Client braucht eine serverseitige Toolkit-Implementierung, die einen Zustandsbaum führt, und ein Protokoll zum Client. Der Aufbau folgt [Das Protokoll von h2forms](#das-protokoll-von-h2forms); abweichende Empfehlungen sind begründet.

- **Serverseitiges Modell:** wie h2forms.
- **Übertragung:** nach Abschluss einer Interaktion. h2forms überträgt immer die vollständige Seite; ein Client mit eigenem Zustand kann stattdessen Änderungen erhalten.
- **Kennungen:** Wer nur Änderungen überträgt, braucht Kennungen, die über Interaktionen hinweg gültig bleiben, und vergibt sie selbst; `conclusionHashCode` gilt nur für die aktuelle Page. Wer wie h2forms je Interaktion alles neu liefert, kommt mit den flüchtigen Kennungen aus.
- **Veraltete Ereignisse:** Sequenznummer wie h2forms; zusätzlich Conclusions serverseitig auf `enabled` prüfen.
- **Zeilenobjekte:** Tabellenzeilen werden als Texte der Converter übertragen; die Zuordnung Zeile ↔ Objekt bleibt auf dem Server, weil die Selektion die Objektinstanz verlangt.
- **Reihenfolge:** Je `ApplicationMDI` ist höchstens ein Ereignis in Arbeit.
- **Feldwerte:** mit jedem Ereignis übertragen, nicht nur mit Conclusions – so gehen Eingaben bei einer Action nicht verloren.
- **Sitzungen:** Eine `ApplicationMDI` je Client-Sitzung; Ende der Sitzung führt zu `internal_immediatelyShutdown()`.
- **Meldungen ohne Anlass:** Ein Kanal vom Server zum Client ist nicht nötig. Er wäre Komfort für Sperrmeldungen und für Ereignisse anderer Instanzen desselben Benutzers.

### MCP-Server zur Fernsteuerung (Entwurf)

Ein MCP-Server stellt die Anwendung einem Agenten als Werkzeuge bereit. Er verwendet dasselbe serverseitige Modell; an die Stelle der HTML-Seite tritt eine textuelle Beschreibung des Zustands.

Empfohlener Zuschnitt der Werkzeuge:

| Werkzeug | Abbildung | Vorbild in h2forms |
| --- | --- | --- |
| Zustand lesen | Aktueller Baum: Tiles und Menü, offene Command-Fenster, das aktive Fenster mit Titel, Meldungen, Formularfeldern (Label, Wert, editierbar, Fehler), Tabellen (Spalten, Zeilen, Auswahl), Actions und Conclusions jeweils mit Bedienbarkeit, offener Dialog | Fehlt als eigener Zugriff. Der MCP-Server muss ihn ohne Nebenwirkung anbieten. Actions dabei per `reevalEnabled()` abfragen. |
| Action ausführen | `CmdAction.reevalEnabled()`, dann `startCommand()` | Vorhanden; bei einer Tabellen-Action zuerst die Zeile selektieren |
| Conclusion ausführen | Feldwerte übernehmen, `enabled` prüfen, `ConclusionEvent` | Vorhanden, aber ohne Prüfung von `enabled` |
| Feld setzen | Wert im Zustandsobjekt des Editors setzen | In h2forms Teil des Conclusion-Requests |
| Zeilen auswählen | `pushSelection` mit den Objekten der genannten Zeilen | Nur zusammen mit einer Action |
| Dialog quittieren | `DlgRunnable.run(confirmed)` | Vorhanden für Fehler und Rückfragen |
| Tab aktivieren | `onTabChangeEvent(index)` | Fehlt; entfällt, wenn nur das oberste Fenster bedienbar ist |

Besonderheiten:

- **Ergebnis einer Aktion** ist der Zustand nach Abschluss der Interaktion, einschließlich neuer Meldungen. Ein offener Fehler- oder Rückfragedialog muss im Ergebnis hervorgehoben sein: Solange er offen ist, nimmt der Server nur die Quittierung an.
- **Hinweise ohne Quittierung:** Hinweise (`INFO_*`), Problemlisten und Toasts können wie in h2forms sofort quittiert und einmal im Ergebnis mitgeliefert werden. Das erspart dem Agenten einen Schritt je Meldung.
- **Bedienbarkeit prüfen:** Ein Werkzeugaufruf für ein gesperrtes Element wird mit einer verständlichen Meldung abgelehnt und nicht an die Runtime gesendet. Für Conclusions ist das zwingend, weil die Runtime sonst den Command beendet.
- **Veralteter Zustand:** Eine Sequenznummer im gelesenen Zustand, die der Agent bei jeder Aktion mitgibt, weist Aktionen auf überholtem Stand ab.
- **Kennungen:** Eigene, sprechende Kennungen (Beschriftung, Property-Name, Zeilenindex) sind für einen Agenten brauchbarer als Hashes. `IDlgt.getPropertyName()` liefert den Namen der Property eines Feldes.
- **Feldwerte** nimmt der Server im Textformat des jeweiligen Delegates entgegen (siehe [Referenz: Textformate](toolkit-referenz.md#textformate-je-delegate)); das Format gehört in die Zustandsbeschreibung. Für Reference und Status bietet sich die Liste der Einträge an; der Server setzt den Text beziehungsweise den Index.
- **`UPD` und `GO`** tragen Bedeutung und gehören markiert in die Zustandsbeschreibung: `UPD` aktualisiert die Page nach einer Eingabe, `GO` ist die Standardaktion. Nach dem Setzen eines Feldes mit `ISSUE UPDATE/SCANABLE` kann der Server die `UPD`-Conclusion selbst auslösen.
- **Adresse öffnen:** `getLastRequestedRedirectAndReset()` nach jeder Interaktion abfragen und die Adresse im Ergebnis melden.
- **Fokus, Farben, Layoutgewichte und Tasten-Hotkeys** sind für einen Agenten ohne Bedeutung; die zugehörigen Methoden bleiben leer oder speichern den Wert. Die Fokusmarke kann als Hinweis auf das nächste erwartete Feld dienen.
- **Plattformwert:** `MoWarePlatform` hat keinen Wert für MCP; in Frage kommt `MOWARE_API`.
- **Alternativen ohne Oberfläche:** Für reine Ablauftests bietet ObjectFlow `run command` in einer `OFXTestSuit`; die Runtime enthält mit `RT.httpapi` außerdem einen oberflächenlosen Fernzugang. Ein MCP-Toolkit ist dann passend, wenn die modellierte Oberfläche selbst – `Page Pane`s, Menüs, Selektion – bedient werden soll.

## Abnahme-Checkliste

Die Liste ist der volle Umfang. Punkte mit dem Zusatz *(Kür)* erfüllt mindestens eine der drei Implementierungen nicht; siehe [Einstieg: Pflicht und Kür](toolkit-schnittstelle.md#pflicht-und-kür).

### Anwendung

- [ ] Start mit und ohne Start-Command; Tiles erscheinen, wenn kein Command offen ist.
- [ ] Dynamische Tile-Beschriftungen aktualisieren sich nach dem Schließen eines Commands.
- [ ] Startmenü; Extra-Menü und Hilfe-Menü mit Submenüs und Trennstrichen *(Kür)*.
- [ ] Hotkeys des Hauptmenüs wirken auch mit Fokus in einem Editor *(Kür)*.
- [ ] Abmelden; Rückfrage bei ungespeicherten Änderungen *(Kür)*.
- [ ] Ein Fehler beim Start führt zu einem Dialog und zum Beenden.

### Commands

- [ ] `SEARCH_CMD`, `GRAPH_OWNER_CMD` als Tab; `GRAPH_OWNER_CMD(modal)` sperrt Menü und andere Tabs.
- [ ] `GRAPH_EDIT_CMD` über seinem Parent.
- [ ] Derselbe Command mit denselben Parametern öffnet kein zweites Fenster, sondern aktiviert das vorhandene.
- [ ] Wechsel auf eine andere Page ersetzt Form und Conclusions; Refresh behält die Form.
- [ ] Successor übernimmt das Fenster des Vorgängers.
- [ ] Precondition im `command init`: Meldung, kein Fenster.
- [ ] Precondition in einer Conclusion: Meldung über der Form, Fenster bleibt.
- [ ] `resolve with`: Schaltfläche an der Meldung startet den Korrektur-Command.
- [ ] Exception und Guard: Fehlerdialog, danach schließt der Command.
- [ ] `user toast message` nach erfolgreichem Abschluss.
- [ ] Read-only-Session: Formulare und Conclusions gesperrt, Abbrechen möglich.
- [ ] Sperre durch anderen Benutzer: Hinweis im Fenster.
- [ ] Eine nicht bedienbare Conclusion lässt sich nicht auslösen, auch nicht durch einen veralteten Request.
- [ ] Mehrfachausführung über eine Mehrfachselektion *(Kür)*.

### Formulare und Editoren

- [ ] Die Delegate-Typen aus [DataUX: Delegates](../../moai/docu/dataux.md#kapitellandkarte-delegates) mit den Optionen `DISABLED`, `OPTIONAL`; `PICKER`, `ALTER`, `WIDE`, `NUM OF LINES`, `TIME PICKER ONLY` *(Kür)*.
- [ ] Leere Eingaben verhalten sich nach [DataUX: Pflichtwerte](../../moai/docu/dataux.md#pflichtwerte-leere-eingaben-und-null).
- [ ] Eingabefehler erscheinen am Feld; das erste fehlerhafte Feld erhält den Fokus.
- [ ] `#Meta.setEnabled`, `setOptional`, `requestFocus`, `setElements` und `setScope` wirken auf den Editor.
- [ ] `ISSUE UPDATE/SCANABLE` löst die `SCAN/UPDATE`-Conclusion aus; danach liegt der Fokus wieder im auslösenden Feld *(Kür)*.
- [ ] Spaltengewichte im `Delegate Form` *(Kür)*.

### Tabellen

- [ ] Zellentexte über die Converter; Selektion wird dargestellt und gemeldet.
- [ ] Zwei Tabellen desselben Typs zeigen dieselbe Selektion.
- [ ] Tabellenmenü mit Actions; Bedienbarkeit folgt der Selektion.
- [ ] `TABLE SUMMARY LINE`.
- [ ] Spaltenbreiten in Prozent; `FOLD`, `IMPORTANT`, `RIGHT ALIGN`, Statusfarben, `COLOR` *(Kür)*.
- [ ] `SELECT FIRST`.
- [ ] Mehrfachselektion *(Kür)*.
- [ ] Doppelklick und Enter führen die `ENTER`-Action aus *(Kür)*.
- [ ] Submenü, Overflow und lokale Hotkeys im Tabellenmenü *(Kür)*.
- [ ] `EDITABLE`-Spalte; gesperrt bei Read-only *(Kür)*.
- [ ] `SELECTION SUMMARY LINE`, Export *(Kür)*.

### Layouts

- [ ] `Grid Layout` mit mehreren Kindern; `FOCUS FORWARD`.
- [ ] Gewichte `-1` und `1*` bis `5*`; `FLEXIBLE` *(Kür)*.
- [ ] `Tab Layout`.
- [ ] Menü des `Page Pane`s; globale Hotkeys *(Kür)*.
- [ ] Page Title und Subtitle; `LABEL` an eingebetteten Elementen.
- [ ] Farbe des `Page Pane`s (`COLOR`, `SCOLOR`) *(Kür)*.

## Bekannte Eigenheiten der Implementierungen

Diese Punkte sind Verhalten einer Implementierung und kein Vertrag. Eine neue Implementierung übernimmt sie nicht ungeprüft.

### turkuforms

| Eigenheit | Klasse |
| --- | --- |
| Ein bei `NO_ESC` ausgeblendeter Abbrechen-Button wird nicht wieder eingeblendet, wenn die Runtime ESC wegen einer Sperre aktiviert. | `TF.views.CmdUi` |
| `setConclusions` verändert `buttonTitle` der übergebenen Objekte (Hotkey-Zusatz). | `TF.views.CmdUi` |
| `showTiles` wirft bei offenen Tabs. | `TF.core2.TurkuMainWin2` |
| Im Kompaktmodus fehlen Extra- und Hilfe-Menü; anwendungsweite Hotkeys wirken nicht, solange ein Command offen ist. | `TF.core2.TurkuMainWin2` |
| `enableKeyReleaseEvents()` wirft. | `TF.editors.FormChild` |
| Inline-Bearbeitung in Tabellen nur für `BigDecimal`. | `TF.forms.TurkuTable` |
| `forceNotEditable()` verhindert das Öffnen der Zelle nicht, sondern lehnt die Übernahme ab. | `TF.forms.TurkuTable` |
| Der Hotkey einer Action auf oberster Ebene eines Tabellenmenüs wird nicht registriert; lokale Hotkeys gelten nur für Einträge des Kontextmenüs. | `TF.forms.MenuContext` |
| Der Zwischenspeicher der Datumsformate ist nur nach dem Format geschlüsselt, nicht nach der Sprache. | `TF.editors.DatePickerEditor` |
| `UploadEditor` behält den Dateinamen beim Wechsel des gebundenen Objekts. | `TF.editors.UploadEditor` |
| Das Feld `initialStartupParams` wird in `parDeploymentForwardNow` gelesen, aber nicht gesetzt. | `TF.core2.TurkuMainWin2` |
| Die Protokolldatei ist als fester Pfad eingetragen; ohne `##<version>` im Bereitstellungspfad scheitert der Start des Servlets. | `TF.core.TurkuServlet` |
| `cmdAccessible` liefert immer `true`. | `TF.core.TurkuAppFactory` |

### fx8forms

| Eigenheit | Klasse |
| --- | --- |
| Anmeldung fest über die Umgebungsvariablen `USERNAME` und `COMPUTERNAME` mit leerem Passwort. | `FX.windows.FX8Application` |
| Unter Linux erreichen außer ESC und den Entwicklertasten keine Hotkeys die Runtime. | `FX.windows.FX8Application` |
| `withModifier` gilt für Ctrl und Alt; Conclusions mit einbuchstabigem Hotkey sind per Taste nicht auslösbar. | `FX.windows.FX8Application` |
| `setConclusions` verändert `buttonTitle`; bei `hotkey == null` entsteht der Zusatz „(null)“. | `FX.windows.FX8CmdUiBasis` |
| `hideWhenDisabled` wird nirgends ausgewertet. | – |
| Doppelklick startet alle `ENTER`-Actions eines Tabellenmenüs, nicht nur die erste. | `FX.windows.FX8ActionHBox` |
| `loadList` kann bei aktivem Suchfilter eine leere Selektion melden. | `FX.windows.FX8TableForm` |
| Ein `selectionChanged` während des eigenen `pushSelection` wird ignoriert, auch wenn die Runtime die Selektion gerade leert. | `FX.windows.FX8TableForm` |
| Die Zellfarbe aus `getBgColor` wird als Schriftfarbe gesetzt. | `FX.windows.FX8TableForm` |
| Inline-Bearbeitung schreibt in die selektierte Zeile, ohne Grenzprüfung. | `FX8EditCellBigdeci`, `FX8EditCellStatus` |
| Der Tab-Listener meldet `onTabChangeEvent` auch bei programmatischen Wechseln, nach dem letzten Tab mit `-1`. | `FX.windows.FX8Application` |
| `close()` des Tabs ist leer; aufgeräumt wird in `ensureTabClosed`. | `FX.windows.FX8CmdUiTab` |
| `flex` wird ignoriert; mehrzeilige Container sind immer verstellbar. | `FX.windows.FX8FormContainer` |
| Datumsformate der Texteditoren sind fest; `setFormatter`, `withPicker` (bei Datum mit Uhrzeit) und `timeOnly` werden ignoriert. Die Datumsauswahl deutet das Joda-Muster als `java.time`-Muster. | `FX.delegates.*` |
| `alter` wird bei Status und Reference ignoriert. | `FX.windows.FX8UiFactory` |
| Mehrzeiliger Editor: `setIssuesUpdateConclusion()` wirft. Datum mit Uhrzeit: `enableKeyReleaseEvents()` wirft. | `FX.delegates.*` |
| Reference: `getText()` leert das Feld, wenn kein Eintrag passt; `setText` wirft bei unbekanntem Text. | `FX.delegates.AutoCompletePopupField` |
| Text und mehrzeiliger Text ersetzen `€` beim Tippen durch `EUR`. | `FX.delegates.*` |
| `PASSIVE_HTML_FULLSCREEN` zeigt das HTML als Rohtext. | `FX.windows.FX8HtmlContent` |
| Die Sperre bei Hintergrundarbeit erfasst nur das Hauptfenster, keine Prompts. | `FX.windows.FX8MainBorder` |
| `setToastMessage("-")` wird verschluckt. | `FX.windows.FX8Application` |
| `initCmdUrlDefaults()` wird nie aufgerufen; kein Command-Start per URL. | – |
| Bild: lokaler Dateipfad. Upload: nur Anzeige. Diagramme fehlen. | `FX.delegates.*` |

### h2forms

| Eigenheit | Klasse |
| --- | --- |
| Beim Rendern einer Tabelle mit Menü wird je Zeile die Selektion der Runtime gesetzt und am Ende zurückgesetzt, um die Bedienbarkeit je Zeile zu berechnen. Jedes Setzen löst das Laden abhängiger Elemente und die Neuauswertung aller Actions aus. | `H2.pebble.TableForm` |
| Selektion mit der Laufzeitklasse der Zeile; die Klasse aus `endOfInitializationForElementClass` wird ignoriert. | `H2.pebble.TableForm` |
| Spaltenüberschriften werden nie ausgegeben; mehr als 12 Spalten werfen. | `H2.pebble.TableForm` |
| `getBgColor` wird als Schriftfarbe verwendet. | `H2.pebble.TableForm` |
| Bei Actions und ESC werden getippte Feldwerte verworfen. | `H2.pebble.CommandContainerUi` |
| Keine serverseitige Prüfung, ob eine Conclusion enabled ist. | `H2.pebble.CommandContainerUi` |
| Standardaktion für Enter ist die letzte bedienbare Conclusion; ist nur ESC bedienbar, schließt Enter den Command. | `H2.pebble.CommandContainerUi` |
| `setToastMessage` überschreibt den Dialog-Slot ohne Prüfung – auch einen offenen blockierenden Dialog. | `H2.core.H2Application` |
| Ein Aufruf der Startadresse ohne Ereignis beendet alle laufenden Commands und verwirft einen offenen Dialog, ohne dessen Runnable zu rufen. | `H2.core.H2Application` |
| Startmenü: Bedienbarkeit beim Anmelden eingefroren. Extras- und Hilfe-Menü fehlen. | `H2.core.H2Application` |
| Abmelden ohne Rückfrage; `onExitRequested` wird nie verwendet. | `H2.core.H2Application` |
| `delayedAfterFullUiInitialized` ist leer; `afterFullUiInitialized` der Formen wird nie aufgerufen. | `H2.pebble.CommandContainerUi` |
| `close()` und `gcClear()` der meisten Klassen sind leer. | `H2.pebble.*` |
| Mehrzeilige Texteditoren sind nur Anzeige. | `H2.pebble.TextEditor` |
| Reference: Der Eintrag für „nichts gewählt“ wird nach `setOptionalAfterLoad(false)` nicht entfernt; zwei gleiche Anzeigetexte werfen beim Rendern. | `H2.pebble.ReferenceEditor` |
| Upload: keine Sitzungsprüfung, `getUploadMaxSize()` unbenutzt; ins Feld kommt der Dateiname des Clients. | `H2.core.H2ApplicationLoader` |
| Die Protokolldatei ist als fester Pfad eingetragen. | `H2.core.H2ApplicationLoader` |
| Die Sitzung endet bei Wechsel des Netzes der Client-Adresse oder des Parameters `username`. | `H2.core.H2ApplicationLoader` |
| `view`, `print`, `openUrl` des Druckdienstes sind leer. | `H2.print.H2UserPrintService` |
| `REFERENCEDLGT_PROVIDE_HINT` ohne Wirkung; Diagramme fehlen. | – |

## Offene Punkte

Diese Fragen ließen sich aus dem ausgewerteten Code nicht beantworten:

- Welche Hotkey-Zeichenketten und Icon-Namen die `Static Ressources` der Modelle liefern. Fest stehen nur `ESC`, `UPD`, `GO`, `ENTER` und die `DBG_`-Werte.
- Welche Farbnotationen Modelle neben `#RRGGBB` liefern. Die Runtime reicht jede Zeichenkette durch; alle drei Implementierungen setzen sie als CSS-Wert ein.
- Ob eine Desktop-Oberfläche dem semantischen Hotkey `GO` eine Taste zuordnen soll. [F] tut es nicht; auf MDE-Geräten ([H]) ist es eine eigene Schaltfläche beziehungsweise Enter.
- Ob ein blockierender Dialog (Runnable nach der Rückkehr von `showDialog`) mit der Ereignisschleife der Runtime verträglich ist. Keine Implementierung tut es.
- Ob Anwendungen `IDataUxElementHook` tatsächlich verwenden. Der Weg ist im [Rückkanal](toolkit-rueckkanal.md#hook-für-tastatureingaben) beschrieben; im ausgewerteten Code installiert niemand einen Hook.
- Welche Druck-Bean die Konfigurationen der fx8forms-Anwendungen setzen.
