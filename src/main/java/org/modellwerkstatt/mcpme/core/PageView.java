package org.modellwerkstatt.mcpme.core;

import org.modellwerkstatt.dataux.runtime.core.ICommandContainer;
import org.modellwerkstatt.dataux.runtime.extensions.IDlgt;
import org.modellwerkstatt.dataux.runtime.genspecification.CmdAction;
import org.modellwerkstatt.dataux.runtime.genspecification.MenuAction;
import org.modellwerkstatt.mcpme.ui.McpChoiceEditor;
import org.modellwerkstatt.mcpme.ui.McpCommandUi;
import org.modellwerkstatt.mcpme.ui.McpDateEditor;
import org.modellwerkstatt.mcpme.ui.McpDelegateForm;
import org.modellwerkstatt.mcpme.ui.McpEditor;
import org.modellwerkstatt.mcpme.ui.McpForm;
import org.modellwerkstatt.mcpme.ui.McpFormContainer;
import org.modellwerkstatt.mcpme.ui.McpChartForm;
import org.modellwerkstatt.mcpme.ui.McpHtmlForm;
import org.modellwerkstatt.mcpme.ui.McpTabForm;
import org.modellwerkstatt.mcpme.ui.McpTableForm;
import org.modellwerkstatt.objectflow.runtime.IOFXProblem;
import org.modellwerkstatt.objectflow.runtime.OFXConclusionInformation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Die aktuelle Seite eines offenen Commands, wie der Agent sie sieht: Formulare mit Feldern, Tabellen,
 * die Commands, die die Seite anbietet, und die Conclusion Actions.
 *
 * Die Sicht wird bei jedem Aufruf aus den Formen neu gebildet. Dabei entstehen die Kennungen, über die
 * der Agent Felder, Tabellen und Einträge benennt; sie sind bei jedem Öffnen derselben Seite gleich.
 */
public class PageView {

    /** Hotkey der Conclusion, die die Seite aktualisiert; sie ist für den Agenten kein eigener Schritt. */
    static final String HOTKEY_UPDATE = "UPD";
    /** Hotkey der Conclusion, die die Standardaktion der Seite ist. */
    static final String HOTKEY_DEFAULT = "GO";

    private static final class FormInfo {
        String id;
        String group;
        McpDelegateForm<?> form;
        List<String> fieldIds = new ArrayList<>();
        List<McpEditor> editors = new ArrayList<>();
    }

    private static final class TableInfo {
        String id;
        String group;
        McpTableForm<?> table;
    }

    private final McpCommandUi ui;
    private final List<FormInfo> forms = new ArrayList<>();
    private final List<TableInfo> tables = new ArrayList<>();
    private final Map<String, McpEditor> fields = new LinkedHashMap<>();
    private final List<Entry> entries = new ArrayList<>();
    private String html;
    private final List<McpChartForm<?>> charts = new ArrayList<>();

    public PageView(McpCommandUi ui) {
        this.ui = ui;
        McpForm<?> root = ui.getContent();
        if (root != null) {
            collect(root, null);
            nameTables();
            nameFields();
            collectEntries(root);
        }
    }

    public McpCommandUi getUi() {
        return ui;
    }

    /** Das Feld mit dieser Kennung, oder null. */
    public McpEditor field(String id) {
        return fields.get(id);
    }

    /** Die Tabelle mit dieser Kennung, oder null. */
    public McpTableForm<?> table(String id) {
        for (TableInfo info : tables) {
            if (info.id.equalsIgnoreCase(id.trim())) {
                return info.table;
            }
        }
        return null;
    }

    public List<String> tableIds() {
        List<String> ids = new ArrayList<>();
        for (TableInfo info : tables) {
            ids.add(info.id);
        }
        return ids;
    }

    /**
     * Zeilen einer Tabelle: ein Bereich nach Position, die Treffer einer Textsuche über die Zellentexte,
     * oder beides. Höchstens maxRows Zeilen; gibt es mehr, nennt das Ergebnis die Position zum Fortsetzen.
     */
    public Map<String, Object> rows(String tableId, Integer from, Integer to, String search, int maxRows,
                                    boolean showFolded) {
        McpTableForm<?> table = table(tableId);
        if (table == null) {
            throw new AppInstance.Refusal("The current page has no table '" + tableId + "'. Tables: " + tableIds());
        }
        int first = from == null ? 0 : Math.max(0, from);
        int last = to == null ? table.getRowCount() - 1 : Math.min(table.getRowCount() - 1, to);
        String needle = search == null || search.isBlank() ? null : search.trim().toLowerCase();

        List<List<Object>> rows = new ArrayList<>();
        Integer continueAt = null;
        int matches = 0;
        for (int position = first; position <= last; position++) {
            if (needle != null && !matches(table, position, needle, showFolded)) {
                continue;
            }
            matches++;
            if (rows.size() < maxRows) {
                rows.add(row(table, position, showFolded));
            } else if (continueAt == null) {
                continueAt = position;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("table", tableId);
        result.put("columns", columnLabels(table, showFolded));
        result.put("rowCount", table.getRowCount());
        if (needle != null) {
            result.put("matches", matches);
        }
        result.put("rows", rows);
        if (continueAt != null) {
            result.put("more", "Not all rows fit; continue with from=" + continueAt + ".");
        }
        result.put("selection", table.selectedPositions());
        return result;
    }

    private static boolean matches(McpTableForm<?> table, int position, String needle, boolean showFolded) {
        for (String text : table.cellTexts(position, showFolded)) {
            if (text.toLowerCase().contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> columnLabels(McpTableForm<?> table, boolean showFolded) {
        List<String> columns = new ArrayList<>();
        for (McpTableForm.Column column : table.getColumns()) {
            if (column.folded && !showFolded) {
                continue;
            }
            columns.add(column.label.isEmpty() ? column.property : column.label);
        }
        return columns;
    }

    public List<String> fieldIds() {
        return new ArrayList<>(fields.keySet());
    }

    /** Die Commands, die die Seite anbietet. */
    public List<Entry> getEntries() {
        return entries;
    }

    /** Ob die Seite gerade eine Fehlermeldung zeigt, etwa nach einer Eingabe, die die Runtime nicht annimmt. */
    public boolean hasErrorMessage() {
        McpForm<?> root = ui.getContent();
        if (root == null) {
            return false;
        }
        for (IOFXProblem problem : root.getProblems()) {
            if (!problem.isWarningOnly()) {
                return true;
            }
        }
        return false;
    }

    /** Die Conclusion mit dieser Kennung, sofern sie für den Agenten eine Conclusion Action ist. */
    public OFXConclusionInformation conclusionAction(int id) {
        List<OFXConclusionInformation> all = ui.getConclusions();
        for (int i = 1; i < all.size(); i++) {
            OFXConclusionInformation info = all.get(i);
            if (info.conclusionHashCode == id && !HOTKEY_UPDATE.equals(info.hotkey)) {
                return info;
            }
        }
        return null;
    }

    public boolean isCancelable() {
        List<OFXConclusionInformation> all = ui.getConclusions();
        return !all.isEmpty() && all.get(0).enabled;
    }

    // ---- Aufbau ----------------------------------------------------------------------------------

    private void collect(McpForm<?> form, String group) {
        if (form instanceof McpDelegateForm<?> delegateForm) {
            FormInfo info = new FormInfo();
            info.id = "form" + (forms.size() + 1);
            info.group = group;
            info.form = delegateForm;
            forms.add(info);
        } else if (form instanceof McpTableForm<?> tableForm) {
            TableInfo info = new TableInfo();
            info.group = group;
            info.table = tableForm;
            tables.add(info);
        } else if (form instanceof McpHtmlForm<?> htmlForm) {
            html = htmlForm.getHtml();
        } else if (form instanceof McpChartForm<?> chartForm) {
            charts.add(chartForm);
        }
        for (McpForm<?> child : form.getChildForms()) {
            String childGroup = group;
            if (form instanceof McpTabForm<?> tabForm) {
                String label = tabForm.labelOf(child);
                childGroup = group == null ? label : group + " / " + label;
            }
            collect(child, childGroup);
        }
    }

    private void nameTables() {
        Map<String, Integer> seen = new HashMap<>();
        for (TableInfo info : tables) {
            Class<?> rowClass = info.table.getRowClass();
            String base = rowClass == null ? "table" : rowClass.getSimpleName();
            int count = seen.merge(base, 1, Integer::sum);
            info.id = count == 1 ? base : base + count;
        }
    }

    private void nameFields() {
        Map<String, Integer> occurrences = new HashMap<>();
        for (FormInfo info : forms) {
            for (McpEditor editor : info.form.getEditors()) {
                if (editor.getKind() == McpEditor.Kind.PLACEHOLDER) {
                    continue;
                }
                occurrences.merge(propertyOf(editor), 1, Integer::sum);
            }
        }
        for (FormInfo info : forms) {
            int unnamed = 0;
            for (McpEditor editor : info.form.getEditors()) {
                if (editor.getKind() == McpEditor.Kind.PLACEHOLDER) {
                    continue;
                }
                String property = propertyOf(editor);
                String id;
                if (property.isEmpty()) {
                    unnamed++;
                    id = info.id + ".field" + unnamed;
                } else if (occurrences.get(property) > 1) {
                    id = info.id + "." + property;
                } else {
                    id = property;
                }
                while (fields.containsKey(id)) {
                    id = id + "_";
                }
                fields.put(id, editor);
                info.fieldIds.add(id);
                info.editors.add(editor);
            }
        }
    }

    private static String propertyOf(McpEditor editor) {
        IDlgt delegate = editor.getDelegate();
        String property = delegate == null ? null : delegate.getPropertyName();
        return property == null ? "" : property;
    }

    private void collectEntries(McpForm<?> root) {
        if (root instanceof McpFormContainer<?> container) {
            addEntries(container.getMenu(), "page", "page");
        }
        for (TableInfo info : tables) {
            addEntries(info.table.getMenu(), info.id, "table " + info.id);
        }
    }

    private void addEntries(MenuAction menu, String idPrefix, String origin) {
        if (menu == null) {
            return;
        }
        int position = 0;
        for (CmdAction action : menu.getAllCmdActionsOfMenu()) {
            position++;
            entries.add(new Entry(idPrefix + "-" + position, action, origin, null));
        }
    }

    // ---- Zustand für den Agenten -----------------------------------------------------------------

    /**
     * @param maxRows    Obergrenze der Zeilen je Tabelle
     * @param showFolded gefaltete Spalten liefern
     */
    public void describeInto(Map<String, Object> state, int maxRows, boolean showFolded, boolean mustCompleteFirst) {
        ICommandContainer container = ui.getContainer();

        Map<String, Object> command = new LinkedHashMap<>();
        command.put("handle", ui.getHandle());
        command.put("command", String.valueOf(container));
        command.put("title", ui.getWindowTitle());
        if (container != null) {
            command.put("readOnly", container.isReadOnly());
        }
        if (!ui.getNotification().isEmpty()) {
            command.put("notice", ui.getNotification());
        }
        if (mustCompleteFirst) {
            // Solange dieser Command offen ist, lässt sich kein anderer starten oder aktivieren.
            command.put("mustCompleteFirst", true);
        }
        state.put("command", command);

        McpForm<?> root = ui.getContent();
        if (root != null) {
            String[] titleLines = root.getTitleText().split("\\r?\\n", 2);
            Map<String, Object> page = new LinkedHashMap<>();
            page.put("title", titleLines[0]);
            if (titleLines.length > 1 && !titleLines[1].isBlank()) {
                page.put("subtitle", titleLines[1]);
            }
            state.put("page", page);

            List<Map<String, Object>> pageMessages = new ArrayList<>();
            for (IOFXProblem problem : root.getProblems()) {
                String text = problem.getSimpleUserText();
                if (text == null || text.isBlank()) {
                    // Nur ein Eingabefehler; der Text steht am Feld.
                    continue;
                }
                Map<String, Object> message = new LinkedHashMap<>();
                message.put("level", problem.isWarningOnly() ? "hint" : "error");
                message.put("text", text);
                pageMessages.add(message);
            }
            if (!pageMessages.isEmpty()) {
                state.put("pageMessages", pageMessages);
            }
        }

        if (html != null) {
            state.put("html", html);
        }
        if (!charts.isEmpty()) {
            List<Map<String, Object>> chartList = new ArrayList<>();
            for (McpChartForm<?> chart : charts) {
                chartList.add(chart.getData());
            }
            state.put("charts", chartList);
        }

        List<Map<String, Object>> formList = new ArrayList<>();
        for (FormInfo info : forms) {
            Map<String, Object> form = new LinkedHashMap<>();
            form.put("form", info.id);
            putIfText(form, "title", info.form.isRoot() ? "" : info.form.getTitleText());
            putIfText(form, "group", info.group);
            List<Map<String, Object>> fieldList = new ArrayList<>();
            for (int i = 0; i < info.editors.size(); i++) {
                fieldList.add(describeField(info.fieldIds.get(i), info.editors.get(i)));
            }
            form.put("fields", fieldList);
            formList.add(form);
        }
        if (!formList.isEmpty()) {
            state.put("forms", formList);
        }

        List<Map<String, Object>> tableList = new ArrayList<>();
        for (TableInfo info : tables) {
            tableList.add(describeTable(info, maxRows, showFolded));
        }
        if (!tableList.isEmpty()) {
            state.put("tables", tableList);
        }

        List<Map<String, Object>> commandList = new ArrayList<>();
        for (Entry entry : entries) {
            commandList.add(entry.describe());
        }
        if (!commandList.isEmpty()) {
            state.put("commands", commandList);
        }

        List<Map<String, Object>> actions = new ArrayList<>();
        List<OFXConclusionInformation> all = ui.getConclusions();
        for (int i = 1; i < all.size(); i++) {
            OFXConclusionInformation info = all.get(i);
            if (HOTKEY_UPDATE.equals(info.hotkey)) {
                continue;
            }
            Map<String, Object> action = new LinkedHashMap<>();
            action.put("action", info.conclusionHashCode);
            action.put("label", info.buttonTitle);
            putIfText(action, "icon", info.iconName);
            if (HOTKEY_DEFAULT.equals(info.hotkey)) {
                action.put("default", true);
            }
            action.put("enabled", info.enabled);
            actions.add(action);
        }
        state.put("conclusionActions", actions);
        state.put("cancelable", isCancelable());
    }

    private Map<String, Object> describeField(String id, McpEditor editor) {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("field", id);
        field.put("label", editor.getLabelText());
        putIfText(field, "description", editor.getDescription());
        field.put("value", editor.valueForAgent());

        switch (editor.getKind()) {
            case TEXT_AREA -> field.put("kind", "text, multi-line");
            case STATUS, REFERENCE -> {
                field.put("kind", "choice");
                field.put("options", ((McpChoiceEditor) editor).getItems());
            }
            case DATE -> {
                field.put("kind", "date");
                putIfText(field, "format", ((McpDateEditor) editor).getFormat());
            }
            case IMAGE -> field.put("kind", "image");
            case UPLOAD -> field.put("kind", "file");
            default -> field.put("kind", "text");
        }
        if (editor.getKind() == McpEditor.Kind.TEXT || editor.getKind() == McpEditor.Kind.TEXT_AREA) {
            // Bei Auswahl- und Datumsfeldern ist der Platzhalter ein Bedienhinweis für die Oberfläche.
            putIfText(field, "hint", editor.getPrompt());
        }

        boolean editable = editor.isEnabled() && editor.getKind() != McpEditor.Kind.IMAGE
                && editor.getKind() != McpEditor.Kind.UPLOAD;
        field.put("editable", editable);
        if (editor.getKind() == McpEditor.Kind.UPLOAD) {
            field.put("note", "Files cannot be uploaded through this server; the person uploads them in the browser application.");
        }
        if (editable && editor.isIssuesUpdate()) {
            field.put("refreshesPage", true);
        }
        putIfText(field, "error", editor.getErrorText());
        return field;
    }

    private Map<String, Object> describeTable(TableInfo info, int maxRows, boolean showFolded) {
        McpTableForm<?> table = info.table;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("table", info.id);
        putIfText(result, "title", table.isRoot() ? "" : table.getTitleText());
        putIfText(result, "group", info.group);

        result.put("columns", columnLabels(table, showFolded));
        result.put("rowCount", table.getRowCount());

        List<Integer> selection = table.selectedPositions();
        List<List<Object>> rows = new ArrayList<>();
        int shown = Math.min(maxRows, table.getRowCount());
        for (int position = 0; position < shown; position++) {
            rows.add(row(table, position, showFolded));
        }
        // Ausgewählte Zeilen stehen immer im Ergebnis, auch jenseits der Obergrenze.
        for (int position : selection) {
            if (position >= shown) {
                rows.add(row(table, position, showFolded));
            }
        }
        result.put("rows", rows);
        result.put("selection", selection);
        putIfText(result, "summary", table.getTableSummary());
        putIfText(result, "selectionSummary", table.getSelectionSummary());
        return result;
    }

    private static List<Object> row(McpTableForm<?> table, int position, boolean showFolded) {
        List<Object> row = new ArrayList<>();
        row.add(position);
        row.addAll(table.cellTexts(position, showFolded));
        return row;
    }

    private static void putIfText(Map<String, Object> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value);
        }
    }
}
