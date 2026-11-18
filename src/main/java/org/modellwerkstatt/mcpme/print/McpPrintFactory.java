package org.modellwerkstatt.mcpme.print;

import org.modellwerkstatt.mcpme.core.McpMainWindow;
import org.modellwerkstatt.objectflow.runtime.IOFXPrintFactory;
import org.modellwerkstatt.objectflow.runtime.IOFXUserEnvironment;
import org.modellwerkstatt.objectflow.runtime.IPrintingServiceImpl;
import org.modellwerkstatt.objectflow.runtime.OFXFatClientFopUserPrintService;

import java.io.File;

/**
 * Druckfabrik des MCP-Servers. Sie nimmt dieselben Angaben wie die von turkuforms, damit sich die
 * Konfiguration einer Anwendung übernehmen lässt: Ausgabeverzeichnis und der HTTP-Pfad, unter dem
 * ein Webserver es ausliefert.
 *
 * Ein Dokument wird als PDF ins Ausgabeverzeichnis geschrieben; der Agent erhält die URL im Ergebnis
 * des Aufrufs, der es erzeugt hat. Die URL ist nicht geschützt; den Schutz des Verzeichnisses regelt
 * der Betrieb.
 */
public class McpPrintFactory implements IOFXPrintFactory {

    protected final String templateClassLoaderFqName;
    protected final String fallBackFileSystemTemplateLoadingPath;
    protected final String printerOutputPath;
    protected final String httpServedPath;
    protected final boolean useFoplandModifier;
    protected final String fontPath;

    public McpPrintFactory(String templateClassLoaderFqName, String fallBackFileSystemTemplateLoadingPath,
                           String printerOutputPath, String httpServedPath, boolean useFoplandModifier,
                           String fontPath) {
        this.templateClassLoaderFqName = templateClassLoaderFqName.trim();
        this.fallBackFileSystemTemplateLoadingPath = fallBackFileSystemTemplateLoadingPath.trim();
        this.printerOutputPath = printerOutputPath.trim();
        this.httpServedPath = httpServedPath.trim();
        this.useFoplandModifier = useFoplandModifier;
        this.fontPath = fontPath;
        if (this.printerOutputPath.isEmpty() || this.httpServedPath.isEmpty()) {
            throw new RuntimeException("Output Path as well as http-served Path must be given.");
        }
    }

    @Override
    public IPrintingServiceImpl createConfiguredUserPrintService(Object tecHandle, IOFXUserEnvironment userEnvironment) {
        // Die Runtime übergibt das Hauptfenster der Anwendungsinstanz.
        UserPrintService service = new UserPrintService((McpMainWindow) tecHandle, httpServedPath, userEnvironment,
                templateClassLoaderFqName, fallBackFileSystemTemplateLoadingPath, printerOutputPath, useFoplandModifier);
        service.selfconfigureFopFactory(fontPath);
        return service;
    }

    @Override
    public void gcClean() {
    }

    private static final class UserPrintService extends OFXFatClientFopUserPrintService {

        private final McpMainWindow mainWindow;
        private final String httpServedPath;

        UserPrintService(McpMainWindow mainWindow, String httpServedPath, IOFXUserEnvironment userEnvironment,
                         String templateClassLoaderFqName, String fallBackDir, String outputPath, boolean useFopland) {
            super(userEnvironment, templateClassLoaderFqName, fallBackDir, outputPath, useFopland, false, true);
            this.mainWindow = mainWindow;
            this.httpServedPath = httpServedPath;
        }

        @Override
        public void view(File pdfFile) {
            if (pdfFile == null) {
                throw new RuntimeException("Given pdfFile for view() is null!");
            }
            String remainder = pdfFile.getAbsolutePath().substring(outputFileDir.length());
            if (remainder.startsWith("/")) {
                remainder = remainder.substring(1);
            }
            mainWindow.addDocument("document", pdfFile.getName(), httpServedPath + "/" + remainder);
        }

        @Override
        public void print(File pdfFile) {
            // Ein Agent hat keinen Drucker; er erhält das Dokument wie beim Ansehen.
            view(pdfFile);
        }

        @Override
        public void openUrl(String url) {
            mainWindow.addDocument("link", null, url);
        }
    }
}
