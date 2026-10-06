package ru.homebooks.library;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import org.json.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
public class GoogleErrorDeviceTest {
 @Test public void inspectError()throws Exception{
  Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("googleError")));
  String key=BuildConfig.GOOGLE_BOOKS_API_KEY;
  String address="https://www.googleapis.com/books/v1/volumes?q="+URLEncoder.encode("\"Непонятное искусство\"","UTF-8")+"&maxResults=10&key="+URLEncoder.encode(key,"UTF-8");
  HttpURLConnection c=(HttpURLConnection)new URL(address).openConnection();c.setConnectTimeout(7000);c.setReadTimeout(8000);c.setRequestProperty("User-Agent","HomeLibrary/0.2 (personal ISBN catalog)");
  try{
   int status=c.getResponseCode();android.util.Log.i("GoogleErrorProbe","HTTP "+status+" Retry-After="+c.getHeaderField("Retry-After"));
   InputStream stream=status>=400?c.getErrorStream():c.getInputStream();if(stream==null)return;
   ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream in=stream){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1&&out.size()<32000)out.write(b,0,n);}
   JSONObject json=new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));
   JSONObject error=json.optJSONObject("error");
   if(error!=null){String safe=error.toString().replace(key,"[KEY]");android.util.Log.i("GoogleErrorProbe",safe);}
   else android.util.Log.i("GoogleErrorProbe","Success items="+(json.optJSONArray("items")==null?0:json.getJSONArray("items").length()));
  }finally{c.disconnect();}
 }
}
