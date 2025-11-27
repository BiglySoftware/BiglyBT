package org.gudy.bouncycastle.jce.interfaces;

import java.math.BigInteger;
import java.security.PrivateKey;
import org.gudy.bouncycastle.jce.spec.ElGamalParameterSpec;

public interface ElGamalPrivateKey
    extends ElGamalKey, PrivateKey
{
    public BigInteger getX();
		@Override
		ElGamalParameterSpec getParams();
}
