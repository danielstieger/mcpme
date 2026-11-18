# Toolkit-Rahmen – Start, Anmeldung, URLs und Betrieb

## Zweck und Geltungsbereich

Die Toolkit-Interfaces beschreiben nur die Oberfläche einer bereits laufenden Anwendung. Dieses Dokument beschreibt alles darum herum: wie ein Toolkit die Runtime startet, einen Benutzer anmeldet, Commands über Adressen startet, die Anwendung beendet und sich in den Betrieb einfügt. Es trennt, was jede Implementierung leisten muss, von dem, was in den drei vorhandenen Implementierungen ihrer Laufzeitumgebung geschuldet ist.

Paketkürzel (`RT`, `OFX`, `TF`, `FX`, `H2`) und die Kennzeichnung **[V]** / **[T]** / **[F]** / **[H]** erklärt die [Einstiegsseite](toolkit-schnittstelle.md); die Signaturen der genannten Klassen stehen in [Signaturen](toolkit-signaturen.md).

## Kapitellandkarte

| Thema | Runtime-Klassen |
| --- | --- |
| [Startsequenz](#startsequenz) | `RT.core.ApplicationMDI`, `BaseUiFactory`, `RT.telemetrics.AppJmxRegistration`, `RT.dsl.app.AppUiModule` |
| [Einstellungen der AppFactory](#einstellungen-der-appfactory) | `RT.core.BaseUiFactory` |
| [Anmeldung](#anmeldung) | `RT.core.LoginController`, `RT.auth.IExtAuthProvider` |
| [Command-Start per URL](#command-start-per-url) | `RT.core.ApplicationBasis`, `CheckerForCmdUrlDefaults`, `OFX.OFXUrlParams` |
| [Beenden](#beenden) | `RT.core.ApplicationMDI`, `Shutdown` |
| [Paralleles Deployment](#paralleles-deployment) | `RT.telemetrics.ParDeployAutoForwarder`, `ApplicationTelemetrics` |
| [Druck und Dateien](#druck-und-dateien) | `OFX.IOFXPrintFactory`, `IPrintingServiceImpl` |
| [Protokollierung und Monitoring](#protokollierung-und-monitoring) | `RT.core.ApplicationReporter`, `OFX.IOFXCoreReporter` |
| [Batchjob mit Oberfläche](#batchjob-mit-oberfläche) | `RT.core.AbstractBatchJobUi`, `RT.dsl.app.BatchJobModule` |

## Startsequenz

### Pflichten jeder Implementierung [V]

**Einmal je Prozess:**

1. Die Spring-Konfiguration der Anwendung laden (`ClassPathXmlApplicationContext` mit der aus der `OFXConfig` generierten XML-Datei).
2. Das Anwendungsmodul erzeugen: eine Instanz der generierten Klasse, die `RT.genspecification.IGenAppUiModule` implementiert (das `AppUI Module` des Modells), über `appContext.getAutowireCapableBeanFactory().createBean(cls)`. Eine Instanz dient allen Benutzern.
3. Die AppFactory aus dem Kontext holen: `appContext.getBean(…)` mit `IToolkit_UiFactory`, `IOFXApplicationFactory` ([F]) oder einem eigenen Interface des Toolkits ([H]).
4. `factory.initCmdUrlDefaults()` aufrufen, wenn Commands über Adressen gestartet werden sollen; ohne den Aufruf bleibt die Liste leer. `factory.initExtAuthProviders()` aufrufen, wenn `IExtAuthProvider`-Beans zur Anmeldung dienen sollen.
5. `new AppJmxRegistration(appFqName, namePostFix, servletPath, homePath)` erzeugen und `registerAppTelemetrics(factory, appFqName, appName, appVersion, frameworkVersion, serverName)` aufrufen. Ohne diesen Aufruf fehlen der Runtime die `ApplicationTelemetrics`, die sie unter anderem vor jedem Command-Start abfragt.

**Je Benutzer und Hauptfenster:**

6. Eine `OFX.UserEnvironmentInformation` anlegen und Gerätedaten setzen: `setDevice(deviceName, deviceSwName, deviceId)`.
7. Den Benutzer anmelden, siehe [Anmeldung](#anmeldung).
8. Das Hauptfenster (`IToolkit_MainWindow`) erzeugen.
9. `new ApplicationMDI(factory, mainWindow, appUiModule, jmxRegistration, platform)` – `platform` ist ein Wert von `IOFXCoreReporter.MoWarePlatform`. Das Toolkit hält die Instanz als Feld oder leitet von ihr ab.
10. `applicationMDI.initializeApplication(urlParams, systemName, userEnv, connectionInfo, startupInfo)`. `urlParams` darf `null` sein. Rückgabe `true`: Ein Start-Command wurde gestartet. Die Aufrufe, die daraus am Hauptfenster folgen, stehen im [Lebenszyklus](toolkit-lebenszyklus.md#anwendungsstart).

Die Reihenfolge der Schritte 7 bis 9 ist frei: [F] erzeugt Hauptfenster und `ApplicationMDI` vor der Anmeldung. Die Instanz registriert sich erst in `initializeApplication` beim Monitoring.

Die AppFactory erwartet im Spring-Kontext weitere Beans, die die `OFXConfig` der Anwendung bereitstellt: `IOFXPrintFactory`, `DataSource`, `IOFXPlatform`, `IMoEventBusService`, `IOFXTranslationProvider` sowie die `IOFXCmdModule`-Beans der Anwendung.

Zusätzlich setzen [F] und [H] beim Start Betriebsinformationen: `context.getBean(IM3DatabaseDescription.class).setSessionInfo(...)` und `factory.getEventBus().setSysInfo(...)`.

### Die drei Starter im Vergleich

| | turkuforms | fx8forms | h2forms |
| --- | --- | --- | --- |
| Prozess | Webanwendung (WAR) für Tomcat | Desktop-Prozess | Webanwendung (WAR) für Tomcat |
| Schritte 1 bis 5 | `TurkuServlet.servletInitialized()`, je Webanwendung | Schritte 1 bis 3 in der Runtime: `RT.dsl.app.AppUiModule.runWithJavaFxFromConsole(appClass, xmlFallback, args)` lädt Spring, erzeugt das Modul und ruft per Reflection `FX8Application.run(appUiModule, factory, userEnv)`. Schritt 4 entfällt. | `H2ApplicationLoader` (Servlet), je Webanwendung |
| Konfiguration | `web.xml`: `xmlConfigFile`, `applicationFqName`, `turkuAppImplClassFq`, `heartbeatInterval` | XML-Datei aus `args[0]`, sonst aus dem Manifest, sonst der Fallback | `web.xml`: `xmlConfigFile`, `applicationFqName`, optional `staticContentUrlPrefix` |
| Version | Aus dem WAR-Namen `<app>##<version>.war` | `IGenAppUiModule.getApplicationVersion()` | Aus dem Bereitstellungspfad `##<version>` |
| `AppJmxRegistration` | je Webanwendung | `(appFqName, "LI", "/localinstance", "/localinstance")` | `(appFqName, version, realPath, servletPath)` |
| Plattform | `MOWARE_TURKU` | `MOWARE_FX8` | `MOWARE_H2` |
| Je Benutzer | `TurkuMainWin2.beforeEnter` je Browser-Tab; eine HTTP-Session hält einen Controller je Tab | Ein Hauptfenster je Prozess | Ein `H2Application` je HTTP-Session, als Session-Attribut |
| `initializeApplication(...)` | | `(null, computerName, userEnv, computerName, "")` | `(urlParams, serverName, userEnv, remoteAddr, cookieInfo)` |
| Aufrufklammer | `TurkuServletService` umschließt jeden Request | – | `synchronized (session)` um jeden Request |

[T] Routen: `/login` und `/logout` führen auf die konfigurierte Anmeldeseite, alle anderen Pfade zunächst auf `SimpleHomeScreen`, nach der Anmeldung auf das Hauptfenster.

[H] Vor der Anmeldung liegt im Session-Attribut ein Login-Controller; nach erfolgreicher Anmeldung ersetzt ihn die Anwendung und bearbeitet denselben Request weiter.

## Einstellungen der AppFactory

Die AppFactory ist eine Spring-Bean; ihre Properties setzt die `OFXConfig` der Anwendung. `BaseUiFactory` definiert:

| Property | Default | Wirkung |
| --- | --- | --- |
| `SilentExLogging` | `false` | Fehlerdialoge ohne technische Details (`ERROR_SMALL`) |
| `MowareTrace` | `false` | Ablaufprotokoll der Runtime; schaltet auch `AppTrace` ein |
| `AppTrace` | `false` | Ablaufprotokoll der Anwendung |
| `LogEventsToStdErr` | `false` | Jede Meldung zusätzlich auf die Standardfehlerausgabe |
| `PortJ`, `PortJLocal` | `false` | Protokollereignisse an den Event-Bus-Kanal `portjlog` |
| `SkipLoggingTcnException` | `false` | Konflikte beim optimistischen Sperren nicht protokollieren |
| `EventBusLocking` | `false` | Pessimistische Sperren (`acquire locks`) über den Event-Bus; wirkt nur zusammen mit einem Schalter der Anwendungsinstanz |
| `ReloadOnTabChange` | `false` | Vor jeder Fokusvergabe alle Elemente neu laden – nicht nur beim Tabwechsel |
| `CollectSelections` | `false` | Bei der Suche nach einer Selektion wird eine leere Selektion eines abhängigen Selektionskontexts übersprungen |
| `AlwaysRollbackSession` | `false` | Sessions werden beim Abschluss zurückgerollt statt committed |
| `CheckAlsoPredecessorDuplication` | `false` | Bei der Prüfung „Command läuft schon“ auch den Vorgänger eines Successors berücksichtigen |
| `DecimalDelegateInsensibleThousand` | `false` | Dezimaleingabe: Tausendertrennzeichen als Dezimaltrennzeichen deuten, wenn kein Dezimaltrennzeichen vorkommt |
| `DefaultEnglish` | – | Nur Setter; schaltet die Systemtexte auf Englisch |
| `AutoParDeploymentForwardGracefully` | `false` | siehe [Paralleles Deployment](#paralleles-deployment) |
| `CheckDeployedVersion` | `true` | Beim Start prüfen, ob Modulversion und bereitgestellte Version übereinstimmen |
| `CommandContainerFqName` | `…core.EventCommandContainer` | Klasse des Command-Containers |

Diese Properties liest die Runtime nicht selbst; sie stehen dem Toolkit zur Verfügung:

| Property | Default | Verwendung |
| --- | --- | --- |
| `ScrollAdjust` | `false` | Tabelle zur Selektion scrollen ([F]) |
| `ExperimentalFeatures` | `false` | frei |
| `OptimKeyboard` | `false` | [H] wertet es aus |
| `UploadLocationRetrieve` | `null` | Adresspräfix zum Anzeigen von Bildern ([T], [H]) |
| `UploadLocationStore` | `null` | [H] Adresse, an die der Client eine Datei sendet |
| `UploadFsLocationStore` | `null` | Verzeichnis für hochgeladene Dateien ([T], [H]) |
| `UploadMaxSize` | `2000000` | Maximale Dateigröße in Bytes ([T]) |

Die Setter der drei Upload-Pfade hängen den Verzeichnistrenner des Betriebssystems an.

Ein Toolkit definiert eigene Properties an seiner Factory-Klasse:

- [T] `CompactMode`, `SingleAppInstanceMode`, `AuthenticatorClassFqName`, `OnLogoutMainLandingPath`, `TurkuSubTheme`, `UseMinimalDelegateFormLabelWidth`, `HideTableSearchWhenBelow`, `TableColumnGrowTo100`.
- [F] `WarningOnlyImageDelegate`.
- [H] `TemplateName`, `AuthenticatorName`, `TableColDivider`, `DebugUsers`, `CamDesiredHeight`, `CamDesiredWidth`, `CamUseSystemViewfinder`, `CamNotZebraSpecific`.

## Anmeldung

### Kern [V]

```java
LoginController login = new LoginController(factory, platform, systemName, connectionInfo);
String error = login.checkLoginPrepareUserEnv(username, password, userEnv, appUiModule);
```

`RT.core.LoginController.checkLoginPrepareUserEnv` öffnet eine temporäre Session, ruft `IGenAppUiModule.isAuthenticated(username, password, userEnv, session)` – die Funktion `isAuthenticated` des `AppUI Module`s – und schließt die Session. Rückgabe `null` bedeutet Erfolg; sonst ein anzuzeigender Fehlertext. `notAuthenticatedButNoProblems()` liefert danach `true`, wenn die Funktion ohne Fehler und ohne Precondition schlicht `false` geliefert hat – der Anlass, ein weiteres Verfahren zu versuchen.

Die modellierte Funktion befüllt die User Environment (`setUserName`, `setUserId`, optional `setCompactMode`, `setBrandingId`, Sprache). Woher Benutzername und Passwort stammen, entscheidet das Toolkit: Anmeldedialog, Betriebssystem-Benutzer, Adresse des Clients, Verzeichnisdienst, Token.

Das Toolkit liest danach aus der User Environment:

| Wert | Verwendung |
| --- | --- |
| `getLangIndex()` | Sprache für Systemtexte (`uiFactory.getSystemLabel(langIndex, key)`); `-1` ist die Standardsprache |
| `isCompactMode()` | Kompakte Darstellung für kleine Bildschirme; nur [T] wertet es aus |
| `getBrandingId()` | Branding; Default `1` |
| `getUserName()` | Anzeige des angemeldeten Benutzers |

### Die Verfahren der drei Implementierungen

| Implementierung | Verfahren |
| --- | --- |
| [T] `authmpreis.IPAuthLandingPage` (Default) | Benutzername ist die IP-Adresse des Clients oder der URL-Parameter `username`, das Passwort leer. Schlägt das fehl, folgt ein Anmeldeformular mit Prüfung über `IMoLdapService`. |
| [T] `authmpreis.LdapAuthLandingPage` | Anmeldeformular mit Prüfung über `IMoLdapService` |
| [T] `authmpreis.OAuthLandingPage` | Genau ein `IExtAuthProvider`: Weiterleitung zum Anbieter, Rückkehr mit `code` und `state`, Benutzername über `retrieveUserWithAccessToken(code)`, Passwort leer |
| [T] `authdemo.MultiOAuthLandingPage` | Auswahl unter mehreren `IExtAuthProvider`n |
| [F] | Benutzername aus der Umgebungsvariable `USERNAME`, Passwort leer, Systemname aus `COMPUTERNAME`. Bei einem Fehler: Fehlerdialog, danach `closeApplicationAndExit()`. |
| [H] `H2MpreisNoLoginAuth` (Default) | Benutzername ist der URL-Parameter `username` oder die IP-Adresse des Clients, Passwort leer |
| [H] `H2MpreisTrialFirstAuth` | Zuerst wie oben; schlägt das fehl, ein Anmeldeformular |
| [H] `H2MpreisLoginOAuth` | Zuerst wie oben; bei `notAuthenticatedButNoProblems()` OAuth über genau einen `IExtAuthProvider` |

Die Auswahl erfolgt über `AuthenticatorClassFqName` ([T]) beziehungsweise `AuthenticatorName` ([H]).

`RT.auth.IExtAuthProvider` (`getAuthProviderName`, `initialRedirect(state)`, `retrieveUserWithAccessToken(code)`, `getNullOrCredentialExpirationDate`) und die Implementierungen `GoogleOAuth2`, `EntraIdOAuth2`, `LinkedinOAuth2` gehören zur Runtime und sind von jedem Toolkit nutzbar, das einen Browser-Redirect ausführen kann.

[T] Benutzername und Passwort liegen als `UserPrincipal` in der HTTP-Session, damit weitere Browser-Tabs ohne erneute Eingabe angemeldet werden. Bei `SingleAppInstanceMode` wird ein zweiter Tab abgewiesen, bis der Benutzer die laufende Instanz beendet.

[H] Die Sitzung endet, wenn sich das Netz der Client-Adresse oder der Parameter `username` ändert.

## Command-Start per URL

Ein Command mit der Option `URL` ist über einen Pfad erreichbar, etwa `/rechnung/4711/details`.

### Runtime [V]

- `factory.getAllCmdUrlDefaults()` liefert je Command eine `IOFXCmdModule.CmdUrlDefaults` mit `url` (erstes Pfadsegment), `minNumParams`, `maxNumParams` und den Parametertypen. `CheckerForCmdUrlDefaults` bricht den Start ab, wenn die Kombination aus Pfad und Parameterzahl mehrfach vorkommt.
- `new OFXUrlParams().parse(path)` zerlegt einen Pfad: Das erste Segment ist der Command-Name, die weiteren sind Parameter.
- `ApplicationBasis.getUrlDefaultFor(defaults, urlParams)` sucht den passenden Eintrag; `cmdStartForUrlDefault(def, urlParams, session)` liefert das Start-Ereignis oder `null`, wenn der Command für den Benutzer nicht bedienbar ist.

Das Toolkit startet den Command nach `initializeApplication`:

```java
applicationMDI.execAfterStartupOrNow(() -> {
    CmdUrlDefaults def = applicationMDI.getUrlDefaultFor(factory.getAllCmdUrlDefaults(), urlParams);
    BasisCmdStart start = (def == null) ? null : applicationMDI.cmdStartForUrlDefault(def, urlParams, applicationMDI.getMainWindowEvalSession());
    if (start != null) { applicationMDI.receiveAndProcess(start); }
});
```

`execAfterStartupOrNow` verschiebt die Ausführung, bis ein laufender Start-Command beendet ist. Die Signaturen stehen in [Signaturen](toolkit-signaturen.md#anwendungsinstanz-und-anmeldung-orgmodellwerkstattdatauxruntimecore-telemetrics).

[H] zeigt einen Fehlerdialog (`ERROR_SMALL`), wenn der Command nicht bedienbar oder unbekannt ist, und bietet zusätzlich jede Tile und jeden Eintrag des Startmenüs unter einer eigenen Adresse an (`_tilemenu<i>`, `_startmenu<i>`).

In der Gegenrichtung liefert die Runtime Adressen an das Toolkit:

| Aufruf | Bedeutung |
| --- | --- |
| `IToolkit_CommandContainerUi.setAdjustUrl(url)` | Adresse des geöffneten Commands (`url param adjust`) |
| `IToolkit_MainWindow.showTiles(tiles, resetUrl)` | `resetUrl == true`: Es ist kein Command mehr offen; die Adresse zurücksetzen. |

Ein Toolkit ohne Adresszeile ignoriert beide Aufrufe ([F]). [H] ignoriert sie ebenfalls und setzt die Adresse selbst per Redirect zurück, wenn kein Command mehr offen ist.

[T] Ein Aufruf mit Pfad vor der Anmeldung wird als Parameter `reroute` durch die Anmeldung gereicht. Die Adresse im Browser wird mit `history.replaceState` nachgeführt, beim Aktivieren eines Tabs auf dessen Adresse, bei den Tiles auf die Startadresse.

## Beenden

### Runtime [V]

| Auslöser | Aufrufe |
| --- | --- |
| Benutzer meldet sich ab oder schließt das Hauptfenster, mit Rückfrage | Toolkit: `onExitRequested(false)`. Runtime: gegebenenfalls Rückfrage, `internal_immediatelyShutdown()`, `mainWindow.closeApplicationAndExit()` |
| Abmelden ohne Rückfrage; die Sitzung endet von außen (Zeitüberschreitung, Prozessende) | Toolkit: `internal_immediatelyShutdown()`, sofern noch nicht ausgeführt |
| Der Prozess endet | Event-Bus schließen, `jmxRegistration.gcClean()`; in einem Server zusätzlich Datenbankverbindungen und Spring-Kontext schließen |

`internal_immediatelyShutdown()` beendet alle Commands mit `FINAL_USER_CANCEL`, schließt deren Fenster über die Schnittstelle, schließt die Sessions der Anwendung und meldet den Client beim Monitoring ab. Die Methode darf je Instanz nur einmal laufen.

`RT.core.Shutdown.shutdownAppAndContext(ServletContext)` fasst das Prozessende für einen Servlet-Container zusammen. Die Methode ist an `javax.servlet` gebunden und erwartet Spring-Kontext, Factory und JMX-Registrierung als Attribute des `ServletContext` unter den Namen `Shutdown.CTX_*`. Andere Laufzeitumgebungen führen die Schritte selbst aus.

### Die drei Implementierungen

| | turkuforms | fx8forms | h2forms |
| --- | --- | --- | --- |
| Abmelden | Menüeintrag „Logout“ → `onExitRequested(false)` | Menüeintrag „Beenden“ und Schließen des Fensters → `onExitRequested(false)`; das Fenster schließt erst in `closeApplicationAndExit()` | Logout-Request → `internal_immediatelyShutdown()` ohne Rückfrage, Redirect |
| `closeApplicationAndExit()` | Controller von der HTTP-Session abmelden, Weiterleitung auf `/logout` | `jmxRegistration.gcClean()`, Fenster schließen; danach Event-Bus schließen | Vormerken, dass die Sitzung nach der Antwort endet |
| Ende von außen | `sendBeacon` beim Schließen des Browser-Tabs; `HttpSessionBindingListener.valueUnbound` | Eine unbehandelte Exception führt nach einem Hinweis zu `onExitRequested(true)` | `valueUnbound` der HTTP-Session |
| Zeitüberschreitung | 3 Minuten vor der Anmeldung, 5 Stunden bei laufender Anwendung, 10 Sekunden nach dem Abmelden des letzten Controllers | – | 1 Stunde; bis Mitternacht, solange eine Schreib-Session offen ist |
| Prozessende | `Shutdown.shutdownAppAndContext` | Schließt weder Spring-Kontext noch Datenbankverbindungen | `Shutdown.shutdownAppAndContext` |

[T] `installCloseConfirmQuestion(true)` installiert die Rückfrage des Browsers vor dem Verlassen der Seite.

## Paralleles Deployment

Tomcat kann zwei Versionen derselben Anwendung gleichzeitig betreiben. Die Runtime leitet Benutzer der alten Version um:

1. Die `ApplicationTelemetrics` erhalten das Kennzeichen „weiterleiten“ – sofort (`parDeployForwardImmediate`) oder schonend (`parDeployForwardGracefully`) –, über JMX oder automatisch bei `AutoParDeploymentForwardGracefully`, wenn eine neuere Version bereitsteht. Für den automatischen Weg ruft der Starter `jmxRegistration.enableAutoForwardGracefully()` ([H]).
2. `ApplicationMDI.checkForAppParDeploymentForward()` läuft vor jedem Command-Start und jeder Conclusion. Bei „sofort“ oder bei „schonend“ ohne offene Schreib-Session unterdrückt die Runtime die Aktion und zeigt einmal einen Hinweisdialog (`INFO_SMALL`).
3. Im Runnable des Dialogs: `internal_immediatelyShutdown()`, dann `mainWindow.parDeploymentForwardNow()`.

[T] turkuforms beendet die HTTP-Session und leitet auf `/login`; der Container führt die neue Sitzung in die neue Version. Sind weitere Browser-Tabs desselben Benutzers offen, führt die Weiterleitung auf eine Hinweisseite.

[H] prüft bei jedem Request mit `jmxRegistration.checkMarkAsForwardGracyFully()`, ob die eigene Version als alt markiert ist; neue Sitzungen erhalten dann nur noch eine Hinweisseite. Der Hinweisdialog ist nicht modal, sein Runnable läuft im selben Request. `parDeploymentForwardNow()` beendet die Sitzung und leitet auf die letzte Adresse mit dem Parameter `fromversion` um.

[F] `parDeploymentForwardNow()` ist leer.

Ohne paralleles Deployment tritt der Ablauf nicht ein; `parDeploymentForwardNow()` kann dann wie `closeApplicationAndExit()` behandelt werden.

## Druck und Dateien

`IToolkit_UiFactory.createConfiguredUserPrintService(tecHandle, userEnv)` delegiert an die `IOFXPrintFactory`-Bean der Konfiguration. `tecHandle` ist das Hauptfenster. Das Ergebnis, ein `IPrintingServiceImpl`, bedient die Funktionen des User Service (`IOFXUserServices`) für diesen Benutzer:

| Methode | Vertrag |
| --- | --- |
| `render(...)`, `renderPrint(...)`, `renderView(...)` | Dokument aus Vorlage und Daten erzeugen; liefert eine Datei |
| `view(file)` | Datei dem Benutzer anzeigen |
| `print(file)` | Datei drucken |
| `openUrl(url)` | Adresse öffnen |
| `gcClean()` | Ressourcen freigeben |

Die Druckfabrik ist eine eigene Bean und nicht Teil der AppFactory; ein Toolkit liefert eine zur Plattform passende Implementierung.

Die ObjectFlow-Runtime bringt eine Basis mit: `OFX.OFXFatClientFopUserPrintService` erzeugt die Dokumente mit Apache FOP; `view`, `print` und `openUrl` arbeiten dort über `java.awt.Desktop` des lokalen Rechners. `OFX.OFXFatClientPrintFactory` liefert einen solchen Dienst für Desktop-Anwendungen.

| | Fabrik und Dienst | `view`, `print`, `openUrl` |
| --- | --- | --- |
| turkuforms | `TurkuPrintFactory`, `TurkuUserPrintService` (abgeleitet von der FOP-Basis) | Öffnen die Datei beziehungsweise die Adresse in einem neuen Browser-Tab; die Datei über einen HTTP-Pfad, unter dem ein Webserver das Ausgabeverzeichnis ausliefert |
| fx8forms | Kein eigener Code; die Konfiguration der Anwendung bestimmt die Bean | Mit der FOP-Basis: Standardanwendung und Drucker des Systems |
| h2forms | `H2PrintFactory`, `H2UserPrintService` (abgeleitet von der FOP-Basis) | alle drei leer |

**Adresse öffnen ohne Druckdienst:** `ApplicationMDI.openUrl(url)` merkt sich die zuletzt angeforderte Adresse. Das Toolkit holt sie nach einer Interaktion mit `getLastRequestedRedirectAndReset()` ab; [H] antwortet dann mit einem Redirect. Für eine entfernte Oberfläche ist das der Weg, „Adresse öffnen“ als Ergebnis einer Interaktion zurückzugeben.

Uploads und Bilder verwenden die Pfade der AppFactory, siehe [Referenz](toolkit-referenz.md#itoolkit_imageeditor-und-itoolkit_uploadeditor).

## Protokollierung und Monitoring

- **Vor dem Start einer Anwendungsinstanz** protokolliert das Toolkit über `factory.report(CoreReporterInfo)`.
- **Danach** über die Methoden von `IApplication` (`logFrmwrkProblem`, `logAppProblem`, `logAppWarning`, `logAppTrace`, `logMowareTracing`); sie ergänzen Benutzer und Sitzung.
- **Unerwartete Fehler im Toolkit** meldet `IApplication.reportException(...)`; die Methode liefert den Text für den Fehlerdialog.
- **Monitoring:** `ApplicationMDI` registriert sich selbst als Client bei der `AppJmxRegistration`. Das Toolkit kann Antwortzeiten melden (`jmxRegistration.getAppTelemetrics().servedRequest(...)`); [H] tut das je Request.

`IOFXCoreReporter.MoWarePlatform` kennt die Werte `MOWARE_H2`, `MOWARE_VAADIN`, `MOWARE_FX8`, `MOWARE_JOB`, `MOWARE_TURKU` und `MOWARE_API`; für weitere Toolkits gibt es keinen eigenen Wert.

## Batchjob mit Oberfläche

Ein `BatchJob Module` mit Oberfläche wird von der Runtime als Anwendungsmodul ohne Menüs und mit Tiles dargestellt (`RT.dsl.app.BatchJobModule`, `RT.core.AbstractBatchJobUi`). Beide Startwege sind fest an fx8forms gebunden:

- `AbstractBatchJobUi.startupUiFromMain` lädt Spring und ruft per Reflection `org.modellwerkstatt.fx8forms.windows.FX8Application.run(...)`.
- `BatchJobModule.jvmStart` entscheidet anhand der Factory-Bean im Kontext zwischen dem Konsolenbetrieb (`org.modellwerkstatt.objectflow.job.console.ConsoleBatchJobAppFactory`) und fx8forms (`org.modellwerkstatt.fx8forms.windows.FX8UiFactory`).

In einem anderen Toolkit läuft ein solches Modul als gewöhnliches `IGenAppUiModule`, wenn dessen Starter es lädt.

## Übertragbarkeit auf andere Laufzeitumgebungen

| Thema | Im Prozess (Swing, JavaFX) | Entfernte Oberfläche (React, MCP) |
| --- | --- | --- |
| Spring-Konfiguration, Anwendungsmodul, AppFactory, `AppJmxRegistration` | wie beschrieben | wie beschrieben, im Serverprozess |
| Anmeldung über `LoginController` | Dialog oder Betriebssystem-Benutzer ([F]) | Über das Protokoll zum Client; `IExtAuthProvider` ist nutzbar |
| Instanzen je Benutzer | Eine `ApplicationMDI` je Prozess | Eine `ApplicationMDI` je Client-Sitzung; Zuordnung und Lebensdauer verwaltet der Server ([H]: Session-Attribut) |
| `distributeTermEventOnOtherInstance` | leer | Nur bei mehreren Instanzen je Benutzer ([T]); sonst leer ([H]) |
| Adressen (`setAdjustUrl`, `resetUrl`) | ignorieren | React: Adresszeile nachführen; MCP: ignorieren |
| `installCloseConfirmQuestion` | Die Rückfrage beim Schließen des Fensters ist bereits durch `onExitRequested(false)` abgedeckt | wie turkuforms beziehungsweise leer |
| Paralleles Deployment | entfällt | abhängig vom Server |
| Druck | lokal anzeigen und drucken | Datei an den Client ausliefern; `openUrl` als Ergebnis der Interaktion |
| Hintergrund-Thread | sinnvoll ([F]) | nicht nötig ([T], [H]) |
| Servlet-Klassen, Sitzungsverwaltung, Routen | entfallen | durch eigene Serverinfrastruktur ersetzt |
