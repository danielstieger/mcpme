package org.modellwerkstatt.mcpme.core;

import org.modellwerkstatt.dataux.runtime.core.GlobalCmdTermEvent;
import org.modellwerkstatt.dataux.runtime.core.IApplication;
import org.modellwerkstatt.dataux.runtime.core.ICommandContainer;
import org.modellwerkstatt.dataux.runtime.core.UxEvent;
import org.modellwerkstatt.dataux.runtime.genspecification.MenuAction;
import org.modellwerkstatt.dataux.runtime.genspecification.Tile;
import org.modellwerkstatt.dataux.runtime.toolkit.IToolkit_CommandContainerUi;
import org.modellwerkstatt.dataux.runtime.toolkit.IToolkit_MainWindow;
import org.modellwerkstatt.mcpme.ui.McpCommandUi;
import org.modellwerkstatt.objectflow.runtime.IOFXProblem;

import java.util.ArrayList;
import java.util.List;

/**
 * Hauptfenster einer Anwendungsinstanz. Es stellt nichts dar, sondern hält, was die Runtime ihm
 * übergibt, als Daten: Menüs, Tiles, offene Command-Fenster und die Meldungen des laufenden Aufrufs.
 * Die Werkzeuge lesen daraus den Zustand für den Agenten.
 *
 * Alle Aufrufe kommen aus dem Thread des jeweiligen Werkzeugaufrufs, unter der Sperre der
 * Anwendungsinstanz (siehe {@link AppInstance}).
 */
public class McpMainWindow implements IToolkit_MainWindow {

    /** Ein Dialog der Runtime, dessen Fortsetzung noch aussteht. */
    public static final class PendingDialog {
        public final DlgType type;
        public final String text;
        public final List<IOFXProblem> problems;
        public final IApplication.DlgRunnable callback;

        PendingDialog(DlgType type, String text, List<IOFXProblem> problems, IApplication.DlgRunnable callback) {
            this.type = type;
            this.text = text;
            this.problems = problems;
            this.callback = callback;
        }
    }

    private MenuAction startMenu;
    private MenuAction extraMenu;
    private MenuAction helpMenu;
    private List<Tile> tiles = new ArrayList<>();

    private String appName = "";
    private String appVersion = "";
    private String dynamicTitle = "";
    private String statusInformation = "";

    private final List<McpCommandUi> openCommandUis = new ArrayList<>();
    private McpCommandUi activeCommandUi;
    private McpCommandUi promptUi;
    private boolean modalLock;
    private int commandCounter;

    private final List<PendingDialog> pendingDialogs = new ArrayList<>();
    private final List<String> toasts = new ArrayList<>();
    private final List<java.util.Map<String, Object>> documents = new ArrayList<>();
    private boolean closed;

    // ---- Zugriff für die Werkzeuge ---------------------------------------------------------------

    public MenuAction getStartMenu() {
        return startMenu;
    }

    public MenuAction getExtraMenu() {
        return extraMenu;
    }

    public MenuAction getHelpMenu() {
        return helpMenu;
    }

    public List<Tile> getTiles() {
        return tiles;
    }

    public String getAppName() {
        return appName;
    }

    public String getAppVersion() {
        return appVersion;
    }

    public String getDynamicTitle() {
        return dynamicTitle;
    }

    public String getStatusInformation() {
        return statusInformation;
    }

    /** Die nebeneinander offenen Commands, in der Reihenfolge, in der die Runtime sie führt. */
    public List<McpCommandUi> getOpenCommandUis() {
        return openCommandUis;
    }

    /** Der Command, der über seinem Ausgangs-Command liegt und zuerst abgeschlossen werden muss, oder null. */
    public McpCommandUi getPromptUi() {
        return promptUi;
    }

    /** Der Command, auf den die Werkzeuge wirken, oder null, wenn keiner offen ist. */
    public McpCommandUi getActiveCommandUi() {
        return promptUi != null ? promptUi : activeCommandUi;
    }

    /** Der aktive unter den nebeneinander offenen Commands, unabhängig von einem Prompt. */
    public McpCommandUi getActiveTabUi() {
        return activeCommandUi;
    }

    /** Solange ein Command die übrigen blockiert, lässt sich von der Startseite nichts starten. */
    public boolean isBlockedByActiveCommand() {
        return promptUi != null || modalLock;
    }

    /** Der Agent hat einen anderen der nebeneinander offenen Commands aktiviert. */
    public void activateTab(McpCommandUi ui) {
        activeCommandUi = ui;
    }

    public String nextCommandHandle() {
        commandCounter++;
        return "c" + commandCounter;
    }

    public void promptOpened(McpCommandUi ui) {
        promptUi = ui;
    }

    public void promptClosed(McpCommandUi ui) {
        if (promptUi == ui) {
            promptUi = null;
        }
    }

    public boolean isClosed() {
        return closed;
    }

    /** Liefert die seit dem letzten Abholen angefallenen Dialoge und leert die Liste. */
    public List<PendingDialog> takePendingDialogs() {
        List<PendingDialog> result = new ArrayList<>(pendingDialogs);
        pendingDialogs.clear();
        return result;
    }

    /** Ein Dokument oder Link, den die Anwendung im laufenden Aufruf zum Öffnen übergeben hat. */
    public void addDocument(String kind, String name, String url) {
        java.util.Map<String, Object> document = new java.util.LinkedHashMap<>();
        document.put("kind", kind);
        if (name != null) {
            document.put("name", name);
        }
        document.put("url", url);
        documents.add(document);
    }

    public boolean hasDocuments() {
        return !documents.isEmpty();
    }

    /** Liefert die seit dem letzten Abholen angefallenen Dokumente und Links und leert die Liste. */
    public List<java.util.Map<String, Object>> takeDocuments() {
        List<java.util.Map<String, Object>> result = new ArrayList<>(documents);
        documents.clear();
        return result;
    }

    /** Liefert die seit dem letzten Abholen angefallenen Kurzmeldungen und leert die Liste. */
    public List<String> takeToasts() {
        List<String> result = new ArrayList<>(toasts);
        toasts.clear();
        return result;
    }

    // ---- IToolkit_MainWindow: Menü, Tiles, Status ------------------------------------------------

    @Override
    public void setMenuAndInit(int langIndex, MenuAction startMenu, MenuAction extraMenu, MenuAction helpMenu) {
        this.startMenu = startMenu;
        this.extraMenu = extraMenu;
        this.helpMenu = helpMenu;
    }

    @Override
    public void showTiles(List<Tile> tiles, boolean resetUrl) {
        // Die Runtime liefert bei jedem Aufruf dieselben Tiles mit aktuellen Beschriftungen.
        this.tiles = new ArrayList<>(tiles);
    }

    @Override
    public void setAppInfo(String appName, String version, String dynTitle, int brandIng) {
        this.appName = appName;
        this.appVersion = version;
        this.dynamicTitle = dynTitle == null ? "" : dynTitle;
    }

    @Override
    public void addStatusInformation(String info) {
        this.statusInformation = info;
    }

    @Override
    public void ensureHotkeyAvailable(List<String> hotkeys) {
        // Ein Agent hat keine Tasten.
    }

    // ---- IToolkit_MainWindow: Command-Fenster ----------------------------------------------------

    @Override
    public void addTab(IToolkit_CommandContainerUi ui) {
        openCommandUis.add((McpCommandUi) ui);
        activeCommandUi = (McpCommandUi) ui;
    }

    @Override
    public void focusTab(IToolkit_CommandContainerUi ui) {
        activeCommandUi = (McpCommandUi) ui;
    }

    @Override
    public void ensureTabClosed(IToolkit_CommandContainerUi ui) {
        openCommandUis.remove(ui);
        if (activeCommandUi == ui) {
            // Wer als Nächstes aktiv wird, sagt die Runtime gleich danach über focusTab.
            activeCommandUi = null;
        }
    }

    @Override
    public void setCurrentTabModal(boolean lock) {
        modalLock = lock;
    }

    // ---- IToolkit_MainWindow: Dialoge und Meldungen ----------------------------------------------

    @Override
    public void showDialog(DlgType type, String text, IApplication.DlgRunnable callback) {
        // Nicht blockierend: Die Fortsetzung läuft, wenn der Aufruf der Runtime zurückgekehrt ist.
        pendingDialogs.add(new PendingDialog(type, text, null, callback));
    }

    @Override
    public void showProblemsDialog(List<IOFXProblem> problems, IApplication.DlgRunnable callback) {
        pendingDialogs.add(new PendingDialog(null, null, new ArrayList<>(problems), callback));
    }

    @Override
    public void setToastMessage(String info) {
        toasts.add(info);
    }

    @Override
    public void showGraphDebugger(List<Object> graph, String controllerInfo) {
        // Entwicklerwerkzeug der Oberflächen; für den Agenten ohne Entsprechung.
    }

    // ---- IToolkit_MainWindow: Ende und Betrieb ---------------------------------------------------

    @Override
    public void closeApplicationAndExit() {
        closed = true;
    }

    @Override
    public void parDeploymentForwardNow() {
        closed = true;
    }

    @Override
    public void distributeTermEventOnOtherInstance(GlobalCmdTermEvent evnt) {
        // Jede MCP-Session hat genau eine Anwendungsinstanz.
    }

    // ---- IToolkit_MainWindow: Threads ------------------------------------------------------------

    @Override
    public void execEventInBackground(ICommandContainer sender, Runnable runable) {
        throw new IllegalStateException("mcpme runs without background thread (useBackgroundThread() is false).");
    }

    @Override
    public void execEventInForeground(ICommandContainer sender, UxEvent event) {
        throw new IllegalStateException("mcpme runs without background thread (useBackgroundThread() is false).");
    }

    @Override
    public boolean inUiThread() {
        return true;
    }
}
