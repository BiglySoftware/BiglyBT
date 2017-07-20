package org.gudy.bouncycastle.asn1;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

class DefiniteLengthInputStream
        extends LimitedInputStream
{
    private int               _length;

    DefiniteLengthInputStream(
        InputStream in,
        int         length)
    {
        super(in);

        if (length < 0)
        {
            throw new IllegalArgumentException("negative lengths not allowed");
        }

        this._length = length;
    }

    @Override
    public int read()
        throws IOException
    {
        if (_length > 0)
        {
            int b = _in.read();

            if (b < 0)
            {
                throw new EOFException();
            }

            --_length;
            return b;
        }

        setParentEofDetect(true);

        return -1;
    }

    @Override
    public int read(byte[] buf, int off, int len)
        throws IOException
    {
        if (_length > 0)
        {
            int toRead = Math.min(len, _length);
            int numRead = _in.read(buf, off, toRead);

            if (numRead < 0)
                throw new EOFException();

            _length -= numRead;
            return numRead;
        }

        setParentEofDetect(true);

        return -1;
    }

    byte[] toByteArray()
        throws IOException
    {
        byte[] bytes = new byte[_length];

        if (_length > 0)
        {
            int pos = 0;
            do
            {
                int read = _in.read(bytes, pos, _length - pos);

                if (read < 0)
                {
                    throw new EOFException();
                }

                pos += read;
            }
            while (pos < _length);

            _length = 0;
        }

        setParentEofDetect(true);

        return bytes;
    }
}
