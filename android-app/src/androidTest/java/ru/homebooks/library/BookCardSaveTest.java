package ru.homebooks.library;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import java.io.*;
import java.util.Collections;
import java.util.UUID;
import static org.junit.Assert.*;

/** Real SQLite rollback and file replacement, confined to the QA application's sandbox. */
public class BookCardSaveTest {
    private Context context;
    private LibraryStore store;
    private String databaseName;
    private Book original;
    private File draft;
    private final byte[] previousCover = {1, 2, 3};
    private final byte[] nextCover = {4, 5, 6};

    @Before public void setUp() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertEquals("ru.homebooks.library.testbed", context.getPackageName());
        databaseName = "card-save-" + UUID.randomUUID() + ".db";
        store = new LibraryStore(context, databaseName);
        original = new Book("", "Original", "Author", 42);
        draft = File.createTempFile("card-cover-", ".jpg", context.getCacheDir());
        try (OutputStream out = new FileOutputStream(draft)) { out.write(nextCover); }
    }

    @After public void tearDown() {
        if (store != null) {
            store.delete(Collections.singleton(original.id));
            store.close();
            context.deleteDatabase(databaseName);
        }
        if (draft != null) draft.delete();
    }

    @Test public void savesBookAndCoverTogether() throws Exception {
        store.save(original);
        store.saveCover(original.id, previousCover);
        store.saveCard(original.id, draft, () -> store.saveEdited(edited()));
        assertEquals("Edited", store.find(original.id).title);
        assertArrayEquals(nextCover, read(store.cover(original.id)));
        assertTrue(draft.isFile()); // The activity owns the draft until it receives success.
        store.close();
        store = new LibraryStore(context, databaseName);
        assertEquals("Edited", store.find(original.id).title);
        assertArrayEquals(nextCover, read(store.cover(original.id)));
    }

    @Test public void metadataFailureRestoresPreviousCoverAndBook() throws Exception {
        store.save(original);
        store.saveCover(original.id, previousCover);
        try {
            store.saveCard(original.id, draft, () -> {
                store.saveEdited(edited());
                store.setBookSeries(original.id, Long.MAX_VALUE, "1", "", "");
            });
            fail("Deleted series must reject the save");
        } catch (IllegalStateException expected) {
            assertEquals("Original", store.find(original.id).title);
            assertArrayEquals(previousCover, read(store.cover(original.id)));
            assertTrue(draft.isFile());
        }
    }

    @Test public void failedNewBookLeavesNeitherBookNorCover() throws Exception {
        try {
            store.saveCard(original.id, draft, () -> {
                store.save(original);
                store.setBookSeries(original.id, Long.MAX_VALUE, "1", "", "");
            });
            fail("Deleted series must reject the save");
        } catch (IllegalStateException expected) {
            assertNull(store.find(original.id));
            assertFalse(store.cover(original.id).exists());
        }
    }

    @Test public void missingDraftKeepsExistingData() throws Exception {
        store.save(original);
        store.saveCover(original.id, previousCover);
        assertTrue(draft.delete());
        try {
            store.saveCard(original.id, draft, () -> store.saveEdited(edited()));
            fail("Missing cover must reject the save");
        } catch (IOException expected) {
            assertEquals("Original", store.find(original.id).title);
            assertArrayEquals(previousCover, read(store.cover(original.id)));
        }
    }

    @Test public void metadataOnlySavePreservesCover() throws Exception {
        store.save(original);
        store.saveCover(original.id, previousCover);
        store.saveCard(original.id, null, () -> store.saveEdited(edited()));
        assertEquals("Edited", store.find(original.id).title);
        assertArrayEquals(previousCover, read(store.cover(original.id)));
    }

    private Book edited() { return new Book(original.id, "Edited", "Author", 42); }

    private static byte[] read(File file) throws IOException {
        try (InputStream in = new FileInputStream(file); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            return out.toByteArray();
        }
    }
}
