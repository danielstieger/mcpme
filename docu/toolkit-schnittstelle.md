# Toolkit-Schnittstelle der DataUX-Runtime – Einstieg

## Zweck und Geltungsbereich

Die **modellwerkstatt MoWare-Werkbank** führt modellierte Anwendungen auf verschiedenen Oberflächen aus. Was dafür je Oberfläche zu programmieren ist, legt die **Toolkit-Schnittstelle** der DataUX-Runtime fest. Drei Implementierungen existieren: turkuforms (Vaadin, Browser), fx8forms (JavaFX, Desktop) und h2forms (servergerendertes HTML für MDE-Geräte).

Diese Dokumentation beschreibt die Schnittstelle so, dass eine weitere Implementierung entstehen kann – etwa mit Swing, React oder als MCP-Server zur Fernsteuerung. Sie richtet sich an Entwickler und an Agenten, die eine solche Implementierung schreiben.

Die Dokumentation ist in sich geschlossen: Sie setzt keinen Zugriff auf den Quelltext der Runtime oder einer der drei Implementierungen voraus. Alle benötigten Signaturen stehen in [Signaturen](toolkit-signaturen.md); zum Übersetzen genügen die Runtime-Bibliotheken (siehe [Quellen](#quellen)).

Die fachliche Seite – was ein `Page Pane`, ein `Command` oder eine `Table` im Modell bedeutet – beschreibt die Werkbank-Dokumentation in MoAI: [MoWare-Werkbank](../../moai/docu/moware-werkbank.md), [DataUX](../../moai/docu/dataux.md), [ObjectFlow](../../moai/docu/objectflow.md), [ManMap](../../moai/docu/manmap.md). Sie wird hier vorausgesetzt. Die relativen Verweise gelten, wenn MoAI neben diesem Projekt ausgecheckt ist; sonst liegen die Dokumente unter `github.com/danielstieger/moai/blob/master/docu`.

## Dokumente

| Dokument | Inhalt | Lesen, um … |
| --- | --- | --- |
| **Einstieg** (dieses Dokument) | Architektur, Rollen, Pflicht und Kür, Begriffe, Quellen | … die Aufgabenteilung zu verstehen |
| [Lebenszyklus](toolkit-lebenszyklus.md) | Aufrufreihenfolgen: Start, Command öffnen, Page-Wechsel, Conclusion, Schließen, Threads | … zu wissen, wann welche Methode kommt |
| [Referenz](toolkit-referenz.md) | Jede Methode jedes Interfaces mit Vertrag und dem Verhalten der drei Implementierungen | … eine einzelne Methode umzusetzen |
| [Rückkanal](toolkit-rueckkanal.md) | Was das Toolkit bei Benutzerereignissen an der Runtime aufruft | … Klicks, Tasten und Auswahl anzubinden |
| [Rahmen](toolkit-rahmen.md) | Start des Prozesses, Anmeldung, URLs, Beenden, Druck, Monitoring | … die Anwendung zum Laufen zu bringen |
| [Portierung](toolkit-portierung.md) | Stufenplan, Entwurfsentscheidungen, das Protokoll von h2forms, Hinweise je Zielplattform, Abnahme-Checkliste, Eigenheiten der Implementierungen | … eine neue Implementierung zu planen und abzunehmen |
| [Signaturen](toolkit-signaturen.md) | Java-Signaturen der Schnittstelle und der Gegenrichtung | … ohne Quelltext der Runtime zu arbeiten |

Empfohlene Reihenfolge für eine neue Implementierung: Einstieg, Lebenszyklus, Portierung; danach Referenz, Rückkanal und Signaturen als Nachschlagewerk je Baustein.

## Architektur

```text
Modell (MPS)                      generierter Java-Code der Anwendung
  AppUI Module              ->      IGenAppUiModule        (Menüs, Tiles, Anmeldung)
  Command, Page             ->      IOFXCommand, IOFXPage  (Ablauf, Daten, Conclusions)
  Page Pane                 ->      PagePaneDescription    (Aufbau der Oberfläche)
                                           │
                                           ▼
DataUX-Runtime  (org.modellwerkstatt.dataux.runtime)
  core            ApplicationMDI, WindowController, CommandContainer,
                  PagePaneSelCrtl – Ablaufsteuerung, Selektion, Ereignisse
  dsl.ui          Builder für Page Panes
  genspecification, delegates
                  Komponenten und Delegates: Bindung, Formatierung, Validierung
  toolkit         IToolkit_*  <──────────────  DIE SCHNITTSTELLE
                                           │
                                           ▼
Toolkit-Implementierung
  org.modellwerkstatt.turkuforms   Vaadin, Browser                 [T]
  org.modellwerkstatt.fx8forms     JavaFX, Desktop                 [F]
  org.modellwerkstatt.h2forms      servergerendertes HTML, MDE     [H]
  <neu>                            Swing, React, MCP, …
```

Die Runtime ist unabhängig von jeder Oberflächentechnik. Sie enthält die gesamte Logik, die allen Oberflächen gemeinsam ist; das Toolkit stellt dar und meldet Benutzerereignisse.

### Aufgabenteilung

| Aufgabe | Runtime | Toolkit |
| --- | --- | --- |
| Command starten, Sessions, Transaktionen, Sperren | ✔ | |
| Reihenfolge der Pages, Ausführen von Conclusions | ✔ | |
| Selektionskontext, Master-Detail, Laden abhängiger Daten | ✔ | |
| Bedienbarkeit von Actions und Conclusions berechnen | ✔ | |
| Werte formatieren, lesen, prüfen (Delegates) | ✔ | |
| Texte übersetzen | ✔ | |
| Fehlerbehandlung, Rückfragen auslösen | ✔ | |
| Fenster, Tabs, Dialoge, Menüs darstellen | | ✔ |
| Formulare, Tabellen, Layouts darstellen | | ✔ |
| Eingaben entgegennehmen und als Text bereitstellen | | ✔ |
| Auswahl, Klicks und Tasten melden | | ✔ |
| Fokus setzen und zwischen Feldern bewegen | | ✔ |
| Sortieren, Suchen, Spalten ein- und ausblenden in Tabellen | | ✔ |
| Prozess starten, Benutzer anmelden, Anwendung beenden | | ✔ |

Daraus folgen drei Eigenschaften der Schnittstelle:

1. **Die Runtime treibt.** Sie erzeugt alle Bausteine über die Fabrik, füllt sie und entscheidet, wann ein Fenster entsteht und verschwindet. Das Toolkit öffnet und schließt nichts von sich aus.
2. **Editoren liefern nur Text.** Typen, Formate und Prüfungen liegen im Delegate; der Editor setzt und liefert Zeichenketten (beim Status einen Index). Für die Darstellung darf ein Editor den Typ seines Delegates ansehen ([H] wählt danach die Bildschirmtastatur).
3. **Das Toolkit hält keine fachlichen Zustände.** Selektion, Bedienbarkeit und Daten gehören der Runtime; das Toolkit zeigt den zuletzt gesetzten Stand oder fragt ihn bei der Darstellung ab.

### Pflicht und Kür

Die drei Implementierungen nutzen die Schnittstelle sehr unterschiedlich weit. Was nur eine oder zwei von ihnen tun, ist kein Vertrag. Die Tabelle zeigt, was eine Implementierung weglassen kann, ohne dass die Runtime fehlerhaft arbeitet:

| Fähigkeit | turkuforms | fx8forms | h2forms | Folge des Weglassens |
| --- | --- | --- | --- | --- |
| Mehrere Tabs, Tabwechsel durch den Benutzer (`onTabChangeEvent`) | ja | ja | nein – ein sichtbares Command-Fenster | Die Runtime führt die Tabs selbst; sichtbar ist das zuletzt geöffnete oder fokussierte. |
| Prompt als eigene Fensterart (`isTabWindow() == false`) | ja | ja | nein – alles ist Tab | `GRAPH_EDIT_CMD` läuft als Tab über seinem Parent. |
| Modale Sperre (`setCurrentTabModal`) | ja | ja | leer | Nur nötig, wenn Menü oder andere Tabs erreichbar bleiben. |
| Hintergrund-Thread (`useBackgroundThread() == true`) | nein | ja | nein | Jede Interaktion läuft vollständig im aufrufenden Thread. |
| Hotkeys (`KeyEvent`, `ensureHotkeyAvailable`) | ja, registriert | ja, alle Tasten durchgereicht | nein, nur `UPD` und `GO` | Bedienung nur über Schaltflächen. |
| `IToolkit_EnableItem` und `attachButton1` (Push der Bedienbarkeit) | ja | ja | nein – fragt `reevalEnabled()` beim Rendern ab | Pull und Push sind gleichwertig. |
| Mehrfachselektion in Tabellen | ja | ja | nein, nur eine Zeile | Keine Mehrfachausführung. |
| Eigenständige Selektion ohne Action | ja | ja | nein, nur zusammen mit einer Action | Master-Detail ohne Action entfällt. |
| Inline-Bearbeitung, Export, Suche, Sortierung in Tabellen | teilweise | teilweise | nein | Leistungsmerkmale des Toolkits. |
| Layoutgewichte (`setLayoutConstraints`, `setColLayoutConstraints`) | ja | ja | leer – alles untereinander | Nur Darstellung. |
| Extras- und Hilfe-Menü, Submenüs | ja | ja | nein – Startmenü flach | Einträge fehlen. |
| Rückfrage beim Abmelden (`onExitRequested(false)`) | ja | ja | nein – beendet direkt | Ungespeicherte Änderungen gehen ohne Rückfrage verloren. |
| Adresse nachführen (`setAdjustUrl`, `resetUrl`) | ja | leer | leer (eigener Redirect) | Keine Deep Links aus der Adresszeile. |
| Diagramme (`PieChartUxElement`, `LineChartUxElement`) | ja | nein | nein | Ein Modell mit Diagramm scheitert beim Öffnen der Page. |
| Druck: `view`, `print` | ja | kein eigener Code; `OFX` enthält einen Fat-Client-Dienst | leer | Dokumente werden erzeugt, aber nicht angezeigt. |

Unverzichtbar sind dagegen in allen drei Implementierungen: Fabrik, ein Hauptfenster, ein Command-Fenster mit Conclusions, Formular, Tabelle, Container, Editoren für Text, Status und Reference, Dialoge mit ihrem `DlgRunnable` und die Meldung von Conclusions und Actions.

### Die Schnittstelle im Überblick

```text
IToolkit_UiFactory                      Fabrik und Einstellungen (Spring-Bean)
 ├─ IToolkit_MainWindow                 Hauptfenster: Menü, Tiles, Tabs, Dialoge      (erzeugt das Toolkit selbst)
 ├─ IToolkit_CommandContainerUi         Fenster eines Commands: Tab oder Prompt
 ├─ IToolkit_Form                       Basis aller Formen
 │   ├─ IToolkit_DelegateForm           Delegate Form
 │   ├─ IToolkit_TableForm              Table
 │   ├─ IToolkit_FormContainer          Grid Layout, Träger des Seitenmenüs
 │   └─ IToolkit_TabForm                Tab Layout
 ├─ IToolkit_TextEditor                 Editor eines Delegates
 │   ├─ IToolkit_StatusEditor
 │   ├─ IToolkit_ReferenceEditor
 │   ├─ IToolkit_DateOrTimeEditor
 │   ├─ IToolkit_ImageEditor
 │   └─ IToolkit_UploadEditor
 └─ IToolkit_EnableItem                 Bedienelement einer Action                     (erzeugt das Toolkit selbst, optional)
```

Das Paket umfasst 15 Interfaces mit rund 380 Zeilen. Hinzu kommen die Typen der Gegenrichtung, die das Toolkit verwendet, aber nicht implementiert:

| Typ | Paket | Rolle für das Toolkit |
| --- | --- | --- |
| `ApplicationMDI` | `RT.core` | Anwendungsinstanz. Das Toolkit erzeugt sie – als Feld ([F], [H]) oder als Unterklasse ([T]) – und kann ihr Tasten, Tabwechsel und Beenden melden. Es gibt keine zweite Anwendungsklasse; `ApplicationBasis` ist abstrakt. |
| `ICommandContainer` | `RT.core` | Empfänger der Ereignisse eines Command-Fensters |
| `UxEvent`, `ConclusionEvent`, `KeyEvent` | `RT.core` | Ereignisse an den Container |
| `IApplication.DlgRunnable` | `RT.core` | Fortsetzung nach einem Dialog |
| `MenuAction`, `CmdAction`, `Tile` | `RT.genspecification` | Menüstruktur, Action, Kachel |
| `IGenSelControlled` | `RT.genspecification` | Empfänger der Tabellenselektion |
| `IDlgt` | `RT.extensions` | Delegate eines Editors |
| `ITableCellStringConverter` | `RT.extensions` | Zellentext und -farbe einer Tabellenspalte |
| `ICustomDataUxElement` | `RT.extensions` | Vertrag eines `Custom UI Element`s |
| `OFXConclusionInformation` | `OFX` | Beschreibung eines Conclusion-Buttons |
| `IOFXSelection`, `Selection` | `OFX` | Selektion eines Typs |
| `IOFXProblem` | `OFX` | Meldung aus Precondition, Guard oder Validierung |
| `IOFXUserEnvironment` | `OFX` | Benutzerkontext: Sprache, Kompaktmodus, Branding |

## Abbildung der Modellkonzepte

| Konzept im Modell | Aufruf an der Fabrik | Interface |
| --- | --- | --- |
| `AppUI Module`: Menüs, `Tile`s | – | `IToolkit_MainWindow.setMenuAndInit`, `showTiles` |
| `SEARCH_CMD`, `GRAPH_OWNER_CMD` | `createTabContainerUi(mainWindow, false)` | `IToolkit_CommandContainerUi` |
| `GRAPH_OWNER_CMD(modal)` | `createTabContainerUi(mainWindow, true)` | `IToolkit_CommandContainerUi` |
| `GRAPH_EDIT_CMD` | `createPromptContainerUi(mainWindow, fullSize)` | `IToolkit_CommandContainerUi` |
| Page Conclusion, Abbrechen | – | `setConclusions`, `reevalConclusions` |
| `Page Pane` | – (kein eigenes Objekt; die Wurzelform trägt Titel, Meldungen und Seitenmenü) | `IToolkit_Form.rootForm` |
| `Delegate Form` | `createToolkitDelegateForm()` | `IToolkit_DelegateForm` |
| `Table` | `createToolkitTableForm()` | `IToolkit_TableForm` |
| `Grid Layout` | `createToolkitFormContainer(false)` | `IToolkit_FormContainer` |
| `Grid Layout` mit `FLEXIBLE` | `createToolkitFormContainer(true)` | `IToolkit_FormContainer` |
| `Tab Layout`, `Tab` | `createToolkitTabForm()` | `IToolkit_TabForm` |
| `Include` | – (wird beim Aufbau aufgelöst) | – |
| `Custom UI Element` | – (Klasse aus dem Modell) | `ICustomDataUxElement` |
| `String`, `Integer`, `BigDecimal` | `createTextEditor()`, `createTextAreaEditor(n)` | `IToolkit_TextEditor` |
| `Status` | `createStatusEditor(alter)` | `IToolkit_StatusEditor` |
| `Reference` | `createReferenceEditor(alter)` | `IToolkit_ReferenceEditor` |
| `LocalDate`, `DateTime (Date Only)` | `createDateEditor(withPicker)` | `IToolkit_DateOrTimeEditor` |
| `DateTime` | `createDateAndTimeEditor(withPicker, timeOnly)` | `IToolkit_DateOrTimeEditor` |
| `Image` | `createImageEditor()` | `IToolkit_ImageEditor` |
| `Upload` | `createUploadEditor()` | `IToolkit_UploadEditor` |
| `Dummy` | `createDummyEditor()` | `IToolkit_TextEditor` |
| `Action`, `Compound Action` | – | `CmdAction` (+ optional `IToolkit_EnableItem`) |
| `Submenu`, `- - - -` | – | `MenuAction` |
| Precondition, `validation`, `resolve with` | – | `IToolkit_Form.setProblems`, `showProblemsDialog` |
| Guard, Exception | – | `IToolkit_MainWindow.showDialog` |
| `user toast message` | – | `IToolkit_MainWindow.setToastMessage` |
| `acquire locks` (Sperre belegt) | – | `IToolkit_CommandContainerUi.setNotification` |
| `COLOR`, `SCOLOR` am `Page Pane` | – | `IToolkit_CommandContainerUi.setColor` |
| Command-Option `URL` | – | `setAdjustUrl`, `showTiles(…, resetUrl)` |

Tabellenspalten haben keine Editor-Objekte: Je Delegate einer `Table` ruft die Runtime `addColumn(...)` mit einem `ITableCellStringConverter`.

## Begriffe und Schreibweisen

| Begriff | Bedeutung |
| --- | --- |
| Runtime | Die DataUX-Runtime, Paket `org.modellwerkstatt.dataux.runtime` |
| Toolkit | Eine Implementierung der Interfaces `IToolkit_*` samt Starter |
| AppFactory | Die Implementierung von `IToolkit_UiFactory`; Spring-Bean der `OFXConfig` |
| Hauptfenster | `IToolkit_MainWindow` |
| Command-Fenster | `IToolkit_CommandContainerUi`; als Tab im Hauptfenster oder als Prompt (modaler Dialog) |
| Form | Ein `IToolkit_Form`: Formular, Tabelle, Layout |
| Wurzelform | Die Form, die dem Command-Fenster übergeben wird; sie zeigt Page Title und Meldungen. |
| Editor | Ein `IToolkit_TextEditor`: das Eingabeelement eines Delegates |
| Delegate | Runtime-Objekt (`IDlgt`), das eine Property an einen Editor bindet |
| Container | `ICommandContainer`: Laufzeitumgebung eines Commands in der Runtime – nicht zu verwechseln mit `IToolkit_FormContainer` |
| Selektion | `IOFXSelection`: die gewählten Objekte eines Typs im Selektionskontext einer Page |
| UI-Thread | Der Thread, in dem das Toolkit seine Oberfläche bedient. In einem Request/Response-Toolkit ist das der jeweilige Request-Thread unter einer Sperre je Sitzung. |

Kennzeichnung in allen Dokumenten:

- **[V]** – Vertrag: gilt für jede Implementierung. Aus dem Code der Runtime abgeleitet und von allen drei Implementierungen eingehalten, soweit sie die Funktion anbieten.
- **[T]** – Verhalten von turkuforms (Vaadin).
- **[F]** – Verhalten von fx8forms (JavaFX).
- **[H]** – Verhalten von h2forms (servergerendertes HTML).

Verhalten mit [T], [F] oder [H] ist ein Beispiel, keine Vorgabe.

Schreibweisen aus dem Quelltext sind unverändert übernommen, auch wo sie ungewöhnlich sind: `IToolkit_UiFactory`, `reciveForeignTermEvent`, `Crtl` für Controller, `wizzardWithFullSizeUis`.

## Quellen

Die Kürzel bezeichnen Java-Pakete. Klassen werden als `Kürzel.Unterpaket.Klasse` genannt, etwa `RT.core.ApplicationMDI`.

| Kürzel | Java-Paket | Bibliothek | Inhalt |
| --- | --- | --- | --- |
| `RT` | `org.modellwerkstatt.dataux.runtime` | `org.modellwerkstatt.dataux.runtime.jar` | DataUX-Runtime |
| `RT.toolkit` | `org.modellwerkstatt.dataux.runtime.toolkit` | wie oben | Die Schnittstelle |
| `OFX` | `org.modellwerkstatt.objectflow.runtime` | `org.modellwerkstatt.objectflow.runtime.jar` | ObjectFlow-Runtime |
| `TF` | `org.modellwerkstatt.turkuforms` | – | turkuforms |
| `FX` | `org.modellwerkstatt.fx8forms` | – | fx8forms; Pakete `windows` und `delegates` |
| `H2` | `org.modellwerkstatt.h2forms` | – | h2forms; Pakete `core`, `pebble`, `mdeauth`, `print` |

Die Runtime ist aus einem MPS-Modell generiert. Ein Toolkit übersetzt gegen `org.modellwerkstatt.dataux.runtime.jar`, `org.modellwerkstatt.objectflow.runtime.jar` und `org.modellwerkstatt.manmap.runtime.jar` sowie Spring und Joda-Time. Die Klassennamen der drei Implementierungen dienen in dieser Dokumentation nur als Beleg; ihr Quelltext wird nicht benötigt.

Aufbau der drei Implementierungen:

| Aufgabe | turkuforms | fx8forms | h2forms |
| --- | --- | --- | --- |
| AppFactory | `TF.core.TurkuAppFactory` | `FX.windows.FX8UiFactory` | `H2.pebble.PAppFactory` |
| Starter | `TF.core.TurkuServlet` | `RT.dsl.app.AppUiModule.runWithJavaFxFromConsole` (in der Runtime), `FX.windows.FX8Application` | `H2.core.H2ApplicationLoader` (Servlet) |
| Hauptfenster | `TF.core2.TurkuMainWin2` | `FX.windows.FX8Application` | `H2.core.H2Application` |
| Anwendungsinstanz | `TurkuAppCrtl2 extends ApplicationMDI` | `ApplicationMDI` als Feld | `ApplicationMDI` als Feld |
| Command-Fenster | `CmdUiTab`, `CmdUiPrompt` | `FX8CmdUiTab`, `FX8CmdUiPrompt` | eine Klasse `CommandContainerUi` |
| Formen | `TF.forms.*` | `FX8DelegateForm`, `FX8TableForm`, `FX8FormContainer`, `FX8TabForm`, `FX8HtmlContent` | `DelegateForm`, `TableForm`, `FormContainer`, `TabForm`, `HtmlContent` |
| Editoren | `TF.editors.*` | `FX.delegates.*` | `H2.pebble.*Editor` |
| Anmeldung | `TF.auth*` | Betriebssystem-Benutzer | `H2.mdeauth.*` |
| Druck | `TF.print.*` | kein eigener Code | `H2.print.*` |
| Umfang | rund 9.000 Zeilen | rund 7.800 Zeilen | rund 7.400 Zeilen Java plus Templates |

## Stand und Grenzen dieser Dokumentation

- Ausgewertet wurden der Quelltext der DataUX-Runtime, von turkuforms, fx8forms und h2forms auf dem Branch `2026_1`.
- Die Aussagen zum Vertrag sind aus den Aufrufern in der Runtime abgeleitet und an den drei Implementierungen geprüft; die Interfaces selbst tragen kaum Kommentare.
- Nicht ausgewertet wurden die Generatoren der DataUX-Sprache, generierter Anwendungscode und die Spring-Konfigurationen der Anwendungen. Welche Hotkey-Zeichenketten, Icon-Namen und Farbwerte Modelle tatsächlich liefern, ist deshalb nicht belegt.
- Die Runtime enthält mit `RT.httpapi` (`ApiLoader`, Plattform `MOWARE_API`) einen weiteren, oberflächenlosen Fernzugang mit eigener Startsequenz. Er verwendet die Toolkit-Schnittstelle nicht und ist hier nicht beschrieben.
- Keine der Aussagen ist durch Ausführen geprüft; alles stammt aus dem Lesen des Codes.
- Fragen, die offen geblieben sind, stehen unter [Portierung: Offene Punkte](toolkit-portierung.md#offene-punkte).
