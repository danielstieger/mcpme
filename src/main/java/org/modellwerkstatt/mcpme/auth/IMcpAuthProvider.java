package org.modellwerkstatt.mcpme.auth;

import org.joda.time.LocalDate;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Die Stelle, bei der sich Personen für den MCP-Server anmelden. Das Gegenstück zu IExtAuthProvider der
 * Oberflächen: Dort holt die Anwendung das Token selbst, hier bringt der Client es mit und der Server
 * prüft es. Ein Provider steht als eigener Baustein in der Konfiguration, üblicherweise in einer
 * Section, die nur Kennungen und Geheimnisse enthält.
 */
public interface IMcpAuthProvider {

    String getAuthProviderName();

    /** Ablaufdatum des Geheimnisses, oder null. */
    LocalDate getNullOrCredentialExpirationDate();

    /** Aussteller der Zugriffstoken, wie er im Token steht. */
    String getIssuer();

    /** Zulässige Werte für die Audience der Token. */
    List<String> getAudiences();

    /** Der Bereich, den ein Client anfordert, oder null. */
    String getScope();

    /** Der Server vermittelt Anmeldung und Tokenabruf und lässt den Parameter 'resource' weg. */
    boolean isRelay();

    /**
     * Der Benutzername der Anwendung für ein geprüftes Token.
     *
     * @param accessToken das Token, wie der Client es geschickt hat
     * @param claims      seine geprüften Angaben
     */
    String retrieveUser(String accessToken, Map<String, Object> claims) throws IOException;
}
