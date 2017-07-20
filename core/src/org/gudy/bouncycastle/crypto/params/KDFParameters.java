package org.gudy.bouncycastle.crypto.params;

import org.gudy.bouncycastle.crypto.DerivationParameters;

/**
 * parameters for Key derivation functions.
 */
public class KDFParameters
    implements DerivationParameters
{
    byte[]  iv;
    byte[]  shared;

    public KDFParameters(
        byte[]  shared,
        byte[]  iv)
    {
        this.shared = shared;
        this.iv = iv;
    }

    public byte[] getSharedSecret()
    {
        return shared;
    }

    public byte[] getIV()
    {
        return iv;
    }
}
