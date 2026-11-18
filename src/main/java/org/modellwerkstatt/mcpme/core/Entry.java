package org.modellwerkstatt.mcpme.core;

import org.modellwerkstatt.dataux.runtime.core.CompoundOuterCmdStart;
import org.modellwerkstatt.dataux.runtime.genspecification.CmdAction;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ein Eintrag, über den sich ein Command aus dem Kontext starten lässt: der Command, seine Beschriftung
 * und die Parameter, die das Modell an dieser Stelle vorsieht. Derselbe Command kann mehrfach
 * vorkommen, auch mit gleicher Beschriftung; die Kennung aus Herkunft und Position unterscheidet sie.
 */
public final class Entry {

    public final String id;
    public final CmdAction action;
    public final String origin;
    public final String group;

    Entry(String id, CmdAction action, String origin, String group) {
        this.id = id;
        this.action = action;
        this.origin = origin;
        this.group = group;
    }

    public String label() {
        return action.labelText == null ? "" : action.labelText;
    }

    /** Fragt die Runtime, ob der Command an dieser Stelle jetzt startbar ist. */
    public boolean isStartable() {
        return action.reevalEnabled();
    }

    /**
     * Der zusammengesetzte Start hinter diesem Eintrag, oder null. Die Runtime legt ihn erst beim Start
     * an und bietet keine Abfrage dafür; der Server ruft deshalb dieselbe Methode wie der Start auf.
     */
    private CompoundOuterCmdStart compound() {
        try {
            for (Class<?> type = action.getClass(); type != null; type = type.getSuperclass()) {
                try {
                    Method method = type.getDeclaredMethod("configCompoundOuterCmdStartOrJustBasisCmdStart", Object[].class);
                    method.setAccessible(true);
                    Object start = method.invoke(action, new Object[]{null});
                    return start instanceof CompoundOuterCmdStart compound && compound.hasInnerGe() ? compound : null;
                } catch (NoSuchMethodException e) {
                    // in der Oberklasse weitersuchen
                }
            }
        } catch (Exception e) {
            // ohne Auskunft gilt der Eintrag als einfacher Start
        }
        return null;
    }

    /** Der Command, den der Eintrag dem Agenten nennt: bei einem zusammengesetzten Start der innere. */
    public String command() {
        CompoundOuterCmdStart compound = compound();
        return compound == null ? action.commandFqName : compound.getInnerGeCmd();
    }

    public Map<String, Object> describe() {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("entry", id);
        entry.put("label", label());
        CompoundOuterCmdStart compound = compound();
        if (compound == null) {
            entry.put("command", action.commandFqName);
        } else {
            // Zusammengesetzter Start: Die Beschriftung meint den inneren Command. Der äußere wird dafür
            // geöffnet und danach von selbst wieder abgeschlossen.
            entry.put("command", compound.getInnerGeCmd());
            entry.put("within", action.commandFqName);
        }
        entry.put("from", origin);
        if (group != null) {
            entry.put("group", group);
        }
        // Der Tooltip des Commands: ein Kommentar aus dem Modell oder, ohne Kommentar, seine Berechtigungen.
        String description = action.getToolTip();
        if (description != null && !description.isBlank()) {
            entry.put("description", description.trim());
        }
        entry.put("startable", isStartable());
        return entry;
    }
}
