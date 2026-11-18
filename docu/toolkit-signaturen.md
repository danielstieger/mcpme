# Toolkit-Signaturen – Quelltext der Schnittstelle und der Gegenrichtung

## Zweck und Geltungsbereich

Dieses Dokument enthält die Java-Signaturen, auf die sich die übrigen Dokumente beziehen. Es macht die Dokumentation unabhängig vom Quelltext der Runtime: Wer ein Toolkit schreibt, braucht zum Übersetzen nur die Runtime-Bibliotheken (`org.modellwerkstatt.dataux.runtime.jar`, `org.modellwerkstatt.objectflow.runtime.jar`, `org.modellwerkstatt.manmap.runtime.jar`) und zum Nachschlagen dieses Dokument.

- [Die Schnittstelle](#die-schnittstelle-orgmodellwerkstattdatauxruntimetoolkit) ist vollständig und unverändert wiedergegeben (ohne Leerzeilen und Importe). Bei Widersprüchen zwischen Prosa und diesen Signaturen gelten die Signaturen.
- [Typen der Gegenrichtung](#typen-der-gegenrichtung) sind auf die Teile gekürzt, die ein Toolkit verwendet.

Stand: Branch `2026_1` der Runtime. Begriffe und Kürzel erklärt die [Einstiegsseite](toolkit-schnittstelle.md).

## Die Schnittstelle (`org.modellwerkstatt.dataux.runtime.toolkit`)

### `IToolkit_UiFactory`

```java
public interface IToolkit_UiFactory extends IOFXApplicationFactory {
  String getSystemLabel(int langIndex, MoWareTranslations.Key key);
  boolean getSkipLoggingTcnException();
  boolean isSilentExLogging();
  boolean getMowareTrace();
  boolean getAppTrace();
  boolean getEventBusLocking();
  boolean isScrollAdjust();
  boolean getExperimentalFeatures();
  boolean getDecimalDelegateInsensibleThousand();
  boolean getCollectSelections();
  boolean getReloadOnTabChange();
  boolean useBackgroundThread();
  boolean flagValidationAdditionally();
  boolean getAlwaysRollbackSession();
  boolean getCheckAlsoPredecessorDuplication();
  String getUploadLocationRetrieve();
  String getUploadLocationStore();
  String getUploadFsLocationStore();
  int getUploadMaxSize();
  boolean isAutoParDeploymentForwardGracefully();
  boolean isCheckDeployedVersion();
  List<IOFXCmdModule.CmdUrlDefaults> getAllCmdUrlDefaults();
  Object createPagePaneInitializerFqName(String fqName);
  IMoEventBusService getEventBus();
  IOFXTranslationProvider getTransProvider();
  ICommandContainer createCommandContainer();
  IToolkit_FormContainer createToolkitFormContainer(boolean flex);
  IToolkit_TabForm createToolkitTabForm();
  IToolkit_TableForm createToolkitTableForm();
  IToolkit_DelegateForm createToolkitDelegateForm();
  IToolkit_Form createCustomForm(CustomForm form);
  IToolkit_CommandContainerUi createTabContainerUi(IToolkit_MainWindow app, boolean modal);
  IToolkit_CommandContainerUi createPromptContainerUi(IToolkit_MainWindow app, boolean fullSize);
  IToolkit_TextEditor createTextEditor();
  IToolkit_TextEditor createDummyEditor();
  IToolkit_ReferenceEditor createReferenceEditor(boolean alter);
  IToolkit_StatusEditor createStatusEditor(boolean alter);
  IToolkit_DateOrTimeEditor createDateEditor(boolean withPicker);
  IToolkit_DateOrTimeEditor createDateAndTimeEditor(boolean withPicker, boolean timeOnly);
  IToolkit_ImageEditor createImageEditor();
  IToolkit_UploadEditor createUploadEditor();
  IToolkit_TextEditor createTextAreaEditor(int numOfLines);
  /**
   * Print service for user, constructed by employing
   * the IOFXPrintServiceFactory, however, the print factory can be configured without
   * touching the app factory. tecHandle and userEnvironment might be null in FatClient environments.
   * (more or less, call is forwarded to iofxprintfactory)
   */
  IPrintingServiceImpl createConfiguredUserPrintService(Object tecHandle, IOFXUserEnvironment userEnvironment);
  boolean isFX8LegacyImpl();
  enum CustomForm {
    PASSIVE_HTML_FULLSCREEN()
  }
}
```

### `IToolkit_MainWindow`

```java
public interface IToolkit_MainWindow {
  void showDialog(DlgType type, String text, IApplication.DlgRunnable callback);
  void showProblemsDialog(List<IOFXProblem> problems, IApplication.DlgRunnable callback);
  void showGraphDebugger(List<Object> graph, String controllerInfo);
  void setToastMessage(String info);
  void addTab(IToolkit_CommandContainerUi ui);
  void focusTab(IToolkit_CommandContainerUi ui);
  void ensureTabClosed(IToolkit_CommandContainerUi ui);
  default void installCloseConfirmQuestion(boolean installOrRemove) {
    // NOOP
  }
  void setCurrentTabModal(boolean lock);
  void closeApplicationAndExit();
  void parDeploymentForwardNow();
  void addStatusInformation(String info);
  void setAppInfo(String appName, String version, String dynTitle, int brandIng);
  void setMenuAndInit(int langIndex, MenuAction startMenu, MenuAction extraMenu, MenuAction helpMenu);
  void showTiles(List<Tile> tiles, boolean resetUrl);
  @Deprecated
  void ensureHotkeyAvailable(List<String> hotkeys);
  void execEventInBackground(ICommandContainer sender, Runnable runable);
  void execEventInForeground(ICommandContainer sender, UxEvent event);
  void distributeTermEventOnOtherInstance(GlobalCmdTermEvent evnt);
  boolean inUiThread();
  enum DlgType {
    INFO_SMALL(),
    ERROR_SMALL(),
    INFO_LARGE(),
    ERROR_LARGE(),
    QUESTIONCLOSE_SMALL()
  }
}
```

### `IToolkit_CommandContainerUi`

```java
public interface IToolkit_CommandContainerUi {
  void setCommandContainer(ICommandContainer container);
  void show(String windowTitle, IToolkit_Form content);
  void adjustWindowTitle(String title);
  void setContent(IToolkit_Form content);
  void setNotification(String notificat);
  void setColor(String color);
  void setConclusions(List<OFXConclusionInformation> conclusionInformations, List<String> additionalHotkeys);
  void reevalConclusions(List<OFXConclusionInformation> conclusionInformations);
  void delayedRequestFocus();
  void delayedAfterFullUiInitialized();
  default void setAdjustUrl(String url) {
    // Noop
  }
  void close();
  boolean isTabWindow();
  boolean isModalTabWindow();
}
```

### `IToolkit_Form` und Ableitungen

```java
public interface IToolkit_Form<BoundElementType> {
  boolean selectionChanged(IOFXSelection<BoundElementType> selection);
  void loadList(List<BoundElementType> objects, IOFXSelection<BoundElementType> selection);
  void gcClear();
  void setTitleText(String text);
  void setProblems(List<IOFXProblem> lop);
  default void rootForm() {
    // responsible for managing PageTitle and problems.
  }
  Object myRequestFocus();
  void afterFullUiInitialized();
}

public interface IToolkit_DelegateForm<T> extends IToolkit_Form<T> {
  String checkDelegatesValidAndFocus();
  void setColLayoutConstraints(List<String> constraints);
  void addDelegate(IDlgt delegate);
}

public interface IToolkit_TableForm<ElementType> extends IToolkit_Form<ElementType> {
  /** Used to instantiate new Selections */
  void endOfInitializationForElementClass(Class cls);
  void setFormController(IGenSelControlled crtl);
  void addTableItemColor(String property, ITableCellStringConverter converter);
  void addColumn(String property, String label, ITableCellStringConverter<?> converter, int width, boolean editable, boolean folded, boolean important);
  void setSelectionSummaryLineText(String text);
  void setTableSummaryLineText(String text);
  void forceNotEditable();
  void setEditPreview();
  void addMenuAndSetButtons(MenuAction folder);
  class TableColumn {
    public int position;
    public ITableCellStringConverter mowareConverter;
    public String propertyName;
    public String headerName;
    public int widthInPercent;
    public boolean visible;
    public TableColumn(int pos, String propname, String header, ITableCellStringConverter conv, int width, boolean vis) { ... }
  }
}

public interface IToolkit_FormContainer<BoundElementType> extends IToolkit_Form<BoundElementType> {
  void setLayoutConstraints(List<String> colConstraints, List<String> rowConstraints);
  void addChildren(IToolkit_Form child);
  void addMenuAndSetButtons(MenuAction menu);
  void skipFocus(int cmptToStartWith);
}

public interface IToolkit_TabForm<T> extends IToolkit_Form<T> {
  void addTab(IToolkit_Form form, String label);
  IToolkit_TabForm<T> getToolkitImplementation();
}
```

### `IToolkit_TextEditor` und Ableitungen

```java
public interface IToolkit_TextEditor {
  void setDelegate(IDlgt dlgt);
  void enableKeyReleaseEvents();
  void setLabelTooltip(String text);
  void setValidationErrorText(String text);
  void setLabel(String text);
  void setEnabled(boolean enabled);
  void setEditorPrompt(String promptText);
  void newObjectBound();
  void setText(String text);
  String getText();
  void setIssuesUpdateConclusion();
  void setOption(IDlgt.Opt... option);
  Object getEditor();
  Object getLabel();
  Object getRightPartComponent();
  void gcClear();
  static boolean has(IDlgt.Opt queryFor, IDlgt.Opt... options) { ... }   // Hilfsfunktion: Option enthalten?
}

public interface IToolkit_StatusEditor extends IToolkit_TextEditor {
  int getSelectedIndex();
  void setSelectedIndex(int i);
  void setItems(List<String> items);
}

public interface IToolkit_ReferenceEditor extends IToolkit_TextEditor {
  void setOptionalAfterLoad(boolean optional);
  void setItems(List<String> items);
}

public interface IToolkit_DateOrTimeEditor extends IToolkit_TextEditor {
  void setFormatter(String format, String locale, int langIndex);
}

public interface IToolkit_ImageEditor extends IToolkit_TextEditor { }

public interface IToolkit_UploadEditor extends IToolkit_TextEditor { }
```

### `IToolkit_EnableItem`

```java
public interface IToolkit_EnableItem {
  void setItemEnabled(boolean enabled);
}
```

## Typen der Gegenrichtung

### Ereignisse und Container (`org.modellwerkstatt.dataux.runtime.core`)

```java
public interface IEventProcessor {
  void receiveAndProcess(UxEvent ev);
  void internal_immediatelyShutdown();
  IOFXSession getSession();
}

public interface ICommandContainer extends IEventProcessor {
  void initContainer(IToolkit_UiFactory fact, IApplication app, IWindowController crtl, ICommandContainer parent, BasisCmdStart cmdStart, String sessId);
  void processPendingEvents();
  boolean isSameCommandInstance(String fqName, Object[] params);
  IOFXSelection getSelectionAbsoluteFromCurrentPagePane(Class cls, boolean includingDerived);
  IToolkit_UiFactory getUiFactory();
  boolean hasRwSessionToCommit();
  boolean isReadOnly();
  boolean isDocumentSearch();
  boolean isStillOkayAsParentForMultiExecution();
  boolean isAutoConNoUi();
}

public abstract class UxEvent implements IOFXSession.IUxEvent {
  public String name();
  public abstract String paramInfo();
}

public class ConclusionEvent extends UxEvent {
  public ConclusionEvent(int conclusionHash, String desc);   // desc dient nur der Protokollierung
  public int getConclusionHash();
}
// CommandContainer.ESC_CONCLUSION_CODE == 0, CommandContainer.UPDATE_CONCLUSION_CODE == 1

public class KeyEvent extends UxEvent {
  public KeyEvent(boolean withModifier, String key);
  public String getKey();
  public boolean isWithModifier();
}

public class FocusEvent extends UxEvent {
  public FocusEvent(boolean bringTabToForeground);
  public boolean isBringTabToForeground();
}

public interface IApplication {
  // ... Auszug: vom Toolkit verwendbar
  IToolkit_MainWindow getMainWindowImpl();
  IOFXUserEnvironment getUserEnvironment();
  IOFXUserServices getUserService();
  IOFXSession getMainWindowEvalSession();
  boolean isApplicationModalTabMode();
  String reportException(String cmdOrSysName, String sessId, String message, String contextInformation, Throwable throwable);
  void logMowareTracing(String commandName, String sessId, String source, String desc, String param1);
  void logFrmwrkProblem(String commandName, String sessId, String source, String desc, String param1);
  void logFrmwrkProblem(String commandName, String sessId, String source, Throwable t, String desc);
  void logAppWarning(String commandName, String sessId, String source, String warningMsg, String param1);
  void logAppProblem(String commandName, String sessId, String source, Throwable t, String desc);
  void logAppTrace(String commandName, String sessId, String source, String desc, String param, Map<String, Object> additionalParamsOrNull);
  interface DlgRunnable {
    void run(boolean confirmed);
  }
}
```

### Anwendungsinstanz und Anmeldung (`org.modellwerkstatt.dataux.runtime.core`, `…telemetrics`)

```java
public class ApplicationMDI extends ApplicationBasis implements IOFXUserServices, IApplication {
  public ApplicationMDI(IToolkit_UiFactory factory, IToolkit_MainWindow appWin, IGenAppUiModule appBehavior,
                        AppJmxRegistration registration, IOFXCoreReporter.MoWarePlatform pltfrm);
  public boolean initializeApplication(OFXUrlParams url, String systemName, IOFXUserEnvironment userEnv,
                                       String userConnectionInfo, String startupInfo);
  public void onKeyPressEvent(KeyEvent ev);
  public void onTabChangeEvent(int currentIndex);
  public void onExitRequested(boolean doNotCheckBackOnOpenGO);
  public void reciveForeignTermEvent(GlobalCmdTermEvent event);
  public void internal_immediatelyShutdown();
  public void receiveAndProcess(UxEvent ev);
  public boolean noGraphsToCommit();
  public void startCommandWithoutParams(CmdAction actionToStart);
  public void legacyStartCommandByUriAndParam(String uri, String firstParam);
  public String fullApplicationCrtlState();
  // aus ApplicationBasis:
  public void execAfterStartupOrNow(Runnable rnbl);
  public boolean isStartupCmdRunning();
  public boolean isCommandRunning();
  public boolean inShutdownMode();
  public IOFXCmdModule.CmdUrlDefaults getUrlDefaultFor(List<IOFXCmdModule.CmdUrlDefaults> urlDefaults, OFXUrlParams urlParam);
  public BasisCmdStart cmdStartForUrlDefault(IOFXCmdModule.CmdUrlDefaults def, OFXUrlParams urlParams, IOFXSession session);
}

public class LoginController implements IOFXUserServices {
  public LoginController(IToolkit_UiFactory factory, IOFXCoreReporter.MoWarePlatform aPlatform, String aSystemName, String connectInfo);
  /** null = angemeldet; sonst der anzuzeigende Fehlertext */
  public String checkLoginPrepareUserEnv(String username, String password, IOFXUserEnvironment env, IGenAppUiModule uiModule);
  public boolean notAuthenticatedButNoProblems();
}

public class AppJmxRegistration {
  public AppJmxRegistration(String appBehaviourFqName, String namePostFix, String servletPath, String homePath);
  public void registerAppTelemetrics(IOFXApplicationFactory factory, String appFqName, String appName,
                                     String appVersion, String frameworkVersion, String guessedServerName);
  public ApplicationTelemetrics getAppTelemetrics();
  public void gcClean();
}

public abstract class BaseUiFactory implements IOFXCoreReporter {
  public BaseUiFactory(MoWareTranslations.TranslationSelection transType);
  // TranslationSelection: MAIN_TRANSLATIONS, H1_TRANSLATIONS, V_TRANSLATIONS, EN_DEFAULT_TRANS
}
```

### Modell der Anwendung (`org.modellwerkstatt.dataux.runtime.genspecification`)

```java
public interface IGenAppUiModule {
  String getApplicationVersion();
  String getShortAppName();
  boolean isAuthenticated(String username, String password, IOFXUserEnvironment userEnvironment, IOFXSession session);
  MenuAction createStartMenu(IOFXUserEnvironment userEnv);
  MenuAction createExtrasMenu(IOFXUserEnvironment userEnv);
  MenuAction createHelpMenu(IOFXUserEnvironment userEnv);
  BasisCmdStart createStartupCmd(IOFXSession session);
  List<Tile> createTiles(IOFXUserEnvironment userEnvironment, IOFXSession session);
  void userLoggedIn(IOFXUserEnvironment env, IOFXUserServices srv);
}

public class AbstractAction {
  public String labelText;
}

public class MenuAction extends AbstractAction {
  public List<AbstractAction> getAllItems();
  public List<CmdAction> getAllCmdActionsOfMenu();
  public List<MenuAction> getAllPlainMenus();
  public List<CmdAction> getTopBarActions();
  public List<CmdAction> getDropDownActions();
}

public class CmdAction extends AbstractAction {
  public String commandFqName;
  public String image;
  public String hotKey;
  public String toolTip;
  public boolean globalHotkey;
  public boolean hideWhenDisabled;
  public boolean urlEnabledCmd;
  public boolean isGraphEdit;
  public void attachButton1(IToolkit_EnableItem item);
  public void attachButton2(IToolkit_EnableItem item);
  public String getToolTip();
  public boolean reevalEnabled();
  public void startCommand();
}

public class Tile {
  public String getColor();
  public String getCustomLabel();
  public CmdAction getCmdAction();
}

public interface IGenSelControlled<BoundObject> extends IOFXSelProvider<BoundObject> {
  // vom Toolkit verwendet: pushSelection(...) aus IOFXSelProvider und
  default String convertAsCsv(List<BoundObject> all, List<BoundObject> selected, List<IToolkit_TableForm.TableColumn> columns);
  // von der Runtime verwendet:
  boolean selectionChanged(IOFXSelection selection, boolean doNotLoadReloadFollows);
  void loadList(List<BoundObject> objects, IOFXSelection selection);
  String saveAndValidate();
  List<IDelegateChange> collectDelegateChanges();
  void forceNotEditable();
  void initializeGen(IToolkit_UiFactory uiFacotry, IPagePaneSelCrtl pageController, ISelectionController.Binding outerBinding, MenuAction outerMenu);
  IToolkit_Form getToolkitImplementation();
  void preDelayedAfterFullUiInitialized();
  void gcClear();
}
```

### Erweiterungspunkte (`org.modellwerkstatt.dataux.runtime.extensions`)

```java
public interface IDlgt<T> extends IUpdateConclusionReceiver.IFocusAbleDelegate {
  // vom Toolkit verwendet:
  IToolkit_TextEditor getDelegateUiImpl();
  String isInputValid();
  boolean isEnabled();
  boolean isRequestFocus();                         // aus IFocusAbleDelegate
  String getPropertyName();
  boolean isCurrentlyInOptionalState();
  void keyReleasedEvent();
  void issueUpdateConclusionAfterContentChange();
  // von der Runtime verwendet:
  void setLabel(String lab);
  void setLabelTooltip(String tooltip);
  void setProperty(String name);
  void setFormat(String frmt);
  void setEnabled(boolean enbld);
  void setDisableFORCE();
  void setOptionalText(String text);
  void setOptional(boolean enbld);
  void load(Object obj);
  IDelegateChange store(Object obj);
  void setValue(T value);
  T getValue();
  void setDelegateDataUxElementHook(IDataUxElementHook hook);
  void setDelegateHook(IDataUxDelegateHook<T> hook);
  void preDelayedAfterFullUiInitialized();
  void gcClear();
  enum Opt {
    STRINGDLGT_FORCE_NUMERICAL_EDITOR(), FOLDED(), WIDE(), ALTER_OR_PICKER(), REFERENCEDLGT_PROVIDE_HINT(),
    DATETIMEDLGT_TIMEONLY(), OPTIONAL(), FORCE_DISABLED(), ISSUE_UPDATE_CONCLUSION()
  }
}

public interface ITableCellStringConverter<T> {
  String convert(T obj);
  boolean isRightAligned();
  String getBgColor(T obj);
  T convertBack(String s);
  default String[] getColorMapOrNull() { return null; }
  default boolean hasDynamicColor() { return false; }
}

public interface ICustomDataUxElement<T> extends IGenSelControlled<T> {
  void addDelegateInfo(String delegateName, String path, String label, String format);
  void setOption(String key, String value);
}

public interface IDataUxElementHook {
  void initialize(ISelectionController selController, List<IDlgt> delegates);
  void forceNotEditable();
  void delegateFormDataChanged(IDlgt sender);
  void boundObjectLoadedOrNull(Object bndObject);
  void gcClear();
}
```

### ObjectFlow (`org.modellwerkstatt.objectflow.runtime`)

```java
public class OFXConclusionInformation {
  public int conclusionHashCode;
  public String buttonTitle;
  public String hotkey;
  public String iconName;
  public boolean hideWhenDisabled;
  public boolean enabled;
}

public interface IOFXSelection<BoundObject> {
  Class getType();
  boolean isSingleObject();
  boolean isEmpty();
  boolean isContainedIn(List<BoundObject> bo);
  BoundObject getObject();              // wirft, wenn nicht genau ein Objekt selektiert ist
  BoundObject getObjectOrNull();
  List<BoundObject> getObjects();
  int size();
  int getIssuer();
}

public class Selection<T> implements IOFXSelection<T> {
  public Selection(Class type, List<T> objects);
  public Selection(Class type, T[] objects);
  public Selection(T object);
  public Selection(Class objectType);   // leere Selektion
  public void setIssuer(int hashCode);
}

public interface IOFXProblem {
  String getSimpleUserText();
  boolean isWarningOnly();
  boolean isFromGuard();
  IOFXSession.IUxEventActionable getResolveActionOrNull();
  // ... weitere Methoden für Protokollierung und Batchjobs
}

// IOFXSession:
interface IUxEventActionable extends IUxEvent {
  void performAction();
  String getLabelText();
  int getTriggerCode();
}

public interface IOFXUserEnvironment extends IM3UserEnvironment {
  String getDeviceName();
  String getDeviceSwName();
  String getDeviceId();
  void adjustDeviceId(String newId);
  long getAppStartupLocalMillis();
  void setAppStartupLocalMillis(long value);
  int getBrandingId();
  void setBrandingId(int id);
  void gcClean();
}

public class UserEnvironmentInformation implements IOFXUserEnvironment {
  public UserEnvironmentInformation();
  public void setDevice(String devName, String devSwName, String devId);
  public void setLang(String val);
  public int getLangIndex();
  public String getLang();
  public void setUserName(String u);
  public String getUserName();
  public void setUserId(int u);
  public int getUserId();
  public void setCompactMode();
  public boolean isCompactMode();
  public void setDynamicStatusInfo(String name);
  public String getDynamicStatusInfo();
}

public class OFXUrlParams {
  public OFXUrlParams();
  public void parse(String path);
  public boolean hasCmdName();
  public String getCmdName();
  public int numParams();
  public String asUrl();
}

// IOFXCmdModule:
class CmdUrlDefaults {
  public String cmdFqName;
  public String desc;
  public String urlAndParamDescription;
  public String url;
  public int minNumParams;
  public int maxNumParams;
  public OFXUrlParams.OFXUrlCmdParams params;
}

// IOFXCoreReporter:
enum MoWarePlatform { MOWARE_H2(), MOWARE_VAADIN(), MOWARE_FX8(), MOWARE_JOB(), MOWARE_TURKU(), MOWARE_API() }

public interface IOFXPrintFactory {
  IPrintingServiceImpl createConfiguredUserPrintService(Object tecHandle, IOFXUserEnvironment userEnvironment);
  void gcClean();
}

public interface IPrintingServiceImpl {
  File renderPrint(String documentFilename, String xsltTemplateName, String xmlGraphData, Object barcode);
  File renderView(String documentFilename, String xsltTemplateName, String xmlGraphData, Object barcode);
  File render(String documentFilename, String xsltTemplateName, String xmlGraphData, Object barcode);
  void view(File pdfFile);
  void print(File pdfFile);
  void openUrl(String url);
  void gcClean();
}
```
