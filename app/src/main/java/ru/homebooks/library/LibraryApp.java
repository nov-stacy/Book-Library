package ru.homebooks.library;

import android.app.Application;
import java.util.concurrent.*;

public final class LibraryApp extends Application {
    public final ExecutorService io = Executors.newSingleThreadExecutor();
    public final ExecutorService network = Executors.newFixedThreadPool(2);
    public LibraryStore store;
    CoverLoader covers;
    @Override public void onCreate() { super.onCreate(); BookLookup.configureCache(getFilesDir()); store = new LibraryStore(this); covers=new CoverLoader(this);covers.start();DailyBackup.schedule(this);io.execute(()->DailyBackup.writeIfDue(this,()->false)); }
}
