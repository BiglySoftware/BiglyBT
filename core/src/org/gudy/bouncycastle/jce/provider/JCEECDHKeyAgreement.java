package org.gudy.bouncycastle.jce.provider;

import java.math.BigInteger;
import java.security.*;
import java.security.spec.AlgorithmParameterSpec;

import javax.crypto.KeyAgreementSpi;
import javax.crypto.SecretKey;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.SecretKeySpec;

import org.gudy.bouncycastle.crypto.BasicAgreement;
import org.gudy.bouncycastle.crypto.CipherParameters;
import org.gudy.bouncycastle.crypto.agreement.ECDHBasicAgreement;
import org.gudy.bouncycastle.jce.interfaces.ECPrivateKey;
import org.gudy.bouncycastle.jce.interfaces.ECPublicKey;

//import org.gudy.bouncycastle.crypto.agreement.ECDHCBasicAgreement;

/**
 * Diffie-Hellman key agreement using elliptic curve keys, ala IEEE P1363
 * both the simple one, and the simple one with cofactors are supported.
 */
public class JCEECDHKeyAgreement
    extends KeyAgreementSpi
{
    private BigInteger          result;
    private CipherParameters    privKey;
    private BasicAgreement      agreement;

    protected JCEECDHKeyAgreement(
        BasicAgreement  agreement)
    {
        this.agreement = agreement;
    }

    public Key
    doPhase(
    	Key		key,
    	boolean	lastPhase )

    	throws InvalidKeyException, IllegalStateException
    {
    	return( engineDoPhase( key, lastPhase ));
    }

    @Override
    protected Key engineDoPhase(
        Key     key,
        boolean lastPhase)
        throws InvalidKeyException, IllegalStateException
    {
        if (privKey == null)
        {
            throw new IllegalStateException("EC Diffie-Hellman not initialised.");
        }

        if (!lastPhase)
        {
            throw new IllegalStateException("EC Diffie-Hellman can only be between two parties.");
        }

        if (!(key instanceof ECPublicKey))
        {
            throw new InvalidKeyException("EC Key Agreement doPhase requires ECPublicKey");
        }

        CipherParameters pubKey = ECUtil.generatePublicKeyParameter((PublicKey)key);

        result = agreement.calculateAgreement(pubKey);

        return null;
    }

    public byte[]
    generateSecret()
    	throws IllegalStateException
    {
    	return( engineGenerateSecret());
    }

    @Override
    protected byte[] engineGenerateSecret()
        throws IllegalStateException
    {
        return result.toByteArray();
    }

    @Override
    protected int engineGenerateSecret(
        byte[]  sharedSecret,
        int     offset)
        throws IllegalStateException, ShortBufferException
    {
        byte[]  secret = result.toByteArray();

        if (sharedSecret.length - offset < secret.length)
        {
            throw new ShortBufferException("ECKeyAgreement - buffer too short");
        }

        System.arraycopy(secret, 0, sharedSecret, offset, secret.length);

        return secret.length;
    }

    @Override
    protected SecretKey engineGenerateSecret(
        String algorithm)
    {
        return new SecretKeySpec(result.toByteArray(), algorithm);
    }

    public void
    init(
    	Key		key )

    	throws InvalidKeyException, InvalidAlgorithmParameterException
    {
    	engineInit( key, null );
    }

    @Override
    protected void engineInit(
        Key                     key,
        AlgorithmParameterSpec  params,
        SecureRandom            random)
        throws InvalidKeyException, InvalidAlgorithmParameterException
    {
        if (!(key instanceof ECPrivateKey))
        {
            throw new InvalidKeyException("ECKeyAgreement requires ECPrivateKey for initialisation");
        }

        privKey = ECUtil.generatePrivateKeyParameter((PrivateKey)key);

        agreement.init(privKey);
    }

    @Override
    protected void engineInit(
        Key             key,
        SecureRandom    random)
        throws InvalidKeyException
    {
        if (!(key instanceof ECPrivateKey))
        {
            throw new InvalidKeyException("ECKeyAgreement requires ECPrivateKey");
        }

        privKey = ECUtil.generatePrivateKeyParameter((PrivateKey)key);

        agreement.init(privKey);
    }

    public static class DH
        extends JCEECDHKeyAgreement
    {
        public DH()
        {
            super(new ECDHBasicAgreement());
        }
    }

    /*
    public static class DHC
        extends JCEECDHKeyAgreement
    {
        public DHC()
        {
            super(new ECDHCBasicAgreement());
        }
    }
    */
}
