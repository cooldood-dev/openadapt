package dev.adapt.v3.ui.theme;

import dev.adapt.v3.util.Displayable;

public enum UiMode implements Displayable {
    MODERN("Modern", null),
    OLDSCHOOL("OldSchool", "BETA");

    public final String displayName;
    public final String badge;

    UiMode(String text, String badge) {
        this.displayName = text;
        this.badge = badge;
    }

    @Override
    public String getDisplayName() {
        return this.displayName;
    }

    @Override
    public String toString() {
        return this.displayName;
    }
}
