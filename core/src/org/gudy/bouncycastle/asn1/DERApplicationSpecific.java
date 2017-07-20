package org.gudy.bouncycastle.asn1;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Base class for an application specific object
 */
public class DERApplicationSpecific
    extends ASN1Object
{
    private int       tag;
    private byte[]    octets;

    public DERApplicationSpecific(
        int        tag,
        byte[]    octets)
    {
        this.tag = tag;
        this.octets = octets;
    }

    public DERApplicationSpecific(
        int                  tag,
        DEREncodable         object)
        throws IOException
    {
        this(true, tag, object);
    }

    public DERApplicationSpecific(
        boolean      explicit,
        int          tag,
        DEREncodable object)
        throws IOException
    {
        ByteArrayOutputStream bOut = new ByteArrayOutputStream();
        DEROutputStream dos = new DEROutputStream(bOut);

        dos.writeObject(object);

        byte[] data = bOut.toByteArray();

        if (tag >= 0x1f)
        {
            throw new IOException("unsupported tag number");
        }

        if (explicit)
        {
            this.tag = tag | DERTags.CONSTRUCTED;
            this.octets = data;
        }
        else
        {
            this.tag = tag;
            int lenBytes = getLengthOfLength(data);
            byte[] tmp = new byte[data.length - lenBytes];
            System.arraycopy(data, lenBytes, tmp, 0, tmp.length);
            this.octets = tmp;
        }
    }

    private int getLengthOfLength(byte[] data)
    {
        int count = 2;               // TODO: assumes only a 1 byte tag number

        while((data[count - 1] & 0x80) != 0)
        {
            count++;
        }

        return count;
    }

    public boolean isConstructed()
    {
        return (tag & DERTags.CONSTRUCTED) != 0;
    }

    public byte[] getContents()
    {
        return octets;
    }

    public int getApplicationTag()
    {
        return tag;
    }

    public DERObject getObject()
        throws IOException
    {
        return new ASN1InputStream(getContents()).readObject();
    }

    /**
     * Return the enclosed object assuming implicit tagging.
     *
     * @param derTagNo the type tag that should be applied to the object's contents.
     * @return  the resulting object
     * @throws IOException if reconstruction fails.
     */
    public DERObject getObject(int derTagNo)
        throws IOException
    {
        if (tag >= 0x1f)
        {
            throw new IOException("unsupported tag number");
        }

        byte[] tmp = this.getEncoded();

        tmp[0] = (byte)derTagNo;

        return new ASN1InputStream(tmp).readObject();
    }

    /* (non-Javadoc)
     * @see org.gudy.bouncycastle.asn1.DERObject#encode(org.gudy.bouncycastle.asn1.DEROutputStream)
     */
    @Override
    void encode(DEROutputStream out) throws IOException
    {
        out.writeEncoded(DERTags.APPLICATION | tag, octets);
    }

    @Override
    boolean asn1Equals(
        DERObject o)
    {
        if (!(o instanceof DERApplicationSpecific))
        {
            return false;
        }

        DERApplicationSpecific other = (DERApplicationSpecific)o;

        if (tag != other.tag)
        {
            return false;
        }

        if (octets.length != other.octets.length)
        {
            return false;
        }

        for (int i = 0; i < octets.length; i++)
        {
            if (octets[i] != other.octets[i])
            {
                return false;
            }
        }

        return true;
    }

    public int hashCode()
    {
        byte[]  b = this.getContents();
        int     value = 0;

        for (int i = 0; i != b.length; i++)
        {
            value ^= (b[i] & 0xff) << (i % 4);
        }

        return value ^ this.getApplicationTag();
    }
}
