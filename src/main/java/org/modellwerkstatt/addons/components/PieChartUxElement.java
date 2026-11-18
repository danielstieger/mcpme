package org.modellwerkstatt.addons.components;

import org.modellwerkstatt.addons.components.support.CustomDataUxListBound;
import org.modellwerkstatt.mcpme.ui.McpChartForm;

/**
 * Tortendiagramm der Anwendung. Unter demselben Namen wie in den Oberflächen, damit das Modell es
 * unverändert verwendet; der Agent erhält statt einer Grafik die Daten.
 */
public class PieChartUxElement<T> extends CustomDataUxListBound<T> {

    public PieChartUxElement() {
        super();
        setToolkitFormImpl(McpChartForm.pie(this));
    }
}
