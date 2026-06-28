package com.miniredis.persistence;

import com.miniredis.store.DataStore;
import com.miniredis.store.DataType;
import com.miniredis.store.RedisObject;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;
import java.util.zip.CheckedOutputStream;

/**
 * RDB Snapshotter — dumps the entire in-memory store to a compressed binary format (MRDB).
 * 
 * Provides fast recovery on startup (load the full snapshot at once).
 * Implements Magic Header, Versioning, and CRC32 Checksum verification.
 */
public class RdbSnapshotter {

    private static final byte[] MAGIC_HEADER = {'M', 'R', 'D', 'B'};
    private static final short VERSION = 1;

    private final String filePath;
    private final DataStore dataStore;

    private final java.util.concurrent.ExecutorService bgExecutor = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "mini-redis-bgsave");
        t.setDaemon(true);
        return t;
    });
    private volatile boolean isSaving = false;

    public RdbSnapshotter(String filePath, DataStore dataStore) {
        this.filePath = filePath;
        this.dataStore = dataStore;
    }

    public String getFilePath() {
        return filePath;
    }

    public synchronized void save() {
        Map<String, RedisObject> storeCopy = new HashMap<>();
        for (String key : dataStore.getRawStore().keySet()) {
            RedisObject obj = dataStore.get(key);
            if (obj != null) {
                storeCopy.put(key, obj);
            }
        }
        Map<String, Long> expiryMapCopy = new HashMap<>(dataStore.getExpiryManager().getRawExpiryMap());
        performSave(storeCopy, expiryMapCopy);
    }

    /**
     * Save the database to a binary snapshot in the background.
     */
    public synchronized boolean backgroundSave() {
        if (isSaving) {
            System.out.println("[RDB] Background save is already in progress.");
            return false;
        }
        isSaving = true;
        System.out.println("[RDB] Background saving started.");

        Map<String, RedisObject> storeCopy = new HashMap<>();
        for (String key : dataStore.getRawStore().keySet()) {
            RedisObject obj = dataStore.get(key);
            if (obj != null) {
                storeCopy.put(key, obj);
            }
        }
        Map<String, Long> expiryMapCopy = new HashMap<>(dataStore.getExpiryManager().getRawExpiryMap());

        bgExecutor.submit(() -> {
            try {
                performSave(storeCopy, expiryMapCopy);
            } finally {
                isSaving = false;
            }
        });
        return true;
    }

    public boolean isSaving() {
        return isSaving;
    }

    public void shutdown() {
        bgExecutor.shutdown();
        try {
            if (!bgExecutor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                bgExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            bgExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void performSave(Map<String, RedisObject> store, Map<String, Long> expiryMap) {
        try {
            // Write to a temp file first, then rename (atomic)
            String tempPath = filePath + ".tmp";
            CRC32 crc = new CRC32();

            try (FileOutputStream fos = new FileOutputStream(tempPath);
                 BufferedOutputStream bos = new BufferedOutputStream(fos);
                 CheckedOutputStream cos = new CheckedOutputStream(bos, crc);
                 DataOutputStream dos = new DataOutputStream(cos)) {

                // 1. Write Header
                dos.write(MAGIC_HEADER);
                dos.writeShort(VERSION);

                // Count active non-expired keys first
                int activeKeyCount = 0;
                long now = System.currentTimeMillis();
                for (Map.Entry<String, RedisObject> entry : store.entrySet()) {
                    String key = entry.getKey();
                    Long expiry = expiryMap.get(key);
                    if (expiry == null || expiry > now) {
                        activeKeyCount++;
                    }
                }

                // 2. Write Key Count
                dos.writeInt(activeKeyCount);

                // 3. Write Data Entries
                for (Map.Entry<String, RedisObject> entry : store.entrySet()) {
                    String key = entry.getKey();
                    RedisObject obj = entry.getValue();

                    Long expiry = expiryMap.get(key);
                    if (expiry != null && expiry <= now) {
                        continue; // Skip expired keys
                    }

                    // a. Expiry flag & value
                    if (expiry != null) {
                        dos.writeByte(1); // Has expiry
                        dos.writeLong(expiry);
                    } else {
                        dos.writeByte(0); // No expiry
                    }

                    // b. DataType tag
                    DataType type = obj.getType();
                    dos.writeByte(typeToByte(type));

                    // c. Key
                    byte[] keyBytes = key.getBytes("UTF-8");
                    dos.writeInt(keyBytes.length);
                    dos.write(keyBytes);

                    // d. Value payload
                    serializeValue(dos, obj);
                }

                // Flush buffer before fetching checksum
                dos.flush();
                long checksum = crc.getValue();

                // 4. Write Checksum
                dos.writeLong(checksum);
            }

            // Atomic rename
            Files.move(Path.of(tempPath), Path.of(filePath), StandardCopyOption.REPLACE_EXISTING);

            System.out.println("[RDB] Background snapshot saved: " + store.size() + " keys → " + filePath);
        } catch (IOException e) {
            System.err.println("[RDB] Snapshot failed: " + e.getMessage());
        }
    }

    /**
     * Load a snapshot from file into the data store.
     */
    public void load() {
        Path path = Path.of(filePath);
        if (!Files.exists(path)) {
            return;
        }

        CRC32 crc = new CRC32();
        try (FileInputStream fis = new FileInputStream(filePath);
             BufferedInputStream bis = new BufferedInputStream(fis);
             CheckedInputStream cis = new CheckedInputStream(bis, crc);
             DataInputStream dis = new DataInputStream(cis)) {

            // 1. Read and Validate Header
            byte[] magic = new byte[4];
            dis.readFully(magic);
            if (!Arrays.equals(magic, MAGIC_HEADER)) {
                throw new IOException("Invalid RDB magic header");
            }

            short version = dis.readShort();
            if (version != VERSION) {
                throw new IOException("Unsupported RDB format version: " + version);
            }

            // 2. Read Key Count
            int keyCount = dis.readInt();
            int keysLoaded = 0;

            // 3. Read Data Entries
            for (int i = 0; i < keyCount; i++) {
                // a. Expiry metadata
                byte hasExpiry = dis.readByte();
                long expiry = -1;
                if (hasExpiry == 1) {
                    expiry = dis.readLong();
                }

                // b. DataType
                byte typeByte = dis.readByte();
                DataType type = byteToType(typeByte);

                // c. Key
                int keyLen = dis.readInt();
                byte[] keyBytes = new byte[keyLen];
                dis.readFully(keyBytes);
                String key = new String(keyBytes, "UTF-8");

                // d. Value
                RedisObject obj = deserializeValue(dis, type);

                // Add to DataStore if not expired
                if (hasExpiry == 0 || expiry > System.currentTimeMillis()) {
                    dataStore.set(key, obj);
                    if (hasExpiry == 1) {
                        dataStore.getExpiryManager().setExpiry(key, expiry);
                    }
                    keysLoaded++;
                }
            }

            // 4. Verify Checksum
            long computedChecksum = crc.getValue();
            long storedChecksum = dis.readLong();
            if (computedChecksum != storedChecksum) {
                throw new IOException("RDB Checksum validation failed! Data may be corrupted.");
            }

            if (keysLoaded > 0) {
                System.out.println("[RDB] Loaded " + keysLoaded + " keys from " + filePath);
            }

        } catch (IOException e) {
            System.err.println("[RDB] Failed to load snapshot: " + e.getMessage());
        }
    }

    // ── Helper Serialization/Deserialization Methods ──

    private byte typeToByte(DataType type) {
        return switch (type) {
            case STRING -> (byte) 1;
            case LIST -> (byte) 2;
            case SET -> (byte) 3;
            case HASH -> (byte) 4;
        };
    }

    private DataType byteToType(byte b) throws IOException {
        return switch (b) {
            case 1 -> DataType.STRING;
            case 2 -> DataType.LIST;
            case 3 -> DataType.SET;
            case 4 -> DataType.HASH;
            default -> throw new IOException("Unknown byte data type: " + b);
        };
    }

    private void serializeValue(DataOutputStream dos, RedisObject obj) throws IOException {
        switch (obj.getType()) {
            case STRING -> {
                byte[] bytes = obj.getStringValue().getBytes("UTF-8");
                dos.writeInt(bytes.length);
                dos.write(bytes);
            }
            case LIST -> {
                var list = obj.getListValue();
                dos.writeInt(list.size());
                for (String item : list) {
                    byte[] bytes = item.getBytes("UTF-8");
                    dos.writeInt(bytes.length);
                    dos.write(bytes);
                }
            }
            case SET -> {
                var set = obj.getSetValue();
                dos.writeInt(set.size());
                for (String item : set) {
                    byte[] bytes = item.getBytes("UTF-8");
                    dos.writeInt(bytes.length);
                    dos.write(bytes);
                }
            }
            case HASH -> {
                var map = obj.getHashValue();
                dos.writeInt(map.size());
                for (Map.Entry<String, String> entry : map.entrySet()) {
                    byte[] fBytes = entry.getKey().getBytes("UTF-8");
                    dos.writeInt(fBytes.length);
                    dos.write(fBytes);

                    byte[] vBytes = entry.getValue().getBytes("UTF-8");
                    dos.writeInt(vBytes.length);
                    dos.write(vBytes);
                }
            }
        }
    }

    private RedisObject deserializeValue(DataInputStream dis, DataType type) throws IOException {
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
}
