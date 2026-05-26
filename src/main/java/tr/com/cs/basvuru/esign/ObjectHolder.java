package tr.com.cs.basvuru.esign;

import java.io.Serializable;

public class ObjectHolder implements Serializable {
    private static final long serialVersionUID = 1L;
    private Object object;

    public ObjectHolder(Object object) {
        this.object = object;
    }

    public Object getObject() {
        return this.object;
    }

    public String toString() {
        return this.object.toString();
    }
}
