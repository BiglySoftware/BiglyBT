package org.gudy.bouncycastle.asn1.x509;

import org.gudy.bouncycastle.asn1.*;

/**
 * The AuthorityInformationAccess object.
 * <pre>
 * id-pe-authorityInfoAccess OBJECT IDENTIFIER ::= { id-pe 1 }
 *
 * AuthorityInfoAccessSyntax  ::=
 *      SEQUENCE SIZE (1..MAX) OF AccessDescription
 * AccessDescription  ::=  SEQUENCE {
 *       accessMethod          OBJECT IDENTIFIER,
 *       accessLocation        GeneralName  }
 *
 * id-ad OBJECT IDENTIFIER ::= { id-pkix 48 }
 * id-ad-caIssuers OBJECT IDENTIFIER ::= { id-ad 2 }
 * id-ad-ocsp OBJECT IDENTIFIER ::= { id-ad 1 }
 * </pre>
 */
public class AuthorityInformationAccess
    extends ASN1Encodable
{
    private AccessDescription[]    descriptions;

    public static AuthorityInformationAccess getInstance(
        Object  obj)
    {
        if (obj instanceof AuthorityInformationAccess)
        {
            return (AuthorityInformationAccess)obj;
        }

        if (obj instanceof ASN1Sequence)
        {
            return new AuthorityInformationAccess((ASN1Sequence)obj);
        }

        if (obj instanceof X509Extension)
        {
            return getInstance(X509Extension.convertValueToObject((X509Extension)obj));
        }

        throw new IllegalArgumentException("unknown object in factory");
    }

    public AuthorityInformationAccess(
        ASN1Sequence   seq)
    {
        descriptions = new AccessDescription[seq.size()];

        for (int i = 0; i != seq.size(); i++)
        {
            descriptions[i] = AccessDescription.getInstance(seq.getObjectAt(i));
        }
    }

    /**
     * create an AuthorityInformationAccess with the oid and location provided.
     */
    public AuthorityInformationAccess(
        DERObjectIdentifier oid,
        GeneralName location)
    {
        descriptions = new AccessDescription[1];

        descriptions[0] = new AccessDescription(oid, location);
    }


    /**
     *
     * @return the access descriptions contained in this object.
     */
    public AccessDescription[] getAccessDescriptions()
    {
        return descriptions;
    }

    @Override
    public DERObject toASN1Object()
    {
        ASN1EncodableVector vec = new ASN1EncodableVector();

        for (int i = 0; i != descriptions.length; i++)
        {
            vec.add(descriptions[i]);
        }

        return new DERSequence(vec);
    }

    public String toString()
    {
        return ("AuthorityInformationAccess: Oid(" + this.descriptions[0].getAccessMethod().getId() + ")");
    }
}
