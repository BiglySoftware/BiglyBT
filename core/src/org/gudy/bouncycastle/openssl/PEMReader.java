package org.gudy.bouncycastle.openssl;

import java.io.*;
import java.security.*;
import java.security.cert.CertificateFactory;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.security.spec.*;
import java.util.StringTokenizer;

import org.gudy.bouncycastle.asn1.*;
import org.gudy.bouncycastle.asn1.cms.ContentInfo;
import org.gudy.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.gudy.bouncycastle.asn1.sec.ECPrivateKeyStructure;
import org.gudy.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.gudy.bouncycastle.asn1.x509.RSAPublicKeyStructure;
import org.gudy.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.gudy.bouncycastle.asn1.x9.X9ObjectIdentifiers;
import org.gudy.bouncycastle.jce.ECNamedCurveTable;
import org.gudy.bouncycastle.jce.PKCS10CertificationRequest;
import org.gudy.bouncycastle.jce.provider.BouncyCastleProvider;
import org.gudy.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.gudy.bouncycastle.util.encoders.Base64;
import org.gudy.bouncycastle.util.encoders.Hex;
import org.gudy.bouncycastle.x509.X509AttributeCertificate;
import org.gudy.bouncycastle.x509.X509V2AttributeCertificate;

/**
 * Class for reading OpenSSL PEM encoded streams containing
 * X509 certificates, PKCS8 encoded keys and PKCS7 objects.
 * <p>
 * In the case of PKCS7 objects the reader will return a CMS ContentInfo object. Keys and
 * Certificates will be returned using the appropriate java.security type.
 */
public class PEMReader extends BufferedReader
{
    private final PasswordFinder    pFinder;
    private final String            provider;

    /**
     * Create a new PEMReader
     *
     * @param reader the Reader
     */
    public PEMReader(
        Reader reader)
    {
        this(reader, null, BouncyCastleProvider.PROVIDER_NAME);
    }

    /**
     * Create a new PEMReader with a password finder
     *
     * @param reader the Reader
     * @param pFinder the password finder
     */
    public PEMReader(
        Reader          reader,
        PasswordFinder  pFinder)
    {
        this(reader, pFinder, BouncyCastleProvider.PROVIDER_NAME);
    }

    /**
     * Create a new PEMReader with a password finder
     *
     * @param reader the Reader
     * @param pFinder the password finder
     * @param provider the cryptography provider to use
     */
    public PEMReader(
        Reader          reader,
        PasswordFinder  pFinder,
        String          provider)
    {
        super(reader);

        this.pFinder = pFinder;
        this.provider = provider;
    }

    public Object readObject()
        throws IOException
    {
        String  line;

        while ((line = readLine()) != null)
        {
            if (line.contains("-----BEGIN PUBLIC KEY"))
            {
                return readPublicKey("-----END PUBLIC KEY");
            }
            if (line.contains("-----BEGIN RSA PUBLIC KEY"))
            {
                return readRSAPublicKey("-----END RSA PUBLIC KEY");
            }
            if (line.contains("-----BEGIN CERTIFICATE REQUEST"))
            {
                return readCertificateRequest("-----END CERTIFICATE REQUEST");
            }
            if (line.contains("-----BEGIN NEW CERTIFICATE REQUEST"))
            {
                return readCertificateRequest("-----END NEW CERTIFICATE REQUEST");
            }
            if (line.contains("-----BEGIN CERTIFICATE"))
            {
                return readCertificate("-----END CERTIFICATE");
            }
            if (line.contains("-----BEGIN PKCS7"))
            {
               return readPKCS7("-----END PKCS7");
            }
            if (line.contains("-----BEGIN X509 CERTIFICATE"))
            {
                return readCertificate("-----END X509 CERTIFICATE");
            }
            if (line.contains("-----BEGIN X509 CRL"))
            {
                return readCRL("-----END X509 CRL");
            }
            if (line.contains("-----BEGIN ATTRIBUTE CERTIFICATE"))
            {
                return readAttributeCertificate("-----END ATTRIBUTE CERTIFICATE");
            }
            if (line.contains("-----BEGIN RSA PRIVATE KEY"))
            {
                try
                {
                    return readKeyPair("RSA", "-----END RSA PRIVATE KEY");
                }
                catch (Exception e)
                {
                    throw new IOException(
                        "problem creating RSA private key: " + e.toString());
                }
            }
            if (line.contains("-----BEGIN DSA PRIVATE KEY"))
            {
                try
                {
                    return readKeyPair("DSA", "-----END DSA PRIVATE KEY");
                }
                catch (Exception e)
                {
                    throw new IOException(
                        "problem creating DSA private key: " + e.toString());
                }
            }
            if (line.contains("-----BEGIN EC PARAMETERS-----"))
            {
                return readECParameters("-----END EC PARAMETERS-----");
            }
            if (line.contains("-----BEGIN EC PRIVATE KEY-----"))
            {
                return readECPrivateKey("-----END EC PRIVATE KEY-----");
            }
        }

        return null;
    }

    private byte[] readBytes(String endMarker)
        throws IOException
    {
        String          line;
        StringBuilder buf = new StringBuilder();

        while ((line = readLine()) != null)
        {
            if (line.contains(endMarker))
            {
                break;
            }
            buf.append(line.trim());
        }

        if (line == null)
        {
            throw new IOException(endMarker + " not found");
        }

        return Base64.decode(buf.toString());
    }

    private PublicKey readRSAPublicKey(String endMarker)
        throws IOException
    {
        ByteArrayInputStream bAIS = new ByteArrayInputStream(readBytes(endMarker));
        ASN1InputStream ais = new ASN1InputStream(bAIS);
        Object asnObject = ais.readObject();
        ASN1Sequence sequence = (ASN1Sequence) asnObject;
        RSAPublicKeyStructure rsaPubStructure = new RSAPublicKeyStructure(sequence);
        RSAPublicKeySpec keySpec = new RSAPublicKeySpec(
                    rsaPubStructure.getModulus(),
                    rsaPubStructure.getPublicExponent());

        try
        {
            KeyFactory keyFact = KeyFactory.getInstance("RSA", provider);

            return keyFact.generatePublic(keySpec);
        }
        catch (NoSuchProviderException e)
        {
            throw new IOException("can't find provider " + provider);
        }
        catch (Exception e)
        {
            throw new IOException("problem extracting key: " + e.toString());
        }
    }

    private PublicKey readPublicKey(String endMarker)
        throws IOException
    {
        KeySpec keySpec = new X509EncodedKeySpec(readBytes(endMarker));
        String[] algorithms = { "DSA", "RSA" };
        for (int i = 0; i < algorithms.length; i++)
        {
            try
            {
                KeyFactory keyFact = KeyFactory.getInstance(algorithms[i],
                                provider);
                PublicKey pubKey = keyFact.generatePublic(keySpec);

                return pubKey;
            }
            catch (NoSuchAlgorithmException e)
            {
                // ignore
            }
            catch (InvalidKeySpecException e)
            {
                // ignore
            }
            catch (NoSuchProviderException e)
            {
                throw new RuntimeException("can't find provider " + provider);
            }
        }

        return null;
    }

    /**
     * Reads in a X509Certificate.
     *
     * @return the X509Certificate
     * @throws IOException if an I/O error occured
     */
    private X509Certificate readCertificate(
        String  endMarker)
        throws IOException
    {
        ByteArrayInputStream    bIn = new ByteArrayInputStream(readBytes(endMarker));

        try
        {
            CertificateFactory certFact
                    = CertificateFactory.getInstance("X.509", provider);

            return (X509Certificate)certFact.generateCertificate(bIn);
        }
        catch (Exception e)
        {
            throw new IOException("problem parsing cert: " + e.toString());
        }
    }

    /**
     * Reads in a X509CRL.
     *
     * @return the X509Certificate
     * @throws IOException if an I/O error occured
     */
    private X509CRL readCRL(
        String  endMarker)
        throws IOException
    {
        ByteArrayInputStream    bIn = new ByteArrayInputStream(readBytes(endMarker));

        try
        {
            CertificateFactory certFact
                    = CertificateFactory.getInstance("X.509", provider);

            return (X509CRL)certFact.generateCRL(bIn);
        }
        catch (Exception e)
        {
            throw new IOException("problem parsing cert: " + e.toString());
        }
    }

    /**
     * Reads in a PKCS10 certification request.
     *
     * @return the certificate request.
     * @throws IOException if an I/O error occured
     */
    private PKCS10CertificationRequest readCertificateRequest(
        String  endMarker)
        throws IOException
    {
        try
        {
            return new PKCS10CertificationRequest(readBytes(endMarker));
        }
        catch (Exception e)
        {
            throw new IOException("problem parsing cert: " + e.toString());
        }
    }

    /**
     * Reads in a X509 Attribute Certificate.
     *
     * @return the X509 Attribute Certificate
     * @throws IOException if an I/O error occured
     */
    private X509AttributeCertificate readAttributeCertificate(
        String  endMarker)
        throws IOException
    {
        return new X509V2AttributeCertificate(readBytes(endMarker));
    }

    /**
     * Reads in a PKCS7 object. This returns a ContentInfo object suitable for use with the CMS
     * API.
     *
     * @return the X509Certificate
     * @throws IOException if an I/O error occured
     */
    private ContentInfo readPKCS7(
        String  endMarker)
        throws IOException
    {
        String                                  line;
        StringBuilder buf = new StringBuilder();
        ByteArrayOutputStream    bOut = new ByteArrayOutputStream();

        while ((line = readLine()) != null)
        {
            if (line.contains(endMarker))
            {
                break;
            }

            line = line.trim();

            buf.append(line.trim());

            Base64.decode(buf.substring(0, (buf.length() / 4) * 4), bOut);

            buf.delete(0, (buf.length() / 4) * 4);
        }

        if (buf.length() != 0)
        {
            throw new RuntimeException("base64 data appears to be truncated");
        }

        if (line == null)
        {
            throw new IOException(endMarker + " not found");
        }

        ByteArrayInputStream    bIn = new ByteArrayInputStream(bOut.toByteArray());

        try
        {
            ASN1InputStream aIn = new ASN1InputStream(bIn);

            return ContentInfo.getInstance(aIn.readObject());
        }
        catch (Exception e)
        {
            throw new IOException("problem parsing PKCS7 object: " + e.toString());
        }
    }

    /**
     * Read a Key Pair
     */
    private KeyPair readKeyPair(
        String  type,
        String  endMarker)
        throws Exception
    {
        boolean         isEncrypted = false;
        String          line = null;
        String          dekInfo = null;
        StringBuilder buf = new StringBuilder();

        while ((line = readLine()) != null)
        {
            if (line.startsWith("Proc-Type: 4,ENCRYPTED"))
            {
                isEncrypted = true;
            }
            else if (line.startsWith("DEK-Info:"))
            {
                dekInfo = line.substring(10);
            }
            else if (line.contains(endMarker))
            {
                break;
            }
            else
            {
                buf.append(line.trim());
            }
        }

        //
        // extract the key
        //
        byte[] keyBytes = Base64.decode(buf.toString());

        if (isEncrypted)
        {
            if (pFinder == null)
            {
                throw new IOException("No password finder specified, but a password is required");
            }

            char[] password = pFinder.getPassword();

            if (password == null)
            {
                throw new IOException("Password is null, but a password is required");
            }

            StringTokenizer tknz = new StringTokenizer(dekInfo, ",");
            String          dekAlgName = tknz.nextToken();
            byte[]          iv = Hex.decode(tknz.nextToken());

            keyBytes = PEMUtilities.crypt(false, provider, keyBytes, password, dekAlgName, iv);
        }


        KeySpec                 pubSpec, privSpec;
        ByteArrayInputStream    bIn = new ByteArrayInputStream(keyBytes);
        ASN1InputStream         aIn = new ASN1InputStream(bIn);
        ASN1Sequence            seq = (ASN1Sequence)aIn.readObject();

        if (type.equals("RSA"))
        {
//            DERInteger              v = (DERInteger)seq.getObjectAt(0);
            DERInteger              mod = (DERInteger)seq.getObjectAt(1);
            DERInteger              pubExp = (DERInteger)seq.getObjectAt(2);
            DERInteger              privExp = (DERInteger)seq.getObjectAt(3);
            DERInteger              p1 = (DERInteger)seq.getObjectAt(4);
            DERInteger              p2 = (DERInteger)seq.getObjectAt(5);
            DERInteger              exp1 = (DERInteger)seq.getObjectAt(6);
            DERInteger              exp2 = (DERInteger)seq.getObjectAt(7);
            DERInteger              crtCoef = (DERInteger)seq.getObjectAt(8);

            pubSpec = new RSAPublicKeySpec(
                        mod.getValue(), pubExp.getValue());
            privSpec = new RSAPrivateCrtKeySpec(
                    mod.getValue(), pubExp.getValue(), privExp.getValue(),
                    p1.getValue(), p2.getValue(),
                    exp1.getValue(), exp2.getValue(),
                    crtCoef.getValue());
        }
        else    // "DSA"
        {
//            DERInteger              v = (DERInteger)seq.getObjectAt(0);
            DERInteger              p = (DERInteger)seq.getObjectAt(1);
            DERInteger              q = (DERInteger)seq.getObjectAt(2);
            DERInteger              g = (DERInteger)seq.getObjectAt(3);
            DERInteger              y = (DERInteger)seq.getObjectAt(4);
            DERInteger              x = (DERInteger)seq.getObjectAt(5);

            privSpec = new DSAPrivateKeySpec(
                        x.getValue(), p.getValue(),
                            q.getValue(), g.getValue());
            pubSpec = new DSAPublicKeySpec(
                        y.getValue(), p.getValue(),
                            q.getValue(), g.getValue());
        }

        KeyFactory          fact = KeyFactory.getInstance(type, provider);

        return new KeyPair(
                    fact.generatePublic(pubSpec),
                    fact.generatePrivate(privSpec));
    }

    private ECNamedCurveParameterSpec readECParameters(String endMarker)
        throws IOException
    {
        DERObjectIdentifier oid = (DERObjectIdentifier)ASN1Object.fromByteArray(readBytes(endMarker));

        return ECNamedCurveTable.getParameterSpec(oid.getId());
    }

    private KeyPair readECPrivateKey(String endMarker)
        throws IOException
    {
        try
        {
            ECPrivateKeyStructure pKey = new ECPrivateKeyStructure((ASN1Sequence)ASN1Object.fromByteArray(readBytes(endMarker)));
            AlgorithmIdentifier   algId = new AlgorithmIdentifier(X9ObjectIdentifiers.id_ecPublicKey, pKey.getParameters());
            PrivateKeyInfo        privInfo = new PrivateKeyInfo(algId, pKey.getDERObject());
            SubjectPublicKeyInfo  pubInfo = new SubjectPublicKeyInfo(algId, pKey.getPublicKey().getBytes());
            PKCS8EncodedKeySpec   privSpec = new PKCS8EncodedKeySpec(privInfo.getEncoded());
            X509EncodedKeySpec    pubSpec = new X509EncodedKeySpec(pubInfo.getEncoded());
            KeyFactory            fact = KeyFactory.getInstance("ECDSA", provider);

            return new KeyPair(fact.generatePublic(pubSpec), fact.generatePrivate(privSpec));
        }
        catch (ClassCastException e)
        {
            throw new IOException("wrong ASN.1 object found in stream");
        }
        catch (Exception e)
        {
            throw new IOException("problem parsing EC private key: " + e);
        }
    }
}
