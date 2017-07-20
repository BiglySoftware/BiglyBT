package org.gudy.bouncycastle.jce;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Hashtable;

import org.gudy.bouncycastle.asn1.*;
import org.gudy.bouncycastle.asn1.pkcs.CertificationRequest;
import org.gudy.bouncycastle.asn1.pkcs.CertificationRequestInfo;
import org.gudy.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.gudy.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.gudy.bouncycastle.asn1.x509.X509Name;
import org.gudy.bouncycastle.jce.provider.BouncyCastleProvider;

/**
 * A class for verifying and creating PKCS10 Certification requests.
 * <pre>
 * CertificationRequest ::= SEQUENCE {
 *   certificationRequestInfo  CertificationRequestInfo,
 *   signatureAlgorithm        AlgorithmIdentifier{{ SignatureAlgorithms }},
 *   signature                 BIT STRING
 * }
 *
 * CertificationRequestInfo ::= SEQUENCE {
 *   version             INTEGER { v1(0) } (v1,...),
 *   subject             Name,
 *   subjectPKInfo   SubjectPublicKeyInfo{{ PKInfoAlgorithms }},
 *   attributes          [0] Attributes{{ CRIAttributes }}
 *  }
 *
 *  Attributes { ATTRIBUTE:IOSet } ::= SET OF Attribute{{ IOSet }}
 *
 *  Attribute { ATTRIBUTE:IOSet } ::= SEQUENCE {
 *    type    ATTRIBUTE.&id({IOSet}),
 *    values  SET SIZE(1..MAX) OF ATTRIBUTE.&Type({IOSet}{\@type})
 *  }
 * </pre>
 */
public class PKCS10CertificationRequest
    extends CertificationRequest
{
    private static Hashtable            algorithms = new Hashtable();
    private static Hashtable            oids = new Hashtable();

    static
    {
        algorithms.put("MD2WITHRSAENCRYPTION", new DERObjectIdentifier("1.2.840.113549.1.1.2"));
        algorithms.put("MD2WITHRSA", new DERObjectIdentifier("1.2.840.113549.1.1.2"));
        algorithms.put("MD5WITHRSAENCRYPTION", new DERObjectIdentifier("1.2.840.113549.1.1.4"));
        algorithms.put("MD5WITHRSA", new DERObjectIdentifier("1.2.840.113549.1.1.4"));
        algorithms.put("RSAWITHMD5", new DERObjectIdentifier("1.2.840.113549.1.1.4"));
        algorithms.put("SHA1WITHRSAENCRYPTION", new DERObjectIdentifier("1.2.840.113549.1.1.5"));
        algorithms.put("SHA1WITHRSA", new DERObjectIdentifier("1.2.840.113549.1.1.5"));
        algorithms.put("RSAWITHSHA1", new DERObjectIdentifier("1.2.840.113549.1.1.5"));
        algorithms.put("RIPEMD160WITHRSAENCRYPTION", new DERObjectIdentifier("1.3.36.3.3.1.2"));
        algorithms.put("RIPEMD160WITHRSA", new DERObjectIdentifier("1.3.36.3.3.1.2"));
        algorithms.put("SHA1WITHDSA", new DERObjectIdentifier("1.2.840.10040.4.3"));
        algorithms.put("DSAWITHSHA1", new DERObjectIdentifier("1.2.840.10040.4.3"));
        algorithms.put("SHA1WITHECDSA", new DERObjectIdentifier("1.2.840.10045.4.1"));
        algorithms.put("ECDSAWITHSHA1", new DERObjectIdentifier("1.2.840.10045.4.1"));

        //
        // reverse mappings
        //
        oids.put(new DERObjectIdentifier("1.2.840.113549.1.1.5"), "SHA1WITHRSA");
        oids.put(new DERObjectIdentifier("1.2.840.113549.1.1.4"), "MD5WITHRSA");
        oids.put(new DERObjectIdentifier("1.2.840.113549.1.1.2"), "MD2WITHRSA");
        oids.put(new DERObjectIdentifier("1.2.840.10040.4.3"), "DSAWITHSHA1");
    }

    private static ASN1Sequence toDERSequence(
        byte[]  bytes)
    {
        try
        {
            ByteArrayInputStream    bIn = new ByteArrayInputStream(bytes);
            DERInputStream          dIn = new DERInputStream(bIn);

            return (ASN1Sequence)dIn.readObject();
        }
        catch (Exception e)
        {
            throw new IllegalArgumentException("badly encoded request");
        }
    }

    /**
     * construct a PKCS10 certification request from a DER encoded
     * byte stream.
     */
    public PKCS10CertificationRequest(
        byte[]  bytes)
    {
        super(toDERSequence(bytes));
    }

    public PKCS10CertificationRequest(
        ASN1Sequence  sequence)
    {
        super(sequence);
    }

    /**
     * create a PKCS10 certfication request using the BC provider.
     */
    public PKCS10CertificationRequest(
        String              signatureAlgorithm,
        X509Name            subject,
        PublicKey           key,
        ASN1Set             attributes,
        PrivateKey          signingKey)
        throws NoSuchAlgorithmException, NoSuchProviderException,
                InvalidKeyException, SignatureException
    {
        this(signatureAlgorithm, subject, key, attributes, signingKey, BouncyCastleProvider.PROVIDER_NAME);
    }

    /**
     * create a PKCS10 certfication request using the named provider.
     */
    public PKCS10CertificationRequest(
        String              signatureAlgorithm,
        X509Name            subject,
        PublicKey           key,
        ASN1Set             attributes,
        PrivateKey          signingKey,
        String              provider)
        throws NoSuchAlgorithmException, NoSuchProviderException,
                InvalidKeyException, SignatureException
    {
        DERObjectIdentifier sigOID = (DERObjectIdentifier)algorithms.get(signatureAlgorithm.toUpperCase());

        if (sigOID == null)
        {
            throw new IllegalArgumentException("Unknown signature type requested");
        }

        if (subject == null)
        {
            throw new IllegalArgumentException("subject must not be null");
        }

        if (key == null)
        {
            throw new IllegalArgumentException("public key must not be null");
        }

        this.sigAlgId = new AlgorithmIdentifier(sigOID, null);

        byte[]                  bytes = key.getEncoded();
        ByteArrayInputStream    bIn = new ByteArrayInputStream(bytes);
        DERInputStream          dIn = new DERInputStream(bIn);

        try
        {
            this.reqInfo = new CertificationRequestInfo(subject, new SubjectPublicKeyInfo((ASN1Sequence)dIn.readObject()), attributes);
        }
        catch (IOException e)
        {
            throw new IllegalArgumentException("can't encode public key");
        }

        Signature sig = null;

        try
        {
            sig = Signature.getInstance(sigAlgId.getObjectId().getId(), provider);
        }
        catch (NoSuchAlgorithmException e)
        {
            sig = Signature.getInstance(signatureAlgorithm, provider);
        }

        sig.initSign(signingKey);

        try
        {
            ByteArrayOutputStream   bOut = new ByteArrayOutputStream();
            DEROutputStream         dOut = new DEROutputStream(bOut);

            dOut.writeObject(reqInfo);

            sig.update(bOut.toByteArray());
        }
        catch (Exception e)
        {
            throw new SecurityException("exception encoding TBS cert request - " + e);
        }

        this.sigBits = new DERBitString(sig.sign());
    }

    /**
     * return the public key associated with the certification request -
     * the public key is created using the BC provider.
     */
    public PublicKey getPublicKey()
        throws NoSuchAlgorithmException, NoSuchProviderException, InvalidKeyException
    {
        return getPublicKey(BouncyCastleProvider.PROVIDER_NAME);
    }

    public PublicKey getPublicKey(
        String  provider)
        throws NoSuchAlgorithmException, NoSuchProviderException,
                InvalidKeyException
    {
        SubjectPublicKeyInfo    subjectPKInfo = reqInfo.getSubjectPublicKeyInfo();

        try
        {
            X509EncodedKeySpec      xspec = new X509EncodedKeySpec(new DERBitString(subjectPKInfo).getBytes());
            AlgorithmIdentifier     keyAlg = subjectPKInfo.getAlgorithmId ();

            return KeyFactory.getInstance(keyAlg.getObjectId().getId (), provider).generatePublic(xspec);
        }
        catch (InvalidKeySpecException e)
        {
            throw new InvalidKeyException("error encoding public key");
        }
    }

    /**
     * verify the request using the BC provider.
     */
    public boolean verify()
        throws NoSuchAlgorithmException, NoSuchProviderException,
                InvalidKeyException, SignatureException
    {
        return verify(BouncyCastleProvider.PROVIDER_NAME);
    }

    public boolean verify(
        String provider)
        throws NoSuchAlgorithmException, NoSuchProviderException,
                InvalidKeyException, SignatureException
    {
        Signature   sig = null;

        try
        {
            sig = Signature.getInstance(sigAlgId.getObjectId().getId(), provider);
        }
        catch (NoSuchAlgorithmException e)
        {
            //
            // try an alternate
            //
            if (oids.get(sigAlgId.getObjectId().getId()) != null)
            {
                String  signatureAlgorithm = (String)oids.get(sigAlgId.getObjectId().getId());

                sig = Signature.getInstance(signatureAlgorithm, provider);
            }
        }

        sig.initVerify(this.getPublicKey(provider));

        try
        {
            ByteArrayOutputStream   bOut = new ByteArrayOutputStream();
            DEROutputStream         dOut = new DEROutputStream(bOut);

            dOut.writeObject(reqInfo);

            sig.update(bOut.toByteArray());
        }
        catch (Exception e)
        {
            throw new SecurityException("exception encoding TBS cert request - " + e);
        }

        return sig.verify(sigBits.getBytes());
    }

    /**
     * return a DER encoded byte array representing this object
     */
    public byte[] getEncoded()
    {
        ByteArrayOutputStream   bOut = new ByteArrayOutputStream();
        DEROutputStream         dOut = new DEROutputStream(bOut);

        try
        {
            dOut.writeObject(this);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e.toString());
        }

        return bOut.toByteArray();
    }
}
