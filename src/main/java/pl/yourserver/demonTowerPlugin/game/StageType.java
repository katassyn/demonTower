package pl.yourserver.demonTowerPlugin.game;

public enum StageType {
    LOBBY,      // Poczekalnia - gracze dołączają, lider startuje
    WAVE,       // Fala mobów z timerem
    BOSS,       // Boss + moby z timerem
    COLLECT     // Zbieranie itemów z mobów (bez timera)
}
