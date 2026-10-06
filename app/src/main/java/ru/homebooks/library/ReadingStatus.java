package ru.homebooks.library;

public enum ReadingStatus {
    NONE(0, "Без статуса", 3, 0xFFEAE6ED, 0xFF655B6E),
    WANT(1, "Хочу прочитать", 1, 0xFFFFEBC7, 0xFF805013),
    READING(2, "Читаю", 0, 0xFFDDEBFF, 0xFF28548F),
    READ(3, "Прочитано", 2, 0xFFDFEFE4, 0xFF2D6745);

    public final int id, sortRank, background, foreground;
    public final String label;
    ReadingStatus(int id, String label, int rank, int background, int foreground) {
        this.id=id; this.label=label; this.sortRank=rank; this.background=background; this.foreground=foreground;
    }
    public static ReadingStatus fromId(int id) {
        for(ReadingStatus status:values())if(status.id==id)return status;
        return NONE;
    }
}
