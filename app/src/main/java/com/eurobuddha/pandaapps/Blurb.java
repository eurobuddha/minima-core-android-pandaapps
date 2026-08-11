package com.eurobuddha.pandaapps;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits a catalog description into the parts the detail screen shows separately.
 *
 * The catalog's `description` is not product copy — it is a running changelog. Across the live
 * catalog, 28 of 37 entries open with the same "NEW SIGNING KEY: …" boilerplate and 23 are
 * concatenated release notes ("v0.5.8: … v0.5.7: …"); Atelier is 6741 chars of pure version
 * history with no product sentence at all. Rendering that verbatim is what made the old list a
 * wall of text, so the detail screen pulls it apart into a warning callout, a lead paragraph and
 * a version list instead.
 *
 * Pure string handling — no Android dependency, so the rules stay easy to reason about.
 */
public final class Blurb {

    /** One "vX.Y.Z: …" entry from the description. */
    public static final class Note {
        public final String version;   // "0.5.8" (no leading v)
        public final String text;
        Note(String version, String text) { this.version = version; this.text = text; }
    }

    /** The signing-key notice with its "NEW SIGNING KEY:" prefix stripped; "" when absent. */
    public final String warning;
    /** Product copy appearing before the first version marker; "" when the entry is pure changelog. */
    public final String lead;
    /** Version entries in catalog order (newest first); empty when the entry carries none. */
    public final List<Note> notes;

    // Boilerplate runs "NEW SIGNING KEY: … (… afterwards)." — anchor on that first ")." so the
    // parenthetical explanation is taken with it rather than left stranded in the lead.
    private static final Pattern SIGNING =
            Pattern.compile("^\\s*NEW SIGNING KEY:.*?\\)\\.\\s*", Pattern.DOTALL);
    private static final String SIGNING_PREFIX = "NEW SIGNING KEY:";
    private static final Pattern MARKER =
            Pattern.compile("\\bv(\\d+\\.\\d+(?:\\.\\d+)?):\\s*");

    private Blurb(String warning, String lead, List<Note> notes) {
        this.warning = warning;
        this.lead = lead;
        this.notes = Collections.unmodifiableList(notes);
    }

    public boolean hasWarning() { return !warning.isEmpty(); }
    public boolean hasLead()    { return !lead.isEmpty(); }
    public boolean hasNotes()   { return !notes.isEmpty(); }

    public static Blurb parse(String description) {
        String d = description == null ? "" : description.trim();

        // --- 1. the signing-key notice ---
        String warning = "";
        Matcher sm = SIGNING.matcher(d);
        if (sm.find()) {
            warning = d.substring(sm.start(), sm.end()).trim();
            d = d.substring(sm.end()).trim();
        } else if (d.startsWith(SIGNING_PREFIX)) {
            // Same boilerplate without the "(…)." to anchor on: cut at the first version marker,
            // else at the end of the first sentence, so it still leaves the notice behind.
            Matcher mk = MARKER.matcher(d);
            int cut = mk.find() ? mk.start() : d.indexOf(". ") + 1;
            if (cut > 0) {
                warning = d.substring(0, cut).trim();
                d = d.substring(cut).trim();
            }
        }
        if (warning.startsWith(SIGNING_PREFIX)) {
            warning = warning.substring(SIGNING_PREFIX.length()).trim();
        }

        // --- 2. split the rest on version markers ---
        List<Note> notes = new ArrayList<>();
        String lead;
        Matcher m = MARKER.matcher(d);
        if (!m.find()) {
            lead = d;                                  // no markers at all — it is all product copy
        } else {
            lead = d.substring(0, m.start()).trim();   // may be "" (Atelier, PandaDEX)
            String version = m.group(1);
            int from = m.end();
            while (m.find()) {
                addNote(notes, version, d.substring(from, m.start()));
                version = m.group(1);
                from = m.end();
            }
            addNote(notes, version, d.substring(from));
        }

        return new Blurb(warning, lead, notes);
    }

    /** Append a note, dropping entries whose body turned out to be empty. */
    private static void addNote(List<Note> notes, String version, String text) {
        String t = text.trim();
        if (!t.isEmpty()) notes.add(new Note(version, t));
    }
}
