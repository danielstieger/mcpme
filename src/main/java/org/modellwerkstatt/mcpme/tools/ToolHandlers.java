package org.modellwerkstatt.mcpme.tools;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.modellwerkstatt.dataux.runtime.telemetrics.Dux;
import org.modellwerkstatt.mcpme.ToolCatalog;
import org.modellwerkstatt.mcpme.core.AppInstance;
import org.modellwerkstatt.mcpme.core.McpApplication;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Führt die Werkzeugaufrufe aus: ordnet den Aufruf der Anwendungsinstanz seiner MCP-Session zu und
 * bildet aus deren Antwort das Ergebnis für den Agenten.
 */
public class ToolHandlers {

    private final McpApplication application;

    /** @param application die gestartete Anwendung, oder null, wenn das WAR ohne Anwendung läuft */
    public ToolHandlers(McpApplication application) {
        this.application = application;
    }

    public CallToolResult call(McpSyncServerExchange exchange, CallToolRequest request) {
        String tool = request.name();
        try {
            if (application == null) {
                return error("This server has no application configured.");
            }
            AppInstance instance = application.instanceFor(exchange.sessionId(), harnessOf(exchange));

            Map<String, Object> args = request.arguments() == null ? Map.of() : request.arguments();
            switch (tool) {
                case ToolCatalog.GET_OVERVIEW:
                    return result(instance.overview());
                case ToolCatalog.GET_STATE:
                    return result(instance.state());
                case ToolCatalog.START_COMMAND:
                    return result(startCommand(instance, args));
                case ToolCatalog.DESCRIBE_COMMAND:
                    return result(instance.describeCommand((String) args.get("command")));
                case ToolCatalog.GET_ROWS:
                    return result(instance.rows((String) args.get("table"), integer(args, "from"), integer(args, "to"),
                            (String) args.get("search")));
                case ToolCatalog.SELECT_ROWS:
                    return result(instance.selectRows((String) args.get("table"), positions(args.get("rows"))));
                case ToolCatalog.SWITCH_COMMAND:
                    return result(instance.switchCommand((String) args.get("command")));
                case ToolCatalog.SET_FIELDS:
                    return result(instance.setFields(map(args, "fields")));
                case ToolCatalog.CONCLUDE:
                    return result(instance.conclude(((Number) args.get("action")).intValue(), map(args, "fields")));
                case ToolCatalog.CANCEL_COMMAND:
                    return result(instance.cancel(Boolean.TRUE.equals(args.get("discard_changes"))));
                default:
                    return error("Tool '" + tool + "' is not implemented yet.");
            }

        } catch (AppInstance.Refusal e) {
            return error(e.getMessage());

        } catch (AppInstance.LoginRefusedException e) {
            return error("Sign-in refused by the application: " + e.getMessage());

        } catch (Exception e) {
            // Unerwartet: vollständig ins Protokoll, knapp an den Agenten.
            StringWriter trace = new StringWriter();
            e.printStackTrace(new PrintWriter(trace));
            Dux.hl("mcpme: tool '" + tool + "' failed in session " + exchange.sessionId() + "\n" + trace);
            return error("Tool '" + tool + "' failed: " + e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : " - " + e.getMessage()));
        }
    }

    private static String harnessOf(McpSyncServerExchange exchange) {
        McpSchema.Implementation client = exchange.getClientInfo();
        if (client == null) {
            return "";
        }
        String name = client.name() == null ? "" : client.name();
        String version = client.version() == null ? "" : client.version();
        return (name + " " + version).trim();
    }

    @SuppressWarnings("unchecked")
    private static AppInstance.Outcome startCommand(AppInstance instance, Map<String, Object> args) {
        String entry = (String) args.get("entry");
        String label = (String) args.get("label");
        String command = (String) args.get("command");
        if (args.get("params") != null) {
            if (command == null) {
                throw new AppInstance.Refusal("A direct start with 'params' needs 'command', the fully qualified name.");
            }
            return instance.startDirect(command, (List<Object>) args.get("params"));
        }
        if (entry == null && label == null && command == null) {
            throw new AppInstance.Refusal("Name what to start: 'entry', 'label' or 'command'.");
        }
        return instance.startFromContext(entry, label, command, (String) args.get("table"),
                args.get("rows") == null ? null : positions(args.get("rows")));
    }

    private static Integer integer(Map<String, Object> args, String name) {
        Object value = args.get(name);
        return value == null ? null : ((Number) value).intValue();
    }

    private static List<Integer> positions(Object value) {
        List<Integer> positions = new ArrayList<>();
        for (Object item : (List<?>) value) {
            positions.add(((Number) item).intValue());
        }
        return positions;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Map<String, Object> args, String name) {
        Object value = args.get(name);
        return value == null ? Map.of() : (Map<String, Object>) value;
    }

    /** Dasselbe Ergebnis strukturiert und als JSON-Text, für Clients, die nur Text auswerten. */
    private static CallToolResult result(AppInstance.Outcome outcome) throws IOException {
        return CallToolResult.builder()
                .structuredContent(outcome.state)
                .addTextContent(McpJsonDefaults.getMapper().writeValueAsString(outcome.state))
                .isError(outcome.error)
                .build();
    }

    private static CallToolResult error(String message) {
        return CallToolResult.builder()
                .addTextContent(message)
                .isError(true)
                .build();
    }
}
