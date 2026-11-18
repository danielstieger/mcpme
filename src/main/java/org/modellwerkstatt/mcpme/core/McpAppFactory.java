package org.modellwerkstatt.mcpme.core;

import org.modellwerkstatt.dataux.runtime.core.BaseUiFactory;
import org.modellwerkstatt.dataux.runtime.toolkit.IToolkit_CommandContainerUi;
import org.modellwerkstatt.dataux.runtime.toolkit.IToolkit_DateOrTimeEditor;
import org.modellwerkstatt.dataux.runtime.toolkit.IToolkit_DelegateForm;
import org.modellwerkstatt.dataux.runtime.toolkit.IToolkit_Form;
import org.modellwerkstatt.dataux.runtime.toolkit.IToolkit_FormContainer;
import org.modellwerkstatt.dataux.runtime.toolkit.IToolkit_ImageEditor;
import org.modellwerkstatt.dataux.runtime.toolkit.IToolkit_MainWindow;
import org.modellwerkstatt.dataux.runtime.toolkit.IToolkit_ReferenceEditor;
import org.modellwerkstatt.dataux.runtime.toolkit.IToolkit_StatusEditor;
import org.modellwerkstatt.dataux.runtime.toolkit.IToolkit_TabForm;
import org.modellwerkstatt.dataux.runtime.toolkit.IToolkit_TableForm;
import org.modellwerkstatt.dataux.runtime.toolkit.IToolkit_TextEditor;
import org.modellwerkstatt.dataux.runtime.toolkit.IToolkit_UiFactory;
import org.modellwerkstatt.dataux.runtime.toolkit.IToolkit_UploadEditor;
import org.modellwerkstatt.dataux.runtime.utils.MoWareTranslations;
import org.modellwerkstatt.mcpme.ui.McpChoiceEditor;
import org.modellwerkstatt.mcpme.ui.McpCommandUi;
import org.modellwerkstatt.mcpme.ui.McpDateEditor;
import org.modellwerkstatt.mcpme.ui.McpDelegateForm;
import org.modellwerkstatt.mcpme.ui.McpEditor;
import org.modellwerkstatt.mcpme.ui.McpFormContainer;
import org.modellwerkstatt.mcpme.ui.McpHtmlForm;
import org.modellwerkstatt.mcpme.ui.McpTabForm;
import org.modellwerkstatt.mcpme.ui.McpTableForm;
import org.modellwerkstatt.objectflow.runtime.IOFXCoreReporter;

/**
 * AppFactory des MCP-Servers: Spring-Bean der Anwendungskonfiguration (OFXConfig), wie bei den
 * anderen Toolkits. Einstellungen und Infrastruktur kommen aus BaseUiFactory, hier stehen die
 * Fabrikmethoden und die eigenen Einstellungen des Servers.
 *
 */
public class McpAppFactory extends BaseUiFactory implements IToolkit_UiFactory {

    // ---- eigene Einstellungen (docu/mcp-spezifikation.md, "Einstellungen an der AppFactory") ----

    private boolean showFolded = true;
    private int maxRowsPerTable = 20;
    private int maxOpenCommands = 5;
    private String fixedUser = null;
    private String platformName = IOFXCoreReporter.MoWarePlatform.MOWARE_TURKU.name();
    private int sessionIdleMinutes = 60;
    private String publicUrl = null;

    public McpAppFactory() {
        super(MoWareTranslations.TranslationSelection.MAIN_TRANSLATIONS);
    }

    /** Gefaltete Spalten und Felder liefern. */
    public boolean isShowFolded() {
        return showFolded;
    }

    public void setShowFolded(boolean showFolded) {
        this.showFolded = showFolded;
    }

    /** Obergrenze der Zeilen je Tabelle in einem Ergebnis und je get_rows. */
    public int getMaxRowsPerTable() {
        return maxRowsPerTable;
    }

    public void setMaxRowsPerTable(int maxRowsPerTable) {
        this.maxRowsPerTable = maxRowsPerTable;
    }

    /** Obergrenze gleichzeitig offener Commands je Session. */
    public int getMaxOpenCommands() {
        return maxOpenCommands;
    }

    public void setMaxOpenCommands(int maxOpenCommands) {
        this.maxOpenCommands = maxOpenCommands;
    }

    /** Anmeldung ohne OAuth mit diesem Benutzernamen; nur für Entwicklung und Tests. null = aus. */
    public String getFixedUser() {
        return fixedUser;
    }

    public void setFixedUser(String fixedUser) {
        this.fixedUser = (fixedUser == null || fixedUser.isBlank()) ? null : fixedUser.trim();
    }

    /** Als welche Plattform der Server gegenüber der Runtime auftritt; Vorgabe wie turkuforms. */
    public IOFXCoreReporter.MoWarePlatform getMowarePlatform() {
        return IOFXCoreReporter.MoWarePlatform.valueOf(platformName);
    }

    public void setMowarePlatform(String name) {
        this.platformName = IOFXCoreReporter.MoWarePlatform.valueOf(name.trim()).name();
    }

    /** Nach so vielen Minuten ohne Aufruf wird eine Anwendungsinstanz heruntergefahren. */
    public int getSessionIdleMinutes() {
        return sessionIdleMinutes;
    }

    public void setSessionIdleMinutes(int sessionIdleMinutes) {
        this.sessionIdleMinutes = sessionIdleMinutes;
    }

    /** Die Adresse des MCP-Endpunkts, wie Clients sie sehen; leer = aus dem Request. */
    public String getPublicUrl() {
        return publicUrl;
    }

    public void setPublicUrl(String url) {
        this.publicUrl = blankToNull(url);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    // ---- Verhalten gegenüber der Runtime ---------------------------------------------------------

    @Override
    public boolean useBackgroundThread() {
        return false;
    }

    @Override
    public boolean flagValidationAdditionally() {
        return true;
    }

    // ---- Fabrikmethoden -------------------------------------------------------------------------

    @Override
    public IToolkit_FormContainer createToolkitFormContainer(boolean flex) {
        return new McpFormContainer<>();
    }

    @Override
    public IToolkit_TabForm createToolkitTabForm() {
        return new McpTabForm<>();
    }

    @Override
    public IToolkit_TableForm createToolkitTableForm() {
        return new McpTableForm<>();
    }

    @Override
    public IToolkit_DelegateForm createToolkitDelegateForm() {
        return new McpDelegateForm<>();
    }

    @Override
    public IToolkit_Form createCustomForm(CustomForm form) {
        if (form == CustomForm.PASSIVE_HTML_FULLSCREEN) {
            return new McpHtmlForm<>();
        }
        throw new UnsupportedOperationException("mcpme: custom form " + form + " is not supported.");
    }

    @Override
    public IToolkit_CommandContainerUi createTabContainerUi(IToolkit_MainWindow app, boolean modal) {
        return new McpCommandUi((McpMainWindow) app, true, modal);
    }

    @Override
    public IToolkit_CommandContainerUi createPromptContainerUi(IToolkit_MainWindow app, boolean fullSize) {
        return new McpCommandUi((McpMainWindow) app, false, false);
    }

    @Override
    public IToolkit_TextEditor createTextEditor() {
        return new McpEditor(McpEditor.Kind.TEXT);
    }

    @Override
    public IToolkit_TextEditor createTextAreaEditor(int numOfLines) {
        return new McpEditor(McpEditor.Kind.TEXT_AREA);
    }

    @Override
    public IToolkit_TextEditor createDummyEditor() {
        return new McpEditor(McpEditor.Kind.PLACEHOLDER);
    }

    @Override
    public IToolkit_ReferenceEditor createReferenceEditor(boolean alter) {
        return new McpChoiceEditor(McpEditor.Kind.REFERENCE);
    }

    @Override
    public IToolkit_StatusEditor createStatusEditor(boolean alter) {
        return new McpChoiceEditor(McpEditor.Kind.STATUS);
    }

    @Override
    public IToolkit_DateOrTimeEditor createDateEditor(boolean withPicker) {
        return new McpDateEditor();
    }

    @Override
    public IToolkit_DateOrTimeEditor createDateAndTimeEditor(boolean withPicker, boolean timeOnly) {
        return new McpDateEditor();
    }

    @Override
    public IToolkit_ImageEditor createImageEditor() {
        return new McpEditor(McpEditor.Kind.IMAGE);
    }

    @Override
    public IToolkit_UploadEditor createUploadEditor() {
        return new McpEditor(McpEditor.Kind.UPLOAD);
    }
}
