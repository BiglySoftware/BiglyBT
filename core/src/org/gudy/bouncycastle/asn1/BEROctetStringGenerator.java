package org.gudy.bouncycastle.asn1;

import java.io.IOException;
import java.io.OutputStream;

public class BEROctetStringGenerator
    extends BERGenerator
{
    public BEROctetStringGenerator(OutputStream out)
        throws IOException
    {
        super(out);

        writeBERHeader(DERTags.CONSTRUCTED | DERTags.OCTET_STRING);
    }

    public BEROctetStringGenerator(
        OutputStream out,
        int tagNo,
        boolean isExplicit)
        throws IOException
    {
        super(out, tagNo, isExplicit);

        writeBERHeader(DERTags.CONSTRUCTED | DERTags.OCTET_STRING);
    }

    public OutputStream getOctetOutputStream()
    {
        return getOctetOutputStream(new byte[1000]); // limit for CER encoding.
    }

    public OutputStream getOctetOutputStream(
        byte[] buf)
    {
        return new BufferedBEROctetStream(buf);
    }

    private class BufferedBEROctetStream
        extends OutputStream
    {
        private byte[] _buf;
        private int    _off;

        BufferedBEROctetStream(
            byte[] buf)
        {
            _buf = buf;
            _off = 0;
        }

        @Override
        public void write(
            int b)
            throws IOException
        {
            _buf[_off++] = (byte)b;

            if (_off == _buf.length)
            {
                _out.write(new DEROctetString(_buf).getEncoded());
                _off = 0;
            }
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException
        {
            while (len > 0)
            {
                int numToCopy = Math.min(len, _buf.length - _off);
                System.arraycopy(b, off, _buf, _off, numToCopy);

                _off += numToCopy;
                if (_off < _buf.length)
                {
                    break;
                }

                _out.write(new DEROctetString(_buf).getEncoded());
                _off = 0;

                off += numToCopy;
                len -= numToCopy;
            }
        }

        @Override
        public void close()
            throws IOException
        {
            if (_off != 0)
            {
                byte[] bytes = new byte[_off];
                System.arraycopy(_buf, 0, bytes, 0, _off);

                _out.write(new DEROctetString(bytes).getEncoded());
            }

             writeBEREnd();
        }
    }
}
