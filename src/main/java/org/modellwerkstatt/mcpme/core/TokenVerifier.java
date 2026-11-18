package org.modellwerkstatt.mcpme.core;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.TypeRef;
import org.modellwerkstatt.mcpme.auth.IMcpAuthProvider;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Prüft die Zugriffstoken, mit denen sich ein Client ausweist. Der Server stellt selbst keine Token
 * aus; er vertraut einem Aussteller (etwa Entra ID) und prüft Signatur, Aussteller, Audience und
 * Ablauf. Die Schlüssel holt er beim Aussteller ab.
 */
public final class TokenVerifier {

    /** Das Token wird nicht angenommen; der Text ist für das Protokoll und den Client. */
    public static final class Rejected extends Exception {
        public Rejected(String message) {
            super(message);
        }
    }

    private final IMcpAuthProvider provider;
    private final String issuer;
    private final List<String> audiences;
    /** Geprüfte Token und ihr Benutzername, damit Schlüsselprüfung und Abfrage nicht bei jedem Aufruf laufen. */
    private final Map<String, String> knownTokens = new ConcurrentHashMap<>();
    private DefaultJWTProcessor<SecurityContext> processor;
    private Map<String, Object> issuerMetadata;

    public TokenVerifier(IMcpAuthProvider provider) {
        this.provider = provider;
        this.issuer = provider.getIssuer();
        this.audiences = provider.getAudiences();
    }

    /** Prüft das Token und liefert den Benutzernamen daraus. */
    public String userOf(String token) throws Rejected {
        JWTClaimsSet claims;
        try {
            claims = processor().process(token, null);
        } catch (Exception e) {
            throw new Rejected("token not accepted: " + e.getMessage());
        }
        if (!issuer.equals(claims.getIssuer())) {
            throw new Rejected("token issued by '" + claims.getIssuer() + "', expected '" + issuer + "'");
        }
        boolean forThisServer = false;
        for (String audience : claims.getAudience()) {
            forThisServer |= audiences.contains(audience);
        }
        if (!forThisServer) {
            throw new Rejected("token is for " + claims.getAudience() + ", expected one of " + audiences);
        }
        if (claims.getExpirationTime() == null || claims.getExpirationTime().before(new Date())) {
            throw new Rejected("token expired");
        }
        // Ein Token gilt höchstens bis zu seinem Ablauf; bis dahin bleibt der Benutzername derselbe.
        String known = knownTokens.get(token);
        if (known != null) {
            return known;
        }
        String user;
        try {
            user = provider.retrieveUser(token, claims.getClaims());
        } catch (Exception e) {
            throw new Rejected("user name could not be determined: " + e.getMessage());
        }
        if (knownTokens.size() > 500) {
            knownTokens.clear();
        }
        knownTokens.put(token, user);
        return user;
    }

    /** Die Angaben des Ausstellers über sich selbst (OpenID-Konfiguration), einmal geholt. */
    public synchronized Map<String, Object> issuerMetadata() throws Exception {
        if (issuerMetadata == null) {
            String base = issuer.endsWith("/") ? issuer.substring(0, issuer.length() - 1) : issuer;
            HttpResponse<String> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create(base + "/.well-known/openid-configuration")).build(),
                    HttpResponse.BodyHandlers.ofString());
            issuerMetadata = McpJsonDefaults.getMapper().readValue(response.body(),
                    new TypeRef<Map<String, Object>>() {
                    });
        }
        return issuerMetadata;
    }

    /** Der Prüfer entsteht beim ersten Token; die Schlüssel des Ausstellers hält und erneuert die Bibliothek. */
    private synchronized DefaultJWTProcessor<SecurityContext> processor() throws Exception {
        if (processor == null) {
            Map<String, Object> metadata = issuerMetadata();
            Object jwksUri = metadata.get("jwks_uri");
            if (!(jwksUri instanceof String)) {
                throw new IllegalStateException("issuer metadata of " + issuer + " has no jwks_uri");
            }
            JWKSource<SecurityContext> keys = JWKSourceBuilder.create(URI.create((String) jwksUri).toURL()).build();
            DefaultJWTProcessor<SecurityContext> created = new DefaultJWTProcessor<>();
            created.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keys));
            processor = created;
        }
        return processor;
    }
}
