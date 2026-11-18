package org.modellwerkstatt.addons.components;

import org.modellwerkstatt.addons.components.support.MultiSeriesUxListBound;
import org.modellwerkstatt.mcpme.ui.McpChartForm;

/**
 * Liniendiagramm der Anwendung. Unter demselben Namen wie in den Oberflächen, damit das Modell es
 * unverändert verwendet; der Agent erhält statt einer Grafik die Daten.
 */
public class LineChartUxElement<T> extends MultiSeriesUxListBound<T> {

    public LineChartUxElement() {
        super();
        setToolkitFormImpl(McpChartForm.line(this));
    }
}
