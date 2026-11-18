package org.modellwerkstatt.mcpme.ui;

import org.modellwerkstatt.dataux.runtime.toolkit.IToolkit_ReferenceEditor;
import org.modellwerkstatt.dataux.runtime.toolkit.IToolkit_StatusEditor;

import java.util.ArrayList;
import java.util.List;

/**
 * Ein Auswahlfeld: Status oder Reference. Beide bieten eine Liste von Texten an; der Agent wählt
 * einen davon. Ein Status meldet der Runtime den Index des Eintrags, eine Reference den Text.
 */
public class McpChoiceEditor extends McpEditor implements IToolkit_StatusEditor, IToolkit_ReferenceEditor {

    private List<String> items = new ArrayList<>();
    private int selectedIndex = -1;
    private boolean optional;

    public McpChoiceEditor(Kind kind) {
        super(kind);
    }

    public List<String> getItems() {
        return items;
    }

    public boolean isOptional() {
        return optional;
    }

    @Override
    public String valueForAgent() {
        if (kind == Kind.STATUS) {
            return selectedIndex >= 0 && selectedIndex < items.size() ? items.get(selectedIndex) : "";
        }
        return text;
    }

    @Override
    public String checkValueFromAgent(String value) {
        if (value == null || value.isEmpty() || indexOf(value) >= 0) {
            return null;
        }
        return "'" + value + "' is not one of the options " + items;
    }

    @Override
    public void setValueFromAgent(String value) {
        int index = value == null || value.isEmpty() ? -1 : indexOf(value);
        if (kind == Kind.STATUS) {
            selectedIndex = index;
        } else {
            text = index < 0 ? "" : items.get(index);
        }
    }

    private int indexOf(String wanted) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).equals(wanted)) {
                return i;
            }
        }
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).equalsIgnoreCase(wanted.trim())) {
                return i;
            }
        }
        return -1;
    }

    // ---- IToolkit_StatusEditor / IToolkit_ReferenceEditor ----------------------------------------

    @Override
    public void setItems(List<String> items) {
        this.items = items == null ? new ArrayList<>() : new ArrayList<>(items);
        this.selectedIndex = -1;
    }

    @Override
    public int getSelectedIndex() {
        return selectedIndex;
    }

    @Override
    public void setSelectedIndex(int i) {
        this.selectedIndex = i;
    }

    @Override
    public void setOptionalAfterLoad(boolean optional) {
        this.optional = optional;
    }

    @Override
    public void setText(String text) {
        if (kind == Kind.STATUS) {
            throw new UnsupportedOperationException("A status field has no text; the runtime uses its index.");
        }
        super.setText(text);
    }

    @Override
    public String getText() {
        if (kind == Kind.STATUS) {
            throw new UnsupportedOperationException("A status field has no text; the runtime uses its index.");
        }
        // Reference: genau ein Eintrag der Liste oder "nichts gewählt"
        return text == null || text.isEmpty() ? null : text;
    }
}
