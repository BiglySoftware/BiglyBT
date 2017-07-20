package org.gudy.bouncycastle.asn1;

import java.io.IOException;
import java.io.InputStream;

public class BERTaggedObjectParser
    implements ASN1TaggedObjectParser
{
    private int _baseTag;
    private int _tagNumber;
    private InputStream _contentStream;

    private boolean _indefiniteLength;

    protected BERTaggedObjectParser(
        int         baseTag,
        int         tagNumber,
        InputStream contentStream)
    {
        _baseTag = baseTag;
        _tagNumber = tagNumber;
        _contentStream = contentStream;
        _indefiniteLength = contentStream instanceof IndefiniteLengthInputStream;
    }

    public boolean isConstructed()
    {
        return (_baseTag & DERTags.CONSTRUCTED) != 0;
    }

    @Override
    public int getTagNo()
    {
        return _tagNumber;
    }

    @Override
    public DEREncodable getObjectParser(
        int     tag,
        boolean isExplicit)
        throws IOException
    {
        if (isExplicit)
        {
            return new ASN1StreamParser(_contentStream).readObject();
        }
        else
        {
            switch (tag)
            {
            case DERTags.SET:
                if (_indefiniteLength)
                {
                    return new BERSetParser(new ASN1ObjectParser(_baseTag, _tagNumber, _contentStream));
                }
                else
                {
                    return new DERSet(loadVector(_contentStream)).parser();
                }
            case DERTags.SEQUENCE:
                if (_indefiniteLength)
                {
                    return new BERSequenceParser(new ASN1ObjectParser(_baseTag, _tagNumber, _contentStream));
                }
                else
                {
                    return new DERSequence(loadVector(_contentStream)).parser();
                }
            case DERTags.OCTET_STRING:
                if (_indefiniteLength || this.isConstructed())
                {
                    return new BEROctetStringParser(new ASN1ObjectParser(_baseTag, _tagNumber, _contentStream));
                }
                else
                {
                    return new DEROctetString(((DefiniteLengthInputStream)_contentStream).toByteArray()).parser();
                }
            }
        }

        throw new RuntimeException("implicit tagging not implemented");
    }

    private ASN1EncodableVector loadVector(InputStream in)
        throws IOException
    {
        ASN1StreamParser        aIn = new ASN1StreamParser(in);
        ASN1EncodableVector     v = new ASN1EncodableVector();
        DEREncodable            obj = aIn.readObject();

        while (obj != null)
        {
            v.add(obj.getDERObject());
            obj = aIn.readObject();
        }

        return v;
    }

    private ASN1EncodableVector rLoadVector(InputStream in)
    {
        try
        {
            return loadVector(in);
        }
        catch (IOException e)
        {
            throw new IllegalStateException(e.getMessage());
        }
    }

    @Override
    public DERObject getDERObject()
    {
        if (_indefiniteLength)
        {
            ASN1EncodableVector v = rLoadVector(_contentStream);

            if (v.size() > 1)
            {
                return new BERTaggedObject(false, _tagNumber, new BERSequence(v));
            }
            else if (v.size() == 1)
            {
                return new BERTaggedObject(true, _tagNumber, v.get(0));
            }
            else
            {
                return new BERTaggedObject(false, _tagNumber, new BERSequence());
            }
        }
        else
        {
            if (this.isConstructed())
            {
                ASN1EncodableVector v = rLoadVector(_contentStream);

                if (v.size() == 1)
                {
                    return new DERTaggedObject(true, _tagNumber, v.get(0));
                }

                return new DERTaggedObject(false, _tagNumber, new DERSequence(v));
            }

            try
            {
                return new DERTaggedObject(false, _tagNumber, new DEROctetString(((DefiniteLengthInputStream)_contentStream).toByteArray()));
            }
            catch (IOException e)
            {
                throw new IllegalStateException(e.getMessage());
            }
        }

    }
}
