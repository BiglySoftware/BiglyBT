package org.gudy.bouncycastle.asn1.pkcs;

import java.util.Enumeration;

import org.gudy.bouncycastle.asn1.*;

public class PBES2Parameters
    implements PKCSObjectIdentifiers, DEREncodable
{
    private KeyDerivationFunc   func;
    private EncryptionScheme    scheme;

    public PBES2Parameters(
        ASN1Sequence  obj)
    {
        Enumeration e = obj.getObjects();
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

        v.add(func);
        v.add(scheme);

        return new DERSequence(v);
    }
}
