package org.gudy.bouncycastle.asn1;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class BEROctetStringParser
    implements ASN1OctetStringParser
{
    private ASN1ObjectParser _parser;

    protected BEROctetStringParser(
        ASN1ObjectParser parser)
    {
        _parser = parser;
    }

    @Override
    public InputStream getOctetStream()
    {
        return new ConstructedOctetStream(_parser);
    }

    @Override
    public DERObject getDERObject()
    {
        ByteArrayOutputStream bOut = new ByteArrayOutputStream();
        InputStream in = this.getOctetStream();
        int         ch;

        try
        {
            while ((ch = in.read()) >= 0)
            {
                bOut.write(ch);
            }
        }
        catch (IOException e)
        {
            throw new IllegalStateException("IOException converting stream to byte array: " + e.getMessage());
        }

        return new BERConstructedOctetString(bOut.toByteArray());
    }
}
