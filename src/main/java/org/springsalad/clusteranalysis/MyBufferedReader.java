package org.springsalad.clusteranalysis;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;

public class MyBufferedReader extends BufferedReader {
    public MyBufferedReader(Reader in){
        super(in);
    }

    public MyBufferedReader(Reader in, int sz){
        super(in,sz);
    }

    @Override
    public String readLine() throws IOException {
        String line;
        while (true){
            line = super.readLine();
            if (line==null) return line;
            line = line.trim();
            if (!line.isEmpty())
                return line;
        }
    }
}
