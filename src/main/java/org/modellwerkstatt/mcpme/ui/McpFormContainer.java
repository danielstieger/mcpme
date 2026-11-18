package org.modellwerkstatt.mcpme.ui;

import org.modellwerkstatt.dataux.runtime.genspecification.MenuAction;
import org.modellwerkstatt.dataux.runtime.toolkit.IToolkit_Form;
import org.modellwerkstatt.dataux.runtime.toolkit.IToolkit_FormContainer;

import java.util.ArrayList;
import java.util.List;

/** Ein Layout: enthält weitere Formen. Als Wurzel einer Seite trägt es deren Menü. */
public class McpFormContainer<T> extends McpForm<T> implements IToolkit_FormContainer<T> {

    private final List<McpForm<?>> children = new ArrayList<>();
    private MenuAction menu;
    private int focusStart;

    /** Das Menü der Seite, oder null. */
    public MenuAction getMenu() {
        return menu;
    }

    @Override
    public List<McpForm<?>> getChildForms() {
        return children;
    }

    @Override
    public void setLayoutConstraints(List<String> colConstraints, List<String> rowConstraints) {
        // Zeilen- und Spaltengewichte sind Darstellung.
    }

    @Override
    public void addChildren(IToolkit_Form child) {
        children.add((McpForm<?>) child);
    }

    @Override
    public void addMenuAndSetButtons(MenuAction menu) {
        this.menu = menu;
    }

    @Override
    public void skipFocus(int cmptToStartWith) {
        this.focusStart = cmptToStartWith;
    }

    @Override
    public Object myRequestFocus() {
        for (int i = Math.max(0, focusStart); i < children.size(); i++) {
            Object focused = children.get(i).myRequestFocus();
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
        menu = null;
    }
}
