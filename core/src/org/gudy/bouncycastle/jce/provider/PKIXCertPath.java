package org.gudy.bouncycastle.jce.provider;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchProviderException;
import java.security.cert.*;
import java.util.*;

import org.gudy.bouncycastle.asn1.*;

/**
 * CertPath implementation for X.509 certificates.
 * <br />
 * <b>TODO: add PKCS #7 encoding support</b>
 **/
public  class PKIXCertPath
	extends CertPath
{
    static final List certPathEncodings;

    static
    {
	List encodings = new ArrayList();
	encodings.add("PkiPath");
	certPathEncodings = Collections.unmodifiableList( encodings );
    }

    private List certificates;

	/**
	 * Creates a CertPath of the specified type.
	 * This constructor is protected because most users should use
	 * a CertificateFactory to create CertPaths.
	 * @param type the standard name of the type of Certificatesin this path
	 **/
    PKIXCertPath( List certificates )
    {
	super("X.509");
	this.certificates = new ArrayList( certificates );
    }

	/**
	 * Creates a CertPath of the specified type.
	 * This constructor is protected because most users should use
	 * a CertificateFactory to create CertPaths.
	 *
	 * <b>TODO: implement PKCS7 decoding</b>
	 *
	 * @param type the standard name of the type of Certificatesin this path
	 **/
    PKIXCertPath( InputStream inStream, String encoding)
	throws CertificateException
    {
	super("X.509");
	try {
	    if ( encoding.equals( "PkiPath" ) )
	    {
		DERInputStream derInStream = new DERInputStream(inStream);
		DERObject derObject = derInStream.readObject();
		if ( derObject == null || ! ( derObject instanceof ASN1Sequence ) )
		{
		    throw new CertificateException("input stream does not contain a ASN1 SEQUENCE while reading PkiPath encoded data to load CertPath" );
		}
		Enumeration enumx = ((ASN1Sequence)derObject).getObjects();
		InputStream certInStream;
		ByteArrayOutputStream outStream;
		DEROutputStream derOutStream;
		certificates = new ArrayList();
		CertificateFactory certFactory= CertificateFactory.getInstance( "X.509", BouncyCastleProvider.PROVIDER_NAME );
		while ( enumx.hasMoreElements() ) {
		    outStream = new ByteArrayOutputStream();
		    derOutStream = new DEROutputStream(outStream);

        	    derOutStream.writeObject(enumx.nextElement());
        	    derOutStream.close();

		    certInStream = new ByteArrayInputStream(outStream.toByteArray());
		    certificates.add(0,certFactory.generateCertificate(certInStream));
		}
	    }
	    else
	    {
		throw new CertificateException( "unsupported encoding" );
	    }
	} catch ( IOException ex ) {
	    throw new CertificateException( "IOException throw while decoding CertPath:\n" + ex.toString() );
	} catch ( NoSuchProviderException ex ) {
	    throw new CertificateException( "BouncyCastle provider not found while trying to get a CertificateFactory:\n" + ex.toString() );
	}
    }

	/**
	 * Returns an iteration of the encodings supported by this
	 * certification path, with the default encoding
	 * first. Attempts to modify the returned Iterator via its
	 * remove method result in an UnsupportedOperationException.
	 *
	 * @return an Iterator over the names of the supported encodings (as Strings)
	 **/
    @Override
    public Iterator getEncodings()
    {
	return certPathEncodings.iterator();
    }

	/**
	 * Returns the encoded form of this certification path, using
	 * the default encoding.
	 *
	 * @return the encoded bytes
	 * @exception CertificateEncodingException if an encoding error occurs
	 **/
    @Override
    public byte[] getEncoded()
	throws CertificateEncodingException
    {
	Iterator iter = getEncodings();
	if ( iter.hasNext() )
	{
	    Object enc = iter.next();
	    if ( enc instanceof String )
	    {
		return getEncoded((String)enc);
	    }
	}
	return null;
    }

	/**
	 * Returns the encoded form of this certification path, using
	 * the specified encoding.
	 *
	 * <b>TODO: implement PKCS7 decoding</b>
	 *
	 * @param encoding the name of the encoding to use
	 * @return the encoded bytes
	 * @exception CertificateEncodingException if an encoding error
	 * occurs or the encoding requested is not supported
	 *
	 **/
    @Override
    public byte[] getEncoded(String encoding)
	throws CertificateEncodingException
    {
	DERObject encoded = null;
	if ( encoding.equals("PkiPath") )
	{
        ASN1EncodableVector v = new ASN1EncodableVector();

		// TODO check ListIterator  implementation for JDK 1.1
	    ListIterator iter = certificates.listIterator(certificates.size());
	    while ( iter.hasPrevious() )
	    {
		    v.add(getEncodedX509Certificate((X509Certificate)iter.previous()));
	    }

        encoded = new DERSequence(v);
	}
	else
	    throw new CertificateEncodingException( "unsupported encoding" );

	if ( encoded == null )
	    return null;

	ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        DEROutputStream derOutStream = new DEROutputStream(outStream);

	try {
	    derOutStream.writeObject( encoded );
	    derOutStream.close();
	} catch ( IOException ex ) {
	    throw new CertificateEncodingException( "IOExeption thrown: " + ex.toString() );
	}

        return outStream.toByteArray();
    }

	/**
	 * Returns the list of certificates in this certification
	 * path. The List returned must be immutable and thread-safe.
	 *
	 * <b>TODO: return immutable List</b>
	 *
	 * @return an immutable List of Certificates (may be empty, but not null)
	 **/
    @Override
    public List getCertificates()
    {
	return new ArrayList( certificates );
    }

	/**
	 * Return a DERObject containing the encoded certificate.
	 *
	 * @param cert the X509Certificate object to be encoded
	 *
	 * @return the DERObject
	 **/
    private DERObject getEncodedX509Certificate( X509Certificate cert )
	throws CertificateEncodingException
    {
	try {
	    ByteArrayInputStream inStream = new ByteArrayInputStream( cert.getEncoded() );
	    DERInputStream derInStream = new DERInputStream( inStream );
	    return derInStream.readObject();
	} catch ( IOException ex ) {
	    throw new CertificateEncodingException( "IOException caught while encoding certificate\n" + ex.toString() );
	}
    }
}
