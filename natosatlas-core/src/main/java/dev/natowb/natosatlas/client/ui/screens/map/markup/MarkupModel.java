package dev.natowb.natosatlas.client.ui.screens.map.markup;

import java.util.ArrayList;
import java.util.List;

/** mp9 (mp11: cell eraser): the markup shapes (in memory only) with undo. Forgotten when markup mode ends unless saved as an image. */
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

    // ---- mp11 eraser: one drag = one undo step; removes cells, and a shape left with no cells is removed

    private java.util.Map<MarkupShape, java.util.Set<Long>> strokeErased;
    private List<Object[]> strokeRemoved; // {index, shape}

    public void beginErase() {
        strokeErased = new java.util.LinkedHashMap<>();
        strokeRemoved = new ArrayList<>();
    }

    /** Erases these cells from every shape of the dimension; returns true if anything changed. */
    public boolean erase(int dim, java.util.Set<Long> cells) {
        if (strokeErased == null) beginErase();
        boolean changed = false;
        for (int i = shapes.size() - 1; i >= 0; i--) {
            MarkupShape s = shapes.get(i);
            if (s.dim != dim) continue;
            java.util.Set<Long> hit = s.erase(cells);
            if (hit.isEmpty()) continue;
            changed = true;
            strokeErased.computeIfAbsent(s, k -> new java.util.HashSet<>()).addAll(hit);
            if (s.cells().isEmpty()) {
                strokeRemoved.add(new Object[]{i, s});
                shapes.remove(i);
            }
        }
        return changed;
    }

    public void endErase() {
        if (strokeErased == null) return;
        final java.util.Map<MarkupShape, java.util.Set<Long>> er = strokeErased;
        final List<Object[]> rm = strokeRemoved;
        strokeErased = null;
        strokeRemoved = null;
        if (er.isEmpty()) return;
        push(list -> {
            for (int i = rm.size() - 1; i >= 0; i--) list.add(Math.min((Integer) rm.get(i)[0], list.size()), (MarkupShape) rm.get(i)[1]);
            for (java.util.Map.Entry<MarkupShape, java.util.Set<Long>> e : er.entrySet()) e.getKey().unerase(e.getValue());
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
