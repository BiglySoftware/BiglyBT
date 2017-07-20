package org.gudy.bouncycastle.asn1.x9;

import java.math.BigInteger;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Set;

import org.gudy.bouncycastle.asn1.DERObjectIdentifier;
import org.gudy.bouncycastle.math.ec.ECCurve;
import org.gudy.bouncycastle.util.encoders.Hex;


/**
 * table of the current named curves defined in X.962 EC-DSA.
 */
public class X962NamedCurves
{
    static final ECCurve cFp192v1 = new ECCurve.Fp(
        new BigInteger("6277101735386680763835789423207666416083908700390324961279"),
        new BigInteger("fffffffffffffffffffffffffffffffefffffffffffffffc", 16),
        new BigInteger("64210519e59c80e70fa7e9ab72243049feb8deecc146b9b1", 16));

    static final X9ECParameters prime192v1 = new X9ECParameters(
        cFp192v1,
        cFp192v1.decodePoint(
            Hex.decode("03188da80eb03090f67cbf20eb43a18800f4ff0afd82ff1012")),
        new BigInteger("ffffffffffffffffffffffff99def836146bc9b1b4d22831", 16),
        BigInteger.valueOf(1),
        Hex.decode("3045AE6FC8422f64ED579528D38120EAE12196D5"));

    static final ECCurve cFp192v2 = new ECCurve.Fp(
        new BigInteger("6277101735386680763835789423207666416083908700390324961279"),
        new BigInteger("fffffffffffffffffffffffffffffffefffffffffffffffc", 16),
        new BigInteger("cc22d6dfb95c6b25e49c0d6364a4e5980c393aa21668d953", 16));

    static final X9ECParameters prime192v2 = new X9ECParameters(
        cFp192v2,
        cFp192v2.decodePoint(
            Hex.decode("03eea2bae7e1497842f2de7769cfe9c989c072ad696f48034a")),
        new BigInteger("FFFFFFFFFFFFFFFFFFFFFFFE5FB1A724DC80418648D8DD31", 16),	// parg fixed F->E...
        BigInteger.valueOf(1),
        Hex.decode("31a92ee2029fd10d901b113e990710f0d21ac6b6"));

    static final ECCurve cFp192v3 = new ECCurve.Fp(
        new BigInteger("6277101735386680763835789423207666416083908700390324961279"),
        new BigInteger("fffffffffffffffffffffffffffffffefffffffffffffffc", 16),
        new BigInteger("22123dc2395a05caa7423daeccc94760a7d462256bd56916", 16));

    static final X9ECParameters prime192v3 = new X9ECParameters(
        cFp192v3,
        cFp192v3.decodePoint(
            Hex.decode("027d29778100c65a1da1783716588dce2b8b4aee8e228f1896")),
        new BigInteger("ffffffffffffffffffffffff7a62d031c83f4294f640ec13", 16),
        BigInteger.valueOf(1),
        Hex.decode("c469684435deb378c4b65ca9591e2a5763059a2e"));

    static final ECCurve cFp239v1 = new ECCurve.Fp(
        new BigInteger("883423532389192164791648750360308885314476597252960362792450860609699839"),
        new BigInteger("7fffffffffffffffffffffff7fffffffffff8000000000007ffffffffffc", 16),
        new BigInteger("6b016c3bdcf18941d0d654921475ca71a9db2fb27d1d37796185c2942c0a", 16));

    static final X9ECParameters prime239v1 = new X9ECParameters(
        cFp239v1,
        cFp239v1.decodePoint(
            Hex.decode("020ffa963cdca8816ccc33b8642bedf905c3d358573d3f27fbbd3b3cb9aaaf")),
        new BigInteger("7fffffffffffffffffffffff7fffff9e5e9a9f5d9071fbd1522688909d0b", 16),
        BigInteger.valueOf(1),
        Hex.decode("e43bb460f0b80cc0c0b075798e948060f8321b7d"));

    static final ECCurve cFp239v2 = new ECCurve.Fp(
        new BigInteger("883423532389192164791648750360308885314476597252960362792450860609699839"),
        new BigInteger("7fffffffffffffffffffffff7fffffffffff8000000000007ffffffffffc", 16),
        new BigInteger("617fab6832576cbbfed50d99f0249c3fee58b94ba0038c7ae84c8c832f2c", 16));

    static final X9ECParameters prime239v2 = new X9ECParameters(
        cFp239v2,
        cFp239v2.decodePoint(
            Hex.decode("0238af09d98727705120c921bb5e9e26296a3cdcf2f35757a0eafd87b830e7")),
        new BigInteger("7fffffffffffffffffffffff800000cfa7e8594377d414c03821bc582063", 16),
        BigInteger.valueOf(1),
        Hex.decode("e8b4011604095303ca3b8099982be09fcb9ae616"));

    static final ECCurve cFp239v3 = new ECCurve.Fp(
        new BigInteger("883423532389192164791648750360308885314476597252960362792450860609699839"),
        new BigInteger("7fffffffffffffffffffffff7fffffffffff8000000000007ffffffffffc", 16),
        new BigInteger("255705fa2a306654b1f4cb03d6a750a30c250102d4988717d9ba15ab6d3e", 16));

    static final X9ECParameters prime239v3 = new X9ECParameters(
        cFp239v3,
        cFp239v3.decodePoint(
            Hex.decode("036768ae8e18bb92cfcf005c949aa2c6d94853d0e660bbf854b1c9505fe95a")),
        new BigInteger("7fffffffffffffffffffffff7fffff975deb41b3a6057c3c432146526551", 16),
        BigInteger.valueOf(1),
        Hex.decode("7d7374168ffe3471b60a857686a19475d3bfa2ff"));

    static final ECCurve cFp256v1 = new ECCurve.Fp(
        new BigInteger("115792089210356248762697446949407573530086143415290314195533631308867097853951"),
        new BigInteger("ffffffff00000001000000000000000000000000fffffffffffffffffffffffc", 16),
        new BigInteger("5ac635d8aa3a93e7b3ebbd55769886bc651d06b0cc53b0f63bce3c3e27d2604b", 16));

    static final X9ECParameters prime256v1 = new X9ECParameters(
        cFp256v1,
        cFp256v1.decodePoint(
            Hex.decode("036b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296")),
        new BigInteger("ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551", 16),
        BigInteger.valueOf(1),
        Hex.decode("c49d360886e704936a6678e1139d26b7819f7e90"));

    static final Hashtable objIds = new Hashtable();
    static final Hashtable curves = new Hashtable();
    static final Hashtable names = new Hashtable();

    static
    {
        objIds.put("prime192v1", X9ObjectIdentifiers.prime192v1);
        objIds.put("prime192v2", X9ObjectIdentifiers.prime192v2);
        objIds.put("prime192v3", X9ObjectIdentifiers.prime192v3);
        objIds.put("prime239v1", X9ObjectIdentifiers.prime239v1);
        objIds.put("prime239v2", X9ObjectIdentifiers.prime239v2);
        objIds.put("prime239v3", X9ObjectIdentifiers.prime239v3);
        objIds.put("prime256v1", X9ObjectIdentifiers.prime256v1);

        names.put(X9ObjectIdentifiers.prime192v1, "prime192v1");
        names.put(X9ObjectIdentifiers.prime192v2, "prime192v2");
        names.put(X9ObjectIdentifiers.prime192v3, "prime192v3");
        names.put(X9ObjectIdentifiers.prime239v1, "prime239v1");
        names.put(X9ObjectIdentifiers.prime239v2, "prime239v2");
        names.put(X9ObjectIdentifiers.prime239v3, "prime239v3");
        names.put(X9ObjectIdentifiers.prime256v1, "prime256v1");

        curves.put(X9ObjectIdentifiers.prime192v1, prime192v1);
        curves.put(X9ObjectIdentifiers.prime192v2, prime192v2);
        curves.put(X9ObjectIdentifiers.prime192v3, prime192v3);
        curves.put(X9ObjectIdentifiers.prime239v1, prime239v1);
        curves.put(X9ObjectIdentifiers.prime239v2, prime239v2);
        curves.put(X9ObjectIdentifiers.prime239v3, prime239v3);
        curves.put(X9ObjectIdentifiers.prime256v1, prime256v1);
    }

    private static Set<String>	missing_oids = new HashSet<>();

    public static X9ECParameters getByName(
        String  name)
    {
        DERObjectIdentifier oid = (DERObjectIdentifier)objIds.get(name);

        if (oid != null)
        {
            return (X9ECParameters)curves.get(oid);
        }

        return null;
    }

    /**
     * return the X9ECParameters object for the named curve represented by
     * the passed in object identifier. Null if the curve isn't present.
     *
     * @param oid an object identifier representing a named curve, if present.
     */
    public static X9ECParameters getByOID(
        DERObjectIdentifier  oid)
    {
    	X9ECParameters result = (X9ECParameters)curves.get(oid);

        if ( result == null ){

        	String id = oid.getId();

        	if ( !missing_oids.contains( id )){

        		missing_oids.add( id );

        		new Exception( "Missing named curve: " + id ).printStackTrace();
        	}
        }

        return( result );
    }

    /**
     * return the object identifier signified by the passed in name. Null
     * if there is no object identifier associated with name.
     *
     * @return the object identifier associated with name, if present.
     */
    public static DERObjectIdentifier getOID(
        String  name)
    {
        return (DERObjectIdentifier)objIds.get(name);
    }

    /**
     * return the named curve name represented by the given object identifier.
     */
    public static String getName(
        DERObjectIdentifier  oid)
    {
        return (String)names.get(oid);
    }

    /**
     * returns an enumeration containing the name strings for curves
     * contained in this structure.
     */
    public static Enumeration getNames()
    {
        return objIds.keys();
    }
}
