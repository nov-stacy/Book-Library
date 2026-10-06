package ru.homebooks.library;

import androidx.lifecycle.MutableLiveData;
import android.content.SharedPreferences;
import android.os.*;
import java.util.*;

/** App-wide resumable queue. Database checks and cover writes use the store executor. */
final class CoverLoader {
    interface Lookup {byte[] find(String isbn);default byte[] find(Book book){return find(book.isbn);}}
    final MutableLiveData<String> progress=new MutableLiveData<>("");
    static final class State {
        final boolean running,cancelled;
        final int checked,total,saved,missing;
        State(boolean running,int checked,int total,int saved,int missing){this(running,false,checked,total,saved,missing);}
        State(boolean running,boolean cancelled,int checked,int total,int saved,int missing){this.running=running;this.cancelled=cancelled;this.checked=checked;this.total=total;this.saved=saved;this.missing=missing;}
        int percent(){return total==0?0:(int)(100L*checked/total);}
    }
    final MutableLiveData<State> state=new MutableLiveData<>(new State(false,0,0,0,0));
    private final LibraryApp app;
    private final Lookup lookup;
    private final SharedPreferences preferences;
    private final LinkedHashSet<String> pending=new LinkedHashSet<>();
    private final Handler main=new Handler(Looper.getMainLooper());
    private boolean running,cancelled;
    private int checked,total,saved,missing,generation;
    CoverLoader(LibraryApp app){this(app,new Lookup(){public byte[] find(String isbn){return new BookLookup().findCover(isbn);}public byte[] find(Book book){return new BookLookup().findCover(book.isbn,book.title);}},"cover_queue");}
    CoverLoader(LibraryApp app,Lookup lookup,String queueName){this.app=app;this.lookup=lookup;preferences=app.getSharedPreferences(queueName,0);pending.addAll(preferences.getStringSet("isbns",Collections.emptySet()));}
    synchronized void enqueue(Collection<String> codes){int before=pending.size();pending.addAll(codes);if(running)total+=pending.size()-before;persist();start();if(running)report();}
    synchronized void start(){if(running||pending.isEmpty())return;running=true;cancelled=false;checked=0;total=pending.size();saved=0;missing=0;int token=++generation;report();app.network.execute(()->work(token));}
    synchronized void cancel(){if(!running)return;generation++;running=false;cancelled=true;pending.clear();persist();report();}
    private synchronized boolean active(int token){return running&&generation==token;}
    private void persist(){preferences.edit().putStringSet("isbns",new HashSet<>(pending)).apply();}
    private void report(){
        State snapshot=new State(running,cancelled,checked,total,saved,missing);
        String message=running?"Ищем обложки · проверено "+checked+" из "+total:cancelled?"Поиск обложек отменён. Добавлено: "+saved:"Обложки: добавлено "+saved+", не найдено или недоступно "+missing+".";
        main.post(()->{state.setValue(snapshot);progress.setValue(message);});
    }
    private void work(int token){
        while(true){
            String id;
            synchronized(this){if(!active(token))return;if(pending.isEmpty()){running=false;report();return;}id=pending.iterator().next();report();}
            boolean found=false,unavailable=false;
            try{
                Book book=app.io.submit(()->app.store.find(id)).get();
                boolean needed=book!=null&&!book.isbn.isEmpty()&&!app.io.submit(()->app.store.cover(id).isFile()).get();
                if(!active(token))return;
                if(needed){
                    byte[] cover=lookup.find(book);
                    if(!active(token))return;
                    if(cover==null)unavailable=true;
                    else found=app.io.submit(()->{
                        // Cancellation and the short atomic save cannot cross each other.
                        synchronized(CoverLoader.this){
                            if(!active(token)||app.store.find(id)==null||app.store.cover(id).isFile())return false;
                            app.store.saveCover(id,cover);saved++;pending.remove(id);checked++;persist();return true;
                        }
                    }).get();
                }
            }catch(Exception e){unavailable=true;}
            synchronized(this){if(!active(token))return;if(!found){if(unavailable)missing++;pending.remove(id);checked++;persist();}}
        }
    }
}
