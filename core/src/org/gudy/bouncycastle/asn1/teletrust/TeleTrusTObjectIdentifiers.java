package org.gudy.bouncycastle.asn1.teletrust;

import org.gudy.bouncycastle.asn1.DERObjectIdentifier;

public interface TeleTrusTObjectIdentifiers
{
    static final String teleTrusTAlgorithm = "1.3.36.3";

    static final DERObjectIdentifier    ripemd160           = new DERObjectIdentifier(teleTrusTAlgorithm + ".2.1");
    static final DERObjectIdentifier    ripemd128           = new DERObjectIdentifier(teleTrusTAlgorithm + ".2.2");
    static final DERObjectIdentifier    ripemd256           = new DERObjectIdentifier(teleTrusTAlgorithm + ".2.3");

	static final String teleTrusTRSAsignatureAlgorithm = teleTrusTAlgorithm + ".3.1";

    static final DERObjectIdentifier    rsaSignatureWithripemd160           = new DERObjectIdentifier(teleTrusTRSAsignatureAlgorithm + ".2");
    static final DERObjectIdentifier    rsaSignatureWithripemd128           = new DERObjectIdentifier(teleTrusTRSAsignatureAlgorithm + ".3");
    static final DERObjectIdentifier    rsaSignatureWithripemd256           = new DERObjectIdentifier(teleTrusTRSAsignatureAlgorithm + ".4");
}
