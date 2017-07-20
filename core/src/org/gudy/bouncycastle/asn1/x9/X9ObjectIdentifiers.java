package org.gudy.bouncycastle.asn1.x9;

import org.gudy.bouncycastle.asn1.DERObjectIdentifier;

public interface X9ObjectIdentifiers
{
    //
    // X9.62
    //
    // ansi-X9-62 OBJECT IDENTIFIER ::= { iso(1) member-body(2)
    //            us(840) ansi-x962(10045) }
    //
    static final String    ansi_X9_62 = "1.2.840.10045";
    static final String    id_fieldType = ansi_X9_62 + ".1";

    static final DERObjectIdentifier    prime_field
                    = new DERObjectIdentifier(id_fieldType + ".1");

    static final DERObjectIdentifier    characteristic_two_field
                    = new DERObjectIdentifier(id_fieldType + ".2");

    static final DERObjectIdentifier    gnBasis
                    = new DERObjectIdentifier(id_fieldType + ".2.3.1");

    static final DERObjectIdentifier    tpBasis
                    = new DERObjectIdentifier(id_fieldType + ".2.3.2");

    static final DERObjectIdentifier    ppBasis
                    = new DERObjectIdentifier(id_fieldType + ".2.3.3");

    static final String    id_ecSigType = ansi_X9_62 + ".4";

    static final DERObjectIdentifier    ecdsa_with_SHA1
                    = new DERObjectIdentifier(id_ecSigType + ".1");

    static final String    id_publicKeyType = ansi_X9_62 + ".2";

    static final DERObjectIdentifier    id_ecPublicKey
                    = new DERObjectIdentifier(id_publicKeyType + ".1");

    //
    // named curves
    //
    static final String    ellipticCurve = ansi_X9_62 + ".3";

    //
    // Prime
    //
    static final String    primeCurve = ellipticCurve + ".1";

    static final DERObjectIdentifier    prime192v1 =
                    new DERObjectIdentifier(primeCurve + ".1");
    static final DERObjectIdentifier    prime192v2 =
                    new DERObjectIdentifier(primeCurve + ".2");
    static final DERObjectIdentifier    prime192v3 =
                    new DERObjectIdentifier(primeCurve + ".3");
    static final DERObjectIdentifier    prime239v1 =
                    new DERObjectIdentifier(primeCurve + ".4");
    static final DERObjectIdentifier    prime239v2 =
                    new DERObjectIdentifier(primeCurve + ".5");
    static final DERObjectIdentifier    prime239v3 =
                    new DERObjectIdentifier(primeCurve + ".6");
    static final DERObjectIdentifier    prime256v1 =
                    new DERObjectIdentifier(primeCurve + ".7");

    //
    // Diffie-Hellman
    //
    // dhpublicnumber OBJECT IDENTIFIER ::= { iso(1) member-body(2)
    //            us(840) ansi-x942(10046) number-type(2) 1 }
    //
    static final DERObjectIdentifier    dhpublicnumber = new DERObjectIdentifier("1.2.840.10046.2.1");

    //
    // DSA
    //
    // dsapublicnumber OBJECT IDENTIFIER ::= { iso(1) member-body(2)
    //            us(840) ansi-x957(10040) number-type(4) 1 }
    static final DERObjectIdentifier    id_dsa = new DERObjectIdentifier("1.2.840.10040.4.1");

    /**
     *   id-dsa-with-sha1 OBJECT IDENTIFIER ::=  { iso(1) member-body(2)
     *         us(840) x9-57 (10040) x9cm(4) 3 }
     */
    public static final DERObjectIdentifier id_dsa_with_sha1 = new DERObjectIdentifier("1.2.840.10040.4.3");
}

