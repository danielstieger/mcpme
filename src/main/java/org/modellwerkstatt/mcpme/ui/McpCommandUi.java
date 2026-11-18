package org.modellwerkstatt.mcpme.ui;

import org.modellwerkstatt.dataux.runtime.core.ICommandContainer;
import org.modellwerkstatt.dataux.runtime.toolkit.IToolkit_CommandContainerUi;
import org.modellwerkstatt.dataux.runtime.toolkit.IToolkit_Form;
import org.modellwerkstatt.mcpme.core.McpMainWindow;
import org.modellwerkstatt.objectflow.runtime.OFXConclusionInformation;

import java.util.ArrayList;
import java.util.List;

/**
 * Das Fenster eines offenen Commands, als Daten: Titel, aktuelle Seite, Conclusion Actions, Hinweise.
 *
 * Die Runtime kennt zwei Arten: ein Fenster neben anderen (Tab) und eines, das über seinem
 * Ausgangs-Command liegt und zuerst abgeschlossen werden muss (Prompt).
 */
public class McpCommandUi implements IToolkit_CommandContainerUi {

    private final McpMainWindow mainWindow;
    private final boolean tabWindow;
    private final boolean modal;
    private final String handle;

    private ICommandContainer container;
    private String windowTitle = "";
    private McpForm<?> content;
    private String notification = "";
    private List<OFXConclusionInformation> conclusions = new ArrayList<>();
    private boolean closed;

    public McpCommandUi(McpMainWindow mainWindow, boolean tabWindow, boolean modal) {
        this.mainWindow = mainWindow;
        this.tabWindow = tabWindow;
        this.modal = modal;
        this.handle = mainWindow.nextCommandHandle();
    }

    // ---- Zugriff für die Werkzeuge ---------------------------------------------------------------

    /** Kennung des offenen Commands für den Agenten. */
    public String getHandle() {
        return handle;
    }

    public ICommandContainer getContainer() {
        return container;
    }

    public String getWindowTitle() {
        return windowTitle;
    }

    /** Die Wurzelform der aktuellen Seite. */
    public McpForm<?> getContent() {
        return content;
    }

    /** Hinweis auf eine Sperre durch andere, oder leer. */
    public String getNotification() {
        return notification;
    }

    /** Alle Conclusions der Runtime; Index 0 ist das Abbrechen. */
    public List<OFXConclusionInformation> getConclusions() {
        return conclusions;
    }

    public boolean isClosed() {
        return closed;
    }

    // ---- IToolkit_CommandContainerUi -------------------------------------------------------------

    @Override
    public void setCommandContainer(ICommandContainer container) {
        this.container = container;
    }

    @Override
    public void show(String windowTitle, IToolkit_Form content) {
        this.windowTitle = windowTitle == null ? "" : windowTitle;
        this.content = (McpForm<?>) content;
        if (!tabWindow) {
            // Ein Prompt meldet sich selbst an; Tabs trägt die Runtime über addTab ein.
            mainWindow.promptOpened(this);
        }
    }

    @Override
    public void adjustWindowTitle(String title) {
        this.windowTitle = title == null ? "" : title;
    }

    @Override
    public void setContent(IToolkit_Form content) {
        this.content = (McpForm<?>) content;
    }

    @Override
    public void setNotification(String notificat) {
        this.notification = notificat == null ? "" : notificat;
    }

    @Override
    public void setColor(String color) {
        // Farbe ist Darstellung.
    }

    @Override
    public void setConclusions(List<OFXConclusionInformation> conclusionInformations, List<String> additionalHotkeys) {
        this.conclusions = new ArrayList<>(conclusionInformations);
    }

    @Override
    public void reevalConclusions(List<OFXConclusionInformation> conclusionInformations) {
        // Dieselbe Liste in derselben Reihenfolge, jetzt mit gültigem enabled.
        this.conclusions = new ArrayList<>(conclusionInformations);
    }

    @Override
    public void delayedRequestFocus() {
        if (content != null) {
            content.myRequestFocus();
        }
    }

    @Override
    public void delayedAfterFullUiInitialized() {
        if (content != null) {
            content.afterFullUiInitialized();
        }
    }

    @Override
    public void close() {
        closed = true;
        if (!tabWindow) {
            mainWindow.promptClosed(this);
        }
    }

    @Override
    public boolean isTabWindow() {
        return tabWindow;
    }

    @Override
    public boolean isModalTabWindow() {
        return modal;
    }
}
