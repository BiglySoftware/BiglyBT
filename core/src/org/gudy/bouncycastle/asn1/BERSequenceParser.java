package org.gudy.bouncycastle.asn1;

import java.io.IOException;

public class BERSequenceParser
    implements ASN1SequenceParser
{
    private ASN1ObjectParser _parser;

    BERSequenceParser(ASN1ObjectParser parser)
    {
        this._parser = parser;
    }

    @Override
    public DEREncodable readObject()
        throws IOException
    {
        return _parser.readObject();
    }

    @Override
    public DERObject getDERObject()
    {
        return new BERSequence(_parser.readVector());
    }
}
