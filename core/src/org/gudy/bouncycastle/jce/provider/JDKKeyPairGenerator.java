package org.gudy.bouncycastle.jce.provider;

import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Hashtable;

import org.gudy.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.gudy.bouncycastle.crypto.generators.ECKeyPairGenerator;
import org.gudy.bouncycastle.crypto.params.ECDomainParameters;
import org.gudy.bouncycastle.crypto.params.ECKeyGenerationParameters;
import org.gudy.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.gudy.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.gudy.bouncycastle.jce.ECNamedCurveTable;
import org.gudy.bouncycastle.jce.spec.ECParameterSpec;

public abstract class JDKKeyPairGenerator
    extends KeyPairGenerator
{
    public JDKKeyPairGenerator(
        String              algorithmName)
    {
        super(algorithmName);
    }

    @Override
    public abstract void initialize(int strength, SecureRandom random);

    @Override
    public abstract KeyPair generateKeyPair();

    /* PARG removed
    public static class RSA
        extends JDKKeyPairGenerator
    {
		final static BigInteger defaultPublicExponent = BigInteger.valueOf(0x10001);
		final static int defaultTests = 8;

        RSAKeyGenerationParameters  param;
        RSAKeyPairGenerator         engine;

        public RSA()
        {
            super("RSA");

            engine = new RSAKeyPairGenerator();
            param = new RSAKeyGenerationParameters(defaultPublicExponent,
                            new SecureRandom(), 2048, defaultTests);
            engine.init(param);
        }

        public void initialize(
            int             strength,
            SecureRandom    random)
        {
            param = new RSAKeyGenerationParameters(defaultPublicExponent,
                            random, strength, defaultTests);

            engine.init(param);
        }

        public void initialize(
            AlgorithmParameterSpec  params,
            SecureRandom            random)
            throws InvalidAlgorithmParameterException
        {
            if (!(params instanceof RSAKeyGenParameterSpec))
            {
                throw new InvalidAlgorithmParameterException("parameter object not a RSAKeyGenParameterSpec");
            }
            RSAKeyGenParameterSpec     rsaParams = (RSAKeyGenParameterSpec)params;

            param = new RSAKeyGenerationParameters(
                            rsaParams.getPublicExponent(),
                            random, rsaParams.getKeysize(), defaultTests);

            engine.init(param);
        }

        public KeyPair generateKeyPair()
        {
            AsymmetricCipherKeyPair     pair = engine.generateKeyPair();
            RSAKeyParameters            pub = (RSAKeyParameters)pair.getPublic();
            RSAPrivateCrtKeyParameters  priv = (RSAPrivateCrtKeyParameters)pair.getPrivate();

            return new KeyPair(new JCERSAPublicKey(pub),
                               new JCERSAPrivateCrtKey(priv));
        }
    }

    public static class DH
        extends JDKKeyPairGenerator
    {
        DHKeyGenerationParameters  param;
        DHBasicKeyPairGenerator    engine = new DHBasicKeyPairGenerator();
        int                        strength = 1024;
        int                        certainty = 20;
        SecureRandom               random = new SecureRandom();
        boolean                    initialised = false;

        public DH()
        {
            super("DH");
        }

        public void initialize(
            int             strength,
            SecureRandom    random)
        {
            this.strength = strength;
            this.random = random;
        }

        public void initialize(
            AlgorithmParameterSpec  params,
            SecureRandom            random)
            throws InvalidAlgorithmParameterException
        {
            if (!(params instanceof DHParameterSpec))
            {
                throw new InvalidAlgorithmParameterException("parameter object not a DHParameterSpec");
            }
            DHParameterSpec     dhParams = (DHParameterSpec)params;

            param = new DHKeyGenerationParameters(random, new DHParameters(dhParams.getP(), dhParams.getG()));

            engine.init(param);
            initialised = true;
        }

        public KeyPair generateKeyPair()
        {
            if (!initialised)
            {
                DHParametersGenerator   pGen = new DHParametersGenerator();

                pGen.init(strength, certainty, random);
                param = new DHKeyGenerationParameters(random, pGen.generateParameters());
                engine.init(param);
                initialised = true;
            }

            AsymmetricCipherKeyPair   pair = engine.generateKeyPair();
            DHPublicKeyParameters     pub = (DHPublicKeyParameters)pair.getPublic();
            DHPrivateKeyParameters priv = (DHPrivateKeyParameters)pair.getPrivate();

            return new KeyPair(new JCEDHPublicKey(pub),
                               new JCEDHPrivateKey(priv));
        }
    }

    public static class DSA
        extends JDKKeyPairGenerator
    {
        DSAKeyGenerationParameters param;
        DSAKeyPairGenerator        engine = new DSAKeyPairGenerator();
        int                        strength = 1024;
        int                        certainty = 20;
        SecureRandom               random = new SecureRandom();
        boolean                    initialised = false;

        public DSA()
        {
            super("DSA");
        }

        public void initialize(
            int             strength,
            SecureRandom    random)
        {
            this.strength = strength;
            this.random = random;
        }

        public void initialize(
            AlgorithmParameterSpec  params,
            SecureRandom            random)
            throws InvalidAlgorithmParameterException
        {
            if (!(params instanceof DSAParameterSpec))
            {
                throw new InvalidAlgorithmParameterException("parameter object not a DSAParameterSpec");
            }
            DSAParameterSpec     dsaParams = (DSAParameterSpec)params;

            param = new DSAKeyGenerationParameters(random, new DSAParameters(dsaParams.getP(), dsaParams.getQ(), dsaParams.getG()));

            engine.init(param);
            initialised = true;
        }

        public KeyPair generateKeyPair()
        {
            if (!initialised)
            {
                DSAParametersGenerator   pGen = new DSAParametersGenerator();

                pGen.init(strength, certainty, random);
                param = new DSAKeyGenerationParameters(random, pGen.generateParameters());
                engine.init(param);
                initialised = true;
            }

            AsymmetricCipherKeyPair   pair = engine.generateKeyPair();
            DSAPublicKeyParameters     pub = (DSAPublicKeyParameters)pair.getPublic();
            DSAPrivateKeyParameters priv = (DSAPrivateKeyParameters)pair.getPrivate();

            return new KeyPair(new JDKDSAPublicKey(pub),
                               new JDKDSAPrivateKey(priv));
        }
    }

    public static class ElGamal
        extends JDKKeyPairGenerator
    {
        ElGamalKeyGenerationParameters  param;
        ElGamalKeyPairGenerator         engine = new ElGamalKeyPairGenerator();
        int                             strength = 1024;
        int                             certainty = 20;
        SecureRandom                    random = new SecureRandom();
        boolean                         initialised = false;

        public ElGamal()
        {
            super("ElGamal");
        }

        public void initialize(
            int             strength,
            SecureRandom    random)
        {
            this.strength = strength;
            this.random = random;
        }

        public void initialize(
            AlgorithmParameterSpec  params,
            SecureRandom            random)
            throws InvalidAlgorithmParameterException
        {
            if (!(params instanceof ElGamalParameterSpec))
            {
                throw new InvalidAlgorithmParameterException("parameter object not a ElGamalParameterSpec");
            }
            ElGamalParameterSpec     elParams = (ElGamalParameterSpec)params;

            param = new ElGamalKeyGenerationParameters(random, new ElGamalParameters(elParams.getP(), elParams.getG()));

            engine.init(param);
            initialised = true;
        }

        public KeyPair generateKeyPair()
        {
            if (!initialised)
            {
                ElGamalParametersGenerator   pGen = new ElGamalParametersGenerator();

                pGen.init(strength, certainty, random);
                param = new ElGamalKeyGenerationParameters(random, pGen.generateParameters());
                engine.init(param);
                initialised = true;
            }

            AsymmetricCipherKeyPair         pair = engine.generateKeyPair();
            ElGamalPublicKeyParameters      pub = (ElGamalPublicKeyParameters)pair.getPublic();
            ElGamalPrivateKeyParameters     priv = (ElGamalPrivateKeyParameters)pair.getPrivate();

            return new KeyPair(new JCEElGamalPublicKey(pub),
                               new JCEElGamalPrivateKey(priv));
        }
    }
	*/
    public static class EC
        extends JDKKeyPairGenerator
    {
        ECKeyGenerationParameters   param;
        ECKeyPairGenerator          engine = new ECKeyPairGenerator();
        ECParameterSpec             ecParams = null;
        int                         strength = 239;
        int                         certainty = 50;
        SecureRandom                random = new SecureRandom();
        boolean                     initialised = false;
        String                      algorithm;

        static private Hashtable    ecParameters;

        static {
            ecParameters = new Hashtable();

            ecParameters.put(new Integer(192),
                    ECNamedCurveTable.getParameterSpec("prime192v1"));
            ecParameters.put(new Integer(239),
                    ECNamedCurveTable.getParameterSpec("prime239v1"));
            ecParameters.put(new Integer(256),
                    ECNamedCurveTable.getParameterSpec("prime256v1"));
        }


        public EC(
            String  algorithm)
        {
            super(algorithm);
            this.algorithm = algorithm;
        }

        @Override
        public void initialize(
            int             strength,
            SecureRandom    random)
        {
            this.strength = strength;
            this.random = random;
            this.ecParams = (ECParameterSpec)ecParameters.get(new Integer(strength));

            if (ecParams != null)
            {
                param = new ECKeyGenerationParameters(new ECDomainParameters(ecParams.getCurve(), ecParams.getG(), ecParams.getN()), random);

                engine.init(param);
                initialised = true;
            }
        }

        @Override
        public void initialize(
            AlgorithmParameterSpec  params,
            SecureRandom            random)
            throws InvalidAlgorithmParameterException
        {
            if (!(params instanceof ECParameterSpec))
            {
                throw new InvalidAlgorithmParameterException("parameter object not a ECParameterSpec");
            }

            this.ecParams = (ECParameterSpec)params;

            param = new ECKeyGenerationParameters(new ECDomainParameters(ecParams.getCurve(), ecParams.getG(), ecParams.getN()), random);

            engine.init(param);
            initialised = true;
        }

        @Override
        public KeyPair generateKeyPair()
        {
            if (!initialised)
            {
                throw new IllegalStateException("EC Key Pair Generator not initialised");
            }

            AsymmetricCipherKeyPair     pair = engine.generateKeyPair();
            ECPublicKeyParameters       pub = (ECPublicKeyParameters)pair.getPublic();
            ECPrivateKeyParameters      priv = (ECPrivateKeyParameters)pair.getPrivate();

            return new KeyPair(new JCEECPublicKey(algorithm, pub, ecParams),
                               new JCEECPrivateKey(algorithm, priv, ecParams));
        }
    }

    public static class ECDSA
        extends EC
    {
        public ECDSA()
        {
            super("ECDSA");
        }
    }

    public static class ECDH
        extends EC
    {
        public ECDH()
        {
            super("ECDH");
        }
    }

    public static class ECDHC
        extends EC
    {
        public ECDHC()
        {
            super("ECDHC");
        }
    }
}
