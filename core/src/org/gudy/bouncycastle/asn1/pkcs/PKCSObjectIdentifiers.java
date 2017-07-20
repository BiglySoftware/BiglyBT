package org.gudy.bouncycastle.asn1.pkcs;

import org.gudy.bouncycastle.asn1.DERObjectIdentifier;

public interface PKCSObjectIdentifiers
{
    //
    // pkcs-1 OBJECT IDENTIFIER ::= {
    //       iso(1) member-body(2) us(840) rsadsi(113549) pkcs(1) 1 }
    //
    static final String                 pkcs_1                  = "1.2.840.113549.1.1";
    static final DERObjectIdentifier    rsaEncryption           = new DERObjectIdentifier(pkcs_1 + ".1");
    static final DERObjectIdentifier    md2WithRSAEncryption    = new DERObjectIdentifier(pkcs_1 + ".2");
    static final DERObjectIdentifier    md4WithRSAEncryption    = new DERObjectIdentifier(pkcs_1 + ".3");
    static final DERObjectIdentifier    md5WithRSAEncryption    = new DERObjectIdentifier(pkcs_1 + ".4");
    static final DERObjectIdentifier    sha1WithRSAEncryption   = new DERObjectIdentifier(pkcs_1 + ".5");
    static final DERObjectIdentifier    srsaOAEPEncryptionSET   = new DERObjectIdentifier(pkcs_1 + ".6");
    static final DERObjectIdentifier    sha256WithRSAEncryption   = new DERObjectIdentifier(pkcs_1 + ".11");
    static final DERObjectIdentifier    sha384WithRSAEncryption   = new DERObjectIdentifier(pkcs_1 + ".12");
    static final DERObjectIdentifier    sha512WithRSAEncryption   = new DERObjectIdentifier(pkcs_1 + ".13");

    //
    // pkcs-3 OBJECT IDENTIFIER ::= {
    //       iso(1) member-body(2) us(840) rsadsi(113549) pkcs(1) 3 }
    //
    static final String                 pkcs_3                  = "1.2.840.113549.1.3";
    static final DERObjectIdentifier    dhKeyAgreement          = new DERObjectIdentifier(pkcs_3 + ".1");

    //
    // pkcs-5 OBJECT IDENTIFIER ::= {
    //       iso(1) member-body(2) us(840) rsadsi(113549) pkcs(1) 5 }
    //
    static final String                 pkcs_5                  = "1.2.840.113549.1.5";

    static final DERObjectIdentifier    id_PBES2                = new DERObjectIdentifier(pkcs_5 + ".13");

    static final DERObjectIdentifier    id_PBKDF2               = new DERObjectIdentifier(pkcs_5 + ".12");

    //
    // encryptionAlgorithm OBJECT IDENTIFIER ::= {
    //       iso(1) member-body(2) us(840) rsadsi(113549) 3 }
    //
    static final String                 encryptionAlgorithm     = "1.2.840.113549.3";

    static final DERObjectIdentifier    des_EDE3_CBC            = new DERObjectIdentifier(encryptionAlgorithm + ".7");
    static final DERObjectIdentifier    RC2_CBC                 = new DERObjectIdentifier(encryptionAlgorithm + ".2");

    //
    // object identifiers for digests
    //

    //
    // md2 OBJECT IDENTIFIER ::=
    //      {iso(1) member-body(2) US(840) rsadsi(113549) digestAlgorithm(2) 2}
    //
    static final DERObjectIdentifier    md2                     = new DERObjectIdentifier("1.2.840.113549.2.2");

    //
    // md5 OBJECT IDENTIFIER ::=
    //      {iso(1) member-body(2) US(840) rsadsi(113549) digestAlgorithm(2) 5}
    //
    static final DERObjectIdentifier    md5                     = new DERObjectIdentifier("1.2.840.113549.2.5");

    //
    // pkcs-7 OBJECT IDENTIFIER ::= {
    //       iso(1) member-body(2) us(840) rsadsi(113549) pkcs(1) 7 }
    //
    static final String                 pkcs_7                  = "1.2.840.113549.1.7";
    static final DERObjectIdentifier    data                    = new DERObjectIdentifier(pkcs_7 + ".1");
    static final DERObjectIdentifier    signedData              = new DERObjectIdentifier(pkcs_7 + ".2");
    static final DERObjectIdentifier    envelopedData           = new DERObjectIdentifier(pkcs_7 + ".3");
    static final DERObjectIdentifier    signedAndEnvelopedData  = new DERObjectIdentifier(pkcs_7 + ".4");
    static final DERObjectIdentifier    digestedData            = new DERObjectIdentifier(pkcs_7 + ".5");
    static final DERObjectIdentifier    encryptedData           = new DERObjectIdentifier(pkcs_7 + ".6");

    //
    // pkcs-9 OBJECT IDENTIFIER ::= {
    //       iso(1) member-body(2) us(840) rsadsi(113549) pkcs(1) 9 }
    //
    static final String                 pkcs_9                  = "1.2.840.113549.1.9";

    static final DERObjectIdentifier    pkcs_9_at_emailAddress  = new DERObjectIdentifier(pkcs_9 + ".1");
    static final DERObjectIdentifier    pkcs_9_at_unstructuredName = new DERObjectIdentifier(pkcs_9 + ".2");
    static final DERObjectIdentifier    pkcs_9_at_contentType = new DERObjectIdentifier(pkcs_9 + ".3");
    static final DERObjectIdentifier    pkcs_9_at_messageDigest = new DERObjectIdentifier(pkcs_9 + ".4");
    static final DERObjectIdentifier    pkcs_9_at_signingTime = new DERObjectIdentifier(pkcs_9 + ".5");
    static final DERObjectIdentifier    pkcs_9_at_counterSignature = new DERObjectIdentifier(pkcs_9 + ".6");
    static final DERObjectIdentifier    pkcs_9_at_challengePassword = new DERObjectIdentifier(pkcs_9 + ".7");
    static final DERObjectIdentifier    pkcs_9_at_unstructuredAddress = new DERObjectIdentifier(pkcs_9 + ".8");
    static final DERObjectIdentifier    pkcs_9_at_extendedCertificateAttributes = new DERObjectIdentifier(pkcs_9 + ".9");

    static final DERObjectIdentifier    pkcs_9_at_signingDescription = new DERObjectIdentifier(pkcs_9 + ".13");
    static final DERObjectIdentifier    pkcs_9_at_extensionRequest = new DERObjectIdentifier(pkcs_9 + ".14");
    static final DERObjectIdentifier    pkcs_9_at_smimeCapabilities = new DERObjectIdentifier(pkcs_9 + ".15");

    static final DERObjectIdentifier    pkcs_9_at_friendlyName  = new DERObjectIdentifier(pkcs_9 + ".20");
    static final DERObjectIdentifier    pkcs_9_at_localKeyId    = new DERObjectIdentifier(pkcs_9 + ".21");

    static final DERObjectIdentifier    x509certType            = new DERObjectIdentifier(pkcs_9 + ".22.1");

    static final DERObjectIdentifier    id_ct_compressedData    = new DERObjectIdentifier(pkcs_9 + ".16.1.9");

	static final DERObjectIdentifier    id_alg_PWRI_KEK    = new DERObjectIdentifier(pkcs_9 + ".16.3.9");

    //
    // SMIME capability sub oids.
    //
    static final DERObjectIdentifier    preferSignedData        = new DERObjectIdentifier(pkcs_9 + ".15.1");
    static final DERObjectIdentifier    canNotDecryptAny        = new DERObjectIdentifier(pkcs_9 + ".15.2");
    static final DERObjectIdentifier    sMIMECapabilitiesVersions = new DERObjectIdentifier(pkcs_9 + ".15.3");

    //
    // other SMIME attributes
    //

	//
	// id-aa OBJECT IDENTIFIER ::= {iso(1) member-body(2) usa(840)
	// rsadsi(113549) pkcs(1) pkcs-9(9) smime(16) attributes(2)}
	//
	static String id_aa = "1.2.840.113549.1.9.16.2";

	/*
	 * id-aa-encrypKeyPref OBJECT IDENTIFIER ::= {id-aa 11}
	 *
	 */
	static DERObjectIdentifier id_aa_encrypKeyPref = new DERObjectIdentifier(id_aa + ".11");

    //
    // pkcs-12 OBJECT IDENTIFIER ::= {
    //       iso(1) member-body(2) us(840) rsadsi(113549) pkcs(1) 12 }
    //
    static final String                 pkcs_12                  = "1.2.840.113549.1.12";
    static final String                 bagtypes                 = pkcs_12 + ".10.1";

    static final DERObjectIdentifier    keyBag                  = new DERObjectIdentifier(bagtypes + ".1");
    static final DERObjectIdentifier    pkcs8ShroudedKeyBag     = new DERObjectIdentifier(bagtypes + ".2");
    static final DERObjectIdentifier    certBag                 = new DERObjectIdentifier(bagtypes + ".3");
    static final DERObjectIdentifier    crlBag                  = new DERObjectIdentifier(bagtypes + ".4");
    static final DERObjectIdentifier    secretBag               = new DERObjectIdentifier(bagtypes + ".5");
    static final DERObjectIdentifier    safeContentsBag         = new DERObjectIdentifier(bagtypes + ".6");
}

