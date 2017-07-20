package org.gudy.bouncycastle.asn1.x509;

import org.gudy.bouncycastle.asn1.*;

public class AttCertValidityPeriod
    extends ASN1Encodable
{
    DERGeneralizedTime  notBeforeTime;
    DERGeneralizedTime  notAfterTime;

    public static AttCertValidityPeriod getInstance(
            Object  obj)
    {
        if (obj instanceof AttCertValidityPeriod)
        {
            return (AttCertValidityPeriod)obj;
        }
        else if (obj instanceof ASN1Sequence)
        {
            return new AttCertValidityPeriod((ASN1Sequence)obj);
        }

        throw new IllegalArgumentException("unknown object in factory");
    }

    public AttCertValidityPeriod(
        ASN1Sequence    seq)
    {
        if (seq.size() != 2)
        {
            throw new IllegalArgumentException("Bad sequence size: "
                    + seq.size());
        }

        notBeforeTime = DERGeneralizedTime.getInstance(seq.getObjectAt(0));
        notAfterTime = DERGeneralizedTime.getInstance(seq.getObjectAt(1));
    }

    /**
     * @param notBeforeTime
     * @param notAfterTime
     */
    public AttCertValidityPeriod(
        DERGeneralizedTime notBeforeTime,
        DERGeneralizedTime notAfterTime)
    {
        this.notBeforeTime = notBeforeTime;
        this.notAfterTime = notAfterTime;
    }

    public DERGeneralizedTime getNotBeforeTime()
    {
        return notBeforeTime;
    }

    public DERGeneralizedTime getNotAfterTime()
    {
        return notAfterTime;
    }

    /**
     * Produce an object suitable for an ASN1OutputStream.
     * <pre>
     *  AttCertValidityPeriod  ::= SEQUENCE {
     *       notBeforeTime  GeneralizedTime,
     *       notAfterTime   GeneralizedTime
     *  }
     * </pre>
     */
    @Override
    public DERObject toASN1Object()
    {
        ASN1EncodableVector  v = new ASN1EncodableVector();

        v.add(notBeforeTime);
        v.add(notAfterTime);

        return new DERSequence(v);
    }
}
