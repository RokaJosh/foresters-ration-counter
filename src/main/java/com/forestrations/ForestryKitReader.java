package com.forestrations;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.Client;
import net.runelite.api.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;

/**
 * Best-effort reader for the currently visible Forestry kit interface.
 * Never guesses a quantity from a bare item icon or an unrelated number.
 */
final class ForestryKitReader
{
    private static final Pattern AFTER = Pattern.compile("(?i)forester(?:'|’)s rations?\\s*(?:[:=x×-]\\s*|\\(\\s*)([\\d,]+)\\s*\\)?");
    private static final Pattern BEFORE = Pattern.compile("(?i)([\\d,]+)\\s*(?:x|×)?\\s*forester(?:'|’)s rations?");
    private static final Pattern NUMBER = Pattern.compile("[\\d,]+");

    static final class Reading
    {
        final boolean kitOpen;
        final Integer rations;
        final String diagnostic;

        Reading(boolean kitOpen, Integer rations, String diagnostic)
        {
            this.kitOpen = kitOpen;
            this.rations = rations;
            this.diagnostic = diagnostic;
        }
    }

    Reading read(Client client)
    {
        Widget[] roots = client.getWidgetRoots();
        if (roots == null)
        {
            return new Reading(false, null, "No root widgets");
        }
        List<Widget> all = new ArrayList<>();
        for (Widget root : roots)
        {
            flatten(root, all, 0);
        }

        // Verify the kit header before accepting any nearby numerical widgets.
        Widget title = null;
        for (Widget widget : all)
        {
            String text = clean(widget.getText());
            if ((text.equalsIgnoreCase("Forestry kit") || text.equalsIgnoreCase("Forestry basket"))
                && !widget.isHidden())
            {
                title = widget;
                break;
            }
        }
        if (title == null)
        {
            return new Reading(false, null, "Forestry kit title not visible");
        }

        // RuneLite packs widget group in the upper 16 bits of its ID.
        // This prevents inventory/bank or unrelated windows from being scanned.
        final int kitGroup = title.getId() >>> 16;
        List<Widget> kit = new ArrayList<>();
        for (Widget w : all)
        {
            if ((w.getId() >>> 16) == kitGroup && !w.isHidden())
            {
                kit.add(w);
            }
        }

        StringBuilder diagnostics = new StringBuilder();
        List<Widget> rationLabels = new ArrayList<>();
        Integer inlineCount = null;
        for (Widget w : kit)
        {
            String label = clean(w.getText());
            String name = clean(w.getName());
            if (label.toLowerCase(Locale.ROOT).contains("ration") || name.toLowerCase(Locale.ROOT).contains("ration"))
            {
                rationLabels.add(w);
                diagnostics.append(describe(w));
                Integer parsed = parseLabel(label);
                if (parsed == null)
                {
                    parsed = parseLabel(name);
                }
                if (parsed != null)
                {
                    inlineCount = parsed;
                }
            }
        }
        if (inlineCount != null)
        {
            return new Reading(true, inlineCount, "In-line label: " + diagnostics);
        }

        // The actual Forestry kit menu (as shown in the screenshot) puts
        // '1844' in a separate widget directly ABOVE the 'Forester's rations'
        // label. Pair the two by canvas position, not by global text order.
        Integer candidate = null;
        int bestScore = Integer.MAX_VALUE;
        // Prefer displayed numeric text over an item sprite's default quantity (often 1).
        int bestSourcePriority = Integer.MAX_VALUE;
        for (Widget label : rationLabels)
        {
            java.awt.Rectangle lb = label.getBounds();
            if (lb == null || lb.width <= 0 || lb.height <= 0)
            {
                continue;
            }
            int labelX = lb.x + lb.width / 2;
            for (Widget numberWidget : kit)
            {
                if (numberWidget == label || numberWidget.isHidden())
                {
                    continue;
                }
                String raw = clean(numberWidget.getText());
                Integer value = parseStrictNumber(raw);
                boolean fromText = value != null;
                // Some interfaces draw the stack count on an item sprite
                // instead of using a standalone text widget.
                if (value == null && numberWidget.getItemId() == ItemID.FORESTERS_RATION
                    && numberWidget.getItemQuantity() > 1)
                {
                    value = numberWidget.getItemQuantity();
                }
                if (value == null)
                {
                    continue;
                }
                java.awt.Rectangle nb = numberWidget.getBounds();
                if (nb == null || nb.width <= 0 || nb.height <= 0)
                {
                    continue;
                }
                int numberX = nb.x + nb.width / 2;
                int dy = lb.y - nb.y;
                int dx = Math.abs(labelX - numberX);
                // Count is in the same grid cell, above the ration label.
                // Require a close horizontal alignment and 1-125px vertical gap.
                if (dx > 75 || dy < 0 || dy > 125)
                {
                    continue;
                }
                int score = dx * 3 + Math.abs(dy - 65);
                int sourcePriority = fromText ? 0 : 1;
                if (sourcePriority < bestSourcePriority || (sourcePriority == bestSourcePriority && score < bestScore))
                {
                    bestSourcePriority = sourcePriority;
                    bestScore = score;
                    candidate = value;
                    diagnostics.append(" [number ").append(describe(numberWidget)).append(" score=").append(score).append(']');
                }
            }
        }
        return new Reading(true, candidate, "Kit group=" + kitGroup + " " + diagnostics);
    }

    private static Integer parseStrictNumber(String text)
    {
        return NUMBER.matcher(text).matches() ? parseInt(text) : null;
    }

    private static String describe(Widget widget)
    {
        return "[id=" + widget.getId() + " item=" + widget.getItemId()
            + " qty=" + widget.getItemQuantity() + " text='" + clean(widget.getText())
            + "' name='" + clean(widget.getName()) + "' bounds=" + widget.getBounds() + "]";
    }

    static Integer parseLabel(String label)
    {
        Matcher after = AFTER.matcher(label);
        if (after.find())
        {
            return parseInt(after.group(1));
        }
        Matcher before = BEFORE.matcher(label);
        if (before.find())
        {
            return parseInt(before.group(1));
        }
        return null;
    }

    private static Integer parseInt(String value)
    {
        String normalized = value.replace(",", "");
        if (!NUMBER.matcher(value).matches() || normalized.length() > 9)
        {
            return null;
        }
        try
        {
            return Integer.parseInt(normalized);
        }
        catch (NumberFormatException ignored)
        {
            return null;
        }
    }

    private static String clean(String value)
    {
        return value == null ? "" : Text.removeTags(value).trim();
    }

    private static void flatten(Widget widget, List<Widget> out, int depth)
    {
        if (widget == null || depth > 16 || out.size() > 5000)
        {
            return;
        }
        out.add(widget);
        // getChildren includes the dynamic children; don't traverse getDynamicChildren again.
        add(widget.getChildren(), out, depth);
        add(widget.getStaticChildren(), out, depth);
        add(widget.getNestedChildren(), out, depth);
    }

    private static void add(Widget[] children, List<Widget> out, int depth)
    {
        if (children != null)
        {
            for (Widget child : children)
            {
                // Prevent duplicates / cyclical widget references.
                if (child != null && !out.contains(child))
                {
                    flatten(child, out, depth + 1);
                }
            }
        }
    }
}
