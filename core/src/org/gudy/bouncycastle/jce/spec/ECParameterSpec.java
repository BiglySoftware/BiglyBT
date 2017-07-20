package org.gudy.bouncycastle.jce.spec;

import java.math.BigInteger;
import java.security.spec.AlgorithmParameterSpec;

import org.gudy.bouncycastle.math.ec.ECCurve;
import org.gudy.bouncycastle.math.ec.ECPoint;

/**
 * basic domain parameters for an Elliptic Curve public or private key.
 */
public class ECParameterSpec
	implements AlgorithmParameterSpec
{
	private ECCurve     curve;
	private byte[]      seed;
	private ECPoint     G;
	private BigInteger  n;
	private BigInteger  h;

	public ECParameterSpec(
		ECCurve     curve,
		ECPoint     G,
		BigInteger  n)
	{
		this.curve = curve;
		this.G = G;
		this.n = n;
        this.h = BigInteger.valueOf(1);
        this.seed = null;
	}

	public ECParameterSpec(
		ECCurve     curve,
		ECPoint     G,
		BigInteger  n,
        BigInteger  h)
	{
		this.curve = curve;
		this.G = G;
		this.n = n;
		this.h = h;
        this.seed = null;
	}

	public ECParameterSpec(
		ECCurve     curve,
		ECPoint     G,
		BigInteger  n,
        BigInteger  h,
        byte[]      seed)
	{
		this.curve = curve;
		this.G = G;
		this.n = n;
		this.h = h;
		this.seed = seed;
	}

    /**
     * return the curve along which the base point lies.
     */
    public ECCurve getCurve()
    {
        return curve;
    }

    /**
     * return the base point we are using for these domain parameters.
     */
	public ECPoint getG()
	{
		return G;
	}

    /**
     * return the order N of G
     */
	public BigInteger getN()
	{
		return n;
	}

    /**
     * return the cofactor H to the order of G.
     */
	public BigInteger getH()
	{
		return h;
	}

    /**
     * return the seed used to generate this curve (if available).
     */
	public byte[] getSeed()
	{
		return seed;
	}
}
