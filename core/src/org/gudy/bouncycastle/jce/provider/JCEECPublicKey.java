package org.gudy.bouncycastle.jce.provider;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.gudy.bouncycastle.asn1.*;
import org.gudy.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.gudy.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.gudy.bouncycastle.asn1.x9.*;
import org.gudy.bouncycastle.crypto.params.ECDomainParameters;
import org.gudy.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.gudy.bouncycastle.jce.interfaces.ECPublicKey;
import org.gudy.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.gudy.bouncycastle.jce.spec.ECParameterSpec;
import org.gudy.bouncycastle.jce.spec.ECPublicKeySpec;
import org.gudy.bouncycastle.math.ec.ECPoint;

public class JCEECPublicKey
    implements ECPublicKey
{
    private String                  algorithm = "EC";
    private ECPoint                 q;
    private ECParameterSpec         ecSpec;

    JCEECPublicKey(
        String              algorithm,
        ECPublicKeySpec     spec)
    {
        this.algorithm = algorithm;
        this.q = spec.getQ();
        this.ecSpec = spec.getParams();
    }

    JCEECPublicKey(
        String                  algorithm,
        ECPublicKeyParameters   params,
        ECParameterSpec         spec)
    {
        ECDomainParameters      dp = params.getParameters();

        this.algorithm = algorithm;
        this.q = params.getQ();

        if (spec == null)
        {
            this.ecSpec = new ECParameterSpec(
                            dp.getCurve(),
                            dp.getG(),
                            dp.getN(),
                            dp.getH(),
                            dp.getSeed());
        }
        else
        {
            this.ecSpec = spec;
        }
    }

    JCEECPublicKey(
        String          algorithm,
        ECPublicKey     key)
    {
        this.q = key.getQ();
        this.algorithm = key.getAlgorithm();
        this.ecSpec = key.getParams();
    }

    JCEECPublicKey(
        String            algorithm,
        ECPoint           q,
        ECParameterSpec   ecSpec)
    {
        this.algorithm = algorithm;
        this.q = q;
        this.ecSpec = ecSpec;
    }

    JCEECPublicKey(
        SubjectPublicKeyInfo    info)
    {
        X962Parameters          params = new X962Parameters((DERObject)info.getAlgorithmId().getParameters());

        if (params.isNamedCurve())
        {
            DERObjectIdentifier oid = (DERObjectIdentifier)params.getParameters();
            X9ECParameters      ecP = X962NamedCurves.getByOID(oid);

            ecSpec = new ECNamedCurveParameterSpec(
                                        X962NamedCurves.getName(oid),
                                        ecP.getCurve(),
                                        ecP.getG(),
                                        ecP.getN(),
                                        ecP.getH(),
                                        ecP.getSeed());
        }
        else
        {
            X9ECParameters ecP = new X9ECParameters(
                        (ASN1Sequence)params.getParameters());
            ecSpec = new ECParameterSpec(
                                        ecP.getCurve(),
                                        ecP.getG(),
                                        ecP.getN(),
                                        ecP.getH(),
                                        ecP.getSeed());
        }

        DERBitString    bits = info.getPublicKeyData();
        byte[]          data = bits.getBytes();
		ASN1OctetString	key = new DEROctetString(data);

        //
        // extra octet string - one of our old certs...
        //
        if (data[0] == 0x04 && data[1] == data.length - 2
            && (data[2] == 0x02 || data[2] == 0x03))
        {
            try
            {
                ByteArrayInputStream    bIn = new ByteArrayInputStream(data);
                DERInputStream          dIn = new DERInputStream(bIn);

                key = (ASN1OctetString)dIn.readObject();
            }
            catch (IOException ex)
            {
                throw new IllegalArgumentException("error recovering public key");
            }
        }

        X9ECPoint       derQ = new X9ECPoint(ecSpec.getCurve(), key);

        this.q = derQ.getPoint();
    }

    @Override
    public String getAlgorithm()
    {
        return algorithm;
    }

    @Override
    public String getFormat()
    {
        return "X.509";
    }

    @Override
    public byte[] getEncoded()
    {
        ByteArrayOutputStream   bOut = new ByteArrayOutputStream();
        DEROutputStream         dOut = new DEROutputStream(bOut);
        X962Parameters          params = null;

        if (ecSpec instanceof ECNamedCurveParameterSpec)
        {
            params = new X962Parameters(X962NamedCurves.getOID(((ECNamedCurveParameterSpec)ecSpec).getName()));
        }
        else
        {
            X9ECParameters          ecP = new X9ECParameters(
                                            ecSpec.getCurve(),
                                            ecSpec.getG(),
                                            ecSpec.getN(),
                                            ecSpec.getH(),
                                            ecSpec.getSeed());
            params = new X962Parameters(ecP);
        }

        ASN1OctetString    p = (ASN1OctetString)(new X9ECPoint(this.getQ()).getDERObject());

        SubjectPublicKeyInfo info = new SubjectPublicKeyInfo(new AlgorithmIdentifier(X9ObjectIdentifiers.id_ecPublicKey, params.getDERObject()), p.getOctets());

        try
        {
            dOut.writeObject(info);
            dOut.close();
        }
        catch (IOException e)
        {
            throw new RuntimeException("Error encoding EC public key");
        }

        return bOut.toByteArray();
    }

    @Override
    public ECParameterSpec getParams()
    {
        return ecSpec;
    }

    @Override
    public ECPoint getQ()
    {
        return q;
    }

    public String toString()
    {
        StringBuilder buf = new StringBuilder();
        String          nl = System.getProperty("line.separator");

        buf.append("EC Public Key").append(nl);
        buf.append("            X: ").append(this.getQ().getX().toBigInteger().toString(16)).append(nl);
        buf.append("            Y: ").append(this.getQ().getY().toBigInteger().toString(16)).append(nl);

        return buf.toString();

    }
/*
    private void readObject(
        ObjectInputStream   in)
        throws IOException, ClassNotFoundException
    {
        in.defaultReadObject();

        boolean named = in.readBoolean();

        if (named)
        {
            ecSpec = new ECNamedCurveParameterSpec(
                        in.readUTF(),
                        (ECCurve)in.readObject(),
                        (ECPoint)in.readObject(),
                        (BigInteger)in.readObject(),
                        (BigInteger)in.readObject(),
                        (byte[])in.readObject());
        }
        else
        {
            ecSpec = new ECParameterSpec(
                        (ECCurve)in.readObject(),
                        (ECPoint)in.readObject(),
                        (BigInteger)in.readObject(),
                        (BigInteger)in.readObject(),
                        (byte[])in.readObject());
        }
    }

    private void writeObject(
        ObjectOutputStream  out)
        throws IOException
    {
        out.defaultWriteObject();

        if (this.ecSpec instanceof ECNamedCurveParameterSpec)
        {
            ECNamedCurveParameterSpec   namedSpec = (ECNamedCurveParameterSpec)ecSpec;

            out.writeBoolean(true);
            out.writeUTF(namedSpec.getName());
        }
        else
        {
            out.writeBoolean(false);
        }

        out.writeObject(ecSpec.getCurve());
        out.writeObject(ecSpec.getG());
        out.writeObject(ecSpec.getN());
        out.writeObject(ecSpec.getH());
        out.writeObject(ecSpec.getSeed());
    }
*/
}
