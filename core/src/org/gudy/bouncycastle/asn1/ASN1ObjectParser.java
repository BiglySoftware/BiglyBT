package org.gudy.bouncycastle.asn1;

import java.io.IOException;
import java.io.InputStream;

public class ASN1ObjectParser
{
    private int              _baseTag;
    private int              _tagNumber;

    private ASN1StreamParser _aIn;

    protected ASN1ObjectParser(
        int         baseTag,
        int         tagNumber,
        InputStream contentStream)
    {
        _baseTag = baseTag;
        _tagNumber = tagNumber;
        _aIn = new ASN1StreamParser(contentStream);
    }

    /**
     * Return the tag number for this object.
     *
     * @return the tag number.
     */
    int getTagNumber()
    {
        return _tagNumber;
    }

    int getBaseTag()
    {
        return _baseTag;
    }

    DEREncodable readObject()
        throws IOException
    {
        return _aIn.readObject();
    }

    ASN1EncodableVector readVector()
        throws IllegalStateException
    {
        ASN1EncodableVector v = new ASN1EncodableVector();
        DEREncodable obj;

        try
        {
            while ((obj = readObject()) != null)
            {
                v.add(obj.getDERObject());
            }
        }
        catch (IOException e)
        {
            throw new IllegalStateException(e.getMessage());
        }

        return v;
    }
}
