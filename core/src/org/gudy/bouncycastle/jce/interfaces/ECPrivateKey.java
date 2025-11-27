package org.gudy.bouncycastle.jce.interfaces;

import java.math.BigInteger;
import java.security.PrivateKey;
import org.gudy.bouncycastle.jce.spec.ECParameterSpec;

/**
 * interface for Elliptic Curve Private keys.
 */
public interface ECPrivateKey
    extends ECKey, PrivateKey
{
    /**
     * return the private value D.
     */
    public BigInteger getD();
    // Resolve conflict between ECKey and potential defaults
		@Override
		ECParameterSpec getParams();

}
