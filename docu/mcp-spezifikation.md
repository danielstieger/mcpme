# MCP-Server für MoWare – Spezifikation

Stand: 2026-11-17. Alle Abschnitte sind entschieden, soweit nicht unter [Offen und zu erproben](#offen-und-zu-erproben) genannt. Erprobt ist noch nichts; die Aussagen über die Runtime stammen aus dem Lesen ihres Codes und des generierten Codes zweier Anwendungen.

## Ziel

Ein eigenständiger MCP-Server in Java ergänzt die MoWare-Plattform. Er macht eine modellierte Anwendung für einen Agenten bedienbar.

- Der Agent sieht, was ein Benutzer sieht, und kann, was ein Benutzer mit seinen Rechten kann – nicht mehr.
- Der Agent unterstützt einen Sachbearbeiter. Dieser hat möglicherweise gleichzeitig die Browser-Anwendung am selben Server offen; beide arbeiten in getrennten Anwendungsinstanzen.

Technisch ist der Server eine weitere Implementierung der Toolkit-Schnittstelle der DataUX-Runtime, siehe [Einstieg](toolkit-schnittstelle.md).

## Leitlinien

1. **MCP ist first-class.** Alles, was der Agent sieht – Werkzeuge, Ergebnisse, Fehlermeldungen, Begriffe –, folgt Stil und Wortwahl von MCP. Begriffe der Oberflächen-Toolkits (Tab, Tile, Prompt-Fenster, Fold, Hotkey) kommen nach außen nicht vor. Die Frage bei jedem Entwurfsschritt lautet „Wie löst MCP das?“, nicht „Wie macht es eine bestehende Oberfläche?“.
2. **Möglichst viel Information.** Der Agent erhält alles, was ein Benutzer an der Oberfläche erreichen kann, ohne es eigens anfordern zu müssen.
3. **Immer im Auftrag einer Person.** Der Agent arbeitet nie selbständig. Jede MCP-Session gehört zu einer Person, die sich selbst angemeldet hat; alles, was der Agent tut, ist dieser Person zugeordnet und als Arbeit eines Agenten erkennbar.
4. **Nichts an der Runtime vorbei.** Rechte, Bedienbarkeit, Validierung und Sperren entscheidet die Runtime. Der Server bietet keinen Zugriff auf Daten oder Aktionen, den die modellierte Oberfläche nicht bietet.

## Begriffe für den Agenten

| Begriff | Bedeutung | Entspricht in der Runtime |
| --- | --- | --- |
| Command | Ein fachlicher Vorgang der Anwendung | `Command` |
| Verfügbare Commands | Was sich jetzt starten lässt | Menüs und Tiles des `AppUI Module`s; Menüs der aktuellen Seite |
| Offene Commands | Was gerade läuft | Laufende Command-Container |
| Aktiver Command | Der offene Command, auf den die Werkzeuge wirken | Aktiver Tab beziehungsweise Prompt |
| Conclusion Action | Ein Schritt, der die aktuelle Seite abschließt oder weiterführt | Page Conclusion (in den Oberflächen die Buttons am unteren Rand) |

Bewusst nicht verwendet: „Context“ (in MCP mehrfach belegt), „Session“ (die MCP-Session), „Task“ (lang laufende Requests in MCP). Die `Action` aus DataUX – ein Menüeintrag, der einen Command startet – heißt nach außen „Command“.

## Betrieb

- Je Anwendung ein WAR in Tomcat 11; MCP über Streamable HTTP. Die Browser-Anwendung bleibt auf Tomcat 9; beide laufen auf demselben Rechner in getrennten Tomcats.
- Je MCP-Session eine eigene Anwendungsinstanz (`ApplicationMDI`). Das Ende der Session fährt sie herunter und schließt alle offenen Commands.
- Der Server läuft neben der Browser-Anwendung; beide teilen sich Datenbank und Konfiguration der Anwendung, aber keine Anwendungsinstanz.
- Der Server tritt gegenüber der Runtime als dieselbe Plattform auf wie turkuforms; der Wert ist einstellbar. Welche Variante eines `Page Pane`s eine Page wählt (`isPlatform(...)`), ergibt sich aus der Plattform der Konfiguration; mit der Vorgabe ist es die Desktop-Variante.

Folge: `RT.core.Shutdown` ist an `javax.servlet` gebunden und in Tomcat 11 nicht verwendbar; der Server führt die Schritte des Prozessendes selbst aus (siehe [Rahmen: Beenden](toolkit-rahmen.md#beenden)).

## Anmeldung und Nachvollziehbarkeit

### Wer angemeldet ist

- Angemeldet ist immer eine Person mit ihrem eigenen Konto. Es gibt kein eigenes Konto für den Agenten und keine Anmeldung der Anwendung selbst (Client Credentials).
- Die Anmeldung folgt dem Autorisierungsverfahren von MCP (OAuth); Zugangsdaten laufen nie durch das LLM.
- Identity Provider ist Entra ID. Google und LinkedIn werden vorerst nicht benötigt.
- Aus der Anmeldung ergibt sich der Benutzername; der Server meldet ihn mit leerem Passwort über `LoginController` an. Die Rechte bestimmt `isAuthenticated` des `AppUI Module`s.
- Der Agent hat dieselben Rechte wie die Person. Eine Anwendung, die ihn einschränken will, erkennt ihn in `isAuthenticated` an `deviceName`; der Server schränkt nichts ein.
- Der Server verlangt keine eigene Zustimmung der Person zum Speichern. Verändernde Werkzeuge sind nach MCP als solche gekennzeichnet, sodass ein Harness die Person fragen kann.
- Endet die Anmeldung der Person (Token abgelaufen und nicht erneuert, Abmeldung), endet die MCP-Session.
- Für Entwicklung und Tests gibt es einen fest konfigurierten Benutzer; der Schalter ist im Betrieb aus.

### Kennzeichnung als Agenten-Sitzung

Die Anwendungsinstanz läuft unter dem Benutzernamen der Person. Der Server kennzeichnet sie über die Gerätedaten der User Environment (`setDevice(deviceName, deviceSwName, deviceId)`):

| Feld | Inhalt | Quelle |
| --- | --- | --- |
| `deviceName` | fest `MCP` | – |
| `deviceSwName` | Harness: Name und Version des MCP-Clients | `clientInfo` aus `initialize` |
| `deviceId` | Zeitstempel des Session-Beginns; davor das verwendete LLM, wenn der Client es mitteilt | Server |

Harness und LLM sind Zusatzangaben nach dem Grundsatz „was einfach geht“. MCP teilt dem Server den Harness mit, das Modell nicht. Fehlt eine Angabe, bleibt sie leer; die Anmeldung scheitert daran nicht.

Die Runtime führt diese Werte in jedem Protokolleintrag, im Monitoring und in der Sperrmeldung an andere Benutzer („gehalten von Person X, Gerät MCP“). Die Anwendung kann sie über die User Environment lesen und an ihren Fachdaten festhalten.

### Ablauf mit Entra ID

Stand der Recherche vom 2026-11-17, nicht erprobt.

1. Einmalig: In Entra ID ist der Server als API registriert (App-Registrierung mit eigener Kennung und einem Scope) und der MCP-Client als Client mit seiner Redirect-Adresse. Die Client-ID wird im MCP-Client eingetragen; Entra ID erlaubt keine Selbstregistrierung von Clients.
2. Der Client ruft den Server ohne Token auf und erhält `401` mit einem Verweis auf die Metadaten des Servers (RFC 9728).
3. Die Metadaten nennen Entra ID als Autorisierungsserver. Der Client öffnet den Browser; die Person meldet sich an (Authorization Code mit PKCE).
4. Der Client sendet jeden Request mit dem Token. Ein Filter vor dem MCP-Endpunkt prüft Signatur, Aussteller, Empfänger (unser Server) und Ablauf und liest den Benutzernamen.
5. Beim Anlegen der MCP-Session meldet der Server diesen Benutzernamen über `LoginController` an und erzeugt die Anwendungsinstanz. Lehnt `isAuthenticated` ab, entsteht keine Session.
6. Jeder weitere Request muss ein gültiges Token derselben Person tragen. Läuft das Token ab, erneuert der Client es; die Session bleibt bestehen.

## Commands

### Mehrere offene Commands

- Mehrere Commands können gleichzeitig offen sein; genau einer ist aktiv. Alle Werkzeuge wirken auf den aktiven.
- Jedes Ergebnis nennt die offenen Commands (Handle, Titel, ungespeicherte Änderungen, aktiv) und beschreibt nur den aktiven im Detail.
- Der Wechsel des aktiven Commands ist ein eigenes Werkzeug.
- Ein Command, der einen anderen blockiert, erscheint dem Agenten als „muss zuerst abgeschlossen werden“: Der aktive Command trägt im Zustand `mustCompleteFirst: true`. Das gilt für einen Teilschritt innerhalb eines Commands (`GRAPH_EDIT_CMD`) und für einen exklusiven Command (`GRAPH_OWNER_CMD(modal)`). Solange es gilt, lehnen `start_command` (von der Startseite und direkt) und `switch_command` ab; es bleiben Conclusion Action und Abbrechen. Einen Teilschritt schließt `cancel_command` ohne `discard_changes`; die Runtime fragt dort nicht nach, die Eingaben sind verloren.
- Höchstens fünf Commands sind gleichzeitig offen (einstellbar). Ist die Grenze erreicht, lehnt `start_command` mit einer Meldung ab, die die offenen Commands nennt. Offene Commands halten Schreib-Sessions und Sperren.

Begründung: Nachschlagen während der Erfassung ist der Normalfall; Abbrechen würde Eingaben verwerfen.

### Starten

Grundlage aus dem Modell: Ein Menüeintrag ist ein Command-Aufruf – der FQ-Name des Commands plus Parameter. Die Parameter kommen aus der Selektion des Aufrufkontexts (`getSelected()`, `getSelectedObjects()`) oder aus Konstanten. Nur der FQ-Name eines Commands ist eindeutig, sein letztes Segment nicht.

1. **Ein Werkzeug startet Commands**, `start_command`, mit dem FQ-Namen als Eingabe. Es gilt für die Startseite (Tiles, Start-, Extras- und Hilfe-Menü, bis etwa 50 Einträge), für die Commands einer Seite und für den direkten Start mit Werten.
2. **Start aus dem Kontext.** Der Server ergänzt die Parameter: Die Objekte, die der Command braucht, kommen wie im Modell aus der aktuellen Selektion oder aus den Konstanten des Eintrags. Der Agent übergibt keine Objekte. Er kann die Zeilen, auf die sich der Command bezieht, im selben Aufruf auswählen.
3. **Direkter Start mit Werten.** Der Agent übergibt die vollständige Parameterliste des Commands in der Reihenfolge der Deklaration. Zulässig sind ganze Zahlen, Zeichenketten und `null` für „nicht vorhanden“. Beispiel: „Bestellung öffnen“ deklariert `int bestellungId`, `Bestellung bestellung`, `BestellJournalEintrag journal` und wird mit `[4711, null, null]` gestartet; sein `command init` lädt die Bestellung dann über die ID.
   - Der Server reicht die Liste unverändert an die Runtime; ganze Zahlen übergibt er als `Integer`, weil der generierte Code sie so ausliest. Er ergänzt nichts und rät nichts. Eine Konvention über Reihenfolge oder Bedeutung der Parameter gibt es nicht; sie ist je Command verschieden.
   - Direkt startbar sind nur Commands mit eigener Session (`SEARCH_CMD`, `GRAPH_OWNER_CMD`); ein `GRAPH_EDIT_CMD` braucht den Kontext seines Ausgangs-Commands.
   - Welche Liste ein Command erwartet, erfährt der Agent aus dem Beschreibungstext des Commands, aus einem Skill oder von der Person („Du kannst Bestellung öffnen mit 4711, null, null starten“).
4. **Die Runtime prüft jeden Start.** Ob die Person den Command mit diesen Parametern starten darf, entscheidet die Runtime (`IOFXCmdModule.getCommandPermission`: Rollen, `generally enabled`), beim Start aus dem Kontext wie beim direkten Start. Das Command-Modul prüft beim Start selbst noch einmal und wirft, wenn die Berechtigung fehlt.
5. **Einträge und ihre Kennung.** Was sich aus dem Kontext starten lässt, ist ein Eintrag: ein Command mit Beschriftung und den Parametern, die das Modell an dieser Stelle vorsieht. Derselbe Command kann mehrfach vorkommen, auch mit gleicher Beschriftung („Reset Order“ viermal im selben Menü). Jeder Eintrag hat deshalb eine Kennung aus Herkunft und Position: `start-1` (Startseite), `menu-2`, `extras-1`, `help-1`, auf einer Seite `page-1` und `<Tabelle>-1`. Die Position ändert sich nicht mit den Rechten; nicht startbare Einträge bleiben in der Liste.
   - `start_command` findet den Eintrag über eine von drei Angaben: die Kennung (`entry`), die Beschriftung (`label`) oder den FQ-Namen (`command`).
   - Beschriftung oder FQ-Name allein genügen, wenn sie unter den gerade angebotenen Einträgen eindeutig sind; Groß- und Kleinschreibung spielt keine Rolle. Sind sie es nicht, lehnt der Server ab und nennt die passenden Einträge mit ihren Kennungen.
   - Kennung und Beschriftung zusammen sind eine Absicherung: Heißt der Eintrag nicht mehr so, lehnt der Server ab und nennt den aktuellen Namen. So lässt sich in einem Skill „starte `menu-2`, Search Orders“ schreiben, ohne dass nach einer Änderung des Menüs stillschweigend etwas anderes startet.
6. **Was startbar ist, steht in den Ergebnissen.** Jedes Ergebnis nennt die Commands, die die aktuelle Seite anbietet; `get_overview` nennt die der Startseite, samt den dynamischen Beschriftungen der Tiles (etwa Zähler offener Vorgänge).

### Beschreibung

- Zu jedem Command gibt es einen Beschreibungstext mit drei Teilen: Parameter, `user doc`, `tec doc`. Der Agent ruft ihn mit `describe_command` über den FQ-Namen ab. Er steht nicht in jedem Ergebnis.
- Der Text kommt als ein Stück aus dem Command-Modul (siehe [Zulieferung](#zulieferung-aus-generator-und-runtime)); der Server reicht ihn unverändert weiter und wertet ihn nicht aus.
- Der Text ist in der Sprache der Entwickler verfasst, also deutsch, und wird nicht übersetzt.
- Ohne die Zulieferung kennt die Runtime zu einem Command nur FQ-Name, Beschriftung, Typ, Icon, Farbe und einen Tooltip mit den Berechtigungen (etwa „RW 'Fuhrparkleiter' RW 'Disponent'“); Namen, Typen und Anzahl der Parameter kennt sie nicht.

### Skills

- Es gibt keinen Generator, der aus dem Modell einen Command-Katalog oder Skills erzeugt.
- Skills entstehen, wenn überhaupt, von Hand je Anwendung. Sie beschreiben Abläufe und nennen Commands, Felder und Tabellen mit ihren Kennungen.
- Der Server liefert vorerst keine Skills aus. MCP kennt dafür eine Erweiterung über Resources (SEP-2640, `io.modelcontextprotocol/skills`); sie bleibt außen vor, bis sie gebraucht wird.

## Seiten

### Felder und Formulare

- Der Agent erhält alle Felder eines Formulars und alle Spalten einer Tabelle, unabhängig von der Option `FOLD`; ein Begriff für „gefaltet“ existiert für ihn nicht. Die Einstellung `ShowFolded` (Vorgabe `true`) steuert das: Mit `false` lässt der Server gefaltete Spalten und Felder weg – ein Ausweg, falls sehr breite Tabellen den Kontext des Agenten sprengen.
- Werte nimmt und liefert der Server als Text im Format des Feldes; das Format steht im Zustand. Auswahlfelder nennen ihre Einträge.
- **Felder, die die Seite aktualisieren** (`ISSUE UPDATE/SCANABLE`): Setzt `set_fields` ein solches Feld, trägt der Server erst alle Werte des Aufrufs ein und löst dann einmal die Aktualisierung aus. Das Ergebnis zeigt die nachgeladenen Werte und sagt, dass aktualisiert wurde. Einen eigenen Schritt dafür gibt es nicht.

### Tabellen

- Ein Ergebnis enthält je Tabelle höchstens 20 Zeilen (einstellbar) und nennt die Gesamtzahl. Grund ist der Kontext des LLM, nicht die Datenmenge: Der Zustand steht in jedem Ergebnis. Die ursprüngliche Vorgabe von 100 war im Test mit einem Harness zu hoch; eine Tabelle mit 99 Zeilen kostete in jedem Ergebnis mehrere tausend Token.
- Jede Zeile trägt ihre Position in der geladenen Liste. Die Position gilt, bis die Tabelle neu lädt; über sie wählt der Agent aus.
- Ausgewählte Zeilen stehen immer im Ergebnis, auch jenseits der Obergrenze.
- Format: Spaltennamen einmal, jede Zeile als Liste – zuerst die Position, dann die Zellentexte in der Reihenfolge der Spalten. Die Erklärung steht in der Beschreibung der Werkzeuge, nicht in jedem Ergebnis.
- `get_rows` liefert einen Bereich (`from`, `to`) oder die Treffer einer Textsuche über die Spaltentexte, jeweils mit Position und mit derselben Obergrenze je Aufruf. Die Suche filtert nur, was der Agent sieht; Daten und Auswahl in der Runtime bleiben unberührt.
- Sortieren bietet der Server nicht an.

### Conclusion Actions

- Jede Conclusion Action hat eine Kennung, die die Runtime liefert (`conclusionHashCode`) und unter der sie ausgelöst wird; turkuforms verfährt genauso.
- Die Kennung ist innerhalb eines offenen Commands eindeutig und gilt, solange er offen ist. Sie gilt nicht über Sessions hinweg; in einem Skill lässt sich eine Conclusion Action deshalb nur über ihre Beschriftung benennen.
- Zu jeder nennt das Ergebnis Kennung, Beschriftung, Icon-Namen, ob sie jetzt ausführbar ist und ob sie die Standardaktion der Seite ist (`GO/OK`).
- Eine Conclusion Action hat eine lesbare Beschriftung oder, selten, nur ein Icon. Im zweiten Fall erschließt der Agent die Bedeutung aus dem Icon-Namen.
- Nicht in der Liste stehen Abbrechen – das ist das Werkzeug `cancel_command`; ob es möglich ist, sagt `cancelable` – und die Aktualisierung (`SCAN/UPDATE`), die in den Oberflächen ebenfalls kein Button ist.

### Meldungen

Für den Agenten gibt es keine offenen Dialoge; jeder Aufruf ist in sich abgeschlossen.

- Hinweise und Problemlisten stehen einmal im Ergebnis des Aufrufs, der sie ausgelöst hat.
- Eine Fehlermeldung der Runtime quittiert der Server selbst und liefert ihren Text als Fehler; der betroffene Command ist danach geschlossen.
- Die einzige Rückfrage der Runtime („Änderungen verwerfen?“) beantwortet der Server anhand der Eingabe `discard_changes` von `cancel_command`.
- Die Person wird vom Server nicht gefragt.

### Dokumente, Bilder und Links

Dateien holt der Agent nicht über MCP ab, sondern über HTTP(S), wie in turkuforms. Der Server liefert Adressen.

- **Dokumente.** Erzeugt ein Command ein Dokument und verlangt Anzeigen oder Drucken (`view`, `print` am Druckdienst), steht im Ergebnis des Aufrufs dessen Adresse, mit der Angabe, was verlangt war. Einen Drucker hat der Server nicht; drucken kann die Person. Die Adresse entsteht wie in turkuforms aus einem konfigurierten HTTP-Pfad, unter dem ein Webserver das Ausgabeverzeichnis ausliefert.
- **Links.** Verlangt ein Command, eine Adresse zu öffnen (`openUrl`), steht sie im Ergebnis des Aufrufs.
- **Bilder.** Ein Bildfeld steht im Zustand mit Beschriftung und Adresse des Bildes: die vollständige Adresse aus dem Feldwert oder der konfigurierte Adresspräfix (`UploadLocationRetrieve`) plus Dateiname.
- **Diagramme.** Der Agent erhält die Rohdaten des Diagramms als Tabelle und wertet sie selbst aus. Der Server bringt dafür die beiden Klassen mit, die die Runtime unter festem Namen sucht (`org.modellwerkstatt.addons.components.PieChartUxElement`, `LineChartUxElement`); ohne sie scheitert das Öffnen einer Seite mit Diagramm.
- **HTML-Seiten** (`PASSIVE_HTML_FULLSCREEN`). Der Server liefert das HTML als Text im Zustand.
- **Upload.** Vorerst nicht unterstützt; MCP hat keinen Weg, eine Datei an den Server zu schicken. Ein Upload-Feld erscheint als nicht editierbar, mit dem aktuellen Dateinamen und dem Hinweis, dass die Person die Datei in der Browser-Anwendung hochlädt.

### Kennungen

- **Feld:** der Pfad seiner Property, wie ihn das Modell angibt (`datBeleg`, `menge.mengeLeh`). Kommt derselbe Pfad auf einer Seite in zwei Formularen vor, wird er mit der Kennung des Formulars ergänzt.
- **Tabelle:** der Name des Zeilentyps (`BestellPosition`). Zeigen zwei Tabellen einer Seite denselben Typ, wird durchnummeriert.
- **Formular:** eine fortlaufende Kennung (`form1`, `form2`); das Formular erfährt über die Schnittstelle weder Typ noch Namen.
- Die Kennungen sind bei jedem Öffnen derselben Seite gleich und in Skills verwendbar.

Geprüft an einer Anwendung mit 373 `Page Pane`s (Zählung über den generierten Code, ungefähr): Pfade mit Punkt kommen in 69 vor; 14 haben mehrere Formulare, davon 7 mehrere ohne Titel und 3 denselben Property-Pfad in zwei Formularen; 8 haben zwei Tabellen desselben Zeilentyps. Die Sonderregeln werden also gebraucht, aber selten.

## Werkzeuge

Der Satz ist klein, fest und für jede Anwendung gleich. Namen und Beschreibungen der Werkzeuge sind englisch, wie in MCP üblich; Inhalte der Anwendung (Beschriftungen, Meldungen, Doku) bleiben in ihrer Sprache. Name, Zweck und Arbeitsweise der Anwendung stehen im Feld `instructions`, das der Server beim Verbinden mitgibt; dort steht auch, dass Conclusion Actions den Page Conclusions des Modells entsprechen.

| Werkzeug | Eingaben | Wirkung | Verändert |
| --- | --- | --- | --- |
| `get_overview` | – | Startseite: verfügbare Commands mit ihren aktuellen Beschriftungen, dazu die offenen Commands | nein |
| `describe_command` | `command` (FQ-Name) | Beschreibungstext des Commands | nein |
| `start_command` | eine von `entry` (Kennung des Eintrags), `label` (Beschriftung) oder `command` (FQ-Name); optional `params` (Liste) für den direkten Start über `command`; optional `table` und `rows` (erst auswählen, dann starten) | Startet aus dem Kontext oder direkt mit Werten; der gestartete Command wird aktiv | ja |
| `get_state` | – | Zustand des aktiven Commands | nein |
| `get_rows` | `table`; optional `from` und `to` (Positionen); optional `search` (Text) | Zeilen einer Tabelle: ein Bereich, die Treffer einer Textsuche, oder beides | nein |
| `set_fields` | `fields` (Feld → Wert) | Trägt Werte in Formularfelder ein; aktualisiert die Seite, wenn eines der Felder das verlangt | ja |
| `select_rows` | `table`, `rows` (Liste von Positionen) | Setzt die Auswahl in einer Tabelle; abhängige Tabellen und Formulare laden neu | ja |
| `conclude` | `action` (Kennung); optional `fields` (erst eintragen, dann auslösen) | Löst eine Conclusion Action der aktuellen Seite aus | ja |
| `cancel_command` | optional `discard_changes` | Bricht den aktiven Command ab. Bei ungespeicherten Änderungen nur mit `discard_changes`; sonst ein Fehler, der das sagt. | ja, verwerfend |
| `switch_command` | `command` (Handle eines offenen Commands) | Macht einen anderen offenen Command aktiv | nein |

Grundsätze:

- **Ein Ergebnis, eine Form.** Jedes Werkzeug außer `get_overview`, `describe_command` und `get_rows` liefert den Zustand nach dem Aufruf, in derselben Struktur wie `get_state`. Der Agent muss nach einer Aktion nicht eigens lesen.
- **Lesen hat keine Nebenwirkung.** `get_overview`, `get_state`, `get_rows` und `describe_command` ändern nichts und sind entsprechend gekennzeichnet (`readOnlyHint`). `cancel_command` ist als verwerfend gekennzeichnet (`destructiveHint`).
- **Fehler sind Ergebnisse.** Was der Agent korrigieren kann – Feld unbekannt, Conclusion Action nicht ausführbar, Command nicht startbar –, kommt als Ergebnis mit `isError` und einer Meldung, die sagt, was stattdessen möglich ist.
- **Die Werkzeugliste ändert sich während einer Session nicht.**

## Zustand

Aufbau des Ergebnisses von `get_state` und der verändernden Werkzeuge:

| Teil | Inhalt |
| --- | --- |
| `openCommands` | Handle, Titel, ungespeicherte Änderungen, aktiv |
| `command` | FQ-Name, Titel, Typ, nur lesend ja/nein, Hinweis auf eine Sperre durch andere |
| `page` | Titel, Untertitel |
| `messages` | Meldungen der Seite (Fehler, Hinweise), mit Korrektur-Command, falls angeboten; einmalige Hinweise und Fehlermeldungen aus dem letzten Aufruf |
| `documents` | Dokumente und Links aus dem letzten Aufruf: Adresse, Dateiname, ob Anzeigen, Drucken oder Öffnen verlangt war |
| `forms` | je Formular: Kennung, Titel, Felder mit Kennung, Beschriftung, Beschreibung, Wert, Art (Text, Datum mit Format, Auswahl mit Einträgen, Bild mit Adresse), editierbar, Fehlertext |
| `charts` | je Diagramm: Titel und Rohdaten als Tabelle |
| `html` | Inhalt einer HTML-Seite, falls die Seite eine ist |
| `tables` | je Tabelle: Kennung, Titel, Spalten, Gesamtzahl der Zeilen, Zeilen als Texte mit ihrer Position, Auswahl, Summenzeile |
| `commands` | Einträge, die die Seite anbietet: Kennung, FQ-Name, Beschriftung, Beschreibung, ausführbar. Der Typ des Commands (`SEARCH_CMD`, `GRAPH_OWNER_CMD`, …) steht nicht dabei: Was daraus folgt, zeigt der Zustand nach dem Start. |
| `conclusionActions` | Kennung, Beschriftung, Icon, Standardaktion ja/nein, ausführbar |
| `cancelable` | ob `cancel_command` jetzt möglich ist |

## Einstellungen an der AppFactory

Die AppFactory des Servers ist eine Spring-Bean der Anwendungskonfiguration, wie bei den anderen Toolkits. Eigene Einstellungen:

| Einstellung | Vorgabe | Wirkung |
| --- | --- | --- |
| `ShowFolded` | `true` | Gefaltete Spalten und Felder liefern |
| Plattform | wie turkuforms | Als welche Plattform der Server gegenüber der Runtime auftritt |
| Zeilen je Tabelle (`MaxRowsPerTable`) | `20` | Obergrenze der Zeilen je Tabelle in einem Ergebnis und je `get_rows` |
| Offene Commands | `5` | Obergrenze gleichzeitig offener Commands je Session |
| Fester Benutzer | aus | Anmeldung ohne OAuth, nur für Entwicklung und Tests |

Dazu kommt die Druckfabrik des Servers als eigene Bean, mit denselben Angaben wie die von turkuforms: Ausgabeverzeichnis und HTTP-Pfad, unter dem es ausgeliefert wird. Den Adresspräfix für Bilder liefert die vorhandene Einstellung `UploadLocationRetrieve`.

Außer `ShowFolded` stehen die Namen der Einstellungen noch nicht fest.

## Umsetzung

### Bibliotheken

- **MCP:** das offizielle MCP-Java-SDK (`io.modelcontextprotocol.sdk`, Stand 2.2.0), nicht selbst geschrieben. Es bringt Jackson 3, Project Reactor und einen JSON-Schema-Validator mit und setzt `jakarta.servlet` voraus, also Tomcat 10 oder neuer; verwendet wird Tomcat 11. Anmeldung enthält es nicht; die prüft ein eigener Filter vor dem MCP-Endpunkt.
- **Runtime und Basis:** wie turkuforms aus `mowarePluginHome` (Eintrag in `gradle.properties`): `org.modellwerkstatt.dataux.runtime.jar`, `…objectflow.runtime.jar`, `…manmap.runtime.jar`, dazu Spring 5.3.27 und Joda-Time aus `jars/basis`. Die Runtime ist für Java 21 übersetzt.
- **Erst im WAR der Anwendung:** Druckbibliotheken, Datenbanktreiber, die MPS-Bibliotheken und die generierten Klassen der Anwendung.
- **Build:** Gradle.

### Umgebung

- **Tomcat:** Tomcat 11.0.29 unter `~/stuff/red11`, HTTP auf Port 8180 (Shutdown 8105, HTTPS 8543). Protokollierung über log4j2 wie im Tomcat 9 unter `~/stuff/green`; Manager-Zugang wie dort.
- **Servlet-API der Runtime:** Die Runtime verwies an zwei Stellen auf `javax.servlet`, die den Server betreffen könnten. Die Protokollklasse `Dux` ist davon bereinigt. `Shutdown` verwendet der Server nicht. Die übrigen Stellen (`httpapi`, `IRemoteIpNginx`, Batchjob-Servlets) lädt der Server nicht.
- **Testanwendung:** `simpleone`.

### Start des Servers

Der Start folgt `TurkuServlet.servletInitialized()` von turkuforms, ohne Vaadin:

- Init-Parameter `xmlConfigFile` und `applicationFqName` aus der `web.xml`, mit denselben Platzhaltern in der Vorlage; Version aus dem `##` im Bereitstellungspfad.
- Reihenfolge: `AppJmxRegistration`, Spring-Kontext, Anwendungsmodul über `createBean`, AppFactory aus dem Kontext, `registerAppTelemetrics`.
- Protokoll über `Dux` in die Datei `mcpme.log`.
- Die Anwendung bekommt für den Server eine eigene `OFXConfig`, in der seine AppFactory und seine Druckfabrik eingetragen sind.

Abweichungen von turkuforms:

- Die Anwendungsinstanzen hängen nicht an der HTTP-Session; MCP-Clients halten keine Cookies. Der Server führt eine eigene Liste mit Zeitüberschreitung.
- `Shutdown.shutdownAppAndContext` ist an `javax.servlet` gebunden; der Server führt die Schritte des Prozessendes selbst aus.
- Eine eigene Druckfabrik; die von turkuforms ruft Vaadin auf.

### Stufen

Jede Stufe endet mit etwas, das sich von einem MCP-Client aus an der Testanwendung `simpleone` prüfen lässt.

| Stufe | Inhalt | Test |
| --- | --- | --- |
| 0 | Gradle-Projekt, WAR in Tomcat 11, SDK eingebunden, die zehn Werkzeuge als leere Hüllen | Client verbindet sich und sieht die Werkzeugliste |
| 1 | Spring-Konfiguration laden, AppFactory, Anmeldung mit festem Benutzer, `ApplicationMDI`, `get_overview` | Übersicht zeigt Menü und Tiles von `simpleone` |
| 2 | `start_command`, `get_state` mit Formularen (Text, Status), `conclude`, `cancel_command` | Einen Command öffnen, ein Formular ausfüllen, speichern, abbrechen |
| 3 | Tabellen, `select_rows`, `get_rows`, Commands der Seite, mehrere offene Commands, `switch_command` | Suchen, Zeile wählen, Bearbeiten aus der Trefferliste, nebenher nachschlagen |
| 4 | Übrige Felder (Reference, Datum), Aktualisierung über ein Feld, Meldungen und Fehler, direkter Start mit Parameterliste, `describe_command` | Die Punkte „zu erproben“ |
| 5 | Dokumente, Links, Bilder, Diagramme, HTML, `ShowFolded` | Ein Dokument drucken lassen, eine Seite mit Diagramm öffnen |
| 6 | OAuth mit Entra ID, Kennzeichnung als Agenten-Sitzung, Sitzungsende, Herunterfahren | Anmeldung als Person, Protokoll zeigt „Gerät MCP“ |

### Stand

- **Stufe 0** erledigt (2026-11-18): Der Server läuft in Tomcat 11, ein Client sieht die zehn Werkzeuge.
- **Stufe 1** erledigt (2026-11-18): `simpleone` startet unter `http://localhost:8180/simpleone/mcp` mit festem Benutzer; `get_overview` liefert die 35 Commands von Startseite und Menüs. Geprüft mit `curl`, nicht mit einem Harness.
- **Stufe 2** erledigt (2026-11-18): `start_command` (aus dem Kontext über Kennung, Beschriftung oder FQ-Namen; direkt mit Parameterliste), `get_state`, `set_fields`, `conclude`, `cancel_command`. Durchgespielt an `simpleone`: Suche öffnen, Filter setzen (die Seite aktualisiert sich), Bestellung aus der Trefferliste öffnen, Kopf in einem Bearbeitungs-Command ändern und abschließen, Bestellung speichern; Abbrechen mit und ohne ungespeicherte Änderungen; direkter Start mit passender und unpassender Liste. Geprüft mit einem Skript (`testclient.py`) und danach mit Claude Code als Harness (Übersicht, Suche öffnen, Filter setzen, abgelehnter Start, Abbrechen); Öffnen und Speichern einer Bestellung nur mit dem Skript.

- **Stufe 3** erledigt (2026-11-18): `get_rows` (Bereich `from`/`to`, Suche über die Zellentexte ohne Groß-/Kleinschreibung, beides zusammen; höchstens `MaxRowsPerTable` Zeilen je Aufruf, sonst Hinweis mit der Position zum Fortsetzen; bei Suche `matches`), `select_rows` (leere Liste hebt die Auswahl auf; die Commands der Seite werden danach neu als startbar oder nicht gemeldet), `table`/`rows` an `start_command` (nur gemeinsam), `switch_command` über den Handle, `mustCompleteFirst`. Geprüft mit dem Skript an `simpleone`: Zeile 8 von 99 auswählen und „Order MainDoc“ in einem Aufruf starten, drei Commands nebeneinander und Wechsel zwischen ihnen, Wechsel bei offenem Teilschritt abgelehnt, Teilschritt ohne Rückfrage abgebrochen. Mit dem Harness noch nicht geprüft.
- **Stufe 4** angeschlossen (2026-11-18): `describe_command` liefert den Text aus `IOFXCmdModule.getCommandDescription(fqName)`; die Ablehnung beim direkten Start hängt den Parameterteil (alles bis zur ersten Leerzeile) an. Der Build packt vor dem Übersetzen die Jars der Runtime neu ins Plugin, wie turkuforms: Er bindet `objectflow/assembly/bmoware.xml` ein und ruft `mowarePluginAdoption` und `mowareUpdatePluginForTukru` auf (`mowareHome` in `gradle.properties`). Der Text aus dem Generator nennt je Parameter Typ, Name und den Ausdruck, mit dem das Modell ihn üblicherweise belegt (`getSelected(Order)`, `getSelected(Order).id`, `"false"`); Zählung ab 0. Konstanten stehen als Wert (`false`, `OrderStat.completed`), leere Abschnitte entfallen samt Überschrift, ein Command ohne Parameter hat `<none>`.
- **Stufe 5** erledigt (2026-11-18): Dokumente, Links und Diagramme. Der Druckdienst erzeugt das PDF wie in turkuforms ins Ausgabeverzeichnis; das Ergebnis des Aufrufs nennt es unter `documents` (`kind: document`, `name`, `url`), ebenso Links der Anwendung (`kind: link`). Drucken und Ansehen sind für den Agenten dasselbe. Die URL ist nicht geschützt; den Schutz des Verzeichnisses regelt der Betrieb (Entscheidung 2026-11-18). Diagramme stehen im Zustand unter `charts`: Torte als `items` aus `[Bezeichnung, Wert]`, Linie als `x` und `series` mit `name` und `values`; dafür bringt der Server `PieChartUxElement` und `LineChartUxElement` unter den Namen der Oberflächen mit. HTML-Seiten stehen unverändert unter `html`. Geprüft mit dem Skript an `simpleone`: „Print Order“ aus der Suche (PDF per HTTP abrufbar), „Open Sensor“ (Linie, drei Reihen), „Open Article“ (Torte), „View external“ (HTML). Nicht geprüft: Bildfelder und Links, weil `simpleone` dafür keinen startbaren Fall bot.
- **Stufe 6** Anmeldung erprobt (2026-11-18) mit Entra ID (Test-Tenant) und Claude Code: Der Server prüft das Zugriffstoken (Signatur über die Schlüssel des Ausstellers, Aussteller, Audience, Ablauf), antwortet ohne Token mit `401` und `WWW-Authenticate: Bearer resource_metadata=…`, liefert die Angaben nach RFC 9728 unter `/.well-known/oauth-protected-resource` und bindet die MCP-Session an die Person aus dem Token. Konfiguriert wird die Anmeldung wie bei den Oberflächen über einen eigenen Baustein in einer eigenen Section der Konfiguration (dort `IExtAuthProvider`/`EntraIdOAuth2`, hier `IMcpAuthProvider`/`org.modellwerkstatt.mcpme.auth.EntraIdMcpAuth`), damit Kennungen und Geheimnisse an einer Stelle stehen und per `include section` eingebunden werden. Konstruktor: Tenant, Client-ID der App-Registrierung des Servers, ihr Geheimnis, Name des Bereichs, Abfrage für den Benutzernamen, Feld mit dem Benutzernamen; dazu `ExpirationDate` (yyyy-MM-dd, Warnung im Protokoll ab 30 Tagen vor Ablauf) und `Relay` (Vorgabe ein). Benutzername wie in turkuforms: Ist eine Abfrage angegeben (üblich `https://graph.microsoft.com/v1.0/me?$select=onPremisesSamAccountName`), tauscht der Server das Token des Clients mit dem Geheimnis gegen eines für die Abfrage im Namen derselben Person (On-Behalf-Of) und liest das Feld aus der Antwort; ohne Abfrage liest er das Feld als Claim aus dem Token und braucht kein Geheimnis. Je Token wird der Benutzername einmal ermittelt. Die Anmeldung ist aktiv, sobald `FixedUser` an der AppFactory leer ist; dort bleibt nur `PublicUrl`. Erprobt ist die Variante ohne Abfrage; die Abfrage über Graph ist gebaut, aber nicht erprobt (braucht ein Geheimnis und die delegierte Berechtigung `User.Read` der App-Registrierung des Servers). Befunde: (1) Entra lehnt den Parameter `resource` ab, den MCP-Clients mitschicken, solange er nicht zur Anwendungs-ID-URI des Bereichs passt (`AADSTS9010010`). Mit `Relay` nennt sich der Server dem Client selbst als Stelle für die Anmeldung und reicht Anmeldung (`/oauth/authorize`) und Tokenabruf (`/oauth/token`) ohne `resource` an Entra weiter; er stellt nichts aus. Alternative für den Betrieb, nicht erprobt: Anwendungs-ID-URI = öffentliche Adresse des MCP-Endpunkts (verifizierte Domain nötig). (2) Entra kennt keine dynamische Registrierung; der Client braucht eine vorab registrierte Client-ID (`claude mcp add --client-id … --callback-port …`), Umleitungs-URI `http://localhost/callback` unter „Mobile and desktop applications“, „Allow public client flows“ eingeschaltet (sonst `AADSTS7000218`). (3) PKCE: Entra nennt `code_challenge_methods_supported` nicht; Claude Code meldet sich trotzdem an. (4) Clients fragen die Angaben auch auf oberster Ebene des Hosts ab (`/.well-known/<art>/<anwendung>[/mcp]`); das liegt außerhalb der Webanwendung und braucht eine Weiterleitung im Tomcat (RewriteValve) oder im Reverse Proxy. (5) Claude Code merkt sich den Stand der Erkennung; nach Änderungen am Server den Eintrag entfernen und neu anlegen. (6) `preferred_username` ist im Test die E-Mail-Adresse der Person. Offen: Sitzungsende und Herunterfahren prüfen, Zuordnung des Benutzernamens zur Anwendung, Aufräumen der Regeln am Test-Tomcat.
- **Zusammengesetzter Start** (2026-11-18): Ein Eintrag kann einen Command innerhalb eines anderen ausführen (im Modell: compound command; die Runtime öffnet den äußeren, führt den inneren aus und schließt den äußeren mit einer automatischen Conclusion). Der Eintrag nennt dann unter `command` den inneren Command, den seine Beschriftung meint, und unter `within` den äußeren; `start_command` findet ihn über den inneren FQ-Namen. Beispiel `simpleone`: „Print Order“ in der Suche ist `Print_Order` within `Order_MainDoc`. Die Runtime bietet dafür keine Abfrage; der Server ruft per Reflection `CmdAction.configCompoundOuterCmdStartOrJustBasisCmdStart(null)` auf. Wunsch an die Runtime: eine öffentliche Abfrage. Liefert ein Aufruf ein Dokument oder einen Link, entfällt die Meldung „ran without opening a page“. Mit Claude Code geprüft: suchen, Bestellung öffnen, Position ändern, drucken, speichern; Drucken aus der Suche über den inneren FQ-Namen.

Befunde aus den Stufen:

- **Session:** Das SDK gibt bei jedem Werkzeugaufruf die ID der MCP-Session und Name und Version des Clients mit. Eine Anwendungsinstanz hängt an der MCP-Session. Beendet der Client die Session, fährt der Server die Instanz herunter; sonst nach 60 Minuten ohne Aufruf (einstellbar).
- **Beschreibung eines Commands in der Übersicht:** Der Tooltip, den die Runtime liefert, ist ein Kommentar aus dem Modell oder, wenn keiner da ist, die Berechtigungen. Das Feld heißt deshalb `description`, nicht „Berechtigungen“.
- **Mehrdeutigkeit:** In `simpleone` steht derselbe Command mehrfach im selben Menü mit gleicher Beschriftung und verschiedenen Parametern („Reset Order“ viermal, „Search Orders“ zweimal). Daraus ist die Kennung je Eintrag entstanden, siehe „Commands – Starten“.
- **Konfiguration der Testanwendung:** `simpleone` hat im Modell `org.modellwerkstatt.simple.infra.configs` eine eigene `OFXConfig` namens `McpConfig`, kopiert von `TurkuConfig`: AppFactory `org.modellwerkstatt.mcpme.core.McpAppFactory` mit `FixedUser`, `ShowFolded` und `MaxOpenCommands`, Druckfabrik `org.modellwerkstatt.mcpme.print.McpPrintFactory` mit Pfaden für `red11`. Der Build nimmt die daraus generierte `McpConfig.xml`.
- **Datenbank:** `simpleone` braucht MariaDB/MySQL auf `localhost` (Datenbank `test`, Benutzer `dan`). Ohne sie startet die Anwendung und liefert die Übersicht; Commands lassen sich nicht sinnvoll öffnen.
- **Dialoge:** Der Server führt die Fortsetzung eines Dialogs aus, nachdem der Aufruf der Runtime zurückgekehrt ist. Erprobt für alle drei Arten: die Rückfrage „Änderungen verwerfen?“ (ohne `discard_changes` bleibt der Command offen), Hinweise nach dem Abschluss eines Commands, und die Fehlermeldung nach einer Exception in einer Conclusion (der Command ist danach geschlossen, das Ergebnis ist ein Fehler und enthält den Text samt Stacktrace, wie ihn die Runtime liefert).
- **Abgelehnte Eingabe:** Zeigt die Seite nach `conclude` oder `set_fields` eine Fehlermeldung, ist das Ergebnis als Fehler gekennzeichnet; der Zustand steht trotzdem darin.
- **Pflichtfeld:** Der Zustand sagt nicht, ob ein Feld einen Wert verlangt. Das prüft die Runtime beim Auslösen einer Conclusion Action: Fehlt ein Wert oder passt er nicht, bleibt die Seite stehen, und der Fehlertext steht am Feld und als Meldung der Seite („Zahl muss im Format #.##0,00 eingegeben werden …“). Erprobt.
- **Texte der Anwendung:** Der Server reicht Texte unverändert weiter. Dass `simpleone` die Einträge einer Reference mit HTML-Auszeichnung liefert, ist eine Eigenheit dieser Testanwendung und wird nicht behandelt.
- **Platzhalter:** Bei Auswahl- und Datumsfeldern ist der Platzhalter der Runtime ein Bedienhinweis für die Oberfläche („<crtl-space> oder <mouse-click>“, „__.__.__“); der Agent erhält ihn nur bei Textfeldern, bei Datumsfeldern das Format.
- **Ungespeicherte Änderungen:** Als solche gilt, was die Session eines Commands meldet, der selbst speichert. In `simpleone` setzt „Master Doc Editor“ das im `command init` absichtlich, um die Rückfrage zu erzwingen.
- **Protokoll des SDK:** Das SDK protokolliert über SLF4J 2; in `red11` fehlt dafür die Anbindung, seine Meldungen gehen verloren. Das eigene Protokoll steht in `red11/logs/mcpme.log`.

## Zulieferung aus Generator und Runtime

Außerhalb dieses Projekts, aber vom Server verwendet, sobald vorhanden. Der Server funktioniert auch ohne diese Angaben; jede davon ersetzt Wissen, das der Agent sonst aus einem Skill oder von der Person braucht.

Ort der Ergänzung ist das generierte Command-Modul je Unit (`CmdModule<Unit>`, abgeleitet von `OFXCmdModule`). Es trägt heute je Command einen Eintrag `CmdDefaults` mit Klasse, FQ-Name, Typ, Beschriftung, Icon, Hotkey, Farbe, Tooltip (Berechtigungen) und der Funktion für die Parameter aus der Selektion; `OFXCmdModule` bietet dazu Abfragen über den FQ-Namen (`getCommandToolTip`, `getCommandDefaultLabel`, …). Neue Angaben folgen demselben Muster: ein weiteres Feld in `CmdDefaults`, eine weitere Abfrage in `IOFXCmdModule`.

Die Erweiterung hat zwei Teile: in der Runtime ein Feld in `CmdDefaults` und eine Abfrage in `IOFXCmdModule` und `OFXCmdModule`; im Generator die Vorlage, die das `CmdModule<Unit>` erzeugt. Sie wird zu Stufe 4 gebraucht und kann dann über MPS vorgenommen werden. Die `OFXConfig` der Testanwendung für den Server entsteht in Stufe 1.

Zugesagt:

- **Ein Beschreibungstext je Command**, zur Laufzeit über den FQ-Namen abrufbar. Er enthält in einem Stück die Parameter des Commands, `user doc` und `tec doc`. Aufbau, die Parameter so, wie sie im Modell stehen, als erster Teil und mit einer Leerzeile abgeschlossen:

  ```text
  Parameter:
  1. int bestellungId = 0
  2. Bestellung bestellung = getSelected(Bestellung)
  3. BestellJournalEintrag journal = null

  User doc:
  …

  Tec doc:
  …
  ```

  Damit erklärt sich `[4711, null, null]` weitgehend von selbst. Dass ein Command mit fehlendem Objekt umgehen kann, steht in seinem `command init`, nicht in der Parameterliste; ein Satz im `tec doc` macht daraus Wissen, etwa „Direkt startbar mit `bestellungId`; `bestellung` und `journal` dürfen `null` sein.“

Wünsche, nach Nutzen geordnet:

1. **Liste der Commands eines Moduls.** `OFXCmdModule` hält die Einträge, gibt sie aber nicht heraus. Mit einer Abfrage dafür kann der Server alle Commands der Anwendung aufzählen und durchsuchbar machen.
2. **Commands je Page:** welche Commands die `Page Pane`s einer Page anbieten (im Editor als „branching commands“ sichtbar). Der Agent kann damit einen Weg planen, ohne Seiten probeweise zu öffnen.
3. **Dokumentation je Page und je Conclusion**, soweit das Modell sie hat.
4. **Technischer Name der Conclusion** in dem, was die Runtime dem Toolkit übergibt (`OFXConclusionInformation`). Der Name steht im generierten Command (`SpeichernUndSchliessen`, `SaveIcon`). Zum Auslösen ist er nicht nötig; er gäbe Skills einen Namen, der über Sessions und Sprachen hinweg gilt.
5. **`generally enabled` als Text:** Die Runtime sagt nur, dass ein Command nicht ausführbar ist, nicht warum. Der Ausdruck als Text gäbe dem Agenten einen Hinweis.

## Offen und zu erproben

Offen:

- **Zugriffsschutz der Adressen:** Dokumente und Bilder liegen unter HTTP-Adressen außerhalb der MCP-Session. Wer sie abrufen darf, regelt der Webserver wie bei turkuforms, nicht der MCP-Server.

Zu erproben:

- **Anmeldung:** ob Entra ID den von MCP verlangten Parameter `resource` annimmt. Metadaten und `401`-Antwort liefert das SDK nicht; sie gehören in den vorgeschalteten Filter.
- **Direkter Start, Fehlermeldung:** Erprobt: Eine unpassende Liste zeigt sich als falscher Typ oder als „nicht ausführbar“, genau wie eine fehlende Berechtigung; der Server kann die Fälle nicht unterscheiden und sagt das in seiner Meldung. Noch offen: den Parameterteil des Beschreibungstexts an die Meldung anhängen, sobald es ihn gibt.
- **Direkter Start mit fehlendem Objekt:** Greift erst das `command init` auf ein Objekt zu, das `null` ist, sollte der Command mit einer Fehlermeldung abbrechen und seine Session verwerfen.
- **Aktualisierung über ein Feld:** Sie läuft in der Runtime wie eine speichernde Conclusion, mit Prüfung aller Formularfelder. Sind andere Pflichtfelder leer, meldet die Seite vermutlich einen Eingabefehler, und die Aktualisierung läuft nicht.

## Verworfen

- **Nur das oberste Fenster bedienbar** (wie h2forms): Nachschlagen würde Abbrechen und damit den Verlust der Eingaben verlangen.
- **Je Einstiegs-Command ein eigenes Werkzeug:** bis 50 Werkzeuge, eine Namensregel für nicht eindeutige Kurznamen und zwei Wege zum Starten.
- **Commands mit der Option `URL` als Werkzeuge mit Eingaben.**
- **Generator für einen Command-Katalog oder für Skills.**
- **Eigenes Konto für den Agenten; Anmeldung ohne Person.**
- **Google und LinkedIn als Identity Provider:** vorerst nicht benötigt. Der Server müsste dafür vermutlich selbst als Autorisierungsserver auftreten und die Anmeldung über `IExtAuthProvider` weiterreichen.
- **Auslieferung von Skills über MCP:** vorerst nicht benötigt.
- **Upload von Dateien durch den Agenten:** vorerst weggelassen.
- **Ein Werkzeug zum Abholen von Dateien über MCP:** Dokumente und Bilder kommen über HTTP(S).
- **Offene Dialoge mit einem Werkzeug zum Beantworten;** Rückfragen über den Client an die Person.
- **Paging mit Cursor** und Sortieren in Tabellen.
- **Das MCP-Protokoll selbst schreiben** (ohne SDK, auf Tomcat 9 neben turkuforms): Es wird das offizielle SDK verwendet.
- **Der Begriff „Context“** für offene Commands.
