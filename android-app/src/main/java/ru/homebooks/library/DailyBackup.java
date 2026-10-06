package ru.homebooks.library;

import android.app.job.*;
import android.content.*;
import android.net.Uri;
import android.provider.DocumentsContract;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Portable backups in a user-owned SAF directory, outside the app sandbox. */
final class DailyBackup {
    static final int JOB_ID=24001;
    static final long DAY=24L*60*60*1000;
    static SharedPreferences prefs(Context c){return c.getSharedPreferences("daily-backup",Context.MODE_PRIVATE);}
    static boolean enabled(Context c){return !prefs(c).getString("tree","").isEmpty();}
    static String folderName(Context c){String tree=prefs(c).getString("tree","");return tree.isEmpty()?"":Uri.decode(Uri.parse(tree).getLastPathSegment());}
    static void choose(LibraryApp app,Uri tree){
        app.getContentResolver().takePersistableUriPermission(tree,Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        prefs(app).edit().putString("tree",tree.toString()).putLong("last",0).putString("error","").apply();
        schedule(app);app.io.execute(()->writeIfDue(app,()->false));
    }
    static void disable(Context c){prefs(c).edit().remove("tree").putString("error","").apply();((JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE)).cancel(JOB_ID);}
    static void schedule(Context c){
        if(!enabled(c))return;
        JobScheduler scheduler=(JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        for(JobInfo job:scheduler.getAllPendingJobs())if(job.getId()==JOB_ID)return;
        int result=scheduler.schedule(new JobInfo.Builder(JOB_ID,new ComponentName(c,DailyBackupService.class)).setPeriodic(60L*60*1000).setPersisted(true).build());
        if(result!=JobScheduler.RESULT_SUCCESS)prefs(c).edit().putString("error","Не удалось включить расписание. Выберите папку повторно.").apply();
    }
    interface Stopped {boolean get();}
    interface Destination {
        Uri create(Uri tree,String name)throws IOException;
        OutputStream open(Uri file)throws IOException;
        void delete(Uri file)throws IOException;
    }
    static boolean writeIfDue(LibraryApp app,Stopped stopped){
        return writeIfDue(app,stopped,new Destination(){
            public Uri create(Uri tree,String name)throws IOException{return DocumentsContract.createDocument(app.getContentResolver(),DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree)),"application/zip",name);}
            public OutputStream open(Uri file)throws IOException{return app.getContentResolver().openOutputStream(file,"wt");}
            public void delete(Uri file)throws IOException{DocumentsContract.deleteDocument(app.getContentResolver(),file);}
        });
    }
    // Runs on the same single-thread executor as library mutations and restore.
    static boolean writeIfDue(LibraryApp app,Stopped stopped,Destination destination){
        SharedPreferences p=prefs(app);String location=p.getString("tree","");
        if(location.isEmpty()||System.currentTimeMillis()-p.getLong("last",0)<DAY)return true;
        File staged=null;Uri created=null;
        try{
            staged=File.createTempFile("daily-backup-",".zip",app.getCacheDir());
            try(OutputStream out=new FileOutputStream(staged)){LibraryBackup.write(out,app.store.all(),app.store::cover,app.store.collections(),app.store.bookTypes(),app.store.readingChallenges(),app.store.bookSeries());}
            if(stopped.get()||!location.equals(p.getString("tree","")))return false;
            Uri tree=Uri.parse(location);
            String name="library-auto-"+new SimpleDateFormat("yyyy-MM-dd-HHmmss",Locale.ROOT).format(new Date())+".zip";
            created=destination.create(tree,name);
            if(created==null)throw new IOException("No document");
            try(InputStream in=new FileInputStream(staged);OutputStream out=destination.open(created)){
                if(out==null)throw new IOException("No output");
                byte[] buffer=new byte[65536];int n;
                while((n=in.read(buffer))!=-1){if(stopped.get())throw new IOException("Stopped");out.write(buffer,0,n);}
            }
            if(stopped.get())throw new IOException("Stopped");
            if(location.equals(p.getString("tree","")))p.edit().putLong("last",System.currentTimeMillis()).putString("error","").putString("file",name).apply();
            created=null;return true;
        }catch(Exception e){if(location.equals(p.getString("tree","")))p.edit().putString("error","Копия не сохранена. Проверьте место и доступ к папке. При необходимости выберите папку заново.").apply();return false;}
        finally{
            if(staged!=null)staged.delete();
            if(created!=null)try{destination.delete(created);}catch(Exception ignored){}
        }
    }
    static String status(Context c){
        SharedPreferences p=prefs(c);if(!enabled(c))return "";
        long last=p.getLong("last",0);String error=p.getString("error","");
        String folder=folderName(c);
        return "Папка: "+folder+(last==0?"":"\nПоследняя копия: "+new SimpleDateFormat("dd.MM.yyyy HH:mm",Locale.getDefault()).format(new Date(last)))+(error.isEmpty()?"":"\n"+error);
    }
}
