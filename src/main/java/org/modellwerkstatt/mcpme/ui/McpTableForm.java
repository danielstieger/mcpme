package org.modellwerkstatt.mcpme.ui;

import org.modellwerkstatt.dataux.runtime.extensions.ITableCellStringConverter;
import org.modellwerkstatt.dataux.runtime.genspecification.IGenSelControlled;
import org.modellwerkstatt.dataux.runtime.genspecification.MenuAction;
import org.modellwerkstatt.dataux.runtime.toolkit.IToolkit_TableForm;
import org.modellwerkstatt.dataux.runtime.utils.MoJSON;
import org.modellwerkstatt.objectflow.runtime.IOFXSelection;
import org.modellwerkstatt.objectflow.runtime.Selection;

import java.util.ArrayList;
import java.util.List;

/**
 * Eine Tabelle: Spalten, die geladenen Zeilenobjekte und die Auswahl. Der Agent sieht je Zelle den
 * Text, den der Converter der Spalte liefert; die Zeilenobjekte bleiben im Server.
 */
public class McpTableForm<T> extends McpForm<T> implements IToolkit_TableForm<T> {

    /** Eine Spalte, wie die Runtime sie anlegt. */
    public static final class Column {
        public final String property;
        public final String label;
        public final ITableCellStringConverter<Object> converter;
        public final boolean folded;

        @SuppressWarnings("unchecked")
        Column(String property, String label, ITableCellStringConverter<?> converter, boolean folded) {
            this.property = property;
            this.label = label == null ? "" : label;
            this.converter = (ITableCellStringConverter<Object>) converter;
            this.folded = folded;
        }
    }

    private final List<Column> columns = new ArrayList<>();
    private List<T> rows = new ArrayList<>();
    private List<T> selected = new ArrayList<>();
    private MenuAction menu;
    private IGenSelControlled<T> controller;
    private Class<?> rowClass;
    private String tableSummary = "";
    private String selectionSummary = "";
    private boolean editPreview;

    public List<Column> getColumns() {
        return columns;
    }

    public int getRowCount() {
        return rows.size();
    }

    public MenuAction getMenu() {
        return menu;
    }

    public IGenSelControlled<T> getController() {
        return controller;
    }

    public Class<?> getRowClass() {
        return rowClass;
    }

    public String getTableSummary() {
        return tableSummary;
    }

    public String getSelectionSummary() {
        return selectionSummary;
    }

    public boolean isEditPreview() {
        return editPreview;
    }

    public T rowAt(int position) {
        return rows.get(position);
    }

    /** Positionen der ausgewählten Zeilen, aufsteigend. */
    public List<Integer> selectedPositions() {
        List<Integer> positions = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            for (T object : selected) {
                if (object == rows.get(i)) {
                    positions.add(i);
                    break;
                }
            }
        }
        return positions;
    }

    /**
     * Meldet der Runtime eine Auswahl des Agenten. Die Runtime verteilt sie an alle Formen der Seite,
     * auch zurück an diese Tabelle, und lädt abhängige Tabellen und Formulare neu.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void pushSelectionFromAgent(List<Integer> positions) {
        List objects = new ArrayList();
        for (int position : positions) {
            objects.add(rows.get(position));
        }
        if (objects.isEmpty()) {
            controller.pushSelection(new Selection(rowClass));
        } else {
            controller.pushSelection(new Selection(rowClass, objects));
        }
    }

    /** Die Zellentexte einer Zeile, je Spalte der Text ihres Converters. */
    public List<String> cellTexts(int position, boolean withFolded) {
        T row = rows.get(position);
        List<String> texts = new ArrayList<>();
        for (Column column : columns) {
            if (column.folded && !withFolded) {
                continue;
            }
            String text;
            try {
                Object value = MoJSON.get(row, column.property);
                text = column.converter.convert(value);
            } catch (RuntimeException e) {
                text = "";
            }
            texts.add(text == null ? "" : text);
        }
        return texts;
    }

    // ---- IToolkit_Form ---------------------------------------------------------------------------

    @Override
    public void loadList(List<T> objects, IOFXSelection<T> selection) {
        this.rows = objects == null ? new ArrayList<>() : new ArrayList<>(objects);
        this.selected = selection == null ? new ArrayList<>() : new ArrayList<>(selection.getObjects());
    }

    @Override
    public boolean selectionChanged(IOFXSelection<T> selection) {
        this.selected = selection == null ? new ArrayList<>() : new ArrayList<>(selection.getObjects());
        return true;
    }

    // ---- IToolkit_TableForm ----------------------------------------------------------------------

    @Override
    public void endOfInitializationForElementClass(Class cls) {
        this.rowClass = cls;
    }

    @Override
    public void setFormController(IGenSelControlled crtl) {
        this.controller = crtl;
    }

    @Override
    public void addTableItemColor(String property, ITableCellStringConverter converter) {
    }

    @Override
    public void addColumn(String property, String label, ITableCellStringConverter<?> converter, int width,
                          boolean editable, boolean folded, boolean important) {
        columns.add(new Column(property, label, converter, folded));
    }

    @Override
    public void setSelectionSummaryLineText(String text) {
        this.selectionSummary = text == null ? "" : text;
    }

    @Override
    public void setTableSummaryLineText(String text) {
        this.tableSummary = text == null ? "" : text;
    }

    @Override
    public void forceNotEditable() {
    }

    @Override
    public void setEditPreview() {
        this.editPreview = true;
    }

    @Override
    public void addMenuAndSetButtons(MenuAction folder) {
        this.menu = folder;
    }

    @Override
    public void gcClear() {
        rows = new ArrayList<>();
        selected = new ArrayList<>();
        controller = null;
        menu = null;
    }
}
