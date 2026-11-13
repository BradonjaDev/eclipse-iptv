package de.eclipseiptv.data;

/** Eine Liste innerhalb eines Bereichs, z. B. "DE • KINO". */
public final class Category {
    /** Besondere Listen, die die App selbst bildet. */
    public static final String ALL = "*all", FAV = "*fav", RECENT = "*recent", NEW = "*new";

    public int kind, count;
    public String ext = "", name = "";
    public boolean hidden, adult;

    public Category() {
    }

    public Category(int kind, String ext, String name) {
        this.kind = kind;
        this.ext = ext;
        this.name = name;
    }

    public boolean isSpecial() {
        return ext.startsWith("*");
    }
}
