package org.gudy.bouncycastle.asn1;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class DEROutputStream
    extends FilterOutputStream implements DERTags
{
    public DEROutputStream(
        OutputStream    os)
    {
        super(os);
    }

    private void writeLength(
        int length)
        throws IOException
    {
        if (length > 127)
        {
            int size = 1;
            int val = length;

            while ((val >>>= 8) != 0)
            {
                size++;
            }

            write((byte)(size | 0x80));

            for (int i = (size - 1) * 8; i >= 0; i -= 8)
            {
                write((byte)(length >> i));
            }
        }
        else
        {
            write((byte)length);
        }
    }

    void writeEncoded(
        int     tag,
        byte[]  bytes)
        throws IOException
    {
        write(tag);
        writeLength(bytes.length);
        write(bytes);
    }

    protected void writeNull()
        throws IOException
    {
        write(NULL);
        write(0x00);
    }

    @Override
    public void write(byte[] buf)
        throws IOException
    {
        out.write(buf, 0, buf.length);
    }

    @Override
    public void write(byte[] buf, int offSet, int len)
        throws IOException
    {
        out.write(buf, offSet, len);
    }

    public void writeObject(
        Object    obj)
        throws IOException
    {
        if (obj == null)
        {
            writeNull();
        }
        else if (obj instanceof DERObject)
        {
            ((DERObject)obj).encode(this);
        }
        else if (obj instanceof DEREncodable)
        {
            ((DEREncodable)obj).getDERObject().encode(this);
        }
        else
        {
            throw new IOException("object not DEREncodable");
        }
    }
}
