package org.modellwerkstatt.mcpme.core;

import org.modellwerkstatt.dataux.runtime.genspecification.IGenAppUiModule;
import org.modellwerkstatt.dataux.runtime.telemetrics.AppJmxRegistration;
import org.modellwerkstatt.dataux.runtime.telemetrics.Dux;
import org.modellwerkstatt.dataux.runtime.toolkit.IToolkit_UiFactory;
import org.modellwerkstatt.manmap.runtime.IM3DatabaseDescription;
import org.modellwerkstatt.objectflow.runtime.MoVersion;
import org.modellwerkstatt.mcpme.auth.IMcpAuthProvider;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Die Anwendung im Serverprozess: einmal je Webanwendung. Lädt die Spring-Konfiguration, erzeugt das
 * Anwendungsmodul und verwaltet die Anwendungsinstanzen der MCP-Sessions.
 *
 * Der Start folgt TurkuServlet.servletInitialized() von turkuforms, ohne Vaadin.
 */
public class McpApplication {

    private final String applicationFqName;
    private final String serverName;
    private final String deployedVersion;

    private final AbstractApplicationContext springContext;
    private final IGenAppUiModule appUiModule;
    private final McpAppFactory factory;
    private final AppJmxRegistration jmxRegistration;

    private final Map<String, AppInstance> instances = new ConcurrentHashMap<>();
    private final Map<String, String> signedInUsers = new ConcurrentHashMap<>();
    private final ScheduledExecutorService sweeper;

    /**
     * @param xmlConfigFile     Spring-Konfiguration der Anwendung auf dem Klassenpfad (aus der OFXConfig)
     * @param applicationFqName Klasse des AppUI Module
     * @param realPath          Verzeichnis der Webanwendung; trägt nach "##" die bereitgestellte Version
     * @param contextPath       Pfad der Webanwendung im Tomcat
     */
    public McpApplication(String xmlConfigFile, String applicationFqName, String realPath, String contextPath)
            throws Exception {
        this.applicationFqName = applicationFqName;
        this.serverName = System.getProperty("server.instancename", "local");
        this.deployedVersion = versionFromPath(realPath);

        // Die generierte Konfiguration verweist auf ${MOWARE_APP_PROPPATH}; ohne Wert scheitert das Laden.
        if (System.getProperty("MOWARE_APP_PROPPATH") == null && System.getenv("MOWARE_APP_PROPPATH") == null) {
            String fallback = System.getProperty("catalina.base", System.getProperty("user.home")) + "/conf";
            System.setProperty("MOWARE_APP_PROPPATH", fallback);
            Dux.hl("mcpme: MOWARE_APP_PROPPATH is not set, using " + fallback);
        }

        jmxRegistration = new AppJmxRegistration(applicationFqName,
                deployedVersion == null ? "mcp" : deployedVersion, realPath, contextPath);

        springContext = new ClassPathXmlApplicationContext(xmlConfigFile);

        Class<?> appClass = getClass().getClassLoader().loadClass(applicationFqName);
        appUiModule = (IGenAppUiModule) springContext.getAutowireCapableBeanFactory().createBean(appClass);
        String nameAndVersion = appUiModule.getShortAppName() + " " + appUiModule.getApplicationVersion();

        IToolkit_UiFactory configured = springContext.getBean(IToolkit_UiFactory.class);
        if (!(configured instanceof McpAppFactory)) {
            throw new IllegalStateException("The configuration " + xmlConfigFile + " declares "
                    + configured.getClass().getName() + " as app factory; the MCP server needs "
                    + McpAppFactory.class.getName() + ".");
        }
        factory = (McpAppFactory) configured;

        springContext.getBean(IM3DatabaseDescription.class).setSessionInfo(nameAndVersion + " " + serverName);
        factory.getEventBus().setSysInfo(factory.getMowarePlatform() + " " + serverName + ": " + nameAndVersion
                + " (" + AppInstance.DEVICE_NAME + ")");

        String version = deployedVersion == null ? appUiModule.getApplicationVersion() : deployedVersion.replace("_", ".");
        jmxRegistration.registerAppTelemetrics(factory, applicationFqName, appUiModule.getShortAppName(), version,
                MoVersion.MOWARE_PLUGIN_VERSION, serverName);
        if (factory.isAutoParDeploymentForwardGracefully()) {
            jmxRegistration.enableAutoForwardGracefully();
        }

        sweeper = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "mcpme-session-sweeper");
            thread.setDaemon(true);
            return thread;
        });
        sweeper.scheduleWithFixedDelay(this::closeIdleInstances, 1, 1, TimeUnit.MINUTES);

        Dux.hl("mcpme: " + nameAndVersion + " (" + applicationFqName + ") ready on " + contextPath
                + (factory.getFixedUser() == null ? "" : ", fixed user '" + factory.getFixedUser() + "'"));
    }

    public McpAppFactory getFactory() {
        return factory;
    }

    public String getAppNameAndVersion() {
        return appUiModule.getShortAppName() + " " + appUiModule.getApplicationVersion();
    }

    /**
     * Liefert die Anwendungsinstanz der MCP-Session und legt sie beim ersten Aufruf an.
     *
     * @param harness Name und Version des MCP-Clients
     */
    public AppInstance instanceFor(String sessionId, String harness) {
        return instances.computeIfAbsent(sessionId, id -> {
            String user = factory.getFixedUser();
            if (user == null) {
                user = signedInUsers.get(id);
            }
            if (user == null) {
                throw new IllegalStateException("No signed-in person for this session.");
            }
            return new AppInstance(id, user, harness, factory, appUiModule, jmxRegistration, serverName);
        });
    }

    /** Die Stelle für die Anmeldung aus der Konfiguration, oder null, wenn keine konfiguriert ist. */
    public IMcpAuthProvider getAuthProvider() {
        Map<String, IMcpAuthProvider> providers = springContext.getBeansOfType(IMcpAuthProvider.class);
        if (providers.size() > 1) {
            throw new IllegalStateException("More than one IMcpAuthProvider configured: " + providers.keySet());
        }
        return providers.isEmpty() ? null : providers.values().iterator().next();
    }

    /**
     * Bindet eine MCP-Session an die Person aus dem Zugriffstoken. Eine Session gehört für ihre ganze
     * Dauer einer Person.
     *
     * @return false, wenn die Session schon einer anderen Person gehört
     */
    public boolean bindUser(String sessionId, String user) {
        String bound = signedInUsers.putIfAbsent(sessionId, user);
        return bound == null || bound.equals(user);
    }

    /** Fährt die Instanz einer MCP-Session herunter, falls es eine gibt. */
    public void closeInstance(String sessionId) {
        signedInUsers.remove(sessionId);
        AppInstance instance = instances.remove(sessionId);
        if (instance != null) {
            shutdownQuietly(instance);
        }
    }

    private void closeIdleInstances() {
        long idleLimit = TimeUnit.MINUTES.toMillis(factory.getSessionIdleMinutes());
        long now = System.currentTimeMillis();
        for (AppInstance instance : new ArrayList<>(instances.values())) {
            if (now - instance.getLastUsedMillis() > idleLimit) {
                Dux.hl("mcpme: session " + instance.getSessionId() + " idle for more than "
                        + factory.getSessionIdleMinutes() + " minutes");
                closeInstance(instance.getSessionId());
            }
        }
    }

    /** Prozessende: alle Instanzen, Event-Bus, Monitoring, Datenbankverbindungen, Spring-Kontext. */
    public void stop() {
        sweeper.shutdownNow();
        List<AppInstance> open = new ArrayList<>(instances.values());
        instances.clear();
        for (AppInstance instance : open) {
            shutdownQuietly(instance);
        }
        try {
            factory.getEventBus().close();
        } catch (RuntimeException e) {
            Dux.hl("mcpme: closing the event bus failed: " + e);
        }
        try {
            jmxRegistration.gcClean();
        } catch (RuntimeException e) {
            Dux.hl("mcpme: unregistering telemetrics failed: " + e);
        }
        // Schließt auch die DataSource, sofern die Konfiguration eine destroy-method nennt.
        springContext.close();
        Dux.hl("mcpme: " + applicationFqName + " stopped");
    }

    private static void shutdownQuietly(AppInstance instance) {
        try {
            instance.shutdown();
        } catch (RuntimeException e) {
            Dux.hl("mcpme: shutting down session " + instance.getSessionId() + " failed: " + e);
        }
    }

    private static String versionFromPath(String realPath) {
        if (realPath == null) {
            return null;
        }
        int start = realPath.indexOf("##");
        if (start < 0 || realPath.length() <= start + 2) {
            return null;
        }
        String version = realPath.substring(start + 2);
        while (version.endsWith("/") || version.endsWith("\\")) {
            version = version.substring(0, version.length() - 1);
        }
        return version.isEmpty() ? null : version;
    }
}
