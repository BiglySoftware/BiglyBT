package org.gudy.bouncycastle.asn1;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

public class ASN1StreamParser
{
    InputStream _in;

    private int     _limit;
    private boolean _eofFound;

    public ASN1StreamParser(
        InputStream in)
    {
        this(in, Integer.MAX_VALUE);
    }

    public ASN1StreamParser(
        InputStream in,
        int         limit)
    {
        this._in = in;
        this._limit = limit;
    }

    public ASN1StreamParser(
        byte[] encoding)
    {
        this(new ByteArrayInputStream(encoding), encoding.length);
    }

    InputStream getParentStream()
    {
        return _in;
    }

    private int readLength()
        throws IOException
    {
        int length = _in.read();
        if (length < 0)
        {
            throw new EOFException("EOF found when length expected");
        }

        if (length == 0x80)
        {
            return -1;      // indefinite-length encoding
        }

        if (length > 127)
        {
            int size = length & 0x7f;

            if (size > 4)
            {
                throw new IOException("DER length more than 4 bytes");
            }

            length = 0;
            for (int i = 0; i < size; i++)
            {
                int next = _in.read();

                if (next < 0)
                {
                    throw new EOFException("EOF found reading length");
                }

                length = (length << 8) + next;
            }

            if (length < 0)
            {
                throw new IOException("corrupted stream - negative length found");
            }

            if (length >= _limit)   // after all we must have read at least 1 byte
            {
                throw new IOException("corrupted stream - out of bounds length found");
            }
        }

        return length;
    }

    public DEREncodable readObject()
        throws IOException
    {
        int tag = _in.read();
        if (tag == -1)
        {
            if (_eofFound)
            {
                throw new EOFException("attempt to read past end of file.");
            }

            _eofFound = true;

            return null;
        }

        //
        // turn of looking for "00" while we resolve the tag
        //
        set00Check(false);

        //
        // calculate tag number
        //
        int baseTagNo = tag & ~DERTags.CONSTRUCTED;
        int tagNo = baseTagNo;

        if ((tag & DERTags.TAGGED) != 0)
        {
            tagNo = tag & 0x1f;

            //
            // with tagged object tag number is bottom 5 bits, or stored at the start of the content
            //
            if (tagNo == 0x1f)
            {
                tagNo = 0;

                int b = _in.read();

                while ((b >= 0) && ((b & 0x80) != 0))
                {
                    tagNo |= (b & 0x7f);
                    tagNo <<= 7;
                    b = _in.read();
                }

                if (b < 0)
                {
                    _eofFound = true;

                    throw new EOFException("EOF encountered inside tag value.");
                }

                tagNo |= (b & 0x7f);
            }
        }

        //
        // calculate length
        //
        int length = readLength();

        if (length < 0)  // indefinite length
        {
            IndefiniteLengthInputStream indIn = new IndefiniteLengthInputStream(_in);

            switch (baseTagNo)
            {
            case DERTags.NULL:
                while (indIn.read() >= 0)
                {
                    // make sure we skip to end of object
                }
                return BERNull.INSTANCE;
            case DERTags.OCTET_STRING:
                return new BEROctetStringParser(new ASN1ObjectParser(tag, tagNo, indIn));
            case DERTags.SEQUENCE:
                return new BERSequenceParser(new ASN1ObjectParser(tag, tagNo, indIn));
            case DERTags.SET:
                return new BERSetParser(new ASN1ObjectParser(tag, tagNo, indIn));
            default:
                return new BERTaggedObjectParser(tag, tagNo, indIn);
            }
        }
        else
        {
            DefiniteLengthInputStream defIn = new DefiniteLengthInputStream(_in, length);

            switch (baseTagNo)
            {
            case DERTags.INTEGER:
                return new DERInteger(defIn.toByteArray());
            case DERTags.NULL:
                defIn.toByteArray(); // make sure we read to end of object bytes.
                return DERNull.INSTANCE;
            case DERTags.OBJECT_IDENTIFIER:
                return new DERObjectIdentifier(defIn.toByteArray());
            case DERTags.OCTET_STRING:
                return new DEROctetString(defIn.toByteArray());
            case DERTags.SEQUENCE:
                return new DERSequence(loadVector(defIn, length)).parser();
            case DERTags.SET:
                return new DERSet(loadVector(defIn, length)).parser();
            default:
                return new BERTaggedObjectParser(tag, tagNo, defIn);
            }
        }
    }

    private void set00Check(boolean enabled)
    {
        if (_in instanceof IndefiniteLengthInputStream)
        {
            ((IndefiniteLengthInputStream)_in).setEofOn00(enabled);
        }
    }

    private ASN1EncodableVector loadVector(InputStream in, int length)
        throws IOException
    {
        ASN1InputStream         aIn = new ASN1InputStream(in, length);
        ASN1EncodableVector     v = new ASN1EncodableVector();

        DERObject obj;
        while ((obj = aIn.readObject()) != null)
        {
            v.add(obj);
        }

        return v;
    }
}
