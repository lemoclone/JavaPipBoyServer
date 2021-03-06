package de.luckydonald.pipboyserver.PipBoyServer.types;

import de.luckydonald.pipboyserver.PipBoyServer.Database;
import de.luckydonald.pipboyserver.PipBoyServer.EntryType;
import de.luckydonald.pipboyserver.PipBoyServer.exceptions.AlreadyInsertedException;
import de.luckydonald.pipboyserver.PipBoyServer.exceptions.AlreadyTakenException;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class DBDict extends DBContainer {
    public static final EntryType TYPE = EntryType.DICT;
    private HashMap<String, DBEntry> data;
    private ConcurrentHashMap<String, DBEntry> inserts;
    private List<DBEntry> removes;
    private ReentrantReadWriteLock updateLock = new ReentrantReadWriteLock();

    public ReentrantReadWriteLock getUpdateLock() {
        return updateLock;
    }

    public DBDict() {
        this(null);
    }
    public DBDict(Database db) {
        super(db);
        this.data = new HashMap<>();
        this.inserts = new ConcurrentHashMap<String, DBEntry>();
        this.removes = new ArrayList<>();
    }

    /**
     * Adds each of our children to the {@link Database}.
     * (Via {@link DBEntry#addToDB(Database)})
     *
     * @param db The Database where we add to.
     * @return Itself
     * @throws AlreadyInsertedException See {@link DBEntry#addToDB(Database)}
     */
    @Override
    public DBContainer addChildrenToDB(Database db) throws AlreadyInsertedException {
        for (Map.Entry<String, DBEntry> entry : this.data.entrySet()) {
            entry.getValue().addToDB(db);
        }
        return this;
    }

    public DBDict(Database db, HashMap<String, DBEntry> data) {
        super(db);
        this.data = data;
        this.inserts = new ConcurrentHashMap<String, DBEntry>(data);
        this.removes = new ArrayList<>();
    }

    public DBDict(Database db, String key, DBEntry data) {
        this(db);
        this.data.put(key, data);
        this.inserts.put(key, data);
    }

    @Override
    public EntryType getType() {
        return TYPE;
    }

    @Override
    public ByteBuffer putValueIntoBuffer(ByteBuffer b) {
        //uint16_t insert_count; //2 //short
        //DictEntry[insert_count];
        //uint32_t id
        //char_t *name //null terminated
        //uint16_t remove_count;
        //uint32_t references[remove_count];
        getLogger().finest("locking DB: read");
        this.getDatabase().getEntriesLock().readLock().lock();
        getLogger().finest("locking Update: write");
        this.getUpdateLock().writeLock().lock();
        int insertCount = this.inserts.size();
        b.putShort((short) insertCount);
        for (Iterator<Map.Entry<String, DBEntry>> iterator = this.inserts.entrySet().iterator(); iterator.hasNext(); ) {
            Map.Entry<String, DBEntry> entry = iterator.next();
            DictEntry e = new DictEntry(entry.getKey(), entry.getValue());
            e.putValueIntoByteBuffer(b);
            iterator.remove();
        }
        int removeCount = this.removes.size();
        b.putShort((short) removeCount);
        for (Iterator<DBEntry> iterator = this.removes.iterator(); iterator.hasNext(); ) {
            DBEntry entry = iterator.next();
            b.putInt(entry.getID());
            iterator.remove();
        }
        this.getUpdateLock().writeLock().unlock();
        getLogger().finest("unlocked Update: write");
        this.getDatabase().getEntriesLock().readLock().unlock();
        getLogger().finest("unlocked DB: read");
        return b;
    }

    @Override
    public int getRequiredValueBufferLength() {
        int length = 2; // insert_count
        getLogger().finest("locking DB: read");
        this.getDatabase().getEntriesLock().readLock().lock();
        for (Map.Entry<String, DBEntry> entry  : this.inserts.entrySet()) {
            length += entry.getKey().getBytes().length + 1; //name string .length + null terminator
            length += 4; // id = 4
        }
        length += 2; // remove_count
        length += (this.removes.size() * 4); //id = 4; n-th times
        this.getDatabase().getEntriesLock().readLock().unlock();
        getLogger().finest("unlocked DB: read");
        return length;
    }

    public DBDict add(DictEntry entry) throws AlreadyTakenException, AlreadyInsertedException {
        //todo UPDATEs
        getLogger().finest("locking DB: write");
        this.getDatabase().getEntriesLock().writeLock().lock();
        if (entry.getDBEntry() == null) {
            throw new IllegalArgumentException("entry.getDBEntry() is null. Did you create it with a DBEntry?");
        }
        DBDict result = this.add(entry.name, entry.getDBEntry());
        this.getDatabase().getEntriesLock().writeLock().unlock();
        getLogger().finest("unlocked DB: write");
        return result;
    }

    /**
     * Retrieves a {@link DBEntry} by its key,
     * or {@code null} if this {@link DBDict} contains no mapping for the key.
     *
     * Lookup is done DB-Thread-safe
     *
     * @param key The key of the item.
     * @return The result
     */
    public DBEntry get(String key) {
        getLogger().finest("locking DB: read");
        this.getDatabase().getEntriesLock().readLock().lock();
        getLogger().finest("locking Update: read");
        this.getUpdateLock().readLock().lock();
        DBEntry dbEntry = this.data.get(key);
        this.getUpdateLock().readLock().unlock();
        getLogger().finest("unlocked DB: read");
        this.getDatabase().getEntriesLock().readLock().unlock();
        getLogger().finest("unlocked Update: read");
        return dbEntry;
    }

    /**
     * Adds a element to the array.
     *
     * //TODO: DB-Thread-safe
     * @param key The key.
     * @param value The {@link DBEntry}.
     * @return itself, the {@link DBDict}.
     */
    public DBDict add(String key, DBEntry value) throws AlreadyInsertedException, AlreadyTakenException {
        //TODO: update events
        getLogger().finest("locking DB: write");
        this.getDatabase().getEntriesLock().writeLock().lock();
        getLogger().finest("locking Update: write");
        this.getUpdateLock().writeLock().lock();
        this.addNewEntryToDB(value);  // check if is not in DB -> db.add
        this.data.put(key, value);
        this.inserts.put(key, value);   //TODO: update events
        this.getUpdateLock().writeLock().unlock();
        getLogger().finest("unlocked Update: write");
        this.getDatabase().getEntriesLock().writeLock().unlock();
        getLogger().finest("unlocked DB: write");
        return this;
    }
    public DBDict remove(DictEntry entry) {
        return this.remove(entry.getName());
    }

    /**
     * removes a Entry by its name.
     *
     * DB-Thread-safe
     *
     * @param key
     * @return
     */
    public DBDict remove(String key) {
        //TODO: update events
        getLogger().finest("locking Update: write");
        this.getUpdateLock().writeLock().lock();
        getLogger().finest("locking DB: write");
        this.getDatabase().getEntriesLock().writeLock().lock();
        DBEntry deleted = this.data.get(key);
        this.data.remove(key);
        if (this.inserts.remove(key) == null) {
            // key was not contained => already synced.
            // So add it to the deletion queue.
            this.removes.add(deleted);
        }
        this.getUpdateLock().writeLock().unlock();
        getLogger().finest("unlocked Update: write");
        this.getDatabase().getEntriesLock().writeLock().unlock();
        getLogger().finest("unlocked DB: write");
        return this;
    }

    /**
     * Removes a Entry by a given id.
     *
     * DB-Thread-safe
     * @param id the id of the element.
     * @returns itself
     */
    public DBDict remove(int id) {
        //TODO: update events
        getLogger().finest("locking DB: write");
        this.getDatabase().getEntriesLock().writeLock().lock();
        getLogger().finest("locking Update: write");
        this.getUpdateLock().writeLock().lock();
        DBEntry deleted = null;
        String del_key = null;
        for (Map.Entry<String, DBEntry> entry  : this.data.entrySet()) {
            if (entry.getValue().getID() == id) {
                deleted = entry.getValue();
                del_key = entry.getKey();
            }
        }
        if(deleted == null || del_key == null) {
            this.getDatabase().getEntriesLock().writeLock().unlock();
            getLogger().finest("unlocked DB: write");
            this.getUpdateLock().writeLock().unlock();
            getLogger().finest("unlocked Update: write");
            throw new IllegalArgumentException("Could not find ID in array.");
        }
        //  Now remove it and add set the update lists accordingly:
        DBDict result = this.remove(del_key);
        this.getDatabase().getEntriesLock().writeLock().unlock();
        getLogger().finest("unlocked DB: write");
        this.getUpdateLock().writeLock().unlock();
        getLogger().finest("unlocked Update: write");
        return result;
    }

    static public class DictEntry {
        private int id; //uint32_t //int
        private String name;
        private DBEntry value = null;

        public DictEntry(String name, int id) {
            this.id = id;
            this.name = name;
        }
        public DictEntry(int id, String name) {
            this(name, id);
        }
        public DictEntry(String name, DBEntry entry){
            this.id = entry.getID();
            this.name = name;
            this.value = entry;
        }
        public DictEntry(DBEntry entry, String name) {
            this(name, entry);
        }
        public int getID() {
            return this.id;
        }
        public String getName() {
            return this.name;
        }
        public DBEntry getDBEntry() {
            return this.value;
        }
        public int getRequiredValueBufferLength() {
            return 4 + this.name.length() + 1; // id = 4, string = s+1 //null termination
        }
        public ByteBuffer putValueIntoByteBuffer(ByteBuffer b) {
            b.putInt(this.id);
            b.put(this.name.getBytes());
            b.put((byte) 0); //Null-Termination of string
            return b;
        }
    }

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder("DBDict(");
        getLogger().finest("locking Update: read");
        this.getUpdateLock().readLock().lock();
        getLogger().finest("locking DB: read");
        this.getDatabase().getEntriesLock().readLock().lock();
        if (this.getID() <= 0){
            s.append("id=").append(this.getID()).append(", ");
        }
        s.append("data={");
        for (Map.Entry<String, DBEntry> entry  : this.data.entrySet()) {
            s.append("\"").append(entry.getKey()).append("\":").append(entry.getValue().toString()).append(", ");
        }
        s.append("}, inserts={");
        for (Map.Entry<String, DBEntry> entry  : this.inserts.entrySet()) {
            s.append("\"").append(entry.getKey()).append("\":").append(entry.getValue().toString()).append(", ");
        }
        s.append("}, removes=[");
        for (DBEntry entry  : this.removes) {
            s.append(entry.toString()).append(", ");
        }
        this.getUpdateLock().readLock().unlock();
        getLogger().finest("unlocked DB: read");
        this.getDatabase().getEntriesLock().readLock().unlock();
        getLogger().finest("unlocked Update: read");
        return s.append("]").toString();
    }

    @Override
    public String toSimpleString(boolean showID) {
        //TODO: showID
        StringBuilder sb = new StringBuilder("DBDict(");
        boolean notFirst = false;
        getLogger().finest("locking Update: read");
        this.getUpdateLock().readLock().lock();
        getLogger().finest("locking DB: read");
        this.getDatabase().getEntriesLock().readLock().lock();
        if (this.getID() <= 0){
            sb.append("id=").append(this.getID()).append(", ");
        }
        sb.append("data={");
        for (Map.Entry<String, DBEntry> entry  : this.data.entrySet()) {
            if (notFirst) {
                sb.append(", ");
            } else {
                notFirst = true;
            }
            sb.append("\"").append(entry.getKey()).append("\":").append(entry.getValue().getID());
        }
        sb.append("}, inserts={");
        notFirst = false;
        for (Map.Entry<String, DBEntry> entry  : this.inserts.entrySet()) {
            if (notFirst) {
                sb.append(", ");
            } else {
                notFirst = true;
            }
            sb.append("\"").append(entry.getKey()).append("\":").append(entry.getValue().getID());
        }
        sb.append("}, removes=[");
        notFirst = false;
        for (DBEntry entry  : this.removes) {
            if (notFirst) {
                sb.append(", ");
            } else {
                notFirst = true;
            }
            sb.append(entry.getID());
        }
        this.getDatabase().getEntriesLock().readLock().unlock();
        getLogger().finest("unlocked Update: read");
        this.getUpdateLock().readLock().unlock();
        getLogger().finest("unlocked DB: read");
        return sb.append("]").toString();
    }

    /**
     * Returns the {@link DBDict} for this node, <b>if and only if</b>
     * this node is a {@link DBDict}. For other
     * types returns {@code null}.
     *
     * @return {@link DBDict} if any, {@code null} else.
     */
    @Override
    public DBDict getDBDict() {
        return this;
    }
}
