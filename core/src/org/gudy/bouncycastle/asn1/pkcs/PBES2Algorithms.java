package org.gudy.bouncycastle.asn1.pkcs;

import java.util.Enumeration;

import org.gudy.bouncycastle.asn1.*;
import org.gudy.bouncycastle.asn1.x509.AlgorithmIdentifier;

/**
 * @deprecated - use AlgorithmIdentifier and PBES2Params
 */
public class PBES2Algorithms
    extends AlgorithmIdentifier implements PKCSObjectIdentifiers
{
    private DERObjectIdentifier objectId;
    private KeyDerivationFunc   func;
    private EncryptionScheme    scheme;

    public PBES2Algorithms(
        ASN1Sequence  obj)
    {
        super(obj);

        Enumeration     e = obj.getObjects();

        objectId = (DERObjectIdentifier)e.nextElement();

        ASN1Sequence seq = (ASN1Sequence)e.nextElement();

        e = seq.getObjects();

        ASN1Sequence  funcSeq = (ASN1Sequence)e.nextElement();

        if (funcSeq.getObjectAt(0).equals(id_PBKDF2))
        {
            func = new PBKDF2Params(funcSeq);
        }
        else
        {
            func = new KeyDerivationFunc(funcSeq);
        }

        scheme = new EncryptionScheme((ASN1Sequence)e.nextElement());
    }

    @Override
    public DERObjectIdentifier getObjectId()
    {
        return objectId;
    }

    public KeyDerivationFunc getKeyDerivationFunc()
    {
        return func;
    }

    public EncryptionScheme getEncryptionScheme()
    {
        return scheme;
    }

    @Override
    public DERObject getDERObject()
    {
        ASN1EncodableVector  v = new ASN1EncodableVector();
        ASN1EncodableVector  subV = new ASN1EncodableVector();

        v.add(objectId);

        subV.add(func);
        subV.add(scheme);
        v.add(new DERSequence(subV));

        return new DERSequence(v);
    }
}
