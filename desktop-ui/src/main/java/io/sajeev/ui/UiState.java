// #41 (WS2) | docs/UIUX | One screen state, as data. No JavaFX types here, so every state is testable without a display.
package io.sajeev.ui;

import java.util.Collections;
import java.util.List;

/** What the liveness pane shows. All text is a guidance key; the pane looks the text up in the person's language. */
public final class UiState {

    /** Visual style of the badge. Colour is never the only signal: the badge always has a text label too. */
    public enum Style { PRIMARY, WARN, BAD, OK }

    private final String titleKey;
    private final String badgeKey;
    private final Style badgeStyle;
    private final boolean showGuide;
    private final String icon;
    private final int progressDone;
    private final int progressTotal;
    private final String messageKey;
    private final List<String> hintKeys;
    private final List<String> buttonKeys;
    private final String footerKey;

    UiState(String titleKey, String badgeKey, Style badgeStyle, boolean showGuide, String icon, int progressDone,
            int progressTotal, String messageKey, List<String> hintKeys, List<String> buttonKeys, String footerKey) {
        this.titleKey = titleKey;
        this.badgeKey = badgeKey;
        this.badgeStyle = badgeStyle;
        this.showGuide = showGuide;
        this.icon = icon;
        this.progressDone = progressDone;
        this.progressTotal = progressTotal;
        this.messageKey = messageKey;
        this.hintKeys = Collections.unmodifiableList(hintKeys);
        this.buttonKeys = Collections.unmodifiableList(buttonKeys);
        this.footerKey = footerKey;
    }

    public String titleKey() { return titleKey; }
    public String badgeKey() { return badgeKey; }
    public Style badgeStyle() { return badgeStyle; }
    public boolean showGuide() { return showGuide; }
    /** @return an icon glyph, or "" for none */
    public String icon() { return icon; }
    public int progressDone() { return progressDone; }
    public int progressTotal() { return progressTotal; }
    public String messageKey() { return messageKey; }
    public List<String> hintKeys() { return hintKeys; }
    public List<String> buttonKeys() { return buttonKeys; }
    /** @return a key for the footer line (online/offline, attempt count), or "" */
    public String footerKey() { return footerKey; }
}
