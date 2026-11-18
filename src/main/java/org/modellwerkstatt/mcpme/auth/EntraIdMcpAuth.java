package org.modellwerkstatt.mcpme.auth;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.TypeRef;
import org.joda.time.LocalDate;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Anmeldung über Entra ID. Die Angaben entsprechen EntraIdOAuth2 der Oberflächen: Tenant, Kennung und
 * Geheimnis der App-Registrierung, die Abfrage für den Benutzernamen und das Feld daraus.
 *
 * Der Benutzername kommt wie dort aus einer Abfrage (üblich: Microsoft Graph, /me). Das Token des
 * Clients gilt nur für den MCP-Server; für die Abfrage tauscht der Server es mit dem Geheimnis der
 * App-Registrierung gegen ein Token im Namen derselben Person (On-Behalf-Of). Ist keine Abfrage
 * angegeben, steht der Benutzername unter dem Feldnamen direkt im Token; ein Geheimnis braucht es dann
 * nicht.
 */
public class EntraIdMcpAuth implements IMcpAuthProvider {

    private final String tokenEndpoint;
    private final String issuer;
    private final String clientId;
    private final String clientSecret;
    private final String scope;
    private final String userInfoEndpoint;
    private final String usernameField;
    private LocalDate expirationDate;
    private boolean relay = true;

    /**
     * @param tenant         Tenant-ID
     * @param client_id      Client-ID der App-Registrierung des MCP-Servers (die mit der verfügbar gemachten API)
     * @param client_secret  ihr Geheimnis; nur nötig, wenn query_url gesetzt ist
     * @param scope_name     Name des Bereichs der API, etwa "access"
     * @param query_url      Abfrage für den Benutzernamen, etwa
     *                       https://graph.microsoft.com/v1.0/me?$select=onPremisesSamAccountName; leer = aus dem Token
     * @param username_field Feld der Antwort beziehungsweise Claim des Tokens mit dem Benutzernamen
     */
    public EntraIdMcpAuth(String tenant, String client_id, String client_secret, String scope_name, String query_url,
                          String username_field) {
        this.tokenEndpoint = "https://login.microsoftonline.com/" + tenant.trim() + "/oauth2/v2.0/token";
        this.issuer = "https://login.microsoftonline.com/" + tenant.trim() + "/v2.0";
        this.clientId = client_id.trim();
        this.clientSecret = client_secret == null ? "" : client_secret.trim();
        this.scope = scope_name.contains("://") ? scope_name.trim() : "api://" + this.clientId + "/" + scope_name.trim();
        this.userInfoEndpoint = query_url == null ? "" : query_url.trim();
        this.usernameField = username_field.trim();
    }

    /** Format yyyy-MM-dd, wie bei EntraIdOAuth2. */
    public void setExpirationDate(String val) {
        try {
            expirationDate = new LocalDate(val);
        } catch (Exception e) {
            expirationDate = null;
        }
    }

    public String getExpirationDate() {
        return "" + expirationDate;
    }

    public void setRelay(boolean relay) {
        this.relay = relay;
    }

    @Override
    public String getAuthProviderName() {
        return "EntraID";
    }

    @Override
    public LocalDate getNullOrCredentialExpirationDate() {
        return expirationDate;
    }

    @Override
    public String getIssuer() {
        return issuer;
    }

    @Override
    public List<String> getAudiences() {
        // Token im Format 2 tragen die Client-ID, ältere die Anwendungs-ID-URI.
        return List.of(clientId, "api://" + clientId);
    }

    @Override
    public String getScope() {
        return scope;
    }

    @Override
    public boolean isRelay() {
        return relay;
    }

    @Override
    public String retrieveUser(String accessToken, Map<String, Object> claims) throws IOException {
        if (userInfoEndpoint.isEmpty()) {
            Object value = claims.get(usernameField);
            if (!(value instanceof String name) || name.isBlank()) {
                throw new IOException("The token has no claim '" + usernameField + "'; claims: " + claims.keySet());
            }
            return name.trim();
        }

        // Token im Namen derselben Person für die Abfrage holen (On-Behalf-Of).
        URI query = URI.create(userInfoEndpoint);
        String form = "grant_type=" + enc("urn:ietf:params:oauth:grant-type:jwt-bearer")
                + "&client_id=" + enc(clientId)
                + "&client_secret=" + enc(clientSecret)
                + "&assertion=" + enc(accessToken)
                + "&scope=" + enc(query.getScheme() + "://" + query.getHost() + "/.default")
                + "&requested_token_use=on_behalf_of";
        Map<String, Object> token = json(send(HttpRequest.newBuilder(URI.create(tokenEndpoint))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build()));
        if (!(token.get("access_token") instanceof String queryToken)) {
            throw new IOException("Did not receive a token for the user query: " + token.get("error") + " "
                    + token.get("error_description"));
        }

        Map<String, Object> user = json(send(HttpRequest.newBuilder(query)
                .header("Authorization", "Bearer " + queryToken).GET().build()));
        Object value = user.get(usernameField);
        if (!(value instanceof String name) || name.isBlank()) {
            throw new IOException("The field " + usernameField + " was not found or is empty in the answer of "
                    + userInfoEndpoint + "; fields: " + user.keySet());
        }
        return name.trim();
    }

    private static String send(HttpRequest request) throws IOException {
        try {
            return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
    }

    private static Map<String, Object> json(String text) throws IOException {
        return McpJsonDefaults.getMapper().readValue(text, new TypeRef<Map<String, Object>>() {
        });
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    @Override
    public String toString() {
        return getAuthProviderName() + " " + issuer;
    }
}
