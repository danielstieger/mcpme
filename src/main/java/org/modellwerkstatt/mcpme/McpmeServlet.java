package org.modellwerkstatt.mcpme;

import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.UnavailableException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.modellwerkstatt.dataux.runtime.telemetrics.Dux;
import io.modelcontextprotocol.json.McpJsonDefaults;
import org.joda.time.LocalDate;
import org.modellwerkstatt.mcpme.auth.IMcpAuthProvider;
import org.modellwerkstatt.mcpme.core.McpApplication;
import org.modellwerkstatt.mcpme.core.TokenVerifier;
import org.modellwerkstatt.mcpme.tools.ToolHandlers;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Einstiegspunkt des MCP-Servers im Tomcat.
 *
 * Beim Start der Webanwendung lädt das Servlet die Anwendung (Init-Parameter wie bei turkuforms:
 * {@code xmlConfigFile} und {@code applicationFqName}) und baut den MCP-Server des SDK auf. Danach
 * reicht es jeden Request an dessen Transport weiter; der Transport ist selbst ein Servlet.
 */
public class McpmeServlet extends HttpServlet {

    /** Pfad des MCP-Endpunkts innerhalb der Webanwendung. */
    public static final String MCP_ENDPOINT = "/mcp";

    private static final String SERVER_NAME = "mcpme";
    private static final String SERVER_VERSION = "0.1";
    private static final String SESSION_HEADER = "Mcp-Session-Id";
    private static final String METADATA_PATH = "/.well-known/oauth-protected-resource";
    private static final String RELAY_PATH = "/oauth";

    private HttpServletStreamableServerTransportProvider transport;
    private McpSyncServer mcpServer;
    private McpApplication application;
    private TokenVerifier verifier;
    private IMcpAuthProvider authProvider;

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        String contextPath = config.getServletContext().getContextPath();

        Dux.init(logFile());

        try {
            String xmlConfigFile = param(config, "xmlConfigFile");
            String applicationFqName = param(config, "applicationFqName");
            if (xmlConfigFile != null && applicationFqName != null) {
                application = new McpApplication(xmlConfigFile, applicationFqName,
                        config.getServletContext().getRealPath("/"), contextPath);
            } else {
                Dux.hl("mcpme: no application configured (xmlConfigFile / applicationFqName), tools will refuse.");
            }
        } catch (Exception e) {
            log("mcpme: starting the application failed", e);
            Dux.hl("mcpme: starting the application failed: " + e);
            throw new UnavailableException("mcpme unavailable: " + e.getClass().getSimpleName() + " " + e.getMessage());
        }

        if (application != null && application.getFactory().getFixedUser() == null) {
            authProvider = application.getAuthProvider();
            if (authProvider == null) {
                throw new UnavailableException("mcpme unavailable: neither FixedUser at the AppFactory nor an "
                        + "IMcpAuthProvider (for example EntraIdMcpAuth) is configured.");
            }
            verifier = new TokenVerifier(authProvider);
            Dux.hl("mcpme: sign-in through " + authProvider);
            LocalDate expires = authProvider.getNullOrCredentialExpirationDate();
            if (expires != null && !expires.isAfter(new LocalDate().plusDays(30))) {
                Dux.hl("mcpme: WARNING - credentials for " + authProvider + " will expire at " + expires + "!");
                log("mcpme: credentials for " + authProvider + " will expire at " + expires);
            }
        }

        transport = HttpServletStreamableServerTransportProvider.builder()
                .mcpEndpoint(MCP_ENDPOINT)
                .build();

        ToolHandlers handlers = new ToolHandlers(application);
        List<SyncToolSpecification> tools = new ArrayList<>();
        for (Tool tool : ToolCatalog.all()) {
            tools.add(SyncToolSpecification.builder()
                    .tool(tool)
                    .callHandler(handlers::call)
                    .build());
        }

        String appInfo = application == null ? "no application" : application.getAppNameAndVersion();
        mcpServer = McpServer.sync(transport)
                .serverInfo(SERVER_NAME, SERVER_VERSION)
                .instructions(instructions(appInfo))
                .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build())
                .tools(tools)
                .build();

        // Der Transport ist ein Servlet und braucht dieselbe Konfiguration wie dieses.
        transport.init(config);

        log("mcpme: MCP server ready at " + contextPath + MCP_ENDPOINT + " for " + appInfo);
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String path = request.getServletPath() + (request.getPathInfo() == null ? "" : request.getPathInfo());
        if (path.startsWith(METADATA_PATH)) {
            writeResourceMetadata(request, response);
            return;
        }
        if (path.startsWith("/.well-known/") || path.startsWith(RELAY_PATH)) {
            relay(path, request, response);
            return;
        }
        if (verifier != null && !signedIn(request, response)) {
            return;
        }
        String endedSession = "DELETE".equals(request.getMethod()) ? request.getHeader(SESSION_HEADER) : null;

        transport.service(request, response);

        // Der Client hat seine MCP-Session beendet: die Anwendungsinstanz dazu herunterfahren.
        if (endedSession != null && application != null) {
            application.closeInstance(endedSession);
        }
    }

    /**
     * Prüft das Zugriffstoken des Requests und bindet die MCP-Session an die Person daraus. Ohne gültiges
     * Token antwortet der Server mit 401 und nennt dem Client, wo die Angaben zur Anmeldung stehen.
     */
    private boolean signedIn(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String header = request.getHeader("Authorization");
        String user = null;
        String problem = "no access token";
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            try {
                user = verifier.userOf(header.substring(7).trim());
            } catch (TokenVerifier.Rejected e) {
                problem = e.getMessage();
                Dux.hl("mcpme: " + problem);
            }
        }
        if (user == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setHeader("WWW-Authenticate", "Bearer resource_metadata=\"" + baseUrl(request) + METADATA_PATH
                    + "\"" + (header == null ? "" : ", error=\"invalid_token\""));
            response.setContentType("text/plain");
            response.getWriter().write("Sign-in required: " + problem);
            return false;
        }
        String session = request.getHeader(SESSION_HEADER);
        if (session != null && !application.bindUser(session, user)) {
            Dux.hl("mcpme: session " + session + " used with a token of another person (" + user + ")");
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "This session belongs to another person.");
            return false;
        }
        return true;
    }

    /** Angaben für Clients nach RFC 9728: welche Ressource dies ist und wer die Token dafür ausstellt. */
    private void writeResourceMetadata(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (verifier == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("resource", baseUrl(request) + MCP_ENDPOINT);
        metadata.put("authorization_servers",
                List.of(authProvider.isRelay() ? baseUrl(request) : authProvider.getIssuer()));
        if (authProvider.getScope() != null) {
            metadata.put("scopes_supported", List.of(authProvider.getScope()));
        }
        metadata.put("bearer_methods_supported", List.of("header"));
        response.setContentType("application/json");
        response.getWriter().write(McpJsonDefaults.getMapper().writeValueAsString(metadata));
    }

    /**
     * Vermittelte Anmeldung (Einstellung OAuthRelay): Der Server nennt sich dem Client als Stelle für die
     * Anmeldung und reicht Anmeldung und Tokenabruf an den Aussteller weiter, ohne den Parameter
     * 'resource'. Er stellt selbst nichts aus und sieht kein Kennwort; die Token kommen vom Aussteller.
     */
    private void relay(String path, HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (verifier == null || !authProvider.isRelay()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        try {
            Map<String, Object> issuer = verifier.issuerMetadata();
            String base = baseUrl(request);
            if (path.startsWith("/.well-known/")) {
                Map<String, Object> metadata = new LinkedHashMap<>();
                metadata.put("issuer", base);
                metadata.put("authorization_endpoint", base + RELAY_PATH + "/authorize");
                metadata.put("token_endpoint", base + RELAY_PATH + "/token");
                metadata.put("response_types_supported", List.of("code"));
                metadata.put("grant_types_supported", List.of("authorization_code", "refresh_token"));
                metadata.put("code_challenge_methods_supported", List.of("S256"));
                metadata.put("token_endpoint_auth_methods_supported", List.of("none"));
                // Clients erreichen diese Angaben unter dem Pfad der Webanwendung nur als OpenID-Konfiguration
                // und verlangen dann deren Pflichtangaben.
                metadata.put("jwks_uri", issuer.get("jwks_uri"));
                metadata.put("subject_types_supported", List.of("public"));
                metadata.put("id_token_signing_alg_values_supported", List.of("RS256"));
                if (authProvider.getScope() != null) {
                    metadata.put("scopes_supported", List.of(authProvider.getScope()));
                }
                response.setContentType("application/json");
                response.getWriter().write(McpJsonDefaults.getMapper().writeValueAsString(metadata));

            } else if (path.equals(RELAY_PATH + "/authorize")) {
                response.sendRedirect(issuer.get("authorization_endpoint") + "?" + formWithoutResource(request));

            } else if (path.equals(RELAY_PATH + "/token") && "POST".equals(request.getMethod())) {
                HttpResponse<String> answer = HttpClient.newHttpClient().send(
                        HttpRequest.newBuilder(URI.create((String) issuer.get("token_endpoint")))
                                .header("Content-Type", "application/x-www-form-urlencoded")
                                .POST(HttpRequest.BodyPublishers.ofString(formWithoutResource(request)))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
                if (answer.statusCode() != 200) {
                    Dux.hl("mcpme: issuer refused the token request: " + answer.statusCode() + " " + answer.body());
                }
                response.setStatus(answer.statusCode());
                response.setContentType("application/json");
                response.setHeader("Cache-Control", "no-store");
                response.getWriter().write(answer.body());

            } else {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            Dux.hl("mcpme: relaying the sign-in failed: " + e);
            response.sendError(HttpServletResponse.SC_BAD_GATEWAY, "The issuer could not be reached.");
        }
    }

    /** Die Parameter des Requests als Formulartext, ohne 'resource'. */
    private static String formWithoutResource(HttpServletRequest request) {
        StringBuilder form = new StringBuilder();
        for (Map.Entry<String, String[]> parameter : request.getParameterMap().entrySet()) {
            if ("resource".equals(parameter.getKey())) {
                continue;
            }
            for (String value : parameter.getValue()) {
                form.append(form.length() == 0 ? "" : "&")
                        .append(URLEncoder.encode(parameter.getKey(), StandardCharsets.UTF_8)).append('=')
                        .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
            }
        }
        return form.toString();
    }

    /** Adresse der Webanwendung, wie Clients sie sehen: aus der Einstellung PublicUrl oder aus dem Request. */
    private String baseUrl(HttpServletRequest request) {
        String configured = application.getFactory().getPublicUrl();
        if (configured != null) {
            String url = configured.endsWith(MCP_ENDPOINT)
                    ? configured.substring(0, configured.length() - MCP_ENDPOINT.length()) : configured;
            return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        }
        boolean defaultPort = request.getServerPort() == ("https".equals(request.getScheme()) ? 443 : 80);
        return request.getScheme() + "://" + request.getServerName()
                + (defaultPort ? "" : ":" + request.getServerPort()) + request.getContextPath();
    }

    @Override
    public void destroy() {
        try {
            if (mcpServer != null) {
                mcpServer.closeGracefully();
            }
        } finally {
            if (transport != null) {
                transport.destroy();
            }
            if (application != null) {
                application.stop();
            }
            super.destroy();
        }
    }

    private static String instructions(String appInfo) {
        return "This server operates the business application '" + appInfo + "' on behalf of the signed-in person, "
                + "with that person's permissions. "
                + "The application consists of commands. Call get_overview to see which commands can be started. "
                + "A started command becomes the active command; every changing tool returns its state. "
                + "Conclusion actions are the steps that complete or advance the current page of a command "
                + "(in the application's model: page conclusions).";
    }

    /** Init-Parameter; ein nicht ersetzter Platzhalter der web.xml-Vorlage gilt als nicht gesetzt. */
    private static String param(ServletConfig config, String name) {
        String value = config.getInitParameter(name);
        if (value == null || value.isBlank() || value.startsWith("APPLICATION_")) {
            return null;
        }
        return value.trim();
    }

    private static String logFile() {
        String base = System.getProperty("catalina.base");
        return (base == null ? System.getProperty("user.home") : base + "/logs") + "/mcpme.log";
    }
}
