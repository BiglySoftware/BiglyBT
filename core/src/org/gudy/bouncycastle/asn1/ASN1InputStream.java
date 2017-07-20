package org.gudy.bouncycastle.asn1;

import java.io.*;
import java.util.Vector;

/**
 * a general purpose ASN.1 decoder - note: this class differs from the
 * others in that it returns null after it has read the last object in
 * the stream. If an ASN.1 NULL is encountered a DER/BER Null object is
 * returned.
 */
public class ASN1InputStream
    extends FilterInputStream
    implements DERTags
{
    private static final DERObject END_OF_STREAM = new DERObject()
    {
        @Override
        void encode(
            DEROutputStream out)
        throws IOException
        {
            throw new IOException("Eeek!");
        }
        public int hashCode()
        {
            return 0;
        }
        public boolean equals(
            Object o)
        {
            return o == this;
        }
    };

    boolean eofFound = false;
    int     limit = Integer.MAX_VALUE;

    public ASN1InputStream(
        InputStream is)
    {
        super(is);
    }

    /**
     * Create an ASN1InputStream based on the input byte array. The length of DER objects in
     * the stream is automatically limited to the length of the input array.
     *
     * @param input array containing ASN.1 encoded data.
     */
    public ASN1InputStream(
        byte[] input)
    {
        this(new ByteArrayInputStream(input), input.length);
    }

    /**
     * Create an ASN1InputStream where no DER object will be longer than limit.
     *
     * @param input stream containing ASN.1 encoded data.
     * @param limit maximum size of a DER encoded object.
     */
    public ASN1InputStream(
        InputStream input,
        int         limit)
    {
        super(input);
        this.limit = limit;
    }

    protected int readLength()
        throws IOException
    {
        int length = read();
        if (length < 0)
        {
            throw new IOException("EOF found when length expected");
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
                int next = read();

                if (next < 0)
                {
                    throw new IOException("EOF found reading length");
                }

                length = (length << 8) + next;
            }

            if (length < 0)
            {
                throw new IOException("corrupted stream - negative length found");
            }

            if (length >= limit)   // after all we must have read at least 1 byte
            {
                throw new IOException("corrupted stream - out of bounds length found");
            }
        }

        return length;
    }

    protected void readFully(
        byte[]  bytes)
        throws IOException
    {
        int     left = bytes.length;
        int     len;

        if (left == 0)
        {
            return;
        }

        while ((len = read(bytes, bytes.length - left, left)) > 0)
        {
            if ((left -= len) == 0)
            {
                return;
            }
        }

        if (left != 0)
        {
            throw new EOFException("EOF encountered in middle of object");
        }
    }

    /**
     * build an object given its tag and the number of bytes to construct it from.
     */
    protected DERObject buildObject(
        int       tag,
        int       tagNo,
        int       length)
        throws IOException
    {
        if ((tag & APPLICATION) != 0)
        {
            return new DERApplicationSpecific(tagNo, readDefiniteLengthFully(length));
        }

        boolean isConstructed = (tag & CONSTRUCTED) != 0;

        if (isConstructed)
        {
            switch (tag)
            {
            case SEQUENCE | CONSTRUCTED:
                return new DERSequence(buildDerEncodableVector(length));
            case SET | CONSTRUCTED:
                return new DERSet(buildDerEncodableVector(length), false);
            case OCTET_STRING | CONSTRUCTED:
                return buildDerConstructedOctetString(length);
            default:
            {
                //
                // with tagged object tag number is bottom 5 bits
                //
                if ((tag & TAGGED) != 0)
                {
                    if (length == 0)     // empty tag!
                    {
                        return new DERTaggedObject(false, tagNo, new DERSequence());
                    }

                    ASN1EncodableVector v = buildDerEncodableVector(length);

                    if (v.size() == 1)
                    {
                        //
                        // explicitly tagged (probably!) - if it isn't we'd have to
                        // tell from the context
                        //
                        return new DERTaggedObject(tagNo, v.get(0));
                    }

                    return new DERTaggedObject(false, tagNo, new DERSequence(v));
                }

                return new DERUnknownTag(tag, readDefiniteLengthFully(length));
            }
            }
        }

        byte[] bytes = readDefiniteLengthFully(length);

        switch (tag)
        {
        case NULL:
            return DERNull.INSTANCE;
        case BOOLEAN:
            return new DERBoolean(bytes);
        case INTEGER:
            return new DERInteger(bytes);
        case ENUMERATED:
            return new DEREnumerated(bytes);
        case OBJECT_IDENTIFIER:
            return new DERObjectIdentifier(bytes);
        case BIT_STRING:
        {
            int     padBits = bytes[0];
            byte[]  data = new byte[bytes.length - 1];

            System.arraycopy(bytes, 1, data, 0, bytes.length - 1);

            return new DERBitString(data, padBits);
        }
        case NUMERIC_STRING:
            return new DERNumericString(bytes);
        case UTF8_STRING:
            return new DERUTF8String(bytes);
        case PRINTABLE_STRING:
            return new DERPrintableString(bytes);
        case IA5_STRING:
            return new DERIA5String(bytes);
        case T61_STRING:
            return new DERT61String(bytes);
        case VISIBLE_STRING:
            return new DERVisibleString(bytes);
        case GENERAL_STRING:
            return new DERGeneralString(bytes);
        case UNIVERSAL_STRING:
            return new DERUniversalString(bytes);
        case BMP_STRING:
            return new DERBMPString(bytes);
        case OCTET_STRING:
            return new DEROctetString(bytes);
        case UTC_TIME:
            return new DERUTCTime(bytes);
        case GENERALIZED_TIME:
            return new DERGeneralizedTime(bytes);
        default:
        {
            //
            // with tagged object tag number is bottom 5 bits
            //
            if ((tag & TAGGED) != 0)
            {
                if (bytes.length == 0)     // empty tag!
                {
                    return new DERTaggedObject(false, tagNo, DERNull.INSTANCE);
                }

                //
                // simple type - implicit... return an octet string
                //
                return new DERTaggedObject(false, tagNo, new DEROctetString(bytes));
            }

            return new DERUnknownTag(tag, bytes);
        }
        }
    }

    private byte[] readDefiniteLengthFully(int length)
        throws IOException
    {
        byte[] bytes = new byte[length];
        readFully(bytes);
        return bytes;
    }

    /**
     * read a string of bytes representing an indefinite length object.
     */
    private byte[] readIndefiniteLengthFully()
        throws IOException
    {
        ByteArrayOutputStream   bOut = new ByteArrayOutputStream();
        int                     b, b1;

        b1 = read();

        while ((b = read()) >= 0)
        {
            if (b1 == 0 && b == 0)
            {
                break;
            }

            bOut.write(b1);
            b1 = b;
        }

        return bOut.toByteArray();
    }

    private BERConstructedOctetString buildConstructedOctetString(DERObject sentinel)
        throws IOException
    {
        Vector octs = new Vector();
        DERObject o;

        while ((o = readObject()) != sentinel)
        {
            octs.addElement(o);
        }

        return new BERConstructedOctetString(octs);
    }

    //
    // yes, people actually do this...
    //
    private BERConstructedOctetString buildDerConstructedOctetString(int length)
        throws IOException
    {
        DefiniteLengthInputStream dIn = new DefiniteLengthInputStream(this, length);
        ASN1InputStream aIn = new ASN1InputStream(dIn, length);

        return aIn.buildConstructedOctetString(null);
    }

    private ASN1EncodableVector buildEncodableVector(DERObject sentinel)
        throws IOException
    {
        ASN1EncodableVector v = new ASN1EncodableVector();
        DERObject o;

        while ((o = readObject()) != sentinel)
        {
            v.add(o);
        }

        return v;
    }

    private ASN1EncodableVector buildDerEncodableVector(int length)
        throws IOException
    {
        DefiniteLengthInputStream dIn = new DefiniteLengthInputStream(this, length);
        ASN1InputStream aIn = new ASN1InputStream(dIn, length);

        return aIn.buildEncodableVector(null);
    }

    public DERObject readObject()
        throws IOException
    {
        int tag = read();
        if (tag == -1)
        {
            if (eofFound)
            {
                throw new EOFException("attempt to read past end of file.");
            }

            eofFound = true;

            return null;
        }

        int tagNo = 0;

        if ((tag & TAGGED) != 0 || (tag & APPLICATION) != 0)
        {
            tagNo = readTagNumber(tag);
        }

        int     length = readLength();

        if (length < 0)    // indefinite length method
        {
            switch (tag)
            {
            case NULL:
                return BERNull.INSTANCE;
            case SEQUENCE | CONSTRUCTED:
                return new BERSequence(buildEncodableVector(END_OF_STREAM));
            case SET | CONSTRUCTED:
                return new BERSet(buildEncodableVector(END_OF_STREAM), false);
            case OCTET_STRING | CONSTRUCTED:
                return buildConstructedOctetString(END_OF_STREAM);
            default:
            {
                //
                // with tagged object tag number is bottom 5 bits
                //
                if ((tag & TAGGED) != 0)
                {
                    //
                    // simple type - implicit... return an octet string
                    //
                    if ((tag & CONSTRUCTED) == 0)
                    {
                        byte[] bytes = readIndefiniteLengthFully();

                        return new BERTaggedObject(false, tagNo, new DEROctetString(bytes));
                    }

                    //
                    // either constructed or explicitly tagged
                    //
                    ASN1EncodableVector v = buildEncodableVector(END_OF_STREAM);

                    if (v.size() == 0)     // empty tag!
                    {
                        return new DERTaggedObject(tagNo);
                    }

                    if (v.size() == 1)
                    {
                        //
                        // explicitly tagged (probably!) - if it isn't we'd have to
                        // tell from the context
                        //
                        return new BERTaggedObject(tagNo, v.get(0));
                    }

                    return new BERTaggedObject(false, tagNo, new BERSequence(v));
                }

                throw new IOException("unknown BER object encountered");
            }
            }
        }
        else
        {
            if (tag == 0 && length == 0)    // end of contents marker.
            {
                return END_OF_STREAM;
            }

            return buildObject(tag, tagNo, length);
        }
    }

    private int readTagNumber(int tag)
        throws IOException
    {
        int tagNo = tag & 0x1f;

        if (tagNo == 0x1f)
        {
            int b = read();

            tagNo = 0;

            while ((b >= 0) && ((b & 0x80) != 0))
            {
                tagNo |= (b & 0x7f);
                tagNo <<= 7;
                b = read();
            }

            if (b < 0)
            {
                eofFound = true;
                throw new EOFException("EOF found inside tag value.");
            }

            tagNo |= (b & 0x7f);
        }

        return tagNo;
    }
}

