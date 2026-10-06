package ru.homebooks.library;

import java.text.Collator;
import java.util.Comparator;
import java.util.Locale;

/** Stable ordering shared by the library list and CSV export. */
public enum BookSort {
    NEWEST("Сначала новые", "Дата добавления — сначала новые"),
    OLDEST("Сначала старые", "Дата добавления — сначала старые"),
    TITLE_ASC("Название: А–Я", "Название — А–Я"),
    TITLE_DESC("Название: Я–А", "Название — Я–А"),
    AUTHOR_ASC("Автор: А–Я", "Автор — А–Я"),
    AUTHOR_DESC("Автор: Я–А", "Автор — Я–А");

    public final String label, option;
    BookSort(String label, String option) { this.label = label; this.option = option; }

    public static BookSort restore(String key) {
        if (key != null) try { return valueOf(key); } catch (IllegalArgumentException ignored) { }
        return NEWEST;
    }

    public Comparator<Book> comparator() {
        Collator alphabet = Collator.getInstance(new Locale("ru"));
        alphabet.setStrength(Collator.SECONDARY);
        return (a, b) -> {
            int result;
            switch (this) {
                case OLDEST: result = Long.compare(a.addedAt, b.addedAt); break;
                case TITLE_ASC: result = alphabet.compare(a.title.trim(), b.title.trim()); break;
                case TITLE_DESC: result = alphabet.compare(b.title.trim(), a.title.trim()); break;
                case AUTHOR_ASC:
                case AUTHOR_DESC:
                    String left = a.author.trim(), right = b.author.trim();
                    // Unknown authors stay at the end in either direction.
                    if (left.isEmpty() != right.isEmpty()) return left.isEmpty() ? 1 : -1;
                    result = this == AUTHOR_ASC ? alphabet.compare(left, right) : alphabet.compare(right, left);
                    break;
                default: result = Long.compare(b.addedAt, a.addedAt);
            }
            if (result == 0) result = alphabet.compare(a.title.trim(), b.title.trim());
            if (result == 0) result = a.id.compareTo(b.id);
            return result;
        };
    }
}
