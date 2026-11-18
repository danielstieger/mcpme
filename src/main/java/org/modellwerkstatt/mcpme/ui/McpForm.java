package org.modellwerkstatt.mcpme.ui;

import org.modellwerkstatt.dataux.runtime.toolkit.IToolkit_Form;
import org.modellwerkstatt.objectflow.runtime.IOFXProblem;
import org.modellwerkstatt.objectflow.runtime.IOFXSelection;

import java.util.ArrayList;
import java.util.List;

/**
 * Basis aller Formen: Formular, Tabelle, Layout. Eine Form hält, was die Runtime ihr übergibt; die
 * Wurzelform einer Seite trägt zusätzlich Seitentitel und Meldungen.
 */
public abstract class McpForm<T> implements IToolkit_Form<T> {

    private String titleText = "";
    private List<IOFXProblem> problems = new ArrayList<>();
    private boolean root;

    /** Titel; bei der Wurzelform der Seitentitel, eine zweite Zeile ist der Untertitel. */
    public String getTitleText() {
        return titleText;
    }

    public List<IOFXProblem> getProblems() {
        return problems;
    }

    public boolean isRoot() {
        return root;
    }

    /** Die Formen, die diese Form enthält, in der Reihenfolge des Modells. */
    public List<McpForm<?>> getChildForms() {
        return List.of();
    }

    @Override
    public boolean selectionChanged(IOFXSelection<T> selection) {
        return false;
    }

    @Override
    public void loadList(List<T> objects, IOFXSelection<T> selection) {
    }

    @Override
    public void setTitleText(String text) {
        this.titleText = text == null ? "" : text;
    }

    @Override
    public void setProblems(List<IOFXProblem> lop) {
        this.problems = lop == null ? new ArrayList<>() : new ArrayList<>(lop);
    }

    @Override
    public void rootForm() {
        this.root = true;
    }

    @Override
    public Object myRequestFocus() {
        return null;
    }

    @Override
    public void afterFullUiInitialized() {
    }

    @Override
    public void gcClear() {
    }
}
