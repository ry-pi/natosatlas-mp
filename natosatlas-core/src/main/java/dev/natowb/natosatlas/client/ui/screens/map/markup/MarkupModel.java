package dev.natowb.natosatlas.client.ui.screens.map.markup;

import java.util.ArrayList;
import java.util.List;

/** mp9: the markup shapes (in memory only) with undo. Forgotten when markup mode ends unless saved as an image. */
public final class MarkupModel {

    private interface Undo {
        void undo(List<MarkupShape> shapes);
    }

    private final List<MarkupShape> shapes = new ArrayList<>();
    private final List<Undo> undo = new ArrayList<>();
    private static final int MAX_UNDO = 200;

    public List<MarkupShape> all() {
        return shapes;
    }

    /** Shapes drawn in one dimension (each shape remembers the dimension the map showed). */
    public List<MarkupShape> forDim(int dim) {
        List<MarkupShape> o = new ArrayList<>();
        for (MarkupShape s : shapes) if (s.dim == dim) o.add(s);
        return o;
    }

    public boolean isEmpty() {
        return shapes.isEmpty();
    }

    private void push(Undo u) {
        undo.add(u);
        if (undo.size() > MAX_UNDO) undo.remove(0);
    }

    public void add(final MarkupShape s) {
        shapes.add(s);
        push(list -> list.remove(s));
    }

    public void remove(final MarkupShape s) {
        final int i = shapes.indexOf(s);
        if (i < 0) return;
        shapes.remove(i);
        push(list -> list.add(Math.min(i, list.size()), s));
    }

    /** Removes every shape of a dimension (undoable). */
    public void clear(int dim) {
        final List<MarkupShape> before = new ArrayList<>(shapes);
        if (!shapes.removeIf(s -> s.dim == dim)) return;
        push(list -> {
            list.clear();
            list.addAll(before);
        });
    }

    public boolean canUndo() {
        return !undo.isEmpty();
    }

    public boolean undo() {
        if (undo.isEmpty()) return false;
        undo.remove(undo.size() - 1).undo(shapes);
        return true;
    }

    /** Forget everything (leaving markup mode). */
    public void discard() {
        shapes.clear();
        undo.clear();
    }
}
