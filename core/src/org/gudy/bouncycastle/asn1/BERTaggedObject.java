package org.gudy.bouncycastle.asn1;

import java.io.IOException;
import java.util.Enumeration;

/**
 * BER TaggedObject - in ASN.1 nottation this is any object proceeded by
 * a [n] where n is some number - these are assume to follow the construction
 * rules (as with sequences).
 */
public class BERTaggedObject
    extends DERTaggedObject
{
    /**
     * @param tagNo the tag number for this object.
     * @param obj the tagged object.
     */
    public BERTaggedObject(
        int             tagNo,
        DEREncodable    obj)
    {
        super(tagNo, obj);
    }

    /**
     * @param explicit true if an explicitly tagged object.
     * @param tagNo the tag number for this object.
     * @param obj the tagged object.
     */
    public BERTaggedObject(
        boolean         explicit,
        int             tagNo,
        DEREncodable    obj)
    {
        super(explicit, tagNo, obj);
    }

    /**
     * create an implicitly tagged object that contains a zero
     * length sequence.
     */
    public BERTaggedObject(
        int             tagNo)
    {
        super(false, tagNo, new BERSequence());
    }

    @Override
    void encode(
        DEROutputStream  out)
        throws IOException
    {
        if (out instanceof ASN1OutputStream || out instanceof BEROutputStream)
        {
            out.write(CONSTRUCTED | TAGGED | tagNo);
            out.write(0x80);

            if (!empty)
            {
                if (!explicit)
                {
                    if (obj instanceof ASN1OctetString)
                    {
                        Enumeration  e;

                        if (obj instanceof BERConstructedOctetString)
                        {
                            e = ((BERConstructedOctetString)obj).getObjects();
                        }
                        else
                        {
                            ASN1OctetString             octs = (ASN1OctetString)obj;
                            BERConstructedOctetString   berO = new BERConstructedOctetString(octs.getOctets());

                            e = berO.getObjects();
                        }

                        while (e.hasMoreElements())
                        {
                            out.writeObject(e.nextElement());
                        }
                    }
                    else if (obj instanceof ASN1Sequence)
                    {
                        Enumeration  e = ((ASN1Sequence)obj).getObjects();

                        while (e.hasMoreElements())
                        {
                            out.writeObject(e.nextElement());
                        }
                    }
                    else if (obj instanceof ASN1Set)
                    {
                        Enumeration  e = ((ASN1Set)obj).getObjects();

                        while (e.hasMoreElements())
                        {
                            out.writeObject(e.nextElement());
                        }
                    }
                    else
                    {
                        throw new RuntimeException("not implemented: " + obj.getClass().getName());
                    }
                }
                else
                {
                    out.writeObject(obj);
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
