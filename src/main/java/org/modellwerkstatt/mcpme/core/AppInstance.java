package org.modellwerkstatt.mcpme.core;

import org.modellwerkstatt.dataux.runtime.core.ApplicationMDI;
import org.modellwerkstatt.dataux.runtime.core.BasisCmdStart;
import org.modellwerkstatt.dataux.runtime.core.ConclusionEvent;
import org.modellwerkstatt.dataux.runtime.core.ICommandContainer;
import org.modellwerkstatt.dataux.runtime.core.LoginController;
import org.modellwerkstatt.dataux.runtime.genspecification.AbstractAction;
import org.modellwerkstatt.dataux.runtime.genspecification.CmdAction;
import org.modellwerkstatt.dataux.runtime.genspecification.IGenAppUiModule;
import org.modellwerkstatt.dataux.runtime.genspecification.MenuAction;
import org.modellwerkstatt.dataux.runtime.genspecification.Tile;
import org.modellwerkstatt.dataux.runtime.telemetrics.AppJmxRegistration;
import org.modellwerkstatt.dataux.runtime.telemetrics.Dux;
import org.modellwerkstatt.dataux.runtime.toolkit.IToolkit_MainWindow;
import org.modellwerkstatt.mcpme.ui.McpCommandUi;
import org.modellwerkstatt.mcpme.ui.McpEditor;
import org.modellwerkstatt.mcpme.ui.McpTableForm;
import org.modellwerkstatt.objectflow.runtime.IOFXCmdModule;
import org.modellwerkstatt.objectflow.runtime.IOFXCommand;
import org.modellwerkstatt.objectflow.runtime.IOFXCoreReporter;
import org.modellwerkstatt.objectflow.runtime.IOFXProblem;
import org.modellwerkstatt.objectflow.runtime.MoVersion;
import org.modellwerkstatt.objectflow.runtime.OFXConclusionInformation;
import org.modellwerkstatt.objectflow.runtime.UserEnvironmentInformation;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Eine Anwendungsinstanz: eine angemeldete Person mit ihrer ApplicationMDI. Je MCP-Session gibt es
 * genau eine.
 *
 * Die Runtime erwartet, dass alle Aufrufe nacheinander ankommen. Deshalb sind alle öffentlichen
 * Methoden synchronisiert: Je Instanz ist höchstens ein Werkzeugaufruf in Arbeit.
 *
 * Jeder Werkzeugaufruf ist in sich abgeschlossen: Dialoge, die die Runtime dabei öffnet, werden noch
 * im selben Aufruf beantwortet, und ihr Text steht im Ergebnis.
 */
public class AppInstance {

    /** deviceName der User Environment: daran erkennen Protokoll und Anwendung den Agenten. */
    public static final String DEVICE_NAME = "MCP";

    /** Ergebnis eines Werkzeugaufrufs: der Zustand danach und ob die Runtime einen Fehler gemeldet hat. */
    public static final class Outcome {
        public final Map<String, Object> state;
        public final boolean error;

        Outcome(Map<String, Object> state, boolean error) {
            this.state = state;
            this.error = error;
        }
    }

    /** Der Aufruf wurde nicht ausgeführt; die Meldung sagt dem Agenten, warum und was stattdessen geht. */
    public static class Refusal extends RuntimeException {
        public Refusal(String message) {
            super(message);
        }
    }

    /** Die Anwendung hat die Anmeldung abgelehnt; die Meldung ist für die Person bestimmt. */
    public static class LoginRefusedException extends RuntimeException {
        public LoginRefusedException(String message) {
            super(message);
        }
    }

    private static final class DialogResult {
        final List<Map<String, Object>> messages = new ArrayList<>();
        boolean error;
        boolean questionDeclined;
    }

    private final String sessionId;
    private final McpAppFactory factory;
    private final UserEnvironmentInformation userEnv;
    private final McpMainWindow mainWindow;
    private final ApplicationMDI application;
    private volatile long lastUsedMillis;
    private boolean shutDown;

    /**
     * Meldet die Person an und startet die Anwendung für sie.
     *
     * @param harness Name und Version des MCP-Clients, soweit bekannt
     * @throws LoginRefusedException wenn die Anwendung die Anmeldung ablehnt
     */
    public AppInstance(String sessionId, String userName, String harness, McpAppFactory factory,
                       IGenAppUiModule appUiModule, AppJmxRegistration jmxRegistration, String serverName) {
        this.sessionId = sessionId;
        this.factory = factory;
        this.lastUsedMillis = System.currentTimeMillis();

        IOFXCoreReporter.MoWarePlatform platform = factory.getMowarePlatform();
        String connectionInfo = DEVICE_NAME + " " + sessionId;

        userEnv = new UserEnvironmentInformation();
        String deviceId = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        userEnv.setDevice(DEVICE_NAME, harness == null ? "" : harness, deviceId);

        LoginController login = new LoginController(factory, platform, serverName, connectionInfo);
        String refused = login.checkLoginPrepareUserEnv(userName, "", userEnv, appUiModule);
        if (refused != null) {
            throw new LoginRefusedException(refused);
        }

        mainWindow = new McpMainWindow();
        application = new ApplicationMDI(factory, mainWindow, appUiModule, jmxRegistration, platform);
        application.initializeApplication(null, serverName, userEnv, connectionInfo, "");
        DialogResult start = runPendingDialogs(false);

        if (mainWindow.isClosed()) {
            // Die Runtime hat den Start mit einem Fehler abgebrochen und sich selbst heruntergefahren.
            shutDown = true;
            throw new IllegalStateException("Application start failed: " + start.messages);
        }
        Dux.hl("mcpme: application started for user '" + userEnv.getUserName() + "' in session " + sessionId
                + " (" + harness + ")");
    }

    public String getSessionId() {
        return sessionId;
    }

    public long getLastUsedMillis() {
        return lastUsedMillis;
    }

    // ---- Werkzeuge: lesen ------------------------------------------------------------------------

    /** Startseite: was sich jetzt starten lässt, und was offen ist. Ändert nichts. */
    public synchronized Outcome overview() {
        touch();
        // Beschriftungen der Startseite und Bedienbarkeit neu berechnen lassen, wie beim Anzeigen der Startseite.
        application.recalcAndShowTiles(true);
        application.reevalEnabledInMenusAndTiles();
        DialogResult dialogs = runPendingDialogs(false);

        Map<String, Object> app = new LinkedHashMap<>();
        app.put("name", mainWindow.getAppName());
        app.put("version", mainWindow.getAppVersion());
        if (!mainWindow.getDynamicTitle().isEmpty()) {
            app.put("status", mainWindow.getDynamicTitle());
        }
        app.put("user", userEnv.getUserName());

        List<Map<String, Object>> commands = new ArrayList<>();
        for (Entry entry : startEntries()) {
            commands.add(entry.describe());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("application", app);
        if (mainWindow.isBlockedByActiveCommand()) {
            result.put("note", "The active command must be completed or cancelled before another one can be started from here.");
        }
        result.put("commands", commands);
        result.put("openCommands", openCommands());
        if (!dialogs.messages.isEmpty()) {
            result.put("messages", dialogs.messages);
        }
        return new Outcome(result, dialogs.error);
    }

    /** Zustand des aktiven Commands. Ändert nichts. */
    public synchronized Outcome state() {
        touch();
        return outcome(new DialogResult());
    }

    // ---- Werkzeuge: Commands starten -------------------------------------------------------------

    /**
     * Startet einen Command aus dem Kontext: Der Eintrag wird über Kennung, Beschriftung oder FQ-Namen
     * unter dem gesucht, was die aktuelle Seite und die Startseite gerade anbieten. Die Parameter
     * ergänzt die Runtime aus der Selektion beziehungsweise aus dem Modell.
     */
    public synchronized Outcome startFromContext(String entryId, String label, String commandFqName,
                                                 String tableId, List<Integer> rows) {
        touch();
        if (tableId != null || rows != null) {
            // Erst auswählen, dann starten: Der Command nimmt seine Parameter aus der Auswahl.
            if (tableId == null || rows == null) {
                throw new Refusal("Give 'table' and 'rows' together.");
            }
            select(activePage(), tableId, rows);
            runPendingDialogs(false);
        }
        Entry entry = findEntry(entryId, label, commandFqName);

        boolean fromStartPage = !entry.origin.startsWith("page") && !entry.origin.startsWith("table");
        if (fromStartPage && mainWindow.getOpenCommandUis().size() >= factory.getMaxOpenCommands()) {
            throw new Refusal("There are already " + mainWindow.getOpenCommandUis().size()
                    + " open commands, which is the limit. Complete or cancel one first: " + openCommands());
        }
        if (!entry.isStartable()) {
            throw new Refusal("'" + entry.label() + "' (" + entry.id + ") cannot be started right now. "
                    + "The person's permissions, the state of the data or the current selection do not allow it.");
        }

        McpCommandUi before = mainWindow.getActiveCommandUi();
        entry.action.startCommand();
        return outcomeAfterStart(before, entry.label());
    }

    /** Die Beschreibung eines Commands aus seinem Command-Modul: Parameter, user doc, tec doc. */
    public synchronized Outcome describeCommand(String commandFqName) {
        touch();
        String description = descriptionOf(commandFqName.trim());
        if (description == null) {
            throw new Refusal("Unknown command '" + commandFqName + "'. Use the fully qualified name as listed by "
                    + "get_overview or in the state.");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("command", commandFqName.trim());
        if (description.isBlank()) {
            result.put("note", "The application provides no description for this command.");
        } else {
            result.put("description", description.strip());
        }
        return new Outcome(result, false);
    }

    /** Der Beschreibungstext, leer, wenn das Modul keinen liefert, oder null bei unbekanntem Command. */
    private String descriptionOf(String commandFqName) {
        try {
            IOFXCmdModule module = factory.getModuleByInstanceName(MoVersion.getCmdModuleInstanceName(commandFqName));
            String description = module.getCommandDescription(commandFqName);
            return description == null ? "" : description;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Der Parameterteil der Beschreibung: alles bis zur ersten Leerzeile. Leer, wenn es keinen gibt. */
    private String parametersOf(String commandFqName) {
        String description = descriptionOf(commandFqName);
        if (description == null || !description.startsWith("Parameter:")) {
            return "";
        }
        int end = description.indexOf("\n\n");
        return (end < 0 ? description : description.substring(0, end)).strip();
    }

    /**
     * Startet einen Command direkt mit seiner vollständigen Parameterliste. Der Server reicht die Liste
     * unverändert weiter; ob die Person den Command so starten darf, entscheidet die Runtime.
     */
    public synchronized Outcome startDirect(String commandFqName, List<Object> params) {
        touch();
        if (mainWindow.isBlockedByActiveCommand()) {
            throw new Refusal("The active command must be completed or cancelled before another one can be started.");
        }
        if (mainWindow.getOpenCommandUis().size() >= factory.getMaxOpenCommands()) {
            throw new Refusal("There are already " + mainWindow.getOpenCommandUis().size()
                    + " open commands, which is the limit. Complete or cancel one first: " + openCommands());
        }

        IOFXCmdModule module;
        boolean ownSession;
        try {
            module = factory.getModuleByInstanceName(MoVersion.getCmdModuleInstanceName(commandFqName));
            ownSession = module.isCommandType(commandFqName, IOFXCommand.OFXCmdTyp.SEARCH_CMD,
                    IOFXCommand.OFXCmdTyp.GRAPH_OWNER_CMD, IOFXCommand.OFXCmdTyp.GRAPH_OWNER_CMD_MODAL);
        } catch (RuntimeException e) {
            throw new Refusal("Unknown command '" + commandFqName + "'. Use the fully qualified name as listed by "
                    + "get_overview or in the state.");
        }
        if (!ownSession) {
            throw new Refusal("'" + commandFqName + "' works on the data of the command it is started from "
                    + "and cannot be started directly. Start it from the page that offers it.");
        }

        Object[] args = new Object[params.size()];
        for (int i = 0; i < args.length; i++) {
            Object value = params.get(i);
            // Der generierte Code liest ganze Zahlen als Integer aus.
            args[i] = value instanceof Number number ? Integer.valueOf(number.intValue()) : value;
        }

        // Die Runtime kennt die Parameter eines Commands nicht als Beschreibung. Eine unpassende Liste zeigt
        // sich als falscher Typ oder, bei falscher Länge oder fehlendem Wert, als "nicht ausführbar" - genau
        // wie eine fehlende Berechtigung. Der Server kann die Fälle nicht unterscheiden.
        String mismatch = "'" + commandFqName + "' cannot be started with the parameter list " + params + ". Either the "
                + "list does not match the declared parameters (length, order, type, or null where a value is "
                + "needed), or the person's permissions or the state of the data do not allow it. "
                + "Call describe_command for the full description."
                + (parametersOf(commandFqName).isEmpty() ? "" : "\n\n" + parametersOf(commandFqName));
        IOFXCmdModule.CommandPermission permission;
        try {
            permission = module.getCommandPermission(IOFXCmdModule.CmdExecStrategy.SINGLE, commandFqName, args,
                    application.getMainWindowEvalSession());
        } catch (ClassCastException e) {
            throw new Refusal(mismatch);
        }
        if (permission == IOFXCmdModule.CommandPermission.DISABLED) {
            throw new Refusal(mismatch);
        }

        McpCommandUi before = mainWindow.getActiveCommandUi();
        application.receiveAndProcess(new BasisCmdStart(commandFqName, args));
        return outcomeAfterStart(before, commandFqName);
    }

    private Outcome outcomeAfterStart(McpCommandUi before, String what) {
        DialogResult dialogs = runPendingDialogs(false);
        if (mainWindow.getActiveCommandUi() == before && dialogs.messages.isEmpty() && !mainWindow.hasDocuments()) {
            dialogs.messages.add(message("info", "'" + what + "' ran without opening a page."));
        }
        return outcome(dialogs);
    }

    // ---- Werkzeuge: im aktiven Command arbeiten --------------------------------------------------

    /** Trägt Werte in Formularfelder ein und aktualisiert die Seite, wenn eines der Felder das verlangt. */
    public synchronized Outcome setFields(Map<String, Object> values) {
        touch();
        PageView page = activePage();
        McpEditor refreshing = applyFields(page, values);

        DialogResult dialogs = new DialogResult();
        if (refreshing != null && refreshing.getDelegate() != null) {
            refreshing.getDelegate().issueUpdateConclusionAfterContentChange();
            dialogs = runPendingDialogs(false);
            dialogs.messages.add(0, message("info", "The page was refreshed after the change."));
        }
        return outcomeOfChange(dialogs);
    }

    /** Zeilen einer Tabelle des aktiven Commands. Ändert nichts. */
    public synchronized Outcome rows(String tableId, Integer from, Integer to, String search) {
        touch();
        return new Outcome(activePage().rows(tableId, from, to, search, factory.getMaxRowsPerTable(),
                factory.isShowFolded()), false);
    }

    /** Setzt die Auswahl in einer Tabelle; abhängige Tabellen und Formulare laden neu. */
    public synchronized Outcome selectRows(String tableId, List<Integer> rows) {
        touch();
        select(activePage(), tableId, rows);
        return outcome(runPendingDialogs(false));
    }

    private void select(PageView page, String tableId, List<Integer> rows) {
        McpTableForm<?> table = page.table(tableId);
        if (table == null) {
            throw new Refusal("The current page has no table '" + tableId + "'. Tables: " + page.tableIds());
        }
        if (table.isEditPreview() || table.getController() == null) {
            throw new Refusal("The table '" + tableId + "' only displays data; its rows cannot be selected.");
        }
        for (int position : rows) {
            if (position < 0 || position >= table.getRowCount()) {
                throw new Refusal("The table '" + tableId + "' has no row at position " + position
                        + "; it has " + table.getRowCount() + " rows, positions start at 0.");
            }
        }
        table.pushSelectionFromAgent(rows);
    }

    /** Macht einen anderen der offenen Commands aktiv. */
    public synchronized Outcome switchCommand(String handle) {
        touch();
        if (mainWindow.isBlockedByActiveCommand()) {
            throw new Refusal("The active command must be completed or cancelled before another one can be made active.");
        }
        List<McpCommandUi> open = mainWindow.getOpenCommandUis();
        for (int index = 0; index < open.size(); index++) {
            McpCommandUi ui = open.get(index);
            if (ui.getHandle().equalsIgnoreCase(handle.trim())) {
                if (ui != mainWindow.getActiveTabUi()) {
                    mainWindow.activateTab(ui);
                    // Die Runtime führt die offenen Commands in derselben Reihenfolge.
                    application.onTabChangeEvent(index);
                }
                return outcome(runPendingDialogs(false));
            }
        }
        List<String> handles = new ArrayList<>();
        for (McpCommandUi ui : open) {
            handles.add(ui.getHandle());
        }
        throw new Refusal("There is no open command '" + handle + "'. Open commands: " + handles);
    }

    /** Löst eine Conclusion Action der aktuellen Seite aus; Feldwerte werden vorher eingetragen. */
    public synchronized Outcome conclude(int actionId, Map<String, Object> values) {
        touch();
        PageView page = activePage();
        OFXConclusionInformation action = page.conclusionAction(actionId);
        if (action == null) {
            throw new Refusal("The current page has no conclusion action " + actionId
                    + ". Available: " + describeActions(page));
        }
        if (!action.enabled) {
            throw new Refusal("The conclusion action '" + action.buttonTitle + "' is not enabled right now.");
        }
        if (values != null && !values.isEmpty()) {
            applyFields(page, values);
        }

        page.getUi().getContainer().receiveAndProcess(new ConclusionEvent(actionId, action.buttonTitle));
        return outcomeOfChange(runPendingDialogs(false));
    }

    /** Bricht den aktiven Command ab. Ungespeicherte Änderungen gehen nur mit discardChanges verloren. */
    public synchronized Outcome cancel(boolean discardChanges) {
        touch();
        PageView page = activePage();
        if (!page.isCancelable()) {
            throw new Refusal("This command cannot be cancelled; it has to be completed with one of its "
                    + "conclusion actions: " + describeActions(page));
        }
        String title = page.getUi().getWindowTitle();

        page.getUi().getContainer().receiveAndProcess(new ConclusionEvent(0, "cancel"));
        DialogResult dialogs = runPendingDialogs(discardChanges);

        if (dialogs.questionDeclined) {
            throw new Refusal("'" + title + "' has unsaved changes. Call cancel_command with discard_changes "
                    + "set to true to discard them, or save with a conclusion action.");
        }
        if (page.getUi().isClosed()) {
            dialogs.messages.add(0, message("info", "'" + title + "' was cancelled."));
        }
        return outcome(dialogs);
    }

    // ---- Ende ------------------------------------------------------------------------------------

    /** Beendet alle offenen Commands ohne Rückfrage und fährt die Instanz herunter. */
    public synchronized void shutdown() {
        if (shutDown) {
            return;
        }
        shutDown = true;
        try {
            if (!application.inShutdownMode()) {
                application.internal_immediatelyShutdown();
            }
        } finally {
            userEnv.gcClean();
        }
        Dux.hl("mcpme: application instance of session " + sessionId + " shut down");
    }

    // ---- Zustand ---------------------------------------------------------------------------------

    private Outcome outcome(DialogResult dialogs) {
        Map<String, Object> state = new LinkedHashMap<>();
        if (!dialogs.messages.isEmpty()) {
            state.put("messages", dialogs.messages);
        }
        List<Map<String, Object>> documents = mainWindow.takeDocuments();
        if (!documents.isEmpty()) {
            // Was die Anwendung in diesem Aufruf zum Öffnen übergeben hat: PDF-Dokumente und Links.
            state.put("documents", documents);
        }
        state.put("openCommands", openCommands());

        McpCommandUi active = mainWindow.getActiveCommandUi();
        if (active == null) {
            state.put("note", "No command is open. Call get_overview to see what can be started.");
        } else {
            new PageView(active).describeInto(state, factory.getMaxRowsPerTable(), factory.isShowFolded(),
                    mainWindow.isBlockedByActiveCommand());
        }
        return new Outcome(state, dialogs.error);
    }

    /**
     * Ergebnis eines Aufrufs, der etwas an der Seite bewirken sollte. Zeigt die Seite danach eine
     * Fehlermeldung, hat die Runtime die Eingabe nicht angenommen; das Ergebnis ist dann ein Fehler.
     */
    private Outcome outcomeOfChange(DialogResult dialogs) {
        McpCommandUi active = mainWindow.getActiveCommandUi();
        if (active != null && new PageView(active).hasErrorMessage()) {
            dialogs.error = true;
        }
        return outcome(dialogs);
    }

    private List<Map<String, Object>> openCommands() {
        List<Map<String, Object>> list = new ArrayList<>();
        McpCommandUi active = mainWindow.getActiveCommandUi();
        List<McpCommandUi> all = new ArrayList<>(mainWindow.getOpenCommandUis());
        if (mainWindow.getPromptUi() != null) {
            all.add(mainWindow.getPromptUi());
        }
        for (McpCommandUi ui : all) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("handle", ui.getHandle());
            entry.put("title", ui.getWindowTitle());
            entry.put("unsavedChanges", hasUnsavedChanges(ui));
            entry.put("active", ui == active);
            list.add(entry);
        }
        return list;
    }

    private static boolean hasUnsavedChanges(McpCommandUi ui) {
        try {
            ICommandContainer container = ui.getContainer();
            // Nur ein Command, der seine Session selbst speichert, kann ungespeicherte Änderungen haben.
            return container != null && container.hasRwSessionToCommit() && container.getSession() != null
                    && container.getSession().isDirty();
        } catch (RuntimeException e) {
            return false;
        }
    }

    private PageView activePage() {
        McpCommandUi active = mainWindow.getActiveCommandUi();
        if (active == null) {
            throw new Refusal("No command is open. Call get_overview to see what can be started.");
        }
        return new PageView(active);
    }

    private static String describeActions(PageView page) {
        List<String> actions = new ArrayList<>();
        List<OFXConclusionInformation> all = page.getUi().getConclusions();
        for (int i = 1; i < all.size(); i++) {
            OFXConclusionInformation info = all.get(i);
            if (!PageView.HOTKEY_UPDATE.equals(info.hotkey)) {
                actions.add(info.conclusionHashCode + " '" + info.buttonTitle + "'" + (info.enabled ? "" : " (not enabled)"));
            }
        }
        return actions.isEmpty() ? "none" : String.join(", ", actions);
    }

    // ---- Felder ----------------------------------------------------------------------------------

    /**
     * Prüft alle Werte und trägt sie erst dann ein, wenn alle annehmbar sind.
     *
     * @return ein geändertes Feld, das die Seite aktualisiert, oder null
     */
    private McpEditor applyFields(PageView page, Map<String, Object> values) {
        Map<McpEditor, String> accepted = new LinkedHashMap<>();
        for (Map.Entry<String, Object> value : values.entrySet()) {
            McpEditor editor = page.field(value.getKey());
            if (editor == null) {
                throw new Refusal("The current page has no field '" + value.getKey() + "'. Fields: " + page.fieldIds());
            }
            if (!editor.isEnabled() || editor.getKind() == McpEditor.Kind.IMAGE || editor.getKind() == McpEditor.Kind.UPLOAD) {
                throw new Refusal("The field '" + value.getKey() + "' is not editable.");
            }
            String text = value.getValue() == null ? "" : String.valueOf(value.getValue());
            String problem = editor.checkValueFromAgent(text);
            if (problem != null) {
                throw new Refusal("Field '" + value.getKey() + "': " + problem);
            }
            accepted.put(editor, text);
        }

        McpEditor refreshing = null;
        for (Map.Entry<McpEditor, String> value : accepted.entrySet()) {
            McpEditor editor = value.getKey();
            String before = editor.valueForAgent();
            editor.setValueFromAgent(value.getValue());
            if (editor.isIssuesUpdate() && !value.getValue().equals(before)) {
                refreshing = editor;
            }
        }
        return refreshing;
    }

    // ---- Einträge --------------------------------------------------------------------------------

    /** Die Einträge der Startseite: hervorgehobene Commands und die drei Menüs. */
    private List<Entry> startEntries() {
        List<Entry> entries = new ArrayList<>();
        int position = 0;
        for (Tile tile : mainWindow.getTiles()) {
            position++;
            entries.add(new Entry("start-" + position, tile.getCmdAction(), "start page", null));
        }
        collect(mainWindow.getStartMenu(), "menu", "start menu", null, entries, new int[1]);
        collect(mainWindow.getExtraMenu(), "extras", "extras menu", null, entries, new int[1]);
        collect(mainWindow.getHelpMenu(), "help", "help menu", null, entries, new int[1]);
        return entries;
    }

    private void collect(MenuAction menu, String idPrefix, String origin, String group, List<Entry> into, int[] position) {
        if (menu == null) {
            return;
        }
        for (AbstractAction item : menu.getAllItems()) {
            if (item instanceof CmdAction action) {
                position[0]++;
                into.add(new Entry(idPrefix + "-" + position[0], action, origin, group));
            } else if (item instanceof MenuAction subMenu) {
                String label = subMenu.labelText == null || subMenu.labelText.isEmpty() ? null : subMenu.labelText;
                String subGroup = group == null ? label : (label == null ? group : group + " / " + label);
                collect(subMenu, idPrefix, origin, subGroup, into, position);
            }
            // alles andere ist ein Trennstrich
        }
    }

    private Entry findEntry(String entryId, String label, String commandFqName) {
        List<Entry> available = new ArrayList<>();
        McpCommandUi active = mainWindow.getActiveCommandUi();
        if (active != null) {
            available.addAll(new PageView(active).getEntries());
        }
        if (!mainWindow.isBlockedByActiveCommand()) {
            available.addAll(startEntries());
        }

        if (entryId != null) {
            for (Entry entry : available) {
                if (entry.id.equalsIgnoreCase(entryId.trim())) {
                    if (label != null && !entry.label().equalsIgnoreCase(label.trim())) {
                        throw new Refusal("Entry " + entry.id + " is now '" + entry.label() + "', not '" + label
                                + "'. Nothing was started.");
                    }
                    return entry;
                }
            }
            throw new Refusal("There is no entry '" + entryId + "' right now. " + availableHint(available));
        }

        List<Entry> matches = new ArrayList<>();
        for (Entry entry : available) {
            boolean labelMatches = label == null || entry.label().equalsIgnoreCase(label.trim());
            boolean commandMatches = commandFqName == null || entry.command().equals(commandFqName.trim());
            if (labelMatches && commandMatches) {
                matches.add(entry);
            }
        }
        String wanted = label != null ? "'" + label + "'" : "'" + commandFqName + "'";
        if (matches.isEmpty()) {
            throw new Refusal("Nothing named " + wanted + " can be started from here right now. " + availableHint(available));
        }
        if (matches.size() > 1) {
            List<String> candidates = new ArrayList<>();
            for (Entry entry : matches) {
                candidates.add(entry.id + " '" + entry.label() + "' (" + entry.origin
                        + (entry.group == null ? "" : ", " + entry.group) + ")");
            }
            throw new Refusal(wanted + " matches " + matches.size() + " entries: " + String.join("; ", candidates)
                    + ". They start the same command with different parameters; name the entry.");
        }
        return matches.get(0);
    }

    private String availableHint(List<Entry> available) {
        if (mainWindow.isBlockedByActiveCommand()) {
            return "The active command must be completed or cancelled before anything can be started from the start page.";
        }
        return available.isEmpty() ? "Nothing can be started." : "Call get_overview or get_state for the entries.";
    }

    // ---- Dialoge ---------------------------------------------------------------------------------

    /**
     * Führt die Fortsetzungen der Dialoge aus, die die Runtime im laufenden Aufruf geöffnet hat, und
     * sammelt deren Texte. Das geschieht erst hier, wenn der Aufruf an die Runtime zurückgekehrt ist.
     * Eine Fortsetzung kann weitere Dialoge öffnen; die Schleife läuft, bis keiner mehr aussteht.
     *
     * @param discardChanges Antwort auf die Rückfrage der Runtime, ob Änderungen verworfen werden sollen
     */
    private DialogResult runPendingDialogs(boolean discardChanges) {
        DialogResult result = new DialogResult();
        for (int round = 0; round < 20; round++) {
            List<McpMainWindow.PendingDialog> dialogs = mainWindow.takePendingDialogs();
            if (dialogs.isEmpty()) {
                break;
            }
            for (McpMainWindow.PendingDialog dialog : dialogs) {
                boolean confirm = true;
                if (dialog.problems != null) {
                    for (IOFXProblem problem : dialog.problems) {
                        result.messages.add(message(problem.isWarningOnly() ? "hint" : "error", problem.getSimpleUserText()));
                        result.error |= !problem.isWarningOnly();
                    }
                } else if (dialog.type == IToolkit_MainWindow.DlgType.QUESTIONCLOSE_SMALL) {
                    confirm = discardChanges;
                    result.questionDeclined |= !discardChanges;
                } else {
                    boolean isError = dialog.type == IToolkit_MainWindow.DlgType.ERROR_SMALL
                            || dialog.type == IToolkit_MainWindow.DlgType.ERROR_LARGE;
                    result.messages.add(message(isError ? "error" : "info", dialog.text));
                    result.error |= isError;
                }
                if (dialog.callback != null) {
                    dialog.callback.run(confirm);
                }
            }
        }
        for (String toast : mainWindow.takeToasts()) {
            result.messages.add(message("info", toast));
        }
        return result;
    }

    private static Map<String, Object> message(String level, String text) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("level", level);
        message.put("text", text == null ? "" : text);
        return message;
    }

    private void touch() {
        lastUsedMillis = System.currentTimeMillis();
    }
}
