package org.gudy.bouncycastle.jce.provider;

import java.security.AlgorithmParameterGeneratorSpi;
import java.security.AlgorithmParameters;
import java.security.InvalidAlgorithmParameterException;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.DSAParameterSpec;

import javax.crypto.spec.DHGenParameterSpec;
import javax.crypto.spec.DHParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.RC2ParameterSpec;

import org.gudy.bouncycastle.crypto.generators.DHParametersGenerator;
import org.gudy.bouncycastle.crypto.generators.DSAParametersGenerator;
import org.gudy.bouncycastle.crypto.generators.ElGamalParametersGenerator;
import org.gudy.bouncycastle.crypto.params.DHParameters;
import org.gudy.bouncycastle.crypto.params.DSAParameters;
import org.gudy.bouncycastle.crypto.params.ElGamalParameters;
import org.gudy.bouncycastle.jce.spec.ElGamalParameterSpec;

public abstract class JDKAlgorithmParameterGenerator
    extends AlgorithmParameterGeneratorSpi
{
    protected SecureRandom  random;
    protected int           strength = 1024;

    @Override
    protected void engineInit(
        int             strength,
        SecureRandom    random)
    {
        this.strength = strength;
        this.random = random;
    }

    public static class DH
        extends JDKAlgorithmParameterGenerator
    {
        private int l = 0;

        @Override
        protected void engineInit(
            AlgorithmParameterSpec  genParamSpec,
            SecureRandom            random)
            throws InvalidAlgorithmParameterException
        {
            if (!(genParamSpec instanceof DHGenParameterSpec))
            {
                throw new InvalidAlgorithmParameterException("DH parameter generator requires a DHGenParameterSpec for initialisation");
            }
            DHGenParameterSpec  spec = (DHGenParameterSpec)genParamSpec;

            this.strength = spec.getPrimeSize();
            this.l = spec.getExponentSize();
            this.random = random;
        }

        @Override
        protected AlgorithmParameters engineGenerateParameters()
        {
            DHParametersGenerator        pGen = new DHParametersGenerator();

			if ( random != null )
			{
				pGen.init(strength, 20, random);
			}
			else
			{
				pGen.init(strength, 20, new SecureRandom());
			}

            DHParameters                p = pGen.generateParameters();

            AlgorithmParameters params;

            try
            {
                params = AlgorithmParameters.getInstance("DH", BouncyCastleProvider.PROVIDER_NAME);
                params.init(new DHParameterSpec(p.getP(), p.getG(), l));
            }
            catch (Exception e)
            {
                throw new RuntimeException(e.getMessage());
            }

            return params;
        }
    }

    public static class DSA
        extends JDKAlgorithmParameterGenerator
    {
        @Override
        protected void engineInit(
            AlgorithmParameterSpec  genParamSpec,
            SecureRandom            random)
            throws InvalidAlgorithmParameterException
        {
            throw new InvalidAlgorithmParameterException("No supported AlgorithmParameterSpec for DSA parameter generation.");
        }

        @Override
        protected AlgorithmParameters engineGenerateParameters()
        {
            DSAParametersGenerator pGen = new DSAParametersGenerator();

			if ( random != null )
			{
				pGen.init(strength, 20, random);
			}
			else
			{
				pGen.init(strength, 20, new SecureRandom());
			}

            DSAParameters p = pGen.generateParameters();

            AlgorithmParameters params;

            try
            {
                params = AlgorithmParameters.getInstance("DSA", BouncyCastleProvider.PROVIDER_NAME);
                params.init(new DSAParameterSpec(p.getP(), p.getQ(), p.getG()));
            }
            catch (Exception e)
            {
                throw new RuntimeException(e.getMessage());
            }

            return params;
        }
    }

    public static class ElGamal
        extends JDKAlgorithmParameterGenerator
    {
        @Override
        protected void engineInit(
            AlgorithmParameterSpec  genParamSpec,
            SecureRandom            random)
            throws InvalidAlgorithmParameterException
        {
            throw new InvalidAlgorithmParameterException("No supported AlgorithmParameterSpec for ElGamal parameter generation.");
        }

        @Override
        protected AlgorithmParameters engineGenerateParameters()
        {
            ElGamalParametersGenerator pGen = new ElGamalParametersGenerator();

			if ( random != null )
			{
				pGen.init(strength, 20, random);
			}
			else
			{
				pGen.init(strength, 20, new SecureRandom());
			}

            ElGamalParameters p = pGen.generateParameters();

            AlgorithmParameters params;

            try
            {
                params = AlgorithmParameters.getInstance("ElGamal", BouncyCastleProvider.PROVIDER_NAME);
                params.init(new ElGamalParameterSpec(p.getP(), p.getG()));
            }
            catch (Exception e)
            {
                throw new RuntimeException(e.getMessage());
            }

            return params;
        }
    }

    public static class DES
        extends JDKAlgorithmParameterGenerator
    {
        @Override
        protected void engineInit(
            AlgorithmParameterSpec  genParamSpec,
            SecureRandom            random)
            throws InvalidAlgorithmParameterException
        {
            throw new InvalidAlgorithmParameterException("No supported AlgorithmParameterSpec for DES parameter generation.");
        }

        @Override
        protected AlgorithmParameters engineGenerateParameters()
        {
            byte[]  iv = new byte[8];

			if (random == null)
			{
                random = new SecureRandom();
			}

            random.nextBytes(iv);

            AlgorithmParameters params;

            try
            {
                params = AlgorithmParameters.getInstance("DES", BouncyCastleProvider.PROVIDER_NAME);
                params.init(new IvParameterSpec(iv));
            }
            catch (Exception e)
            {
                throw new RuntimeException(e.getMessage());
            }

            return params;
        }
    }

    public static class RC2
        extends JDKAlgorithmParameterGenerator
    {
        RC2ParameterSpec    spec = null;

        @Override
        protected void engineInit(
            AlgorithmParameterSpec  genParamSpec,
            SecureRandom            random)
            throws InvalidAlgorithmParameterException
        {
            if (genParamSpec instanceof RC2ParameterSpec)
            {
                spec = (RC2ParameterSpec)genParamSpec;
                return;
            }

            throw new InvalidAlgorithmParameterException("No supported AlgorithmParameterSpec for RC2 parameter generation.");
        }

        @Override
        protected AlgorithmParameters engineGenerateParameters()
        {
            AlgorithmParameters params;

            if (spec == null)
            {
                byte[]  iv = new byte[8];

                if (random == null)
                {
                    random = new SecureRandom();
                }

                random.nextBytes(iv);

                try
                {
                    params = AlgorithmParameters.getInstance("RC2", BouncyCastleProvider.PROVIDER_NAME);
                    params.init(new IvParameterSpec(iv));
                }
                catch (Exception e)
                {
                    throw new RuntimeException(e.getMessage());
                }
            }
            else
            {
                try
                {
                    params = AlgorithmParameters.getInstance("RC2", BouncyCastleProvider.PROVIDER_NAME);
                    params.init(spec);
                }
                catch (Exception e)
                {
                    throw new RuntimeException(e.getMessage());
                }
            }

            return params;
        }
    }

    public static class AES
        extends JDKAlgorithmParameterGenerator
    {
        @Override
        protected void engineInit(
            AlgorithmParameterSpec  genParamSpec,
            SecureRandom            random)
            throws InvalidAlgorithmParameterException
        {
            throw new InvalidAlgorithmParameterException("No supported AlgorithmParameterSpec for AES parameter generation.");
        }

        @Override
        protected AlgorithmParameters engineGenerateParameters()
        {
            byte[]  iv = new byte[16];

			if (random == null)
			{
                random = new SecureRandom();
			}

            random.nextBytes(iv);

            AlgorithmParameters params;

            try
            {
                params = AlgorithmParameters.getInstance("AES", BouncyCastleProvider.PROVIDER_NAME);
                params.init(new IvParameterSpec(iv));
            }
            catch (Exception e)
            {
                throw new RuntimeException(e.getMessage());
            }

            return params;
        }
    }

    public static class IDEA
        extends JDKAlgorithmParameterGenerator
    {
        @Override
        protected void engineInit(
            AlgorithmParameterSpec  genParamSpec,
            SecureRandom            random)
            throws InvalidAlgorithmParameterException
        {
            throw new InvalidAlgorithmParameterException("No supported AlgorithmParameterSpec for IDEA parameter generation.");
        }

        @Override
        protected AlgorithmParameters engineGenerateParameters()
        {
            byte[]  iv = new byte[8];

			if (random == null)
			{
                random = new SecureRandom();
			}

            random.nextBytes(iv);

            AlgorithmParameters params;

            try
            {
                params = AlgorithmParameters.getInstance("IDEA", BouncyCastleProvider.PROVIDER_NAME);
                params.init(new IvParameterSpec(iv));
            }
            catch (Exception e)
            {
                throw new RuntimeException(e.getMessage());
            }

            return params;
        }
    }

    public static class CAST5
        extends JDKAlgorithmParameterGenerator
    {
        @Override
        protected void engineInit(
            AlgorithmParameterSpec  genParamSpec,
            SecureRandom            random)
            throws InvalidAlgorithmParameterException
        {
            throw new InvalidAlgorithmParameterException("No supported AlgorithmParameterSpec for CAST5 parameter generation.");
        }

        @Override
        protected AlgorithmParameters engineGenerateParameters()
        {
            byte[]  iv = new byte[8];

			if (random == null)
			{
                random = new SecureRandom();
			}

            random.nextBytes(iv);

            AlgorithmParameters params;

            try
            {
                params = AlgorithmParameters.getInstance("CAST5", BouncyCastleProvider.PROVIDER_NAME);
                params.init(new IvParameterSpec(iv));
            }
            catch (Exception e)
            {
                throw new RuntimeException(e.getMessage());
            }

            return params;
        }
    }
}
