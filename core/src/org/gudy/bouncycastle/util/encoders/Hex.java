package org.gudy.bouncycastle.util.encoders;

/**
 * Converters for going from hex to binary and back.
 * <p>
 * Note: this class assumes ASCII processing.
 */
public class Hex
{
    private static HexTranslator   encoder = new HexTranslator();

    private static final byte[]   hexTable =
        {
            (byte)'0', (byte)'1', (byte)'2', (byte)'3', (byte)'4', (byte)'5', (byte)'6', (byte)'7',
            (byte)'8', (byte)'9', (byte)'a', (byte)'b', (byte)'c', (byte)'d', (byte)'e', (byte)'f'
        };

    public static byte[] encode(
        byte[]  array)
    {
        return encode(array, 0, array.length);
    }

    public static byte[] encode(
        byte[]  array,
        int     off,
        int     length)
    {
        byte[]      enc = new byte[length * 2];

        encoder.encode(array, off, length, enc, 0);

        return enc;
    }

    public static byte[] decode(
        String  string)
    {
        byte[]          bytes = new byte[string.length() / 2];
        String          buf = string.toLowerCase();

        for (int i = 0; i < buf.length(); i += 2)
        {
			char    left  = buf.charAt(i);
			char    right = buf.charAt(i+1);
            int     index = i / 2;

            if (left < 'a')
            {
                bytes[index] = (byte)((left - '0') << 4);
            }
            else
            {
                bytes[index] = (byte)((left - 'a' + 10) << 4);
            }
            if (right < 'a')
            {
                bytes[index] += (byte)(right - '0');
            }
            else
            {
                bytes[index] += (byte)(right - 'a' + 10);
            }
        }

        return bytes;
    }

    public static byte[] decode(
        byte[]  array)
    {
        byte[]          bytes = new byte[array.length / 2];

        encoder.decode(array, 0, array.length, bytes, 0);

        return bytes;
    }
}
