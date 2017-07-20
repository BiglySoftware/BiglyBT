package org.gudy.bouncycastle.jce.spec;

import java.math.BigInteger;

import org.gudy.bouncycastle.math.ec.ECCurve;
import org.gudy.bouncycastle.math.ec.ECPoint;

/**
 * specification signifying that the curve parameters can also be
 * refered to by name.
 */
public class ECNamedCurveParameterSpec
	extends ECParameterSpec
{
    private String  name;

	public ECNamedCurveParameterSpec(
        String      name,
		ECCurve     curve,
		ECPoint     G,
		BigInteger  n)
	{
        super(curve, G, n);

        this.name = name;
	}

	public ECNamedCurveParameterSpec(
        String      name,
		ECCurve     curve,
		ECPoint     G,
		BigInteger  n,
        BigInteger  h)
	{
        super(curve, G, n, h);

        this.name = name;
	}

	public ECNamedCurveParameterSpec(
        String      name,
		ECCurve     curve,
		ECPoint     G,
		BigInteger  n,
        BigInteger  h,
        byte[]      seed)
	{
        super(curve, G, n, h, seed);

        this.name = name;
	}

    /**
     * return the name of the curve the EC domain parameters belong to.
     */
	public String getName()
	{
		return name;
	}
}
