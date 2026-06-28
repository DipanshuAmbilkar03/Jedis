package com.miniredis.store;

/**
 * Wraps a value stored in Mini Redis with its type tag.
 * 
 * The value field holds the actual data:
 *   - STRING → String
 *   - LIST   → java.util.LinkedList<String>
 *   - SET    → java.util.LinkedHashSet<String>
 *   - HASH   → java.util.LinkedHashMap<String, String>
 */
public class RedisObject {

    private final DataType type;
    private final Object value;
    private volatile long lastAccessTime;
    private volatile int accessFrequency;

    public RedisObject(DataType type, Object value) {
        this.type = type;
        this.value = value;
        this.lastAccessTime = System.currentTimeMillis();
        this.accessFrequency = 5; // default starting frequency
    }

    public void touch() {
        lastAccessTime = System.currentTimeMillis();
        // Logarithmic decay/increment approximation
        double r = Math.random();
        double p = 1.0 / ((accessFrequency - 5) * 10.0 + 1.0);
        if (r < p) {
            accessFrequency = Math.min(255, accessFrequency + 1);
        }
    }

    public long getIdleTime() {
        return System.currentTimeMillis() - lastAccessTime;
    }

    public int getAccessFrequency() {
        return accessFrequency;
    }

    public DataType getType() {
        return type;
    }

    public Object getValue() {
        return value;
    }

    /**
     * Get the value cast to String (for STRING type).
     */
    @SuppressWarnings("unchecked")
    public String getStringValue() {
        return (String) value;
    }

    /**
     * Get the value cast to LinkedList (for LIST type).
     */
    @SuppressWarnings("unchecked")
    public java.util.LinkedList<String> getListValue() {
        return (java.util.LinkedList<String>) value;
    }

    /**
     * Get the value cast to LinkedHashSet (for SET type).
     */
    @SuppressWarnings("unchecked")
    public java.util.LinkedHashSet<String> getSetValue() {
        return (java.util.LinkedHashSet<String>) value;
    }

    /**
     * Get the value cast to LinkedHashMap (for HASH type).
     */
    @SuppressWarnings("unchecked")
    public java.util.LinkedHashMap<String, String> getHashValue() {
        return (java.util.LinkedHashMap<String, String>) value;
    }

    /**
     * Create a STRING RedisObject.
     */
    public static RedisObject string(String value) {
        return new RedisObject(DataType.STRING, value);
    }

    /**
     * Create a LIST RedisObject with an empty LinkedList.
     */
    public static RedisObject list() {
        return new RedisObject(DataType.LIST, new java.util.LinkedList<String>());
    }

    /**
     * Create a SET RedisObject with an empty LinkedHashSet.
     */
    public static RedisObject set() {
        return new RedisObject(DataType.SET, new java.util.LinkedHashSet<String>());
    }

    /**
     * Create a HASH RedisObject with an empty LinkedHashMap.
     */
    public static RedisObject hash() {
        return new RedisObject(DataType.HASH, new java.util.LinkedHashMap<String, String>());
    }

    public byte[] toBytes() throws java.io.IOException {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        java.io.DataOutputStream dos = new java.io.DataOutputStream(baos);

        dos.writeByte(type.ordinal());

        switch (type) {
            case STRING -> {
                byte[] bytes = getStringValue().getBytes("UTF-8");
                dos.writeInt(bytes.length);
                dos.write(bytes);
            }
            case LIST -> {
                var list = getListValue();
                dos.writeInt(list.size());
                for (String item : list) {
                    byte[] bytes = item.getBytes("UTF-8");
                    dos.writeInt(bytes.length);
                    dos.write(bytes);
                }
            }
            case SET -> {
                var set = getSetValue();
                dos.writeInt(set.size());
                for (String item : set) {
                    byte[] bytes = item.getBytes("UTF-8");
                    dos.writeInt(bytes.length);
                    dos.write(bytes);
                }
            }
            case HASH -> {
                var map = getHashValue();
                dos.writeInt(map.size());
                for (java.util.Map.Entry<String, String> entry : map.entrySet()) {
                    byte[] fBytes = entry.getKey().getBytes("UTF-8");
                    dos.writeInt(fBytes.length);
                    dos.write(fBytes);

                    byte[] vBytes = entry.getValue().getBytes("UTF-8");
                    dos.writeInt(vBytes.length);
                    dos.write(vBytes);
                }
            }
        }
        dos.flush();
        return baos.toByteArray();
    }

    public static RedisObject fromBytes(byte[] data) throws java.io.IOException {
        java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(data);
        java.io.DataInputStream dis = new java.io.DataInputStream(bais);

        byte typeOrd = dis.readByte();
        DataType type = DataType.values()[typeOrd];

        return switch (type) {
            case STRING -> {
                int len = dis.readInt();
                byte[] bytes = new byte[len];
                dis.readFully(bytes);
                yield RedisObject.string(new String(bytes, "UTF-8"));
            }
            case LIST -> {
                int size = dis.readInt();
                RedisObject listObj = RedisObject.list();
                var list = listObj.getListValue();
                for (int i = 0; i < size; i++) {
                    int len = dis.readInt();
                    byte[] bytes = new byte[len];
                    dis.readFully(bytes);
                    list.add(new String(bytes, "UTF-8"));
                }
                yield listObj;
            }
            case SET -> {
                int size = dis.readInt();
                RedisObject setObj = RedisObject.set();
                var set = setObj.getSetValue();
                for (int i = 0; i < size; i++) {
                    int len = dis.readInt();
                    byte[] bytes = new byte[len];
                    dis.readFully(bytes);
                    set.add(new String(bytes, "UTF-8"));
                }
                yield setObj;
            }
            case HASH -> {
                int size = dis.readInt();
                RedisObject hashObj = RedisObject.hash();
                var map = hashObj.getHashValue();
                for (int i = 0; i < size; i++) {
                    int fLen = dis.readInt();
                    byte[] fBytes = new byte[fLen];
                    dis.readFully(fBytes);
                    String field = new String(fBytes, "UTF-8");

                    int vLen = dis.readInt();
                    byte[] vBytes = new byte[vLen];
                    dis.readFully(vBytes);
                    String value = new String(vBytes, "UTF-8");

                    map.put(field, value);
                }
                yield hashObj;
            }
        };
    }

    @Override
    public String toString() {
        return "RedisObject{type=" + type + ", value=" + value + "}";
    }
}
