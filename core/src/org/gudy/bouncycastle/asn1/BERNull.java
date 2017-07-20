package org.gudy.bouncycastle.asn1;

import java.io.IOException;

/**
 * A BER NULL object.
 */
public class BERNull
    extends DERNull
{
    public static final BERNull INSTANCE = new BERNull();

    public BERNull()
    {
    }

    @Override
    void encode(
        DEROutputStream  out)
        throws IOException
    {
        if (out instanceof ASN1OutputStream || out instanceof BEROutputStream)
        {
            out.write(NULL);
        }
        else
        {
            super.encode(out);
        }
    }
}
