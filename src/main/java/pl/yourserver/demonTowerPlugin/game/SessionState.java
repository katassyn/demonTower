package pl.yourserver.demonTowerPlugin.game;

public enum SessionState {
    WAITING,          // W poczekalni, czekanie na graczy
    ACTIVE,           // Aktywna fala/etap
    BOSS,             // Walka z bossem
    COLLECTING,       // Zbieranie itemów
    FLOOR_COMPLETED,  // Piętro ukończone, gracze mogą używać mechanik
    FLOOR_TRANSITION, // Przejście zainicjowane, odliczanie do następnego piętra
    COMPLETED,        // Cały DT ukończony (wszystkie piętra)
    FAILED            // Przegrana (czas minął)
}
