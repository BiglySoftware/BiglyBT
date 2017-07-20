package org.gudy.bouncycastle.util;

import java.math.BigInteger;

public class IPAddress
{
    private static final BigInteger ZERO = BigInteger.valueOf(0);
    /**
     * Validate the given IPv4 or IPv6 address.
     *
     * @param address the IP address as a String.
     *
     * @return true if a valid address, false otherwise
     */
    public static boolean isValid(
        String address)
    {
        return isValidIPv4(address) || isValidIPv6(address);
    }

    /**
     * Validate the given IPv4 address.
     *
     * @param address the IP address as a String.
     *
     * @return true if a valid IPv4 address, false otherwise
     */
    private static boolean isValidIPv4(
        String address)
    {
        if (address.length() == 0)
        {
            return false;
        }

        BigInteger octet;
        int octets = 0;

        String temp = address+".";

        int pos;
        int start = 0;
        while (start < temp.length()
            && (pos = temp.indexOf('.', start)) > start)
        {
            if (octets == 4)
            {
                return false;
            }
            try
            {
                octet = (new BigInteger(temp.substring(start, pos)));
            }
            catch (NumberFormatException ex)
            {
                return false;
            }
            if (octet.compareTo(ZERO) == -1
                || octet.compareTo(BigInteger.valueOf(255)) == 1)
            {
                return false;
            }
            start = pos + 1;
            octets++;
        }

        return octets == 4;
    }

    /**
     * Validate the given IPv6 address.
     *
     * @param address the IP address as a String.
     *
     * @return true if a valid IPv4 address, false otherwise
     */
    private static boolean isValidIPv6(
        String address)
    {
        if (address.length() == 0)
        {
            return false;
        }

        BigInteger octet;
        int octets = 0;

        String temp = address + ":";

        int pos;
        int start = 0;
        while (start < temp.length()
            && (pos = temp.indexOf(':', start)) > start)
        {
            if (octets == 8)
            {
                return false;
            }
            try
            {
                octet = (new BigInteger(temp.substring(start, pos), 16));
            }
            catch (NumberFormatException ex)
            {
                return false;
            }
            if (octet.compareTo(ZERO) == -1
                || octet.compareTo(BigInteger.valueOf(0xFFFF)) == 1)
            {
                return false;
            }
            start = pos + 1;
            octets++;
        }

        return octets == 8;
    }
}


