package org.gudy.bouncycastle.asn1.x509;

import org.gudy.bouncycastle.asn1.*;

/**
 * The AccessDescription object.
 * <pre>
 * AccessDescription  ::=  SEQUENCE {
 *       accessMethod          OBJECT IDENTIFIER,
 *       accessLocation        GeneralName  }
 * </pre>
 */
public class AccessDescription
    extends ASN1Encodable
{
    public final static DERObjectIdentifier id_ad_caIssuers = new DERObjectIdentifier("1.3.6.1.5.5.7.48.2");

    public final static DERObjectIdentifier id_ad_ocsp = new DERObjectIdentifier("1.3.6.1.5.5.7.48.1");

    DERObjectIdentifier accessMethod = null;
    GeneralName accessLocation = null;

    public static AccessDescription getInstance(
        Object  obj)
    {
        if (obj instanceof AccessDescription)
        {
            return (AccessDescription)obj;
        }
        else if (obj instanceof ASN1Sequence)
        {
            return new AccessDescription((ASN1Sequence)obj);
        }

        throw new IllegalArgumentException("unknown object in factory");
    }

    public AccessDescription(
        ASN1Sequence   seq)
    {
        if (seq.size() != 2)
        {
            throw new IllegalArgumentException("wrong number of elements in inner sequence");
        }

        accessMethod = DERObjectIdentifier.getInstance(seq.getObjectAt(0));
        accessLocation = GeneralName.getInstance(seq.getObjectAt(1));
    }

    /**
     * create an AccessDescription with the oid and location provided.
     */
    public AccessDescription(
        DERObjectIdentifier oid,
        GeneralName location)
    {
        accessMethod = oid;
        accessLocation = location;
    }

    /**
     *
     * @return the access method.
     */
    public DERObjectIdentifier getAccessMethod()
    {
        return accessMethod;
    }

    /**
     *
     * @return the access location
     */
    public GeneralName getAccessLocation()
    {
        return accessLocation;
    }

    @Override
    public DERObject toASN1Object()
    {
        ASN1EncodableVector accessDescription  = new ASN1EncodableVector();

        accessDescription.add(accessMethod);
        accessDescription.add(accessLocation);

        return new DERSequence(accessDescription);
    }

    public String toString()
    {
        return ("AccessDescription: Oid(" + this.accessMethod.getId() + ")");
    }
}
