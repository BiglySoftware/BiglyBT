package org.gudy.bouncycastle.asn1.pkcs;

import org.gudy.bouncycastle.asn1.*;

public class CertBag
    implements DEREncodable
{
	ASN1Sequence		seq;
    DERObjectIdentifier         certId;
    DERObject                   certValue;

	public CertBag(
		ASN1Sequence	seq)
	{
        this.seq = seq;
        this.certId = (DERObjectIdentifier)seq.getObjectAt(0);
        this.certValue = ((DERTaggedObject)seq.getObjectAt(1)).getObject();
	}

    public CertBag(
        DERObjectIdentifier certId,
        DERObject           certValue)
    {
        this.certId = certId;
        this.certValue = certValue;
    }

	public DERObjectIdentifier getCertId()
	{
		return certId;
	}

    public DERObject getCertValue()
    {
		return certValue;
    }

    @Override
    public DERObject getDERObject()
    {
        ASN1EncodableVector  v = new ASN1EncodableVector();

        v.add(certId);
        v.add(new DERTaggedObject(0, certValue));

        return new DERSequence(v);
    }
}
