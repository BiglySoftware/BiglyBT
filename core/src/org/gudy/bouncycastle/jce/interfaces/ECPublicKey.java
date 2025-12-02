package org.gudy.bouncycastle.jce.interfaces;

import java.security.PublicKey;

import org.gudy.bouncycastle.math.ec.ECPoint;
import org.gudy.bouncycastle.jce.spec.ECParameterSpec;

/**
 * interface for elliptic curve public keys.
 */
public interface ECPublicKey
    extends ECKey, PublicKey
{
    /**
     * return the public point Q
     */
    public ECPoint getQ();

		// ElGamalPrivateKey.java
		@Override
		ECParameterSpec getParams();
}
