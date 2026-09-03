package com.eurobuddha.pandaapps;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Tiny programmatic-UI helpers so the store list stays readable. */
public final class Ui {

    private Ui() {}

    public static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics()));
    }

    public static GradientDrawable rounded(int fill, int stroke, float radiusDp, Context c) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(c, radiusDp));
        if (stroke != 0) g.setStroke(dp(c, 1.5f), stroke);
        return g;
    }

    public static LinearLayout col(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static LinearLayout card(Context c) {
        LinearLayout l = row(c);
        l.setBackground(rounded(Theme.PANEL, Theme.BORDER, 10, c));
        int p = dp(c, 12);
        l.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(c, 10);
        l.setLayoutParams(lp);
        return l;
    }

    public static TextView text(Context c, String s, int color, float sp, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(sp);
        if (bold) t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        return t;
    }

    public static Button button(Context c, String s, int bg, int fg) {
        Button b = new Button(c);
        b.setText(s);
        b.setAllCaps(true);
        b.setTextColor(fg);
        b.setTextSize(12);
        b.setTypeface(b.getTypeface(), android.graphics.Typeface.BOLD);
        b.setBackground(rounded(bg, 0, 7, c));
        b.setPadding(dp(c, 16), dp(c, 8), dp(c, 16), dp(c, 8));
        b.setMinWidth(dp(c, 92));
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        return b;
    }

    /** The platform's pressed/ripple highlight, for rows that act as one big tap target.
     *  Null if the current theme has no such attribute — callers pass it straight to
     *  setForeground(), which accepts null as "no highlight". */
    public static android.graphics.drawable.Drawable ripple(Context c) {
        TypedValue tv = new TypedValue();
        if (!c.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
                || tv.resourceId == 0) {
            return null;
        }
        return androidx.core.content.ContextCompat.getDrawable(c, tv.resourceId);
    }

    /** Trailing "›" affordance on a row that opens another screen. */
    public static TextView chevron(Context c) {
        TextView t = text(c, "›", Theme.DIM, 20, false);
        t.setPadding(dp(c, 10), 0, 0, dp(c, 2));
        return t;
    }

    /** Glowing card background for a catalog-highlighted entry: a soft accent halo around a
     *  warm-tinted panel with a full accent stroke. The halo is drawn by the layer itself, so
     *  no elevation/shadow is involved and the dark theme stays flat everywhere else. */
    public static android.graphics.drawable.Drawable glowCard(Context c) {
        GradientDrawable halo = new GradientDrawable();
        halo.setColor(0x2EF9A03F);
        halo.setCornerRadius(dp(c, 13));
        GradientDrawable core = new GradientDrawable();
        core.setColor(0xFF241C10);
        core.setCornerRadius(dp(c, 10));
        core.setStroke(dp(c, 1.5f), Theme.ACCENT);
        android.graphics.drawable.LayerDrawable l = new android.graphics.drawable.LayerDrawable(
                new android.graphics.drawable.Drawable[]{halo, core});
        int i = dp(c, 3);
        l.setLayerInset(1, i, i, i, i);
        return l;
    }

    public static TextView badge(Context c, String s, int fg, int bg) {
        TextView t = text(c, s.toUpperCase(), fg, 9, true);
        t.setLetterSpacing(0.08f);
        t.setBackground(rounded(bg, 0, 4, c));
        t.setPadding(dp(c, 7), dp(c, 3), dp(c, 7), dp(c, 3));
        return t;
    }
}
