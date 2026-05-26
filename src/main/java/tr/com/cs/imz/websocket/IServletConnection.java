package tr.com.cs.imz.websocket;

import java.net.URLConnection;
import java.util.LinkedList;
import tr.com.cs.imz.websocket.model.BasvuruTemel;

public interface IServletConnection {
    URLConnection getURLConn();

    LinkedList<String> writeObjects(
            BasvuruTemel paramBasvuruTemel, String paramString, byte[] paramArrayOfbyte)
            throws Exception;

    LinkedList<String> writeObjects(String paramString, byte[] paramArrayOfbyte) throws Exception;
}
