package com.universalplatform.storage;
import static org.junit.jupiter.api.Assertions.*; import java.io.*; import java.nio.charset.StandardCharsets; import java.nio.file.*; import org.junit.jupiter.api.*; import org.junit.jupiter.api.io.TempDir;
class LocalStorageAdapterTest {
 @TempDir Path temp;
 @Test void streamsRoundTripWithoutEscapingRoot() throws Exception {LocalStorageAdapter a=new LocalStorageAdapter(temp.toString());byte[] payload="learning-content".getBytes(StandardCharsets.UTF_8);String key=a.put("tenant/2026/object.bin",new ByteArrayInputStream(payload),payload.length,"application/octet-stream");try(InputStream in=a.open(key)){assertArrayEquals(payload,in.readAllBytes());}a.delete(key);assertThrows(IOException.class,()->a.open(key));}
 @Test void rejectsTraversal(){LocalStorageAdapter a=new LocalStorageAdapter(temp.toString());assertThrows(IllegalArgumentException.class,()->a.open("../escape"));}
 @Test void rejectsLengthMismatch() {LocalStorageAdapter a=new LocalStorageAdapter(temp.toString());assertThrows(IOException.class,()->a.put("tenant/bad.bin",new ByteArrayInputStream(new byte[]{1,2}),3,"application/octet-stream"));}
}
