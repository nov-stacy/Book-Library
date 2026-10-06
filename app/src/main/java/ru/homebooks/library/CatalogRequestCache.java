package ru.homebooks.library;

import java.io.*;
import java.net.*;
import java.util.*;

/** Bounded in-memory response cache. Network calls never hold its shared lock. */
final class CatalogRequestCache implements BookLookup.Transport {
    interface Clock {long now();}
    private static final long MAX_BYTES=24L*1024*1024;
    private final BookLookup.Transport upstream;
    private final Clock clock;
    private final LinkedHashMap<String,Entry> entries=new LinkedHashMap<>(32,.75f,true);
    private final Map<String,Health> hosts=new HashMap<>();
    private long bytes;
    private static final class Entry {byte[] data;long until;Entry(byte[] data,long until){this.data=data;this.until=until;}}
    private static final class Health {int failures;long until;}
    CatalogRequestCache(BookLookup.Transport upstream){this(upstream,System::currentTimeMillis);}
    CatalogRequestCache(BookLookup.Transport upstream,Clock clock){this.upstream=upstream;this.clock=clock;}
    public byte[] get(String url,int limit)throws IOException{
        BookLookup.LookupControl control=BookLookup.ACTIVE.get();if(control!=null)control.check();
        String host=new URL(url).getHost();
        // Google's persistent cache owns its quota and empty-result policy.
        if(host.equals("www.googleapis.com"))return upstream.get(url,limit);
        synchronized(this){
            Entry entry=entries.get(url);
            if(entry!=null){
                if(entry.until>clock.now()){
                    if(entry.data==null)throw new BookLookup.HttpFailure(404);
                    if(entry.data.length>limit)throw new IOException("Response too large");
                    return entry.data.clone();
                }
                remove(url);
            }
            Health health=hosts.get(host);if(health!=null&&health.until>clock.now())throw new IOException("Catalog temporarily paused");
        }
        try{
            byte[] data=upstream.get(url,limit);
            if(control!=null)control.check();
            if(data.length>limit)throw new IOException("Response too large");
            synchronized(this){hosts.remove(host);put(url,data,30L*60*1000);}
            return data;
        }catch(IOException e){
            synchronized(this){
                if(e instanceof BookLookup.HttpFailure&&((BookLookup.HttpFailure)e).status==404)put(url,null,10L*60*1000);
                boolean timedOut=e instanceof SocketTimeoutException;
                boolean unavailable=e instanceof BookLookup.HttpFailure&&(((BookLookup.HttpFailure)e).status==429||((BookLookup.HttpFailure)e).status>=500);
                // Cancellation/overall deadline is not evidence of a broken source.
                if((timedOut||unavailable)&&(control==null||(!control.stopped&&(control.parent==null||!control.parent.stopped)&&System.nanoTime()<control.deadline))){
                    Health health=hosts.get(host);if(health==null){health=new Health();hosts.put(host,health);}
                    if(++health.failures>=2)health.until=clock.now()+2L*60*1000;
                }
            }
            throw e;
        }
    }
    private void remove(String key){Entry old=entries.remove(key);if(old!=null&&old.data!=null)bytes-=old.data.length;}
    private void put(String key,byte[] data,long ttl){
        remove(key);if(data!=null&&data.length>MAX_BYTES)return;
        entries.put(key,new Entry(data==null?null:data.clone(),clock.now()+ttl));if(data!=null)bytes+=data.length;
        while(bytes>MAX_BYTES||entries.size()>256)remove(entries.keySet().iterator().next());
    }
}
