package ru.homebooks.library;

import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Shared across lookups; contains catalog responses only, never library data or raw API keys. */
final class GoogleRequestCache implements BookLookup.Transport {
    interface Clock {long now();}
    private final File directory;
    private final BookLookup.Transport upstream;
    private final Clock clock;
    private final Map<String,Long> paused=new HashMap<>();
    GoogleRequestCache(File directory,BookLookup.Transport upstream){this(directory,upstream,System::currentTimeMillis);}
    GoogleRequestCache(File directory,BookLookup.Transport upstream,Clock clock){this.directory=directory;this.upstream=upstream;this.clock=clock;}
    @Override public byte[] get(String url,int limit)throws IOException{
        if(!url.startsWith("https://www.googleapis.com/books/v1/volumes?"))return upstream.get(url,limit);
        return google(url,limit);
    }
    private synchronized byte[] google(String url,int limit)throws IOException{
        if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();
        int keyAt=url.indexOf("&key=");
        String request=hash(url),project=hash(keyAt<0?"no-key":url.substring(keyAt));
        File cached=new File(directory,request+".cache"),pause=new File(directory,project+".pause");
        byte[] saved=read(cached,limit);if(saved!=null)return saved;
        long until=paused.containsKey(project)?paused.get(project):0;
        try(DataInputStream in=new DataInputStream(new FileInputStream(pause))){until=Math.max(until,in.readLong());}catch(IOException ignored){}
        if(until>clock.now())throw new BookLookup.HttpFailure(429);
        try{
            byte[] bytes=upstream.get(url,limit);
            try{
                JSONObject json=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
                if(!json.has("error")){
                    boolean empty=json.optJSONArray("items")==null||json.optJSONArray("items").length()==0;
                    write(cached,bytes,clock.now()+(empty?6L*60*60*1000:7L*24*60*60*1000));
                }
            }catch(org.json.JSONException ignored){}
            return bytes;
        }catch(BookLookup.HttpFailure failure){
            if(failure.status==429){
                long cooldown=failure.dailyQuota?24L*60*60*1000:60L*1000;
                until=clock.now()+cooldown;paused.put(project,until);
                directory.mkdirs();try(DataOutputStream out=new DataOutputStream(new FileOutputStream(pause))){out.writeLong(until);}catch(IOException ignored){}
            }
            throw failure;
        }
    }
    private byte[] read(File file,int limit){
        try(DataInputStream in=new DataInputStream(new FileInputStream(file))){
            long expiry=in.readLong();int size=in.readInt();if(expiry<=clock.now()||size<0||size>limit){file.delete();return null;}
            byte[] bytes=new byte[size];in.readFully(bytes);return bytes;
        }catch(IOException e){return null;}
    }
    private void write(File file,byte[] bytes,long expiry){
        directory.mkdirs();
        try(DataOutputStream out=new DataOutputStream(new FileOutputStream(file))){out.writeLong(expiry);out.writeInt(bytes.length);out.write(bytes);}catch(IOException ignored){file.delete();}
        File[] files=directory.listFiles((dir,name)->name.endsWith(".cache"));
        if(files!=null&&files.length>256){Arrays.sort(files,(a,b)->Long.compare(a.lastModified(),b.lastModified()));for(int i=0;i<files.length-256;i++)files[i].delete();}
    }
    private static String hash(String text)throws IOException{
        try{byte[] bytes=MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));StringBuilder result=new StringBuilder();for(byte b:bytes)result.append(String.format(Locale.ROOT,"%02x",b&255));return result.toString();}
        catch(java.security.NoSuchAlgorithmException e){throw new IOException(e);}
    }
}
