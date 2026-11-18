package org.modellwerkstatt.mcpme.ui;

import org.modellwerkstatt.dataux.runtime.toolkit.IToolkit_DateOrTimeEditor;

/** Ein Datums- oder Zeitfeld: Text im Format, das die Runtime vorgibt. */
public class McpDateEditor extends McpEditor implements IToolkit_DateOrTimeEditor {

    private String format = "";

    public McpDateEditor() {
        super(Kind.DATE);
    }

    /** Joda-Time-Muster, in dem der Wert steht und erwartet wird; leer, wenn die Runtime keines nennt. */
    public String getFormat() {
        return format;
    }

    @Override
    public void setFormatter(String format, String locale, int langIndex) {
        this.format = format == null ? "" : format;
    }
}
