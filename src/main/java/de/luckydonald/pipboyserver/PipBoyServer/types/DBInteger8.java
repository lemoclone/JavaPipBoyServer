package de.luckydonald.pipboyserver.PipBoyServer.types;

import de.luckydonald.pipboyserver.PipBoyServer.Database;
import de.luckydonald.pipboyserver.PipBoyServer.EntryType;
import de.luckydonald.pipboyserver.PipBoyServer.exceptions.ParserException;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class DBInteger8 extends DBSimple<Byte> {
    public static final EntryType TYPE = EntryType.INT8;

    public DBInteger8(int i) {
        this(null, i);
    }

    public DBInteger8(byte i) {
        this(null, i);
    }

    public DBInteger8(Database db, int i) {
        this(db, (byte) i);
        if (i > 255 || i < 0) {
            throw new IllegalArgumentException("Smaller as 0 or bigger as 255.");
        }
    }
    public DBInteger8(Database db, byte i) {
        super(db);
        this.value = i;
    }

    @Override
    public EntryType getType() {
        return TYPE;
    }

    public byte[] getBytes_() {
        ByteBuffer content = ByteBuffer.allocate(1 + 4 + 1); //type=1, id=4, bool=1
        content.order(ByteOrder.LITTLE_ENDIAN);
        content.put(this.getType().getByte());
        content.put(this.value);
        return content.array();
    }

    @Override
    public int getRequiredValueBufferLength() {
        return 1; // int8 = 1
    }

    @Override
    public ByteBuffer putValueIntoBuffer(ByteBuffer b) {
        b.put(this.value);
        return b;
    }

    @Override
    public String toString(boolean showID) {
        if (!showID || this.getID() == -1 && this.getDatabase() == null) {
            return "DBInteger8(" + value + ")";
        }
        String s = "DBInteger8(";
        if (this.getID() != -1) {
            s += "id=" + this.getID() + ", ";
        }
        if (this.getDatabase() == null) {
            s += "database=" + this.getDatabase() + ", ";
        }
        return s + "value=" + value + ")";
    }

    /**
     * Parses and applies the {@code byte} value from a given {@code String}.
     *
     * @param s The string to parse.
     * @return the updated {@link DBInteger8} entry.
     * @throws ParserException if parsing as {@code byte} fails.
     */
    @Override
    public DBInteger8 setValueFromString(String s) throws ParserException {
        try {
            Byte b = new Byte(s);
            this.setValue(b);
        } catch (NumberFormatException e) {
            throw new ParserException(e);
        }
        return this;
    }

    /**
     * Returns numeric value for this node, <b>if and only if</b>
     * this node is numeric ({@link #isNumber} returns true); otherwise
     * returns null
     *
     * @return Number value this node contains, if any (null for non-number
     * nodes).
     */
    @Override
    public Number numberValue() {
        return (this.getValue());
    }

    /**
     * Returns 16-bit short value for this node, <b>if and only if</b>
     * this node is numeric ({@link #isNumber} returns true). For other
     * types returns 0.
     * For floating-point numbers, value is truncated using default
     * Java coercion, similar to how cast from double to short operates.
     *
     * @return Short value this node contains, if any; 0 for non-number
     * nodes.
     */
    @Override
    public short shortValue() {
        return ((short) this.getValue());
    }

    /**
     * Returns integer value for this node, <b>if and only if</b>
     * this node is numeric ({@link #isNumber} returns true). For other
     * types returns 0.
     * For floating-point numbers, value is truncated using default
     * Java coercion, similar to how cast from double to int operates.
     *
     * @return Integer value this node contains, if any; 0 for non-number
     * nodes.
     */
    @Override
    public int intValue() {
        return ((int) this.getValue());
    }

    /**
     * Returns 64-bit long value for this node, <b>if and only if</b>
     * this node is numeric ({@link #isNumber} returns true). For other
     * types returns 0.
     * For floating-point numbers, value is truncated using default
     * Java coercion, similar to how cast from double to long operates.
     *
     * @return Long value this node contains, if any; 0 for non-number
     * nodes.
     */
    @Override
    public long longValue() {
        return ((long) this.getValue());
    }

    /**
     * Returns 32-bit floating value for this node, <b>if and only if</b>
     * this node is numeric ({@link #isNumber} returns true). For other
     * types returns 0.0.
     * For integer values, conversion is done using coercion; this means
     * that an overflow is possible for `long` values
     *
     * @return 32-bit float value this node contains, if any; 0.0 for non-number nodes.
     * @since 2.2
     */
    @Override
    public float floatValue() {
        return ((float) this.getValue());
    }

    /**
     * Returns 64-bit floating point (double) value for this node, <b>if and only if</b>
     * this node is numeric ({@link #isNumber} returns true). For other
     * types returns 0.0.
     * For integer values, conversion is done using coercion; this may result
     * in overflows with {@link BigInteger} values.
     *
     * @return 64-bit double value this node contains, if any; 0.0 for non-number nodes.
     * @since 2.2
     */
    @Override
    public double doubleValue() {
        return ((double) this.getValue());
    }
}
