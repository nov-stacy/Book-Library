package ru.homebooks.library;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.StringWriter;
import java.util.Collections;

public class CoreTest {
    @Test public void canonicalIsbnPreventsDuplicates() {
        assertEquals("9780306406157", Isbn.normalize("0-306-40615-2"));
        assertEquals("9780306406157", Isbn.normalize("978-0-306-40615-7"));
        assertEquals("9780804429573", Isbn.normalize("080442957X"));
    }
    @Test public void invalidAndNonBookBarcodesAreRejected() {
        assertNull(Isbn.normalize("9780306406158"));
        assertNull(Isbn.normalize("4006381333931"));
        assertNull(Isbn.normalize("0306406153"));
        assertNull(Isbn.normalize("9780306406157garbage"));
        assertNull(Isbn.normalize(null));
    }
    @Test public void csvPreservesRussianQuotesAndMultilineFields() throws Exception {
        StringWriter writer = new StringWriter();
        Csv.write(writer, Collections.singletonList(new Book("9780306406157", "Книга, \"часть 1\"\nПродолжение", "Автор", 0)));
        assertTrue(writer.toString().startsWith("\uFEFFISBN,"));
        assertTrue(writer.toString().contains("\"Книга, \"\"часть 1\"\"\nПродолжение\""));
        assertTrue(writer.toString().contains("1970-01-01T00:00:00Z"));
    }
    @Test public void spreadsheetFormulasAreEscaped() {
        assertEquals("\"'=HYPERLINK(\"\"bad\"\")\"", Csv.cell("=HYPERLINK(\"bad\")"));
        assertEquals("\"'  +123\"", Csv.cell("  +123"));
    }
}
