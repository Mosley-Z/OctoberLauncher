package com.inkread.weekread.kernel.render;

/** Accesses the shipped kernel wrapping without duplicating its rules. */
public final class HostNoteLayout {
    private HostNoteLayout() {}

    public static float tagBlock(String quote, String idea, float width, float size) {
        return CardLayout.layoutNote(quote, idea, width, size, 11f, 0, 0).tagBlock;
    }

    public static String[][] lines(String quote, String idea, float width, float size) {
        CardLayout.NoteBody body = CardLayout.layoutNote(quote, idea, width, size, 11f, 0, 0);
        return new String[][] {body.quote, body.idea};
    }
}
