package org.modellwerkstatt.mcpme;

import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpSchema.ToolAnnotations;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Der feste Satz der Werkzeuge: Namen, Beschreibungen, Eingabeschemata und Kennzeichnung.
 * Er ist für jede Anwendung gleich (docu/mcp-spezifikation.md, Abschnitt "Werkzeuge").
 *
 * Namen und Beschreibungen sind englisch, wie in MCP üblich; sie richten sich an den Agenten.
 */
public final class ToolCatalog {

    public static final String GET_OVERVIEW = "get_overview";
    public static final String DESCRIBE_COMMAND = "describe_command";
    public static final String START_COMMAND = "start_command";
    public static final String GET_STATE = "get_state";
    public static final String GET_ROWS = "get_rows";
    public static final String SET_FIELDS = "set_fields";
    public static final String SELECT_ROWS = "select_rows";
    public static final String CONCLUDE = "conclude";
    public static final String CANCEL_COMMAND = "cancel_command";
    public static final String SWITCH_COMMAND = "switch_command";

    private ToolCatalog() {
    }

    public static List<Tool> all() {
        List<Tool> tools = new ArrayList<>();

        tools.add(tool(GET_OVERVIEW, "Get overview",
                "Returns the entry points of the application: the commands that can be started right now, "
                        + "with their current labels, and the commands that are already open. Call this first.",
                readOnly(),
                schema(props(), List.of())));

        tools.add(tool(DESCRIBE_COMMAND, "Describe command",
                "Returns the description of a command: its parameters in declaration order, the user "
                        + "documentation and the technical documentation. Use it before starting a command "
                        + "directly with parameter values.",
                readOnly(),
                schema(props(
                        "command", string("Fully qualified name of the command.")),
                        List.of("command"))));

        tools.add(tool(START_COMMAND, "Start command",
                "Starts a command and makes it the active command. "
                        + "From the context: name an entry that get_overview or the current page offers, by "
                        + "'entry' (its identifier, always unambiguous), by 'label' or by 'command'; label and "
                        + "command only work if they match exactly one entry. The command takes its arguments "
                        + "from the entry and from the current row selection. Give 'entry' together with 'label' "
                        + "to make sure the entry is still the one you mean. An entry with 'within' runs its "
                        + "command inside the command named there, which is opened for it and completed "
                        + "automatically afterwards. "
                        + "Directly: give 'command' together with 'params', the complete parameter list in "
                        + "declaration order.",
                changing(false),
                schema(props(
                        "entry", string("Identifier of the entry, for example 'start-1', 'menu-2' or 'page-1'."),
                        "label", string("Label of the entry."),
                        "command", string("Fully qualified name of the command."),
                        "params", array("Complete parameter list for a direct start, in declaration order. "
                                + "Allowed values: integers, strings and null.",
                                Map.of("type", List.of("integer", "string", "null"))),
                        "table", string("Table whose rows are selected before the command starts."),
                        "rows", array("Positions of the rows to select in 'table' before the command starts.",
                                Map.of("type", "integer"))),
                        List.of())));

        tools.add(tool(GET_STATE, "Get state",
                "Returns the state of the active command: page, messages, forms with their fields, tables, "
                        + "the commands the page offers, the conclusion actions and the list of open commands. "
                        + "Every changing tool returns the same structure, so call this only to re-read. "
                        + "A table row is a list: its position first, then the cell texts in column order. "
                        + "A table shows its first rows and its selected rows; 'rowCount' is the total, "
                        + "get_rows returns the others. 'documents' lists what the application handed over "
                        + "to open during the call: PDF documents and links, each with a URL to fetch. "
                        + "'charts' holds the data of the charts on the page.",
                readOnly(),
                schema(props(), List.of())));

        tools.add(tool(GET_ROWS, "Get rows",
                "Returns rows of a table of the active command: a range by position, the matches of a text "
                        + "search over the cell texts, or both. A row is a list: its position first, then the "
                        + "cell texts in column order.",
                readOnly(),
                schema(props(
                        "table", string("Identifier of the table."),
                        "from", integer("First position of the range (inclusive)."),
                        "to", integer("Last position of the range (inclusive)."),
                        "search", string("Text to search for in the cell texts.")),
                        List.of("table"))));

        tools.add(tool(SET_FIELDS, "Set fields",
                "Sets values of form fields of the active command. Values are given as text in the format "
                        + "the field states. If one of the fields refreshes the page on change, all values are "
                        + "applied first and the page is refreshed once.",
                changing(false),
                schema(props(
                        "fields", object("Field identifier to value.",
                                Map.of("type", List.of("string", "null")))),
                        List.of("fields"))));

        tools.add(tool(SELECT_ROWS, "Select rows",
                "Sets the row selection of a table of the active command. Dependent tables and forms reload "
                        + "for the new selection, and the commands the page offers become startable or not "
                        + "accordingly.",
                changing(false),
                schema(props(
                        "table", string("Identifier of the table."),
                        "rows", array("Positions of the rows to select; an empty list clears the selection.",
                                Map.of("type", "integer"))),
                        List.of("table", "rows"))));

        tools.add(tool(CONCLUDE, "Conclude",
                "Runs a conclusion action of the current page of the active command, for example saving or "
                        + "moving on to the next page. Field values given in 'fields' are applied first.",
                changing(false),
                schema(props(
                        "action", integer("Identifier of the conclusion action, as listed in the state."),
                        "fields", object("Field identifier to value, applied before the action runs.",
                                Map.of("type", List.of("string", "null")))),
                        List.of("action"))));

        tools.add(tool(CANCEL_COMMAND, "Cancel command",
                "Cancels the active command and closes it. A command that saves its own data and has unsaved "
                        + "changes is only cancelled with 'discard_changes' set to true; the changes are then "
                        + "lost. A command marked 'mustCompleteFirst' that is a step within another command is "
                        + "closed without that confirmation, and what was entered in it is lost.",
                changing(true),
                schema(props(
                        "discard_changes", bool("Confirms that unsaved changes are discarded.")),
                        List.of())));

        tools.add(tool(SWITCH_COMMAND, "Switch command",
                "Makes another open command the active command. Not possible while the active command is "
                        + "marked 'mustCompleteFirst'.",
                changing(false),
                schema(props(
                        "command", string("Handle of the open command, as listed under the open commands.")),
                        List.of("command"))));

        return tools;
    }

    // ---- Bausteine -------------------------------------------------------------------------------

    private static Tool tool(String name, String title, String description, ToolAnnotations annotations,
                             Map<String, Object> inputSchema) {
        return Tool.builder(name, inputSchema)
                .title(title)
                .description(description)
                .annotations(annotations)
                .build();
    }

    private static ToolAnnotations readOnly() {
        return ToolAnnotations.builder()
                .readOnlyHint(true)
                .openWorldHint(false)
                .build();
    }

    private static ToolAnnotations changing(boolean destructive) {
        return ToolAnnotations.builder()
                .readOnlyHint(false)
                .destructiveHint(destructive)
                .idempotentHint(false)
                .openWorldHint(false)
                .build();
    }

    private static Map<String, Object> schema(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        schema.put("additionalProperties", false);
        return schema;
    }

    /** Name und Schema abwechselnd, in der Reihenfolge der Anzeige. */
    private static Map<String, Object> props(Object... nameThenSchema) {
        Map<String, Object> properties = new LinkedHashMap<>();
        for (int i = 0; i < nameThenSchema.length; i += 2) {
            properties.put((String) nameThenSchema[i], nameThenSchema[i + 1]);
        }
        return properties;
    }

    private static Map<String, Object> string(String description) {
        return typed("string", description);
    }

    private static Map<String, Object> integer(String description) {
        return typed("integer", description);
    }

    private static Map<String, Object> bool(String description) {
        return typed("boolean", description);
    }

    private static Map<String, Object> array(String description, Map<String, Object> items) {
        Map<String, Object> schema = typed("array", description);
        schema.put("items", items);
        return schema;
    }

    private static Map<String, Object> object(String description, Map<String, Object> valueSchema) {
        Map<String, Object> schema = typed("object", description);
        schema.put("additionalProperties", valueSchema);
        return schema;
    }

    private static Map<String, Object> typed(String type, String description) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", type);
        schema.put("description", description);
        return schema;
    }
}
