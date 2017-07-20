package org.gudy.bouncycastle.jce.spec;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.KeySpec;

import org.gudy.bouncycastle.jce.interfaces.IESKey;

/**
 * key pair for use with an integrated encryptor - together
 * they provide what's required to generate the message.
 */
public class IEKeySpec
    implements KeySpec, IESKey
{
    private PublicKey   pubKey;
    private PrivateKey  privKey;

    /**
     * @param privKey our private key.
     * @param pubKey the public key of the sender/recipient.
     */
    public IEKeySpec(
        PrivateKey  privKey,
        PublicKey   pubKey)
    {
        this.privKey = privKey;
        this.pubKey = pubKey;
    }

    /**
     * return the intended recipient's/sender's public key.
     */
    @Override
    public PublicKey getPublic()
    {
        return pubKey;
    }

    /**
     * return the local private key.
     */
    @Override
    public PrivateKey getPrivate()
    {
        return privKey;
    }

    /**
     * return "IES"
     */
    @Override
    public String getAlgorithm()
    {
        return "IES";
    }

    /**
     * return null
     */
    @Override
    public String getFormat()
    {
        return null;
    }

    /**
     * returns null
     */
    @Override
    public byte[] getEncoded()
    {
        return null;
    }
}
