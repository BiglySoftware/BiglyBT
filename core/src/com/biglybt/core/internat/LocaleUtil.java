/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
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

package com.biglybt.core.internat;

import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;

public class
LocaleUtil
{

  private static final String systemEncoding = System.getProperty("file.encoding");

  private static final String[] manual_charset = {
	systemEncoding,	// must be first entry due to code below that gets the system decoder
	"Big5","EUC-JP","EUC-KR","GB18030","GB2312","GBK","ISO-2022-JP","ISO-2022-KR",
	"Shift_JIS","KOI8-R",
	"TIS-620",	// added for bug #1008848
	Constants.DEFAULT_ENCODING,"windows-1251",Constants.BYTE_ENCODING
  };

	// the general ones *must* also be members of the above manual ones

  protected static final String[] generalCharsets = {
	Constants.BYTE_ENCODING, Constants.DEFAULT_ENCODING, systemEncoding
  };

   private static final LocaleUtil singleton = new LocaleUtil();

   public static LocaleUtil
   getSingleton()
   {
   	return( singleton );
   }

   private final LocaleUtilDecoder[] 	all_decoders;
   private final LocaleUtilDecoder[]	general_decoders;
   private LocaleUtilDecoder	system_decoder;
   private final LocaleUtilDecoder	fallback_decoder;



  private
  LocaleUtil()
  {
	List	decoders 		= new ArrayList();
  	List	decoder_names	= new ArrayList();

	for (int i = 0; i < manual_charset.length; i++) {
	   try {
		 String	name = manual_charset[i];

		 CharsetDecoder decoder = Charset.forName(name).newDecoder();

		 if ( decoder != null ){

			 LocaleUtilDecoder	lu_decoder =  new LocaleUtilDecoderReal(decoders.size(),decoder);

			 decoder_names.add( lu_decoder.getName());

			 if ( i == 0 ){

			 	system_decoder = lu_decoder;
			 }

			 decoders.add( lu_decoder );

		 }else if ( i == 0 ){

		 	Debug.out( "System decoder failed to be found!!!!" );
		 }

	   }catch (Exception ignore) {
	   }
	 }

	general_decoders = new LocaleUtilDecoder[generalCharsets.length];

	for (int i=0;i<general_decoders.length;i++){

		int	gi = decoder_names.indexOf( generalCharsets[i]);

		if ( gi != -1 ){

			general_decoders[i] = (LocaleUtilDecoder)decoders.get(gi);
		}
	}

	boolean show_all = COConfigurationManager.getBooleanParameter("File.Decoder.ShowAll" );

	if ( show_all ){

		Map m = Charset.availableCharsets();

		Iterator it = m.keySet().iterator();

		while(it.hasNext()){

			String	charset_name = (String)it.next();

			if ( !decoder_names.contains( charset_name)){

				try {
				  CharsetDecoder decoder = Charset.forName(charset_name).newDecoder();

				  if ( decoder != null ){

				  	LocaleUtilDecoder	lu_decoder = new LocaleUtilDecoderReal(decoders.size(),decoder);

				  	decoders.add( lu_decoder);

				  	decoder_names.add( lu_decoder.getName());
				  }

				} catch (Exception ignore) {
				}
			}
		}
	}

	fallback_decoder = new LocaleUtilDecoderFallback(decoders.size());

	decoders.add( fallback_decoder );

	all_decoders	= new LocaleUtilDecoder[ decoders.size()];

	decoders.toArray( all_decoders);
  }

  public String
  getSystemEncoding()
  {
  	return( systemEncoding );
  }

  public LocaleUtilDecoder[]
  getDecoders()
  {
  	return( all_decoders );
  }

    public LocaleUtilDecoder[]
	getGeneralDecoders()
	{
	   	return( general_decoders );
	}

  public LocaleUtilDecoder getFallBackDecoder() {
  	return fallback_decoder;
  }

  public LocaleUtilDecoder
  getSystemDecoder()
  {
  	return( system_decoder );
  }

  /**
   * Determine which locales are candidates for handling the supplied type of
   * string
   *
   * @param array String in an byte array
   * @return list of candidates.  Valid candidates have getDecoder() non-null
   */
  protected LocaleUtilDecoderCandidate[]
  getCandidates(
	byte[] array )
  {
	LocaleUtilDecoderCandidate[] candidates = new LocaleUtilDecoderCandidate[all_decoders.length];

	boolean show_less_likely_conversions = COConfigurationManager.getBooleanParameter("File.Decoder.ShowLax" );

	for (int i = 0; i < all_decoders.length; i++){

	  candidates[i] = new LocaleUtilDecoderCandidate(i);

	  try{
			LocaleUtilDecoder decoder = all_decoders[i];

			String str = decoder.tryDecode( array, show_less_likely_conversions );

			if ( str != null ){

				candidates[i].setDetails( decoder, str );
			}
	  } catch (Exception ignore) {

	  }
	}

	/*
	System.out.println( "getCandidates: = " + candidates.length );

	for (int i=0;i<candidates.length;i++){

		LocaleUtilDecoderCandidate	cand = candidates[i];

		if ( cand != null ){

			String	value = cand.getValue();

			if ( value != null ){

				System.out.println( cand.getDecoder().getName() + "/" + (value==null?-1:value.length()) + "/" + value );
			}
		}
	}
	*/

	return candidates;
  }

  /**
   * Determine which decoders are candidates for handling the supplied type of
   * string
   *
   * @param array String in a byte array
   * @return list of possibly valid decoders.  LocaleUtilDecoder
   */
  protected List
  getCandidateDecoders(
  	byte[]		array )
  {
  	LocaleUtilDecoderCandidate[] 	candidates = getCandidates( array );

  	List	decoders = new ArrayList();

  	for (int i=0;i<candidates.length;i++){

  		LocaleUtilDecoder	d = candidates[i].getDecoder();

  		if (d != null)
  			decoders.add(d);
  	}

  	return decoders;
  }

  /**
   *
   * @param array
   * @return List of LocaleUtilDecoderCandidate
   */
  protected List getCandidatesAsList(byte[] array) {
		LocaleUtilDecoderCandidate[] candidates = getCandidates(array);

		List candidatesList = new ArrayList();

		for (int i = 0; i < candidates.length; i++) {
			if (candidates[i].getDecoder() != null)
				candidatesList.add(candidates[i]);
		}

		return candidatesList;
	}

}