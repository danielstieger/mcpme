package org.modellwerkstatt.mcpme.ui;

/** Eine Seite, die nur HTML zeigt; die Runtime liefert es über den Titel. */
public class McpHtmlForm<T> extends McpForm<T> {

    public String getHtml() {
        return getTitleText();
    }
}
