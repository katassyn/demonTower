package pl.yourserver.demonTowerPlugin.mechanics;

public enum ItemState {
    NORMAL("", ""),
    CORRUPTED("&c&lCORRUPTED", "&c"),
    SMELTED("&6Smelted", "&6"),
    SANCTIFIED("&9Sanctified", "&9"),
    ULTIMATE("&5&lULTIMATE", "&5");

    private final String tag;
    private final String colorCode;

    ItemState(String tag, String colorCode) {
        this.tag = tag;
        this.colorCode = colorCode;
    }

    public String getTag() {
        return tag;
    }

    public String getColorCode() {
        return colorCode;
    }

    public boolean isEmpty() {
        return this == NORMAL;
    }
}
