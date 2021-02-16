/*
 * File    : DisplayFormatters.java
 * Created : 07-Oct-2003
 * By      : gardnerpar
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.biglybt.core.util;

/**
 * @author gardnerpar
 *
 */

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.disk.DiskManager;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerState;
import com.biglybt.core.download.DownloadManagerStats;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.internat.MessageText.MessageTextListener;
import com.biglybt.core.torrent.TOTorrent;



public class
DisplayFormatters
{
	final private static boolean ROUND_NO = true;
	//final private static boolean ROUND_YES = false;
	final private static boolean TRUNCZEROS_NO = false;
	final private static boolean TRUNCZEROS_YES = true;

	final public static int UNIT_B  = 0;
	final public static int UNIT_KB = 1;
	final public static int UNIT_MB = 2;
	final public static int UNIT_GB = 3;
	final public static int UNIT_TB = 4;

	final private static int UNITS_PRECISION[] =	 {	 0, // B
	                                                     1, //KB
	                                                     2, //MB
	                                                     2, //GB
	                                                     3 //TB
	                                                  };

	final private static NumberFormat[]	cached_number_formats = new NumberFormat[20];

	private static NumberFormat	percentage_format;

	private static final String[]	all_units = new String[5];

	private static String[] units;
	private static String[] units_bits;
	private static String[] units_rate;
	private static int unitsStopAt = UNIT_TB;

	private static String[] units_base10;

	private static String		per_sec;

	private static boolean use_si_units;
	private static boolean force_si_values;
	private static boolean use_units_rate_bits;
	private static boolean not_use_GB_TB;

    private static int message_text_state = 0;

    private static boolean	separate_prot_data_stats;
    private static boolean	data_stats_only;
	private static char decimalSeparator;

	private static volatile Map<String,Formatter>	format_map = new HashMap<>();

	static{
		COConfigurationManager.addAndFireParameterListeners(
				new String[]{
					"config.style.useSIUnits",
					"config.style.forceSIValues",
					"config.style.useUnitsRateBits",
					"config.style.doNotUseGB",
					"config.style.formatOverrides",
				},
				new ParameterListener()
				{
					@Override
					public void
					parameterChanged(
						String	x )
					{
						use_si_units 			= COConfigurationManager.getBooleanParameter("config.style.useSIUnits");
						force_si_values 		= COConfigurationManager.getBooleanParameter("config.style.forceSIValues");
						use_units_rate_bits 	= COConfigurationManager.getBooleanParameter("config.style.useUnitsRateBits");
			            not_use_GB_TB 			= COConfigurationManager.getBooleanParameter("config.style.doNotUseGB");

			            unitsStopAt = (not_use_GB_TB) ? UNIT_MB : UNIT_TB;

						setUnits();

						updateFormatOverrides( COConfigurationManager.getStringParameter( "config.style.formatOverrides", "" ));
					}
				});

		COConfigurationManager.addAndFireParameterListeners(
				new String[]{
						"config.style.dataStatsOnly",
						"config.style.separateProtDataStats",
				},
				new ParameterListener()
				{
					@Override
					public void
					parameterChanged(
						String	x )
					{
						separate_prot_data_stats = COConfigurationManager.getBooleanParameter("config.style.separateProtDataStats");
						data_stats_only			 = COConfigurationManager.getBooleanParameter("config.style.dataStatsOnly");
					}
				});

		MessageText.addAndFireListener(new MessageTextListener() {
			@Override
			public void localeChanged(Locale old_locale, Locale new_locale) {
				setUnits();
				loadMessages();
			}
		});

	}

  public static void
  setUnits()
  {
      // (1) http://physics.nist.gov/cuu/Units/binary.html
      // (2) http://www.isi.edu/isd/LOOM/documentation/unit-definitions.text

	units 		= new String[unitsStopAt + 1];
	units_bits 	= new String[unitsStopAt + 1];
    units_rate 	= new String[unitsStopAt + 1];

    if ( use_si_units ){
      all_units[UNIT_TB] = getUnit("TiB");
      all_units[UNIT_GB] = getUnit("GiB");
      all_units[UNIT_MB] = getUnit("MiB");
      all_units[UNIT_KB] = getUnit("KiB");
      all_units[UNIT_B]  = getUnit("B");

      	// fall through intentional

      switch (unitsStopAt) {
        case UNIT_TB:
         units[UNIT_TB] = all_units[UNIT_TB];
         units_bits[UNIT_TB] = getUnit("Tibit");
         units_rate[UNIT_TB] = (use_units_rate_bits) ? getUnit("Tibit")  : getUnit("TiB");
        case UNIT_GB:
          units[UNIT_GB]= all_units[UNIT_GB];
          units_bits[UNIT_GB]= getUnit("Gibit");
          units_rate[UNIT_GB] = (use_units_rate_bits) ? getUnit("Gibit")  : getUnit("GiB");
        case UNIT_MB:
          units[UNIT_MB] = all_units[UNIT_MB];
          units_bits[UNIT_MB] = getUnit("Mibit");
          units_rate[UNIT_MB] = (use_units_rate_bits) ? getUnit("Mibit")  : getUnit("MiB");
        case UNIT_KB:
          // can be upper or lower case k
          units[UNIT_KB] = all_units[UNIT_KB];
          units_bits[UNIT_KB] = getUnit("Kibit");

          // can be upper or lower case k, upper more consistent
          units_rate[UNIT_KB] = (use_units_rate_bits) ? getUnit("Kibit")  : getUnit("KiB");
        case UNIT_B:
          units[UNIT_B] =all_units[UNIT_B];
          units_bits[UNIT_B] = getUnit("bit");
          units_rate[UNIT_B] = (use_units_rate_bits)  ?   getUnit("bit")  :   getUnit("B");
      }
    }else{
      all_units[UNIT_TB] = getUnit("TB");
      all_units[UNIT_GB] = getUnit("GB");
      all_units[UNIT_MB] = getUnit("MB");
      all_units[UNIT_KB] = getUnit("kB");
      all_units[UNIT_B]  = getUnit("B");

      switch (unitsStopAt) {
        case UNIT_TB:
          units[UNIT_TB] = all_units[UNIT_TB];
          units_bits[UNIT_TB] = getUnit("Tbit");
          units_rate[UNIT_TB] = (use_units_rate_bits) ? getUnit("Tbit")  : getUnit("TB");
        case UNIT_GB:
          units[UNIT_GB]= all_units[UNIT_GB];
          units_bits[UNIT_GB]= getUnit("Gbit");
          units_rate[UNIT_GB] = (use_units_rate_bits) ? getUnit("Gbit")  : getUnit("GB");
        case UNIT_MB:
          units[UNIT_MB] = all_units[UNIT_MB];
          units_bits[UNIT_MB] = getUnit("Mbit");
          units_rate[UNIT_MB] = (use_units_rate_bits) ? getUnit("Mbit")  : getUnit("MB");
        case UNIT_KB:
          // yes, the k should be lower case
          units[UNIT_KB] = all_units[UNIT_KB];
          units_bits[UNIT_KB] = getUnit("kbit");
          units_rate[UNIT_KB] = (use_units_rate_bits) ? getUnit("kbit")  : getUnit("kB");
        case UNIT_B:
          units[UNIT_B] = all_units[UNIT_B];
          units_bits[UNIT_B] = getUnit("bit");
          units_rate[UNIT_B] = (use_units_rate_bits)  ?  getUnit("bit")  :  getUnit("B");
      }
    }


    per_sec = getResourceString( "Formats.units.persec", "/s" );

    units_base10 =
    	new String[]{
    		getUnit( use_units_rate_bits?"bit":"B"),
    		getUnit( use_units_rate_bits?"kbit":"KB"),
    		getUnit( use_units_rate_bits?"Mbit":"MB" ),
    		getUnit( use_units_rate_bits?"Gbit":"GB"),
    		getUnit( use_units_rate_bits?"Tbit":"TB" )};

    for (int i = 0; i <= unitsStopAt; i++) {
      units[i] 		= units[i];
      units_rate[i] = units_rate[i] + per_sec;
    }

	Arrays.fill( cached_number_formats, null );

	percentage_format = NumberFormat.getPercentInstance();
	percentage_format.setMinimumFractionDigits(1);
	percentage_format.setMaximumFractionDigits(1);

		 decimalSeparator = new DecimalFormatSymbols().getDecimalSeparator();
   }

	private static String
	getUnit(
		String	key )
	{
		String res = " " + getResourceString( "Formats.units." + key, key );

		return( res );
	}

	private static String	PeerManager_status_finished;
	private static String	PeerManager_status_finishedin;
	private static String	Formats_units_alot;
	private static String	discarded;
	private static String	ManagerItem_waiting;
	private static String	ManagerItem_initializing;
	private static String	ManagerItem_allocating;
	private static String	ManagerItem_checking;
	private static String	ManagerItem_finishing;
	private static String	ManagerItem_ready;
	private static String	ManagerItem_downloading;
	private static String	ManagerItem_swarmMerge;
	private static String	ManagerItem_seeding;
	private static String	ManagerItem_lightseeding;
	private static String	ManagerItem_superseeding;
	private static String	ManagerItem_stopping;
	private static String	ManagerItem_stopped;
	private static String	ManagerItem_paused;
	private static String	ManagerItem_queued;
	private static String	ManagerItem_error;
	private static String	ManagerItem_forced;
	private static String	ManagerItem_moving;

	private static String	yes;
	private static String	no;

	public static void
	loadMessages()
	{
		PeerManager_status_finished 	= getResourceString( "PeerManager.status.finished", "Finished" );
		PeerManager_status_finishedin	= getResourceString( "PeerManager.status.finishedin", "Finished in" );
		Formats_units_alot				= getResourceString( "Formats.units.alot", "A lot" );
		discarded						= getResourceString( "discarded", "discarded" );
		ManagerItem_waiting				= getResourceString( "ManagerItem.waiting", "waiting" );
		ManagerItem_initializing		= getResourceString( "ManagerItem.initializing", "initializing" );
		ManagerItem_allocating			= getResourceString( "ManagerItem.allocating", "allocating" );
		ManagerItem_checking			= getResourceString( "ManagerItem.checking", "checking" );
		ManagerItem_finishing			= getResourceString( "ManagerItem.finishing", "finishing" );
		ManagerItem_ready				= getResourceString( "ManagerItem.ready", "ready" );
		ManagerItem_downloading			= getResourceString( "ManagerItem.downloading", "downloading" );
		ManagerItem_swarmMerge			= getResourceString( "TableColumn.header.mergeddata", "swarm merge" );
		ManagerItem_seeding				= getResourceString( "ManagerItem.seeding", "seeding" );
		ManagerItem_superseeding		= getResourceString( "ManagerItem.superseeding", "superseeding" );
		ManagerItem_lightseeding		= getResourceString( "ManagerItem.lightseeding", "lightseeding" );
		ManagerItem_stopping			= getResourceString( "ManagerItem.stopping", "stopping" );
		ManagerItem_stopped				= getResourceString( "ManagerItem.stopped", "stopped" );
		ManagerItem_paused				= getResourceString( "ManagerItem.paused", "paused" );
		ManagerItem_queued				= getResourceString( "ManagerItem.queued", "queued" );
		ManagerItem_error				= getResourceString( "ManagerItem.error", "error" );
		ManagerItem_forced				= getResourceString( "ManagerItem.forced", "forced" );
		ManagerItem_moving				= getResourceString( "ManagerItem.moving", "moving" );
		yes								= getResourceString( "GeneralView.yes", "Yes" );
		no								= getResourceString( "GeneralView.no", "No" );
	}

	private static String
	getResourceString(
		String	key,
		String	def )
	{
		if ( message_text_state == 0 ){

				// this fooling around is to permit the use of this class in the absence of the (large) overhead
				// of resource bundles

			try{
				MessageText.class.getName();

				message_text_state	= 1;

			}catch( Throwable e ){

				message_text_state	= 2;
			}
		}

		if ( message_text_state == 1 ){

			return( MessageText.getString( key ));

		}else{

			return( def );
		}
	}

	public static String
	getYesNo(
		boolean	b )
	{
		return( b?yes:no );
	}

	public static String
	getRateUnit(
		int		unit_size )
	{
		return( units_rate[unit_size].substring(1, units_rate[unit_size].length()) );
	}
	public static String
	getUnit(
		int		unit_size )
	{
		return( units[unit_size].substring(1, units[unit_size].length()) );
	}

	public static String
	getRateUnitBase10(int unit_size) {
		return units_base10[unit_size] + per_sec;
	}

	public static String
	getUnitBase10(int unit_size) {
		return units_base10[unit_size];
	}

	public static boolean
	isRateUsingBits()
	{
		return( use_units_rate_bits );
	}

	public static String
	formatByteCountToKiBEtc(int n)
	{
		return( formatByteCountToKiBEtc((long)n));
	}

	public static String
	formatByteCountToKiBEtc(
		long n )
	{
		return( formatByteCountToKiBEtc( n, false, TRUNCZEROS_NO));
	}

	public static
	String formatByteCountToKiBEtc(
		long n, boolean bTruncateZeros )
	{
		return( formatByteCountToKiBEtc( n, false, bTruncateZeros ));
	}

	public static
	String formatByteCountToKiBEtc(
		long	n,
		boolean	rate,
		boolean bTruncateZeros)
	{
		return formatByteCountToKiBEtc(n, rate, bTruncateZeros, -1);
	}

	public static int
	getKinB()
	{
		return( force_si_values?1024:(use_si_units?1024:1000));
	}

	public static long
	getMinB()
	{
		long k = getKinB();
		
		return( k * k );
	}
	
	public static
	String formatByteCountToKiBEtc(
		long	n,
		boolean	rate,
		boolean bTruncateZeros,
		int precision)
	{
		double dbl = (rate && use_units_rate_bits) ? n * 8 : n;

	  	int unitIndex = UNIT_B;

        long	div = force_si_values?1024:(use_si_units?1024:1000);

	  	while (dbl >= div && unitIndex < unitsStopAt){

		  dbl /= div;
		  unitIndex++;
		}

	  if (precision < 0) {
	  	precision = UNITS_PRECISION[unitIndex];
	  }

	  // round for rating, because when the user enters something like 7.3kbps
		// they don't want it truncated and displayed as 7.2
		// (7.3*1024 = 7475.2; 7475/1024.0 = 7.2998;  trunc(7.2998, 1 prec.) == 7.2
	  //
		// Truncate for rest, otherwise we get complaints like:
		// "I have a 1.0GB torrent and it says I've downloaded 1.0GB.. why isn't
		//  it complete? waaah"

		return formatDecimal(dbl, precision, bTruncateZeros, rate)
				+ (rate ? units_rate[unitIndex] : units[unitIndex]);
	}

	public static
	String formatByteCountToKiBEtc(
			long	n,
			boolean	rate,
			boolean bTruncateZeros,
			int precision,
			int	minUnit )
	{
		double dbl = (rate && use_units_rate_bits) ? n * 8 : n;

		int unitIndex = UNIT_B;

		long	div = force_si_values?1024:(use_si_units?1024:1000);

		while (dbl >= div && unitIndex < unitsStopAt){

			dbl /= div;
			unitIndex++;
		}

		while( unitIndex < minUnit ){
			dbl /= div;
			unitIndex++;
		}
		if (precision < 0) {
			precision = UNITS_PRECISION[unitIndex];
		}

		// round for rating, because when the user enters something like 7.3kbps
		// they don't want it truncated and displayed as 7.2
		// (7.3*1024 = 7475.2; 7475/1024.0 = 7.2998;  trunc(7.2998, 1 prec.) == 7.2
		//
		// Truncate for rest, otherwise we get complaints like:
		// "I have a 1.0GB torrent and it says I've downloaded 1.0GB.. why isn't
		//  it complete? waaah"

		return formatDecimal(dbl, precision, bTruncateZeros, rate)
		+ (rate ? units_rate[unitIndex] : units[unitIndex]);
	}

	public static boolean
	isDataProtSeparate()
	{
		return( separate_prot_data_stats );
	}

	public static String
	formatDataProtByteCountToKiBEtc(
		long	data,
		long	prot )
	{
		if ( separate_prot_data_stats ){
			if ( data == 0 && prot == 0 ){
				return( formatByteCountToKiBEtc(0));
			}else if ( data == 0 ){
				return( "(" + formatByteCountToKiBEtc( prot) + ")");
			}else if ( prot == 0 ){
				return( formatByteCountToKiBEtc( data ));
			}else{
				return(formatByteCountToKiBEtc(data)+" ("+ formatByteCountToKiBEtc(prot)+")");
			}
		}else if ( data_stats_only ){
			return( formatByteCountToKiBEtc( data ));
		}else{
			return( formatByteCountToKiBEtc( prot + data ));
		}
	}

	public static String
	formatDataProtByteCountToKiBEtcPerSec(
		long	data,
		long	prot )
	{
		if ( separate_prot_data_stats ){
			if ( data == 0 && prot == 0 ){
				return(formatByteCountToKiBEtcPerSec(0));
			}else if ( data == 0 ){
				return( "(" + formatByteCountToKiBEtcPerSec( prot) + ")");
			}else if ( prot == 0 ){
				return( formatByteCountToKiBEtcPerSec( data ));
			}else{
				return(formatByteCountToKiBEtcPerSec(data)+" ("+ formatByteCountToKiBEtcPerSec(prot)+")");
			}
		}else if ( data_stats_only ){
			return( formatByteCountToKiBEtcPerSec( data ));
		}else{
			return( formatByteCountToKiBEtcPerSec( prot + data ));
		}
	}

	public static String
	formatByteCountToKiBEtcPerSec(
		long		n )
	{
		return( formatByteCountToKiBEtc(n,true,TRUNCZEROS_NO));
	}


    public static String
	formatByteCountToKiBEtcPerSec(
		long		n,
		boolean bTruncateZeros)
	{
		return( formatByteCountToKiBEtc(n,true, bTruncateZeros));
	}

		// base 10 ones

	public static String
	formatByteCountToBase10KBEtc(
			long n)
	{
		if ( use_units_rate_bits ){
			n *= 8;
		}

		if (n < 1000){

			return n + units_base10[UNIT_B];

		}else if (n < 1000 * 1000){

			return 	(n / 1000) + "." +
					((n % 1000) / 100) +
					units_base10[UNIT_KB];

		}else if ( n < 1000L * 1000L * 1000L  || not_use_GB_TB ){

			return 	(n / (1000L * 1000L)) + "." +
					((n % (1000L * 1000L)) / (1000L * 100L)) +
					units_base10[UNIT_MB];

		}else if (n < 1000L * 1000L * 1000L * 1000L){

			return (n / (1000L * 1000L * 1000L)) + "." +
					((n % (1000L * 1000L * 1000L)) / (1000L * 1000L * 100L))+
					units_base10[UNIT_GB];

		}else if (n < 1000L * 1000L * 1000L * 1000L* 1000L){

			return (n / (1000L * 1000L * 1000L* 1000L)) + "." +
					((n % (1000L * 1000L * 1000L* 1000L)) / (1000L * 1000L * 1000L* 100L))+
					units_base10[UNIT_TB];
		}else{

			return Formats_units_alot;
		}
	}

	public static String
	formatByteCountToBase10KBEtcPerSec(
			long		n )
	{
		return( formatByteCountToBase10KBEtc(n) + per_sec );
	}


    /**
     * Print the BITS/second in an international format.
     * @param n - always formatted using SI (i.e. decimal) prefixes
     * @return String in an internationalized format.
     * @deprecated Dunno who thought this was a good idea to use decimal calc but binary/decimal selected unit text
     */
    public static String
    formatByteCountToBitsPerSec(
        long n)
    {
        double dbl = n * 8;

        int unitIndex = UNIT_B;

        long	div = 1000;

        while (dbl >= div && unitIndex < unitsStopAt){

          dbl /= div;
          unitIndex++;
        }

        int  precision = UNITS_PRECISION[unitIndex];

        return( formatDecimal(dbl, precision, true, true) + units_bits[unitIndex] + per_sec );
    }
    
    /**
     * Prints byte value in BITS/second in either binary or decimal units as required
     * @param n byte count
     * @return
     */

    public static String
    formatByteCountToBitsPerSec2(
        long n)
    {
        double dbl = n * 8;

        int unitIndex = UNIT_B;

        long	div = getKinB();

        while (dbl >= div && unitIndex < unitsStopAt){

          dbl /= div;
          unitIndex++;
        }

        int  precision = UNITS_PRECISION[unitIndex];

        return( formatDecimal(dbl, precision, true, true) + units_bits[unitIndex] + per_sec );
    }
    
    public static String
    formatETA(long eta)
    {
    	return( formatETA( eta, false ));
    }

    private static final SimpleDateFormat abs_df = new SimpleDateFormat( "yyyy/MM/dd HH:mm:ss" );

    public static String
    formatETA(long eta,boolean abs )
    {
    	if (eta == 0) return PeerManager_status_finished;
    	if (eta == -1) return "";
    	if (eta > 0){
    		if ( abs && !(eta == Constants.CRAPPY_INFINITY_AS_INT || eta >= Constants.CRAPPY_INFINITE_AS_LONG )){

    			long now 	= SystemTime.getCurrentTime();
    			long then 	= now + eta*1000;

    			if ( eta > 5*60 ){

    				then = (then/(60*1000))*(60*1000);
    			}

     			String	str1;
      			String	str2;

      			synchronized( abs_df ){
      				str1 = abs_df.format(new Date( now ));
      				str2 = abs_df.format(new Date( then ));
      			}

      			int	len = Math.min(str1.length(), str2.length())-2;

      			int	diff_at = len;

      			for ( int i=0; i<len; i++){

      				char	c1 = str1.charAt( i );

      				if ( c1 != str2.charAt(i)){

      					diff_at = i;

      					break;
      				}
      			}

      			String	res;

      			if ( diff_at >= 11 ){

      				res = str2.substring( 11 );

      			}else if ( diff_at >= 5 ){

      				res = str2.substring( 5 );

      			}else{

      				res = str2;
      			}

      			return( res  );

    		}else{
    			return TimeFormatter.format(eta);
    		}
    	}

    	return PeerManager_status_finishedin + " " + TimeFormatter.format(eta * -1);
    }


	public static String
	formatDownloaded(
		DownloadManagerStats	stats )
	{
		long	total_discarded = stats.getDiscarded();
		long	total_received 	= stats.getTotalGoodDataBytesReceived();

		if(total_discarded == 0){

			return formatByteCountToKiBEtc(total_received);

		}else{

			return formatByteCountToKiBEtc(total_received) + " ( " +
					DisplayFormatters.formatByteCountToKiBEtc(total_discarded) + " " +
					discarded + " )";
		}
	}

	public static String
	formatHashFails(
		DownloadManager		download_manager )
	{
		TOTorrent	torrent = download_manager.getTorrent();

		if ( torrent != null ){

			long bad = download_manager.getStats().getHashFailBytes();

					// size can exceed int so ensure longs used in multiplication

			long count = bad / (long)torrent.getPieceLength();

			String result = count + " ( " + formatByteCountToKiBEtc(bad) + " )";

			return result;
	  	}

  		return "";
	}

	public static String
	formatDownloadStatus(
		DownloadManager		manager )
	{
		if ( manager == null ){

			return( ManagerItem_error + ": Download is null" );
		}

		int state = manager.getState();

		String	tmp = "";

		switch (state) {
			case DownloadManager.STATE_QUEUED:{
				
				long[]	mp = manager.getMoveProgress();
				
				if ( mp != null ){
					
					 tmp = ManagerItem_moving + ": "	+ formatPercentFromThousands( (int)mp[0] );
					 
				}else{	 

					int substate = manager.getSubState();
					
					if ( substate == DownloadManager.STATE_SEEDING ){
						
						tmp = ManagerItem_lightseeding;
						
					}else{
					
						tmp = ManagerItem_queued;
					}
				}
				
				break;
			}
			case DownloadManager.STATE_DOWNLOADING:{
				
				long[]	mp = manager.getMoveProgress();
				
				if ( mp != null ){
					
					 tmp = ManagerItem_moving + ": "	+ formatPercentFromThousands( (int)mp[0] );
					 
				}else{
					
					tmp = ManagerItem_downloading;
					
					if ( manager.isSwarmMerging()){
						
						tmp += " + " + ManagerItem_swarmMerge;
					}
				}
				break;
			}
			case DownloadManager.STATE_SEEDING:{

				DiskManager diskManager = manager.getDiskManager();

				if ( diskManager != null ){

					long[]	mp = diskManager.getMoveProgress();

					if ( mp != null ){

						tmp = ManagerItem_moving + ": "	+ formatPercentFromThousands( (int)mp[0] );

					}else{
						int done = diskManager.getCompleteRecheckStatus();

						if ( done != -1 ){

							// tmp = ManagerItem_seeding + " + " + ManagerItem_checking + ": "	+ formatPercentFromThousands(done);
							tmp = formatPercentFromThousands(done) + " " + ManagerItem_checking + "; " + ManagerItem_seeding;
						}
					}
				}

				if ( tmp == "" ){

					if (manager.getPeerManager() != null && manager.getPeerManager().isSuperSeedMode()) {

						tmp = ManagerItem_superseeding;

					}else{

						tmp = ManagerItem_seeding;
					}
				}

				break;
			}
			case DownloadManager.STATE_STOPPED:
				
				long[]	mp = manager.getMoveProgress();
				
				if ( mp != null ){
					
					 tmp = ManagerItem_moving + ": "	+ formatPercentFromThousands( (int)mp[0] );
					 
				}else{
					
					tmp = manager.isPaused() ? ManagerItem_paused : ManagerItem_stopped;

					String sr = manager.getStopReason();
					if ( sr != null && !sr.isEmpty()){
						if ( sr.startsWith( "{" ) && sr.endsWith( "}" )){
							sr = MessageText.getString( sr.substring( 1, sr.length() - 1 ));
						}
						tmp += " (" + sr + ")";
					}
				}
				
				break;

			case DownloadManager.STATE_ERROR:
				tmp = ManagerItem_error + ": " + manager.getErrorDetails();
				break;

			case DownloadManager.STATE_WAITING:
				tmp = ManagerItem_waiting;
				break;

			case DownloadManager.STATE_INITIALIZING:
				tmp = ManagerItem_initializing;
				break;

			case DownloadManager.STATE_INITIALIZED:
				tmp = ManagerItem_initializing;
				break;

			case DownloadManager.STATE_ALLOCATING:{
				tmp = ManagerItem_allocating;
				DiskManager diskManager = manager.getDiskManager();
				if (diskManager != null){
					tmp += ": " + formatPercentFromThousands( diskManager.getPercentAllocated());
				}
				break;
			}
			case DownloadManager.STATE_CHECKING:
				tmp = ManagerItem_checking + ": "
						+ formatPercentFromThousands(manager.getStats().getCompleted());
				break;

			case DownloadManager.STATE_FINISHING:
				tmp = ManagerItem_finishing;
				break;

			case DownloadManager.STATE_READY:
				tmp = ManagerItem_ready;
				break;

			case DownloadManager.STATE_STOPPING:
				tmp = ManagerItem_stopping;
				break;

			default:
				tmp = String.valueOf(state);
		}

		if (manager.isForceStart() &&
		    (state == DownloadManager.STATE_SEEDING ||
		     state == DownloadManager.STATE_DOWNLOADING))
			tmp = ManagerItem_forced + " " + tmp;
		return( tmp );
	}

	public static String
	formatDownloadStatusDefaultLocale(
		DownloadManager		manager )
	{
		int state = manager.getState();

		String	tmp = "";

		DiskManager	dm = manager.getDiskManager();

		switch (state) {
		  case DownloadManager.STATE_WAITING :
			tmp = MessageText.getDefaultLocaleString("ManagerItem.waiting");
			break;
		  case DownloadManager.STATE_INITIALIZING :
			  tmp = MessageText.getDefaultLocaleString("ManagerItem.initializing");
			  break;
		  case DownloadManager.STATE_INITIALIZED :
			  tmp = MessageText.getDefaultLocaleString("ManagerItem.initializing");
			  break;
		  case DownloadManager.STATE_ALLOCATING :
			tmp = MessageText.getDefaultLocaleString("ManagerItem.allocating");
			break;
		  case DownloadManager.STATE_CHECKING :
			tmp = MessageText.getDefaultLocaleString("ManagerItem.checking");
			break;
		  case DownloadManager.STATE_FINISHING :
		    tmp = MessageText.getDefaultLocaleString("ManagerItem.finishing");
		    break;
         case DownloadManager.STATE_READY :
			tmp = MessageText.getDefaultLocaleString("ManagerItem.ready");
			break;
		  case DownloadManager.STATE_DOWNLOADING :
			tmp = MessageText.getDefaultLocaleString("ManagerItem.downloading");
			if ( manager.isSwarmMerging()){
				tmp += " + " + MessageText.getDefaultLocaleString( "TableColumn.header.mergeddata" );
			}
			break;
		  case DownloadManager.STATE_SEEDING :
		  	if (dm != null && dm.getCompleteRecheckStatus() != -1 ) {
		  		int	done = dm.getCompleteRecheckStatus();

				if ( done == -1 ){
				  done = 1000;
				}

		  		//tmp = MessageText.getDefaultLocaleString("ManagerItem.seeding") + " + " +
		  		//		MessageText.getDefaultLocaleString("ManagerItem.checking") +
		  		//		": " + formatPercentFromThousands( done );
		  		
		  		tmp = formatPercentFromThousands( done ) + " " + 
		  				MessageText.getDefaultLocaleString("ManagerItem.checking") + "; " + 
		  				MessageText.getDefaultLocaleString("ManagerItem.seeding");
		  		
		  	}
		  	else if(manager.getPeerManager()!= null && manager.getPeerManager().isSuperSeedMode()){

		  		tmp = MessageText.getDefaultLocaleString("ManagerItem.superseeding");
		  	}
		  	else {
		  		tmp = MessageText.getDefaultLocaleString("ManagerItem.seeding");
		  	}
		  	break;
		  case DownloadManager.STATE_STOPPING :
		  	tmp = MessageText.getDefaultLocaleString("ManagerItem.stopping");
		  	break;
		  case DownloadManager.STATE_STOPPED :
			tmp = MessageText.getDefaultLocaleString(manager.isPaused()?"ManagerItem.paused":"ManagerItem.stopped");
			break;
		  case DownloadManager.STATE_QUEUED :
			tmp = MessageText.getDefaultLocaleString("ManagerItem.queued");
			break;
		  case DownloadManager.STATE_ERROR :
			tmp = MessageText.getDefaultLocaleString("ManagerItem.error").concat(": ").concat(manager.getErrorDetails()); //$NON-NLS-1$ //$NON-NLS-2$
			break;
			default :
			tmp = String.valueOf(state);
		}

		return( tmp );
	}

	public static String
	trimDigits(
		String		str,
		int			num_digits )
	{
		char[] 	chars 	= str.toCharArray();
		String 	res 	= "";
		int		digits 	= 0;

		for (int i=0;i<chars.length;i++){
			char c = chars[i];
			if ( Character.isDigit(c)){
				digits++;
				if ( digits <= num_digits ){
					res += c;
				}
			}else if ( c == '.' && digits >= 3 ){

			}else{
				res += c;
			}
		}

		return( res );
	}

  public static String formatPercentFromThousands(int thousands) {

    return percentage_format.format(thousands / 1000.0);
  }

  public static String formatTimeStamp(long time) {
    StringBuilder sb = new StringBuilder();
    Calendar calendar = Calendar.getInstance();
    calendar.setTimeInMillis(time);
    sb.append('[');
    sb.append(formatIntToTwoDigits(calendar.get(Calendar.DAY_OF_MONTH)));
    sb.append('.');
    sb.append(formatIntToTwoDigits(calendar.get(Calendar.MONTH)+1));	// 0 based
    sb.append('.');
    sb.append(calendar.get(Calendar.YEAR));
    sb.append(' ');
    sb.append(formatIntToTwoDigits(calendar.get(Calendar.HOUR_OF_DAY)));
    sb.append(':');
    sb.append(formatIntToTwoDigits(calendar.get(Calendar.MINUTE)));
    sb.append(':');
    sb.append(formatIntToTwoDigits(calendar.get(Calendar.SECOND)));
    sb.append(']');
    return sb.toString();
  }

  public static String formatIntToTwoDigits(int n) {
    return n < 10 ? "0".concat(String.valueOf(n)) : String.valueOf(n);
  }

  private static String formatDate(long date, String format) {
	  if (date == 0) {return "";}
	  SimpleDateFormat temp = new SimpleDateFormat(format);
	  return temp.format(new Date(date));
  }

  public static String formatDate(long date) {
  	return formatDate(date, "dd-MMM-yyyy HH:mm:ss");
  }

  public static String formatDateShort(long date) {
	  return formatDate(date, "MMM dd, HH:mm");
    }

  public static String formatDateNum(long date) {
	  return formatDate(date, "yyyy-MM-dd HH:mm:ss");
  }

  //
  // These methods will be exposed in the plugin API.
  //

  public static String formatCustomDateOnly(long date) {
	  if (date == 0) {return "";}
	  return formatDate(date, "dd-MMM-yyyy");
  }

  public static String formatCustomTimeOnly(long date) {
	  return formatCustomTimeOnly(date, true);
  }

  public static String formatCustomTimeOnly(long date, boolean with_secs) {
	  if (date == 0) {return "";}
	  return formatDate(date, (with_secs) ? "HH:mm:ss" : "HH:mm");
  }

  public static String formatCustomDateTime(long date) {
	  if (date == 0) {return "";}
	  return formatDate(date);
  }

  //
  // End methods
  //

  public static String
  formatTime(
    long    time )
  {
    return( TimeFormatter.formatColon( time / 1000 ));
  }

  /**
   * Format a real number to the precision specified.  Does not round the number
   * or truncate trailing zeros.
   *
   * @param value real number to format
   * @param precision # of digits after the decimal place
   * @return formatted string
   */
  public static String
  formatDecimal(
  	double value,
  	int		precision)
  {
  	return formatDecimal(value, precision, TRUNCZEROS_NO, ROUND_NO);
  }


  /**
   * Format a real number
   *
   * @param value real number to format
   * @param precision max # of digits after the decimal place
   * @param bTruncateZeros remove any trailing zeros after decimal place
   * @param bRound Whether the number will be rounded to the precision, or
   *                truncated off.
   * @return formatted string
   */
	public static String
	formatDecimal(
			double value,
			int precision,
			boolean bTruncateZeros,
			boolean bRound)
	{
		if (Double.isNaN(value) || Double.isInfinite(value)) {
			return Constants.INFINITY_STRING;
		}

		double tValue;
		if (bRound) {
			tValue = value;
		} else {
			// NumberFormat rounds, so truncate at precision
			if (precision == 0) {
				tValue = (long) value;
			} else {
				double shift = Math.pow(10, precision);
				tValue = ((long) (value * shift)) / shift;
			}
		}

		int cache_index = (precision << 2) + ((bTruncateZeros ? 1 : 0) << 1)
				+ (bRound ? 1 : 0);

		NumberFormat nf = null;

		if (cache_index < cached_number_formats.length) {
			nf = cached_number_formats[cache_index];
		}

		if (nf == null) {
			nf = NumberFormat.getNumberInstance();
			nf.setGroupingUsed(false); // no commas
			if (!bTruncateZeros) {
				nf.setMinimumFractionDigits(precision);
			}
			if (bRound) {
				nf.setMaximumFractionDigits(precision);
			}

			if (cache_index < cached_number_formats.length) {
				cached_number_formats[cache_index] = nf;
			}
		}

		return nf.format(tValue);
	}

  		/**
  		 * Attempts vaguely smart string truncation by searching for largest token and truncating that
  		 * @param str
  		 * @param width
  		 * @return
  		 */

  	public static String
	truncateString(
		String	str,
		int		width )
  	{
  		int	excess = str.length() - width;

  		if ( excess <= 0 ){

  			return( str );
  		}

  		excess += 3;	// for ...

  		int	token_start = -1;
  		int	max_len		= 0;
  		int	max_start	= 0;

  		for (int i=0;i<str.length();i++){

  			char	c = str.charAt(i);

  			if ( Character.isLetterOrDigit( c ) || c == '-' || c == '~' ){

  				if ( token_start == -1 ){

  					token_start	= i;

  				}else{

  					int	len = i - token_start;

  					if ( len > max_len ){

  						max_len		= len;
  						max_start	= token_start;
  					}
  				}
  			}else{

  				token_start = -1;
  			}
  		}

  		if ( max_len >= excess ){

  			int	trim_point = max_start + max_len;

  			return( str.substring( 0, trim_point - excess ) + "..." + str.substring( trim_point ));
  		}else{

  			return( str.substring( 0, width-3 ) + "..." );
  		}
  	}


	public static char getDecimalSeparator() {
		return decimalSeparator;
	}

	private static void
	updateFormatOverrides(
		String	formats )
	{
		Map<String,Formatter> map = new HashMap<>();

		String[] lines = formats.split( "\n" );

		List<String>	errors = new ArrayList<>();

		for ( String line: lines ){

			String error = null;

			line = line.trim();

			if ( line.length() == 0 ){

				continue;
			}

			String[] key_value = line.split( ":", 2 );

			if ( key_value.length != 2 ){

				error = "is missing ':'";

			}else{

				String	key 	= key_value[0].trim();
				String	value 	= key_value[1].trim();

				Formatter formatter = new Formatter();

				error = formatter.parse( value );

				if ( error == null ){

					map.put( key, formatter );
				}
			}

			if ( error != null ){

				errors.add( "'" + line + "' " + error );
			}
		}

		String status_msg;

		if ( errors.size() > 0 ){

			status_msg = "Format parsing failed: " + errors;

		}else{

			status_msg = "";
		}

		COConfigurationManager.setParameter( "config.style.formatOverrides.status", status_msg );

		format_map = map;
	}

	public static String
	formatCustomRate(
		String		key,
		long		value )
	{
		Formatter formatter = format_map.get( key );

		if ( formatter != null ){

			return( formatter.format( value, true ));
		}

		return( null );
	}

	public static String
	formatCustomSize(
		String		key,
		long		value )
	{
		Formatter formatter = format_map.get( key );

		if ( formatter != null ){

			return( formatter.format( value, false ));
		}

		return( null );
	}

	private static class
	Formatter
	{
		private final static int FORMAT_UNIT_B	= 0x0001;
		private final static int FORMAT_UNIT_K	= 0x0002;
		private final static int FORMAT_UNIT_M	= 0x0004;
		private final static int FORMAT_UNIT_G	= 0x0008;
		private final static int FORMAT_UNIT_T	= 0x0010;

		private final static int FORMAT_UNIT_NONE	= 0x0000;
		private final static int FORMAT_UNIT_ALL	= 0xffff;

		private final static int[] tens = { 1, 10, 100, 1000, 10000, 100000, 1000000 };

		private int 	unit_formats 	= FORMAT_UNIT_ALL;
		private boolean	hide_units		= false;
		private boolean	short_units		= false;
		private Boolean	rate_units		= null;

		private NumberFormat 	number_format 		= null;
		private long			number_format_fact	= 1;

		private int rounding = BigDecimal.ROUND_HALF_EVEN;	// decimal format default

		private String
		parse(
			String	str )
		{
			try{
				String[] args = str.split( "," );

				for ( String arg: args ){

					arg = arg.trim();

					if ( arg.length() == 0 ){

						continue;
					}

					String[] sub_args = arg.split( ";" );

					if ( sub_args.length == 0 ){

						return( "invalid argument '" + arg + "'" );
					}

					String main_arg = null;

					for ( String sub_arg: sub_args ){

						sub_arg = sub_arg.trim();

						String[] bits = sub_arg.split( "=" );

						if ( bits.length != 2 ){

							return( "invalid argument '" + arg + "'" );
						}

						String arg_name 	= bits[0].trim().toLowerCase( Locale.US );
						String arg_value	= bits[1].trim();

						if ( main_arg == null ){

							main_arg = arg_name;

							if ( main_arg.equals( "units" )){

								int	mask = arg_value.contains( "-" )?FORMAT_UNIT_ALL:FORMAT_UNIT_NONE;

								String[] units = arg_value.toLowerCase( Locale.US ).split( "&" );

								for ( String unit: units ){

									boolean	remove;

									if ( unit.startsWith( "-" )){

										unit = unit.substring(1);

										remove = true;

									}else{

										remove = false;
									}

									char c = unit.charAt(0);

									int	m;

									if ( c == 'b' ){

										m = FORMAT_UNIT_B;

									}else if ( c == 'k' ){

										m = FORMAT_UNIT_K;

									}else if ( c == 'm' ){

										m = FORMAT_UNIT_M;

									}else if ( c == 'g' ){

										m = FORMAT_UNIT_G;

									}else if ( c == 't' ){

										m = FORMAT_UNIT_T;

									}else{

										return( "Invalid unit: " + unit );
									}

									if ( remove ){

										mask = mask & ~m;

									}else{

										mask = mask | m;
									}
								}

								unit_formats = mask;

							}else if ( main_arg.equals( "format" )){

								number_format = NumberFormat.getInstance();

								if ( number_format instanceof DecimalFormat ){

									((DecimalFormat)number_format).applyPattern( arg_value );

								}else{

									Debug.out( "Number pattern isn't a DecimalFormat: " + number_format );
								}

								int max_fd = number_format.getMaximumFractionDigits();

								if ( max_fd < tens.length ){

									number_format_fact = tens[max_fd];

								}else{

									number_format_fact = 1;

									for (int i=0;i<max_fd;i++){

										number_format_fact *= 10;
									}
								}
							}else{

								Debug.out( "TODO: " + main_arg );
							}
						}else{

							if ( main_arg.equals( "units" )){

								if ( arg_name.equals( "hide" )){

									hide_units = arg_value.toLowerCase( Locale.US ).startsWith( "y" );

								}else if ( arg_name.equals( "short" )){

									short_units = arg_value.toLowerCase( Locale.US ).startsWith( "y" );

								}else if ( arg_name.equals( "rate" )){

									rate_units = arg_value.toLowerCase( Locale.US ).startsWith( "y" );

								}else{

									Debug.out( "TODO: " + arg_name );
								}
							}else if ( main_arg.equals( "format" )){

								if ( arg_name.equals( "round" )){

									String r = arg_value.toLowerCase( Locale.US );

									if ( r.equals( "up" )){

										rounding = BigDecimal.ROUND_UP;

									}else if ( r.equals( "down" )){

										rounding = BigDecimal.ROUND_DOWN;

									}else if ( r.equals( "halfup" )){

										rounding = BigDecimal.ROUND_HALF_UP;

									}else if ( r.equals( "halfdown" )){

										rounding = BigDecimal.ROUND_HALF_DOWN;

									}else{

										return( "Invald round mode: " + r );
									}
								}
							}else{

								Debug.out( "TODO: " + arg_name );
							}
						}
					}
				}

				return( null );

			}catch( Throwable e ){

				return( Debug.getNestedExceptionMessage( e ));
			}
		}

		private String
		format(
			long		_value,
			boolean		is_rate )
		{
			try{
				double value = _value;

				String	unit_str = "";

				if ( unit_formats == FORMAT_UNIT_K ){

					value = value/1024;

					unit_str = all_units[ UNIT_KB ];

				}else if ( unit_formats == FORMAT_UNIT_M ){

					value = value/(1024*1024);

					unit_str = all_units[ UNIT_MB ];

				}else if ( unit_formats == FORMAT_UNIT_G ){

					value = value/(1024*1024*1024L);

					unit_str = all_units[ UNIT_GB ];

				}else if ( unit_formats == FORMAT_UNIT_T ){

					value = value/(1024*1024*1024*1024L);

					unit_str = all_units[ UNIT_TB ];
				}

				String result;

				if ( number_format != null ){

					if ( rounding != BigDecimal.ROUND_HALF_EVEN ){

							// meh, DecimalFormat doesn't support rounding modes so we have to pre-calculate and hope

						double	l_value = (long)value;

						double	fraction = value - l_value;

						fraction *= number_format_fact;

						double l_fraction = (long)fraction;

						double rem = fraction - l_fraction;

						if ( rounding == BigDecimal.ROUND_DOWN ){

						}else if ( rounding == BigDecimal.ROUND_UP ){

							if ( rem > 0 ){

								l_fraction++;
							}
						}else if ( rounding == BigDecimal.ROUND_HALF_UP ){

							if ( rem >= 0.5 ){

								l_fraction++;
							}
						}else if ( rounding == BigDecimal.ROUND_HALF_DOWN ){

							if ( rem > 0.5 ){

								l_fraction++;
							}
						}

						l_fraction /= number_format_fact;

						//System.out.println( value + " -> " + l_value + "/" + l_fraction + "/" + rem );

						value = l_value + l_fraction;
					}

					synchronized( number_format ){

						result = number_format.format( value );
					}

				}else{

					result = String.valueOf( value );
				}

				if ( hide_units ){

					return( result );
				}

				if ( unit_str.length() > 0 ){

					if ( short_units ){

						result += " " + unit_str.charAt(1);

					}else{

						result += unit_str;
					}
				}

				if ( is_rate && ( rate_units == null || rate_units )){

					result += per_sec;
				}

				return( result );

			}catch( Throwable e ){

				Debug.out( e );

				return( String.valueOf( _value ));
			}
		}
	}

	private static int	share_ratio_progress_interval;
	
	static{
		COConfigurationManager.addAndFireParameterListener(
			"Share Ratio Progress Interval",
			(n)->{
				share_ratio_progress_interval = COConfigurationManager.getIntParameter(n);
			});
	}
	
	public static long[]
	getShareRatioProgressInfo(
		DownloadManager		dm )
	{
		int dm_state = dm.getState();

		long	next_eta = -1;

		if ( 	share_ratio_progress_interval > 0 &&
				( dm_state == DownloadManager.STATE_DOWNLOADING || dm_state == DownloadManager.STATE_SEEDING )){

			DownloadManagerStats stats = dm.getStats();

			long	downloaded 	= stats.getTotalGoodDataBytesReceived();
			long	uploaded 	= stats.getTotalDataBytesSent();

			if ( downloaded <= 0 ){

				next_eta = -2;

			}else{

				int current_sr = (int) ((1000 * uploaded) / downloaded);

				int	mult = current_sr / share_ratio_progress_interval;

				int	next_target_sr = (mult+1)*share_ratio_progress_interval;

				long	up_speed	= stats.getDataSendRate()==0?0:stats.getSmoothedDataSendRate();

				if ( up_speed <= 0 ){

					next_eta = -2;

				}else{

					if ( dm_state == DownloadManager.STATE_SEEDING ){

							// simple case

						long	target_upload = ( next_target_sr * downloaded ) / 1000;

						next_eta = ( target_upload - uploaded )/up_speed;

					}else{
							// more complex when downloading as we have to consider the fact that
							// at some point the download will complete and therefore download speed will
							// drop to 0

						DiskManager disk_man = dm.getDiskManager();

						if ( disk_man != null ){

							long	remaining = disk_man.getRemainingExcludingDND();

							long	down_speed	= (dm_state==DownloadManager.STATE_SEEDING||stats.getDataReceiveRate()==0)?0:stats.getSmoothedDataReceiveRate();

							if ( down_speed <= 0 || remaining <= 0 ){

									// same as if we are just seeding

								long	target_upload = ( next_target_sr * downloaded ) / 1000;

								next_eta = ( target_upload - uploaded )/up_speed;

							}else{

								/*
								 	time T until the target share ration is met is

								  		uploaded + ( T * upload_speed )
										------------------------------          = target_sr
										downloaded + ( T * download_speed )
								*/

								long time_to_sr = (( next_target_sr * downloaded )/1000 - uploaded ) / ( up_speed - ( down_speed * next_target_sr )/1000 );

								long time_to_completion = remaining / down_speed;

								if ( time_to_sr > 0 &&  time_to_sr <= time_to_completion ){

									next_eta = time_to_sr;

								}else{

										// basic calculation shows eta is > download complete time so we need
										// to refactor things

									long uploaded_at_completion 	= uploaded + ( up_speed * time_to_completion );
									long downloaded_at_completion 	= downloaded + ( down_speed * time_to_completion );

										// usual seeding calculation for time after completion

									long	target_upload = ( next_target_sr * downloaded_at_completion ) / 1000;

									next_eta = time_to_completion + ( target_upload - uploaded_at_completion )/up_speed;
								}
							}
						}else{

							next_eta = -2;
						}
					}
				}
			}
		}

		long data = dm.getDownloadState().getLongAttribute( DownloadManagerState.AT_SHARE_RATIO_PROGRESS );

		long	sr 			= (int)data;
		long	timestamp 	= (data>>>32)*1000;

		return( new long[]{ sr, timestamp, next_eta });
	}

 	// Used to test fractions and displayformatter.
  	// Keep until everything works okay.
  	public static void
  	main(String[] args)
  	{
  		// set decimal display to ","
  		//Locale.setDefault(Locale.GERMAN);

  		double d = 0.000003991630774821635;
  		NumberFormat nf =  NumberFormat.getNumberInstance();
  		nf.setMaximumFractionDigits(6);
  		nf.setMinimumFractionDigits(6);
  		String s = nf.format(d);

  		System.out.println("Actual: " + d);  // Displays 3.991630774821635E-6
  		System.out.println("NF/6:   " + s);  // Displays 0.000004
  		// should display 0.000003
			System.out.println("DF:     " + DisplayFormatters.formatDecimal(d , 6));
  		// should display 0
			System.out.println("DF 0:   " + DisplayFormatters.formatDecimal(d , 0));
  		// should display 0.000000
			System.out.println("0.000000:" + DisplayFormatters.formatDecimal(0 , 6));
  		// should display 0.001
			System.out.println("0.001:" + DisplayFormatters.formatDecimal(0.001, 6, TRUNCZEROS_YES, ROUND_NO));
  		// should display 0
			System.out.println("0:" + DisplayFormatters.formatDecimal(0 , 0));
  		// should display 123456
			System.out.println("123456:" + DisplayFormatters.formatDecimal(123456, 0));
  		// should display 123456
			System.out.println("123456:" + DisplayFormatters.formatDecimal(123456.999, 0));
			System.out.println(DisplayFormatters.formatDecimal(0.0/0, 3));
	}
}