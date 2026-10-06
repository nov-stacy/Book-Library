package ru.homebooks.library;

import android.app.job.*;
import android.os.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DailyBackupService extends JobService {
    private AtomicBoolean stopped;
    @Override public boolean onStartJob(JobParameters parameters){
        AtomicBoolean token=new AtomicBoolean();stopped=token;
        LibraryApp app=(LibraryApp)getApplication();
        app.io.execute(()->{boolean success=DailyBackup.writeIfDue(app,token::get);new Handler(Looper.getMainLooper()).post(()->{if(!token.get())jobFinished(parameters,!success);});});
        return true;
    }
    @Override public boolean onStopJob(JobParameters parameters){if(stopped!=null)stopped.set(true);return true;}
}
