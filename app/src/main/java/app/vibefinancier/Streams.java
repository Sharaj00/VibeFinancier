package app.vibefinancier;

import java.io.*;

public final class Streams {
    private Streams(){}
    public static byte[] read(InputStream input,int limit)throws IOException {
        if(input==null)throw new IOException("Cannot open input file");
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int count;
        while((count=input.read(buffer))!=-1){if(out.size()+count>limit)throw new IOException("Input exceeds the "+limit+" byte limit");out.write(buffer,0,count);}return out.toByteArray();
    }
}
