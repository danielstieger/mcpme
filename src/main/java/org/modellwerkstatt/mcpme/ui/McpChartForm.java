package org.modellwerkstatt.mcpme.ui;

import org.modellwerkstatt.addons.components.support.CustomDataUxListBound;
import org.modellwerkstatt.addons.components.support.MultiSeriesUxListBound;
import org.modellwerkstatt.dataux.runtime.customcomponents.ExtCmpt;
import org.modellwerkstatt.objectflow.runtime.IOFXSelection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ein Diagramm einer Seite. Es zeichnet nichts, sondern hält die Daten, aus denen eine Oberfläche das
 * Diagramm zeichnen würde; der Agent wertet sie selbst aus.
 */
public class McpChartForm<T> extends McpForm<T> {

    private final CustomDataUxListBound<T> pie;
    private final MultiSeriesUxListBound<T> line;
    private Map<String, Object> data = new LinkedHashMap<>();

    private McpChartForm(CustomDataUxListBound<T> pie, MultiSeriesUxListBound<T> line) {
        this.pie = pie;
        this.line = line;
    }

    public static <T> McpChartForm<T> pie(CustomDataUxListBound<T> provider) {
        return new McpChartForm<>(provider, null);
    }

    public static <T> McpChartForm<T> line(MultiSeriesUxListBound<T> provider) {
        return new McpChartForm<>(null, provider);
    }

    /** Die Daten des Diagramms, wie zuletzt geladen. */
    public Map<String, Object> getData() {
        return data;
    }

    @Override
    public void loadList(List<T> objects, IOFXSelection<T> selection) {
        data = pie != null ? pieData(objects) : lineData(objects);
    }

    private Map<String, Object> pieData(List<T> objects) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("kind", "pie");
        if (!objects.isEmpty() && pie.hasLabel(ExtCmpt.PIECHART_TITEL_ON_FIRST_ITEM)) {
            result.put("title", pie.getString(objects.get(0), ExtCmpt.PIECHART_TITEL_ON_FIRST_ITEM));
        }
        List<List<Object>> items = new ArrayList<>();
        for (T object : objects) {
            List<Object> item = new ArrayList<>();
            item.add(pie.getString(object, ExtCmpt.PIECHART_ITEM_LABEL));
            item.add(pie.getBigDecimal(object, ExtCmpt.PIECHART_ITEM_VALUE));
            items.add(item);
        }
        result.put("items", items);
        return result;
    }

    private Map<String, Object> lineData(List<T> objects) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("kind", "line");
        if (line.getOption(ExtCmpt.LINECHART_TITLE) != null) {
            result.put("title", line.getOption(ExtCmpt.LINECHART_TITLE));
        }
        if (line.getOption(ExtCmpt.LINECHART_YTITLE) != null) {
            result.put("yTitle", line.getOption(ExtCmpt.LINECHART_YTITLE));
        }
        List<String> x = new ArrayList<>();
        for (T object : objects) {
            x.add(line.getXValueAsString(object));
        }
        result.put("x", x);

        List<Map<String, Object>> allSeries = new ArrayList<>();
        for (MultiSeriesUxListBound.Series series : line.getSeries()) {
            List<Object> values = new ArrayList<>();
            for (T object : objects) {
                values.add(line.getYValueAsBigDecimal(object, series.getName()));
            }
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("name", series.getName());
            one.put("values", values);
            allSeries.add(one);
        }
        result.put("series", allSeries);
        return result;
    }

    @Override
    public void setTitleText(String text) {
        // Ein Diagramm bezieht seinen Titel aus den Daten oder den Optionen.
    }
}
