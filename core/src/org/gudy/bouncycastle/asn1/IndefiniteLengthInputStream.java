package org.gudy.bouncycastle.asn1;

import java.io.IOException;
import java.io.InputStream;

class IndefiniteLengthInputStream
    extends LimitedInputStream
{
    private int _b1;
    private int _b2;
    private boolean _eofReached = false;
    private boolean _eofOn00 = true;

    IndefiniteLengthInputStream(
        InputStream in)
        throws IOException
    {
        super(in);

        _b1 = in.read();
        _b2 = in.read();
        _eofReached = (_b2 < 0);
    }

    void setEofOn00(
        boolean eofOn00)
    {
        _eofOn00 = eofOn00;
    }

    boolean checkForEof()
    {
        if (_eofOn00 && (_b1 == 0x00 && _b2 == 0x00))
        {
            _eofReached = true;
            setParentEofDetect(true);
        }
        return _eofReached;
    }

    @Override
    public int read(byte[] b, int off, int len)
        throws IOException
    {
        // Only use this optimisation if we aren't checking for 00
        if (_eofOn00 || len < 3)
        {
            return super.read(b, off, len);
        }

        if (_eofReached)
        {
            return -1;
        }

        int numRead = _in.read(b, off + 2, len - 2);

        if (numRead < 0)
        {
//          throw new EOFException();
            _eofReached = true;
            return -1;
        }

        b[off] = (byte)_b1;
        b[off + 1] = (byte)_b2;

        _b1 = _in.read();
        _b2 = _in.read();

        if (_b2 < 0)
        {
            // Corrupted stream
//            throw new EOFException();
            _eofReached = true;
            // Just fall thru...
        }

        return numRead + 2;
    }

    @Override
    public int read()
        throws IOException
    {
        if (checkForEof())
        {
            return -1;
        }

        int b = _in.read();

        //
        // strictly speaking we should return b1 and b2, but if this happens the stream
        // is corrupted so we are already in trouble.
        //
        if (b < 0)
        {
            // Corrupted stream
//            throw new EOFException();
            _eofReached = true;

            return -1;
        }

        int v = _b1;

        _b1 = _b2;
        _b2 = b;

        return v;
    }
}
