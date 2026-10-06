package ru.homebooks.library;
public enum BookLocation {
    UNKNOWN(0,"Не указано"), HOME(1,"У меня"), PARENTS(2,"У родителей");
    public final int id;public final String label;
    BookLocation(int id,String label){this.id=id;this.label=label;}
    public static BookLocation fromId(int id){return id==PARENTS.id?PARENTS:HOME;}
}
