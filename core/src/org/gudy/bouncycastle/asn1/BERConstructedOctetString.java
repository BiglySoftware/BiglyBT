package org.gudy.bouncycastle.asn1;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Vector;

public class BERConstructedOctetString
    extends DEROctetString
{
    private static final int MAX_LENGTH = 1000;

    /**
     * convert a vector of octet strings into a single byte string
     */
    static private byte[] toBytes(
        Vector  octs)
    {
        ByteArrayOutputStream   bOut = new ByteArrayOutputStream();

        for (int i = 0; i != octs.size(); i++)
        {
            try
            {
                DEROctetString  o = (DEROctetString)octs.elementAt(i);

                bOut.write(o.getOctets());
            }
            catch (ClassCastException e)
            {
                throw new IllegalArgumentException(octs.elementAt(i).getClass().getName() + " found in input should only contain DEROctetString");
            }
            catch (IOException e)
            {
                throw new IllegalArgumentException("exception converting octets " + e.toString());
            }
        }

        return bOut.toByteArray();
    }

    private Vector  octs;

    /**
     * @param string the octets making up the octet string.
     */
    public BERConstructedOctetString(
        byte[]  string)
    {
        super(string);
    }

    public BERConstructedOctetString(
        Vector  octs)
    {
        super(toBytes(octs));

        this.octs = octs;
    }

    public BERConstructedOctetString(
        DERObject  obj)
    {
        super(obj);
    }

    public BERConstructedOctetString(
        DEREncodable  obj)
    {
        super(obj.getDERObject());
    }

    @Override
    public byte[] getOctets()
    {
        return string;
    }

    /**
     * return the DER octets that make up this string.
     */
    public Enumeration getObjects()
    {
        if (octs == null)
        {
            return generateOcts().elements();
        }

        return octs.elements();
    }

    private Vector generateOcts()
    {
        int     start = 0;
        int     end = 0;
        Vector  vec = new Vector();

        while ((end + 1) < string.length)
        {
            if (string[end] == 0 && string[end + 1] == 0)
            {
                byte[]  nStr = new byte[end - start + 1];

                System.arraycopy(string, start, nStr, 0, nStr.length);

                vec.addElement(new DEROctetString(nStr));
                start = end + 1;
            }
            end++;
        }

        byte[]  nStr = new byte[string.length - start];

        System.arraycopy(string, start, nStr, 0, nStr.length);

        vec.addElement(new DEROctetString(nStr));

        return vec;
    }

    @Override
    public void encode(
        DEROutputStream out)
        throws IOException
    {
        if (out instanceof ASN1OutputStream || out instanceof BEROutputStream)
        {
            out.write(CONSTRUCTED | OCTET_STRING);

            out.write(0x80);

            //
            // write out the octet array
            //
            if (octs != null)
            {
                for (int i = 0; i != octs.size(); i++)
                {
                    out.writeObject(octs.elementAt(i));
                }
            }
            else
            {
                for (int i = 0; i < string.length; i += MAX_LENGTH)
                {
                    int end;

                    if (i + MAX_LENGTH > string.length)
                    {
                        end = string.length;
                    }
                    else
                    {
                        end = i + MAX_LENGTH;
                    }

                    byte[]  nStr = new byte[end - i];

                    System.arraycopy(string, i, nStr, 0, nStr.length);

                    out.writeObject(new DEROctetString(nStr));
                }
            }

            out.write(0x00);
            out.write(0x00);
        }
        else
        {
            super.encode(out);
        }
    }
}
