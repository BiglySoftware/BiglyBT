package org.gudy.bouncycastle.jce.provider;

import java.security.InvalidKeyException;
import java.security.PrivateKey;
import java.security.PublicKey;

import org.gudy.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.gudy.bouncycastle.crypto.params.ECDomainParameters;
import org.gudy.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.gudy.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.gudy.bouncycastle.jce.interfaces.ECPrivateKey;
import org.gudy.bouncycastle.jce.interfaces.ECPublicKey;
import org.gudy.bouncycastle.jce.spec.ECParameterSpec;

/**
 * utility class for converting jce/jca ECDSA, ECDH, and ECDHC
 * objects into their org.gudy.bouncycastle.crypto counterparts.
 */
public class ECUtil
{
    static public AsymmetricKeyParameter generatePublicKeyParameter(
        PublicKey    key)
        throws InvalidKeyException
    {
        if (key instanceof ECPublicKey)
        {
            ECPublicKey    k = (ECPublicKey)key;
            ECParameterSpec s = k.getParams();

            return new ECPublicKeyParameters(
                            k.getQ(),
                            new ECDomainParameters(s.getCurve(), s.getG(), s.getN()));
        }

        throw new InvalidKeyException("can't identify EC public key.");
    }

    static public AsymmetricKeyParameter generatePrivateKeyParameter(
        PrivateKey    key)
        throws InvalidKeyException
    {
        if (key instanceof ECPrivateKey)
        {
            ECPrivateKey    k = (ECPrivateKey)key;
            ECParameterSpec s = k.getParams();

            return new ECPrivateKeyParameters(
                            k.getD(),
                            new ECDomainParameters(s.getCurve(), s.getG(), s.getN()));
        }

        throw new InvalidKeyException("can't identify EC private key.");
    }
}
