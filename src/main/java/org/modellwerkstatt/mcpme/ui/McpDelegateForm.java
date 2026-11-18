package org.modellwerkstatt.mcpme.ui;

import org.modellwerkstatt.dataux.runtime.extensions.IDlgt;
import org.modellwerkstatt.dataux.runtime.toolkit.IToolkit_DelegateForm;

import java.util.ArrayList;
import java.util.List;

/** Ein Formular: eine Folge von Feldern, je eines je Delegate der Runtime. */
public class McpDelegateForm<T> extends McpForm<T> implements IToolkit_DelegateForm<T> {

    private final List<IDlgt> delegates = new ArrayList<>();

    /** Die Felder in der Reihenfolge des Modells. */
    public List<McpEditor> getEditors() {
        List<McpEditor> editors = new ArrayList<>();
        for (IDlgt delegate : delegates) {
            editors.add((McpEditor) delegate.getDelegateUiImpl());
        }
        return editors;
    }

    @Override
    public String checkDelegatesValidAndFocus() {
        // Alle prüfen, damit jedes Feld seinen Fehlertext erhält; gemeldet wird der erste Fehler.
        String firstError = null;
        for (IDlgt delegate : delegates) {
            String error = delegate.isInputValid();
            if (error != null && firstError == null) {
                firstError = error;
            }
        }
        return firstError;
    }

    @Override
    public void setColLayoutConstraints(List<String> constraints) {
        // Spaltengewichte sind Darstellung.
    }

    @Override
    public void addDelegate(IDlgt delegate) {
        delegates.add(delegate);
    }

    @Override
    public Object myRequestFocus() {
        for (IDlgt delegate : delegates) {
            if (delegate.isRequestFocus()) {
                return delegate;
            }
        }
        for (IDlgt delegate : delegates) {
            if (delegate.isEnabled()) {
                return delegate;
            }
        }
        return null;
    }

    @Override
    public void gcClear() {
        delegates.clear();
    }
}
