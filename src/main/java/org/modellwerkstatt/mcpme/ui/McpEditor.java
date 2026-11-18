package org.modellwerkstatt.mcpme.ui;

import org.modellwerkstatt.dataux.runtime.extensions.IDlgt;
import org.modellwerkstatt.dataux.runtime.toolkit.IToolkit_ImageEditor;
import org.modellwerkstatt.dataux.runtime.toolkit.IToolkit_TextEditor;
import org.modellwerkstatt.dataux.runtime.toolkit.IToolkit_UploadEditor;

/**
 * Ein Feld eines Formulars. Es stellt nichts dar, sondern hält, was die Runtime setzt, und den Wert,
 * den der Agent einträgt. Die Runtime liest den Wert über {@link #getText()}, wenn sie speichert.
 *
 * Typen, Formate und Prüfungen liegen im Delegate der Runtime; das Feld kennt nur Text.
 */
public class McpEditor implements IToolkit_TextEditor, IToolkit_ImageEditor, IToolkit_UploadEditor {

    public enum Kind {
        TEXT, TEXT_AREA, PLACEHOLDER, STATUS, REFERENCE, DATE, IMAGE, UPLOAD
    }

    protected final Kind kind;
    protected IDlgt delegate;
    protected IDlgt.Opt[] options = new IDlgt.Opt[0];
    protected String label = "";
    protected String description = "";
    protected String prompt = "";
    protected boolean enabled = true;
    protected String text = "";
    protected String errorText = "";
    protected boolean issuesUpdate;

    public McpEditor(Kind kind) {
        this.kind = kind;
    }

    // ---- Zugriff für die Werkzeuge ---------------------------------------------------------------

    public Kind getKind() {
        return kind;
    }

    public IDlgt getDelegate() {
        return delegate;
    }

    public String getLabelText() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    public String getPrompt() {
        return prompt;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getErrorText() {
        return errorText;
    }

    /** Das Feld aktualisiert die Seite, sobald sein Wert geändert wurde (ISSUE UPDATE/SCANABLE). */
    public boolean isIssuesUpdate() {
        return issuesUpdate;
    }

    public boolean hasOption(IDlgt.Opt option) {
        return IToolkit_TextEditor.has(option, options);
    }

    /** Der Wert, wie ihn der Agent sieht. */
    public String valueForAgent() {
        return text;
    }

    /**
     * Prüft, ob das Feld diesen Wert des Agenten annehmen kann, ohne ihn einzutragen. Ob der Wert
     * fachlich gültig ist, prüft erst die Runtime beim Speichern.
     *
     * @return null, wenn annehmbar; sonst der Grund der Ablehnung
     */
    public String checkValueFromAgent(String value) {
        return null;
    }

    /** Trägt einen Wert des Agenten ein; vorher mit {@link #checkValueFromAgent} geprüft. */
    public void setValueFromAgent(String value) {
        text = value == null ? "" : value;
    }

    // ---- IToolkit_TextEditor ---------------------------------------------------------------------

    @Override
    public void setDelegate(IDlgt dlgt) {
        this.delegate = dlgt;
    }

    @Override
    public void enableKeyReleaseEvents() {
        // Einzelne Tastendrücke gibt es beim Agenten nicht.
    }

    @Override
    public void setLabelTooltip(String text) {
        this.description = text == null ? "" : text;
    }

    @Override
    public void setValidationErrorText(String text) {
        this.errorText = text == null ? "" : text;
    }

    @Override
    public void setLabel(String text) {
        this.label = text == null ? "" : text;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public void setEditorPrompt(String promptText) {
        this.prompt = promptText == null ? "" : promptText;
    }

    @Override
    public void newObjectBound() {
        errorText = "";
    }

    @Override
    public void setText(String text) {
        this.text = text == null ? "" : text;
    }

    @Override
    public String getText() {
        return text;
    }

    @Override
    public void setIssuesUpdateConclusion() {
        this.issuesUpdate = true;
    }

    @Override
    public void setOption(IDlgt.Opt... option) {
        this.options = option == null ? new IDlgt.Opt[0] : option;
    }

    @Override
    public Object getEditor() {
        return this;
    }

    @Override
    public Object getLabel() {
        return label;
    }

    @Override
    public Object getRightPartComponent() {
        return this;
    }

    @Override
    public void gcClear() {
        delegate = null;
    }
}
