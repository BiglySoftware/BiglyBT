package org.gudy.bouncycastle.jce.provider;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.*;
import java.security.interfaces.DSAKey;
import java.security.spec.AlgorithmParameterSpec;

import org.gudy.bouncycastle.asn1.*;
import org.gudy.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.gudy.bouncycastle.asn1.x509.X509ObjectIdentifiers;
import org.gudy.bouncycastle.crypto.CipherParameters;
import org.gudy.bouncycastle.crypto.DSA;
import org.gudy.bouncycastle.crypto.Digest;
import org.gudy.bouncycastle.crypto.digests.SHA1Digest;
import org.gudy.bouncycastle.crypto.params.ParametersWithRandom;
import org.gudy.bouncycastle.crypto.signers.ECDSASigner;
import org.gudy.bouncycastle.jce.interfaces.ECKey;

//import org.gudy.bouncycastle.crypto.signers.DSASigner;

public class JDKDSASigner
    extends Signature implements PKCSObjectIdentifiers, X509ObjectIdentifiers
{
    private Digest                  digest;
    private DSA                     signer;
    private SecureRandom            random;

    protected JDKDSASigner(
        String                  name,
        Digest                  digest,
        DSA                     signer)
    {
        super(name);

        this.digest = digest;
        this.signer = signer;
    }

    @Override
    protected void engineInitVerify(
        PublicKey   publicKey)
        throws InvalidKeyException
    {
        CipherParameters    param = null;

        if (publicKey instanceof ECKey)
        {
            param = ECUtil.generatePublicKeyParameter(publicKey);
        }
        else if (publicKey instanceof DSAKey)
        {
            param = DSAUtil.generatePublicKeyParameter(publicKey);
        }
        else
        {
            try
            {
            	/*
                byte[]  bytes = publicKey.getEncoded();

                publicKey = JDKKeyFactory.createPublicKeyFromDERStream(
                                        new ByteArrayInputStream(bytes));

                if (publicKey instanceof ECKey)
                {
                    param = ECUtil.generatePublicKeyParameter(publicKey);
                }
                else if (publicKey instanceof DSAKey)
                {
                    param = DSAUtil.generatePublicKeyParameter(publicKey);
                }
                else
                {
                */
                    throw new InvalidKeyException("can't recognise key type in DSA based signer");
                //}
            }
            catch (Exception e)
            {
                throw new InvalidKeyException("can't recognise key type in DSA based signer");
            }
        }

        digest.reset();
        signer.init(false, param);
    }

    @Override
    protected void engineInitSign(
        PrivateKey      privateKey,
        SecureRandom    random)
        throws InvalidKeyException
    {
        this.random = random;
        engineInitSign(privateKey);
    }

    @Override
    protected void engineInitSign(
        PrivateKey  privateKey)
        throws InvalidKeyException
    {
        CipherParameters    param = null;

        if (privateKey instanceof ECKey)
        {
            param = ECUtil.generatePrivateKeyParameter(privateKey);
        }
        else
        {
            param = DSAUtil.generatePrivateKeyParameter(privateKey);
        }

        digest.reset();

        if (random != null)
        {
            signer.init(true, new ParametersWithRandom(param, random));
        }
        else
        {
            signer.init(true, param);
        }
    }

    @Override
    protected void engineUpdate(
        byte    b)
        throws SignatureException
    {
        digest.update(b);
    }

    @Override
    protected void engineUpdate(
        byte[]  b,
        int     off,
        int     len)
        throws SignatureException
    {
        digest.update(b, off, len);
    }

    @Override
    protected byte[] engineSign()
        throws SignatureException
    {
        byte[]  hash = new byte[digest.getDigestSize()];

        digest.doFinal(hash, 0);

        try
        {
            BigInteger[]    sig = signer.generateSignature(hash);

            return derEncode(sig[0], sig[1]);
        }
        catch (Exception e)
        {
            throw new SignatureException(e.toString());
        }
    }

    @Override
    protected boolean engineVerify(
        byte[]  sigBytes)
        throws SignatureException
    {
        byte[]  hash = new byte[digest.getDigestSize()];

        digest.doFinal(hash, 0);

        BigInteger[]    sig;

        try
        {
            sig = derDecode(sigBytes);
        }
        catch (Exception e)
        {
            throw new SignatureException("error decoding signature bytes.");
        }

        return signer.verifySignature(hash, sig[0], sig[1]);
    }

    @Override
    protected void engineSetParameter(
        AlgorithmParameterSpec params)
    {
        throw new UnsupportedOperationException("engineSetParameter unsupported");
    }

    /**
     * @deprecated replaced with <a href = "#engineSetParameter(java.security.spec.AlgorithmParameterSpec)">
     */
    @Override
    protected void engineSetParameter(
        String  param,
        Object  value)
    {
        throw new UnsupportedOperationException("engineSetParameter unsupported");
    }

    /**
     * @deprecated
     */
    @Override
    protected Object engineGetParameter(
        String      param)
    {
        throw new UnsupportedOperationException("engineSetParameter unsupported");
    }

    private byte[] derEncode(
        BigInteger  r,
        BigInteger  s)
        throws IOException
    {
        ByteArrayOutputStream   bOut = new ByteArrayOutputStream();
        DEROutputStream         dOut = new DEROutputStream(bOut);
        ASN1EncodableVector     v = new ASN1EncodableVector();

        v.add(new DERInteger(r));
        v.add(new DERInteger(s));

        dOut.writeObject(new DERSequence(v));

        return bOut.toByteArray();
    }

    private BigInteger[] derDecode(
        byte[]  encoding)
        throws IOException
    {
        ByteArrayInputStream    bIn = new ByteArrayInputStream(encoding);
        DERInputStream          dIn = new DERInputStream(bIn);
        ASN1Sequence            s = (ASN1Sequence)dIn.readObject();

        BigInteger[]            sig = new BigInteger[2];

        sig[0] = ((DERInteger)s.getObjectAt(0)).getValue();
        sig[1] = ((DERInteger)s.getObjectAt(1)).getValue();

        return sig;
    }

    /*
    static public class stdDSA
        extends JDKDSASigner
    {
        public stdDSA()
        {
            super("SHA1withDSA", new SHA1Digest(), new DSASigner());
        }
    }
	*/

    static public class ecDSA
        extends JDKDSASigner
    {
        public ecDSA()
        {
            super("SHA1withECDSA", new SHA1Digest(), new ECDSASigner());
        }
    }
}
