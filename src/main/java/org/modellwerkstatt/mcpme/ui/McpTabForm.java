package org.modellwerkstatt.mcpme.ui;

import org.modellwerkstatt.dataux.runtime.toolkit.IToolkit_Form;
import org.modellwerkstatt.dataux.runtime.toolkit.IToolkit_TabForm;

import java.util.ArrayList;
import java.util.List;

/**
 * Ein Tab-Layout innerhalb einer Seite: benannte Abschnitte mit je einer Form. Für den Agenten gibt
 * es kein "sichtbar" und "verdeckt"; er erhält alle Abschnitte.
 */
public class McpTabForm<T> extends McpForm<T> implements IToolkit_TabForm<T> {

    private final List<McpForm<?>> children = new ArrayList<>();
    private final List<String> labels = new ArrayList<>();

    /** Beschriftung des Abschnitts, in dem das Kind steht. */
    public String labelOf(McpForm<?> child) {
        int index = children.indexOf(child);
        return index < 0 ? "" : labels.get(index);
    }

    @Override
    public List<McpForm<?>> getChildForms() {
        return children;
    }

    @Override
    public void addTab(IToolkit_Form form, String label) {
        children.add((McpForm<?>) form);
        labels.add(label == null ? "" : label);
    }

    @Override
    public IToolkit_TabForm<T> getToolkitImplementation() {
        return this;
    }

    @Override
    public Object myRequestFocus() {
        for (McpForm<?> child : children) {
            Object focused = child.myRequestFocus();
            if (focused != null) {
                return focused;
            }
        }
        return null;
    }

    @Override
    public void afterFullUiInitialized() {
        for (McpForm<?> child : children) {
            child.afterFullUiInitialized();
        }
    }

    @Override
    public void gcClear() {
        children.clear();
        labels.clear();
    }
}
