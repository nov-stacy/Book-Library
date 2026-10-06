package ru.homebooks.library;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public final class Csv {
    private Csv() {}
    public static void write(Writer out, List<Book> books) throws IOException {
        out.write("\uFEFFISBN,Название,Автор,Дата добавления (UTC),Статус,Местонахождение,ID книги,Дата прочтения\r\n");
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        for (Book book : books) {
            out.write(cell(book.isbn) + "," + cell(book.title) + "," + cell(book.author) + "," + cell(format.format(new Date(book.addedAt))) + "," + cell(book.status.label) + "," + cell(book.location.label) + "," + cell(book.id) + "," + cell(book.readOn) + "\r\n");
        }
        out.flush();
    }
    /** Reads UTF-8 CSV produced here or a table with ISBN and title columns. */
    public static List<Book> read(Reader reader) throws IOException {
        StringBuilder text=new StringBuilder();char[] buffer=new char[8192];int n;
        while((n=reader.read(buffer))!=-1){if(text.length()+n>16*1024*1024)throw new IOException("CSV слишком большой");text.append(buffer,0,n);}
        if(text.length()>0&&text.charAt(0)=='\uFEFF')text.deleteCharAt(0);
        boolean quoted=false;char delimiter=',';
        for(int i=0;i<text.length();i++){char c=text.charAt(i);if(c=='"')quoted=!quoted;if(!quoted&&(c=='\n'||c=='\r'))break;if(!quoted&&c==';'){delimiter=';';break;}}
        List<List<String>> rows=new ArrayList<>();List<String> row=new ArrayList<>();StringBuilder cell=new StringBuilder();boolean inQuote=false,closed=false;
        for(int i=0;i<text.length();i++){
            char c=text.charAt(i);
            if(inQuote){if(c=='"'){if(i+1<text.length()&&text.charAt(i+1)=='"'){cell.append('"');i++;}else{inQuote=false;closed=true;}}else cell.append(c);}
            else if(c==delimiter||c=='\n'||c=='\r'){
                row.add(cell.toString());cell.setLength(0);closed=false;
                if(c!=delimiter){if(c=='\r'&&i+1<text.length()&&text.charAt(i+1)=='\n')i++;rows.add(row);row=new ArrayList<>();if(rows.size()>50001)throw new IOException("Слишком много строк в CSV");}
            }else if(c=='"'&&cell.length()==0&&!closed)inQuote=true;
            else{if(closed||c=='"')throw new IOException("Неверные кавычки в CSV");cell.append(c);}
        }
        if(inQuote)throw new IOException("Не закрыты кавычки в CSV");
        if(cell.length()>0||!row.isEmpty()||closed){row.add(cell.toString());rows.add(row);}
        if(rows.isEmpty())throw new IOException("CSV пуст");
        List<String> header=rows.get(0);int isbn=column(header,"isbn","isbn-13","isbn13"),title=column(header,"название","title"),author=column(header,"автор","author"),date=column(header,"дата добавления (utc)","added_at","addedat"),status=column(header,"статус","status"),location=column(header,"местонахождение","location"),identity=column(header,"id книги","book_id"),readDate=column(header,"дата прочтения","read_on","readon");
        if(title<0)throw new IOException("Нужен столбец Название (или title)");
        List<Book> books=new ArrayList<>();long now=System.currentTimeMillis();
        SimpleDateFormat format=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'",Locale.ROOT);format.setTimeZone(TimeZone.getTimeZone("UTC"));format.setLenient(false);
        for(int i=1;i<rows.size();i++){
            List<String> values=rows.get(i);if(values.size()==1&&values.get(0).trim().isEmpty())continue;
            String error="Строка "+(i+1)+": ";
            if(values.size()!=header.size())throw new IOException(error+"число столбцов не совпадает с заголовком");
            String rawIsbn=isbn<0?"":values.get(isbn).trim();String code=rawIsbn.isEmpty()?"":Isbn.normalize(rawIsbn);
            if(code==null)throw new IOException(error+"некорректный ISBN");
            String key=identity<0?"":values.get(identity).trim();
            if(!key.isEmpty()&&(!Book.validId(key)||(!code.isEmpty()&&!Book.isLocalId(key)&&!key.equals(code))||(code.isEmpty()&&!Book.isLocalId(key))))throw new IOException(error+"некорректный ID книги");
            String actualIsbn=code;
            if(!key.isEmpty())code=key;else if(code.isEmpty())code=Book.newLocalId();
            String name=unescape(values.get(title)),by=author<0?"":unescape(values.get(author));
            if(code==null||name.trim().isEmpty()||name.length()>10000||by.length()>10000)throw new IOException(error+"проверьте ISBN и название");
            long timestamp=now;
            if(date>=0&&!values.get(date).trim().isEmpty()){
                String raw=values.get(date).trim();java.text.ParsePosition pos=new java.text.ParsePosition(0);Date parsed=format.parse(raw,pos);
                if(parsed==null||pos.getIndex()!=raw.length()||parsed.getTime()<0)throw new IOException(error+"дата должна быть в формате 2026-09-28T12:00:00Z");timestamp=parsed.getTime();
            }
            ReadingStatus reading=ReadingStatus.NONE;
            if(status>=0&&!values.get(status).trim().isEmpty()){
                String raw=values.get(status).trim();boolean found=false;
                for(ReadingStatus candidate:ReadingStatus.values())if(candidate.label.equalsIgnoreCase(raw)||candidate.name().equalsIgnoreCase(raw)){reading=candidate;found=true;break;}
                if(!found)throw new IOException(error+"неизвестный статус чтения");
            }
            BookLocation place=BookLocation.UNKNOWN;
            if(location>=0&&!values.get(location).trim().isEmpty()){
                boolean found=false;for(BookLocation option:BookLocation.values())if(option.label.equalsIgnoreCase(values.get(location).trim())||option.name().equalsIgnoreCase(values.get(location).trim())){place=option;found=true;break;}
                if(!found)throw new IOException(error+"неизвестное местонахождение");
            }
            String readOn=readDate<0?"":values.get(readDate).trim();
            if(!ReadingDate.valid(readOn))throw new IOException(error+"дата прочтения должна быть в формате ГГГГ-ММ-ДД");
            books.add(new Book(code,actualIsbn,name,by,timestamp,reading,place,readOn));
        }
        return books;
    }
    private static int column(List<String> header,String... names)throws IOException{
        int found=-1;for(int i=0;i<header.size();i++)for(String name:names)if(name.equalsIgnoreCase(header.get(i).trim())){if(found>=0&&found!=i)throw new IOException("Повторяющийся столбец: "+name);found=i;break;}return found;
    }
    private static String unescape(String value){
        if(value.startsWith("'")){String rest=value.substring(1).trim();if(!rest.isEmpty()&&"=+-@".indexOf(rest.charAt(0))>=0)return value.substring(1);}return value;
    }
    static String cell(String value) {
        // Neutralize spreadsheet formulas, including those hidden behind whitespace.
        String trimmed = value.trim();
        if (!trimmed.isEmpty() && "=+-@".indexOf(trimmed.charAt(0)) >= 0) value = "'" + value;
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
