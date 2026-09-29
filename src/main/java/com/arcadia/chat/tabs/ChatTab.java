package com.arcadia.chat.tabs;

import java.util.EnumSet;
import java.util.Set;

/** Onglets du chat et messages que chacun affiche. */
public enum ChatTab {
    ALL("all", EnumSet.allOf(Category.class)),
    GLOBAL("global", EnumSet.of(Category.REMOTE, Category.LOCAL)),
    SERVER("server", EnumSet.of(Category.LOCAL)),
    PRIVATE("private", EnumSet.of(Category.PRIVATE)),
    SYSTEM("system", EnumSet.of(Category.SYSTEM));

    private final String key;
    private final Set<Category> categories;

    ChatTab(String key, Set<Category> categories) {
        this.key = key;
        this.categories = categories;
    }

    public boolean accepts(Category category) {
        return categories.contains(category);
    }

    public String translationKey() {
        return "arcadiachat.tab." + key;
    }
}
